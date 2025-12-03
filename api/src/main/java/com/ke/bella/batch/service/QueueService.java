package com.ke.bella.batch.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.batch.RedisMesh;
import com.ke.bella.batch.TaskExecutor;
import com.ke.bella.batch.db.IDGenerator;
import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.enums.QueueLevel;
import com.ke.bella.batch.enums.ResponseMode;
import com.ke.bella.batch.enums.TakeStrategy;
import com.ke.bella.batch.tables.pojos.QueueDB;
import com.ke.bella.batch.tables.pojos.QueueMetadataDB;
import com.ke.bella.batch.utils.HttpUtils;
import com.ke.bella.batch.utils.JsonUtils;
import com.ke.bella.batch.utils.OpenapiUtils;
import com.ke.bella.batch.utils.TimeUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.metadata.Channel;
import com.ke.bella.queue.TaskEvent;
import com.theokanning.openai.queue.EventbusConfig;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ke.bella.batch.service.callback.BlockingCallback.BODY;

@Service
@Slf4j
@SuppressWarnings("all")
public class QueueService {

    @Resource
    private QueueRepo queueRepo;

    @Resource
    private JedisPool jedisPool;

    @Resource
    private RedisMesh redisMesh;

    @Resource
    private BatchService bs;

    @Resource
    private QueueTaskCountUpdater updater;

    @Resource
    private QueueHeadUpdater queueHeadUpdater;

    @Value("${bella.queue.redis.host}")
    private String redisHost;
    @Value("${bella.queue.redis.port}")
    private int redisPort;
    @Value("${bella.queue.redis.password:}")
    private String redisPassword;

    private static final String LOAD_LOCK_PREFIX = "queue:load:lock:";
    @Autowired
    private BatchService batchService;

    @PostConstruct
    @SuppressWarnings("all")
    public void init() {
        TaskExecutor.scheduleAtFixedRate(() -> updater.flush(), 30);
        TaskExecutor.scheduleAtFixedRate(() -> updater.trySharding(), 60 * 60);
        TaskExecutor.scheduleAtFixedRate(() -> queueHeadUpdater.flushWriteHeads(), 5);
        TaskExecutor.scheduleAtFixedRate(() -> queueHeadUpdater.flushStats(), 30);

        redisMesh.registerListener(TaskEvent.Completion.NAME, new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event event) {
                TaskEvent.Completion.Payload completion = TaskEvent.Completion.fromPayload(event.getPayload());
                Optional.ofNullable(TASK_RUNS.remove(completion.getTaskId()))
                        .ifPresent(callback -> callback.onCompletionEvent(completion));
            }
        });
        redisMesh.registerListener(TaskEvent.Progress.NAME, new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event event) {
                TaskEvent.Progress.Payload progress = TaskEvent.Progress.fromPayload(event.getPayload());
                Optional.ofNullable(TASK_RUNS.get(progress.getTaskId()))
                        .ifPresent(callback -> callback.onProgressEvent(progress));
            }
        });
    }

    public Task put(Put put) {
        if(StringUtils.isBlank(put.getQueue())) {
            put.setQueue(OpenapiUtils.exchangeQueueName(put.getEndpoint(), put.getData()));
        }
        if(StringUtils.isBlank(put.getQueue())) {
            throw new IllegalArgumentException("Queue config cannot be found");
        }
        QueueMetadataDB queueMeta = queueRepo.findMetadataByName(put.getQueue());
        String taskId = IDGenerator.newQueueTaskId(queueMeta.getId(), put.getQueueLevel(), put.getResponseMode());
        Task task = Task.builder().taskId(taskId).queue(put.getQueue())
                .level(put.getQueueLevel())
                .traceId(put.getTraceId())
                .endpoint(put.getEndpoint())
                .ak(BellaContext.getApikey().getApikey())
                .data(put.getData())
                .instanceId(Configs.INSTANCE_ID)
                .startTime(System.currentTimeMillis())
                .expireTime(TimeUtils.toEpochMilli(LocalDateTime.now().plusSeconds(put.getTaskTimeout())))
                .callbackUrl(put.getCallbackUrl())
                .responseMode(put.getResponseMode())
                .build();

        if(QueueLevel.isOfflineQueue(put.getFullQueueName())) {
            String shardingKey = queueRepo.saveTask(task);
            updater.increase(shardingKey);
        } else if(QueueLevel.isOnlineQueue(put.getFullQueueName())) {
            getQueue(put.getFullQueueName()).add(task);
        } else {
            throw new IllegalArgumentException("Unsupported response mode: " + put.getResponseMode());
        }

        return task;
    }

    @SuppressWarnings("all")
    public void cancel(String taskId) {
        QueueMetadataDB queueMetadata = queueRepo.findMetadataById(IDGenerator.parseQueueId(taskId));
        if(queueMetadata == null) {
            return;
        }

        Task task = Task.builder()
                .taskId(taskId)
                .queue(queueMetadata.getQueue())
                .level(IDGenerator.parseLevel(taskId))
                .build();
        getQueue(task.getFullQueueName()).remove(task);

        ResponseMode responseMode = IDGenerator.parseResponseMode(taskId);
        if(ResponseMode.isOfflineMode(responseMode.name())) {
            queueRepo.cancelTask(taskId);
        }
    }

    public Map<String, List<Task>> take(Take take) {
        TakeStrategy strategy = TakeStrategy.valueOf(take.getStrategy());

        List<Task> selectedTasks = strategy.take(
                take.getQueues(),
                take.getSize(),
                this::getQueue);

        Map<String, List<Task>> tasksByQueue = selectedTasks.stream()
                .collect(Collectors.groupingBy(Task::getFullQueueName));

        Collection<String> unselectedQueues = CollectionUtils.subtract(take.getQueues(), tasksByQueue.keySet());
        for (String unselectedQueue : unselectedQueues) {
            if(QueueLevel.isOfflineQueue(unselectedQueue) && getQueue(unselectedQueue).isEmpty()) {
                TaskExecutor.submit(() -> loadTasks(unselectedQueue));
            }
        }

        tasksByQueue.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(task -> {
                if((task.isExpire())) {
                    return true;
                }
                return StringUtils.isNotBlank(task.getTraceId())
                        && task.getTraceId().startsWith(IDGenerator.BATCH_PREFIX)
                        && batchService.isCanceled(task.getTraceId());
            });
            return entry.getValue().isEmpty();
        });

        return tasksByQueue;
    }

    public void complete(String taskId, Map<String, Object> result) {
        int level = IDGenerator.parseLevel(taskId);
        if(QueueLevel.isOnline(level)) {
            QueueMetadataDB queueMeta = queueRepo.findMetadataById(IDGenerator.parseQueueId(taskId));
            if(queueMeta == null) {
                return;
            }
            FullQueueName fullQueueName = new FullQueueName(queueMeta.getQueue(), level);
            RedisBlockingQueue queue = (RedisBlockingQueue) getQueue(fullQueueName.toString());
            Task task = queue.getTaskMetadata(taskId);
            if(task == null) {
                log.warn("Task metadata not found for online taskId: {}", taskId);
                return;
            }
            TaskExecutor.submit(() -> HttpUtils.postWithRetry(task.getCallbackUrl(), result));
            TaskExecutor.submitUsage(() -> reportUsage(task, result));
            queue.removeTaskMetadata(taskId);
            releaseSequentialLock(fullQueueName.toString(), taskId);
            return;
        }

        QueueDB task = queueRepo.findTask(taskId);
        boolean completed = queueRepo.completeTask(task, result);
        if(!completed) {
            return;
        }

        ResponseMode responseMode = IDGenerator.parseResponseMode(taskId);
        if(responseMode == ResponseMode.callback) {
            TaskExecutor.submit(() -> HttpUtils.postWithRetry(task.getCallbackUrl(), result));
        } else if(responseMode == ResponseMode.batch) {
            TaskExecutor.submit(() -> bs.writeResult(task, result));
        }
        TaskExecutor.submitUsage(() -> reportUsage(task, result));

        FullQueueName fullQueueName = new FullQueueName(task.getQueue(), level);
        queueHeadUpdater.increaseCompletedCnt(fullQueueName.toString(), 1L);
        releaseSequentialLock(fullQueueName.toString(), taskId);
    }

    private void reportUsage(QueueDB queueDB, Map<String, Object> result) {
        reportUsage(queueRepo.parseTask(queueDB), result);
    }

    private void reportUsage(Task task, Map<String, Object> result) {
        Channel channel = OpenapiUtils.getChannelByQueue(task.getQueue());
        if(channel == null) {
            return;
        }

        ApikeyInfo apikeyInfo = OpenapiUtils.getInstance().whoami(task.getAk());
        boolean isBatch = StringUtils.startsWith(task.getTraceId(), IDGenerator.BATCH_PREFIX);
        Map channelInfo = JsonUtils.fromJson(channel.getChannelInfo(), Map.class);
        String encodingType = MapUtils.getString(channelInfo, "encodingType");

        EndpointProcessData processData = EndpointProcessData.builder()
                .endpoint(task.getEndpoint())
                .akCode(apikeyInfo.getCode())
                .apikey(apikeyInfo.getApikey())
                .akSha(apikeyInfo.getAkSha())
                .channelCode(channel.getChannelCode())
                .supplier(channel.getSupplier())
                .model(task.getQueue())
                .protocol(channel.getProtocol())
                .forwardUrl(channel.getUrl())
                .priceInfo(channel.getPriceInfo())
                .requestId(task.getTaskId())
                .requestRaw(JsonUtils.toJson(task.getData()))
                .responseRaw(JsonUtils.toJson(result.get(BODY)))
                .batch(isBatch)
                .innerLog(true)
                .encodingType(encodingType)
                .bellaTraceId(task.getTaskId())
                .build();

        try {
            OpenapiUtils.getInstance().log(processData);
        } catch (Exception e) {
            log.error("Failed to report usage for taskId: {}", task.getTaskId(), e);
        }
    }

    private static final ExpiringMap<String, ITaskCallback> TASK_RUNS = ExpiringMap.builder()
            .variableExpiration()
            .maxSize(10240)
            .expiration(10, TimeUnit.MINUTES)
            .asyncExpirationListener((new ExpirationListener<String, ITaskCallback>() {
                @Override
                public void expired(String taskId, ITaskCallback callback) {
                    callback.onTimeout(taskId);
                }
            }))
            .build();

    public void registerTaskCallback(String taskId, ITaskCallback callback) {
        TASK_RUNS.put(taskId, callback, callback.getTimeout(), TimeUnit.SECONDS);
    }

    private final Cache<String, BlockingQueue<Task>> QUEUE_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(1024).build();

    @SneakyThrows
    public BlockingQueue<Task> getQueue(String queueName) {
        return QUEUE_CACHE.get(queueName, () -> {
            FullQueueName fullQueueName = FullQueueName.valueOf(queueName); // validate
            String baseQueueName = fullQueueName.getQueueName();
            int level = fullQueueName.getLevel();
            if(queueRepo.findMetadataByName(baseQueueName) == null) {
                throw new IllegalArgumentException("Queue not registered: " + queueName);
            }
            Integer capacity = QueueLevel.getCapacity(level);
            return new RedisBlockingQueue(queueName, capacity, jedisPool);
        });
    }

    public EventbusConfig getEventbusConfig() {
        StringBuilder redisUrl = new StringBuilder("redis://");
        if(!redisPassword.isEmpty()) {
            redisUrl.append(":").append(redisPassword).append("@");
        }
        redisUrl.append(redisHost).append(":").append(redisPort);
        redisUrl.append("/").append(0);

        EventbusConfig eventbusConfig = new EventbusConfig();
        eventbusConfig.setUrl(redisUrl.toString());
        eventbusConfig.setTopic(redisMesh.getPrivateTopicPrefix());
        return eventbusConfig;
    }

    private void releaseSequentialLock(String queueName, String taskId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String runningKey = "sequential:running:" + queueName;
            String runningTaskId = jedis.get(runningKey);

            if(taskId.equals(runningTaskId)) {
                jedis.del(runningKey);
            }
        } catch (Exception e) {
            log.error("Failed to release sequential lock for queue: {}, task: {}", queueName, taskId, e);
        }
    }

    private void loadTasks(String fullQueueName) {
        if(!queueRepo.hasMoreTask(fullQueueName)) {
            return;
        }

        String lockKey = LOAD_LOCK_PREFIX + fullQueueName;
        boolean lockAcquired = acquireLock(lockKey);
        if(!lockAcquired) {
            return;
        }

        try {
            queueRepo.loadTasks(fullQueueName, getQueue(fullQueueName));
        } finally {
            releaseLock(lockKey);
        }
    }

    private boolean acquireLock(String lockKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.setnx(lockKey, "1") == 1L && jedis.expire(lockKey, 60) == 1L;
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseLock(String lockKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(lockKey);
        } catch (Exception e) {
            log.error("Failed to release lock: {}", lockKey, e);
        }
    }

}
