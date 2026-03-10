package com.ke.bella.batch.service;

import com.google.common.collect.Maps;
import com.ke.bella.batch.RedisMesh;
import com.ke.bella.batch.TaskExecutor;
import com.ke.bella.batch.db.IDGenerator;
import com.ke.bella.batch.db.repo.BatchRepo;
import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.enums.BatchErrorType;
import com.ke.bella.batch.enums.BatchStatus;
import com.ke.bella.batch.enums.CompletionWindow;
import com.ke.bella.batch.enums.QueueLevel;
import com.ke.bella.batch.enums.ResponseMode;
import com.ke.bella.batch.enums.TaskStatus;
import com.ke.bella.batch.tables.pojos.BatchDB;
import com.ke.bella.batch.tables.pojos.QueueDB;
import com.ke.bella.batch.tables.pojos.QueueMetadataDB;
import com.ke.bella.batch.utils.FileUtils;
import com.ke.bella.batch.utils.JsonUtils;
import com.ke.bella.batch.utils.OpenapiUtils;
import com.ke.bella.batch.utils.TimeUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.queue.TaskWrapper;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Task;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@SuppressWarnings("all")
public class BatchService {

    @Resource
    private BatchRepo batchRepo;
    @Resource
    private QueueRepo queueRepo;
    @Resource
    private RedisMesh redisMesh;
    @Resource
    private QueueService queueService;
    @Resource
    private JedisPool jedisPool;

    @Resource
    private BatchCompleteCountUpdater batchCompleteCountUpdater;

    @Resource
    private MeterRegistry meterRegistry;

    private static final int FLUSH_THRESHOLD = 100;

    @Value("${batch.max.splitting:100}")
    private int maxSplittingBatches;

    private static final AtomicInteger SPLITTING_COUNTER = new AtomicInteger(0);

    private static final String REDIS_KEY_BATCH_CANCEL_STSTUS = "bella-batch:cancel:";

    @PostConstruct
    public void init() {
        redisMesh.registerListener("batch-expire-event", new RedisMesh.MessageListener() {
            @Override
            public void processMessage(RedisMesh.Event event) {
                doFinalize(event.getPayload(), BatchStatus.expired.name());
            }
        });

        TaskExecutor.scheduleAtFixedRate(() -> {
            batchRepo.findExpiredBatches().forEach(batchId -> {
                sendExpireEvent(batchId);
            });
        }, 10 * 60);

        TaskExecutor.scheduleAtFixedRate(() -> {
            batchCompleteCountUpdater.flush();
        }, 5);
    }

    private void sendExpireEvent(String batchId) {
        redisMesh.sendGroupMessage(RedisMesh.Event.builder()
                .name("batch-expire-event")
                .payload(batchId)
                .context(StringUtils.EMPTY)
                .build());
    }

    public Batch create(BatchRequest create, String queue) {
        QueueMetadataDB queueMeta = getQueueMeta(create.getEndpoint(), create.getInputFileId(), queue);
        String batchId = IDGenerator.newQueueBatchId(queueMeta.getId(), QueueLevel.L1.getLevel());
        Batch batch = batchRepo.saveBatch(create, batchId);
        queueService.put(Put.builder()
                .endpoint(StringUtils.EMPTY)
                .level(QueueLevel.L1.getLevel())
                .queue(Configs.BATCH_SPLIT_QUEUE_NAME)
                .callbackUrl(StringUtils.EMPTY)
                .data(Map.of("batchId", batchId))
                .timeout(CompletionWindow.toSeconds(create.getCompletionWindow()))
                .build());

        meterRegistry.counter("batch.create.total", "queue", queueMeta.getQueue()).increment();
        return batch;
    }

    public Batch cancel(String batchId) {
        TaskExecutor.submitBatch(() -> {
            doFinalize(batchId, BatchStatus.cancelled.name());
        });
        return batchRepo.findBatch(batchId);
    }

    public boolean isCanceled(String batchId) {
        String key = REDIS_KEY_BATCH_CANCEL_STSTUS + batchId;
        try (Jedis jedis = jedisPool.getResource()) {
            String status = jedis.get(key);
            if(status != null) {
                return "1".equals(status);
            }
        }

        Batch batch = batchRepo.findBatch(batchId);
        boolean isCancelled = BatchStatus.cancelled.name().equals(batch.getStatus());
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, 30 * 60, isCancelled ? "1" : "0");
        }
        return isCancelled;
    }

    public void split(TaskWrapper task) {
        String batchId = MapUtils.getString(task.getTask().getData(), "batchId");
        Map<String, Object> result = Maps.newHashMap();
        try {
            Long queueId = IDGenerator.parseQueueIdFromBatchId(batchId);
            BatchDB batch = batchRepo.findBatchDB(batchId);

            ApikeyInfo apikeyInfo = OpenapiUtils.getInstance().whoami(batch.getAk());
            BellaContext.setApikey(ApikeyInfo.builder().apikey(batch.getAk()).build());
            BellaContext.setOperator(Operator.builder()
                    .userId(apikeyInfo.getUserId())
                    .userName(apikeyInfo.getOwnerName())
                    .build());

            QueueMetadataDB queueMeta = queueRepo.findMetadataById(queueId);
            boolean setResult = batchRepo.setInprogress(batchId);
            if(!setResult) {
                return;
            }

            Path file = Configs.getBatchInputFile(batchId);
            if(Files.notExists(file)) {
                OpenapiUtils.download(batch.getInputFileId(), file);
            }

            doSplit(batch, file, queueMeta);

            // if errors equal total requests, set batch as completed
            Batch batchDetail = batchRepo.findBatch(batchId);
            if(batchDetail.isCompleted()) {
                doFinalize(batchId, BatchStatus.completed.name());
            }
            result.put("success", "done");
            meterRegistry.counter("batch.splitted.total", "queue", queueMeta.getQueue()).increment();
        } catch (Exception e) {
            log.error("Failed to split batch batchId: {}", batchId, e);
            batchRepo.setFailed(batchId);
            result.put("error", e.getMessage());
        } finally {
            BellaContext.clearAll();
            task.markComplete(result);
        }
    }

    @SneakyThrows
    private void doSplit(BatchDB batch, Path file, QueueMetadataDB queueMeta) {

        String filePath = file.toString();
        String batchId = batch.getBatchId();

        List<Task> tasks = new ArrayList<>();
        List<Task> failed = new ArrayList<>();

        long skips = batch.getRequestCountsTotal();

        AtomicLong lines = new AtomicLong(skips);

        FullQueueName fullQueueName = new FullQueueName(queueMeta.getQueue(), QueueLevel.L1.getLevel());
        FileUtils.processLines(filePath, skips, line -> {
            if(StringUtils.isEmpty(line)) {
                return;
            }

            try {
                Task task = createTask(batch, queueMeta, line, lines.get());
                tasks.add(task);
                meterRegistry.counter("queue.task.put.total", "queue", fullQueueName.toString()).increment();
            } catch (Exception e) {
                Task task = new Task();
                task.setTaskId(StringUtils.EMPTY);
                task.setCustomId(String.valueOf(lines.get()));
                failed.add(task);
            }

            lines.incrementAndGet();

            if(tasks.size() + failed.size() >= FLUSH_THRESHOLD) {
                flush(batchId, tasks, failed);
            }
        });

        flush(batchId, tasks, failed);
    }

    private void doFinalize(String batchId, String status) {
        if(!batchRepo.setFinalizing(batchId)) {
            return;
        }
        try {
            BatchDB batchDB = batchRepo.findBatchDB(batchId);

            mergeFiles(batchDB);
            batchRepo.updateFileIds(batchId, batchDB.getOutputFileId(), batchDB.getErrorFileId());

            if(BatchStatus.expired.name().equals(status)) {
                batchRepo.expireBatch(batchId);
            } else if(BatchStatus.completed.name().equals(status)) {
                batchRepo.completeBatch(batchId);
            } else if(BatchStatus.cancelled.name().equals(status)) {
                doCancel(batchId);
            }

            Long queueId = IDGenerator.parseQueueIdFromBatchId(batchId);
            QueueMetadataDB queueMeta = queueRepo.findMetadataById(queueId);
            meterRegistry.counter("batch.finalize.total", "queue"
                    , queueMeta.getQueue(), "status", status).increment();
        } finally {
            batchCompleteCountUpdater.remove(batchId);
            FileUtils.removeAll(Configs.getBatchDir(batchId));
        }
    }

    private void doCancel(String batchId) {
        boolean cancelled = batchRepo.cancelBatch(batchId);
        if(cancelled) {
            String key = REDIS_KEY_BATCH_CANCEL_STSTUS + batchId;
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(key, 30 * 60, "1");
            }
        }
    }

    private void flush(String batchId, List<Task> tasks, List<Task> failed) {
        Batch batch = batchRepo.findBatch(batchId);
        if(!tasks.isEmpty() || !failed.isEmpty()) {
            batchRepo.flush(batchId, tasks, failed);
            tasks.clear();
        }

        if(!failed.isEmpty()) {
            writeErrors(batchId, failed, BatchErrorType.PARSE_ERROR);
            failed.clear();
        }
    }

    private Task createTask(BatchDB batch, QueueMetadataDB queueMeta
            , String line, Long lineNum) {
        String taskId = IDGenerator.newQueueTaskId(
                queueMeta.getId(), QueueLevel.L1.getLevel(),
                ResponseMode.batch.name());

        Map lineMap = JsonUtils.fromJson(line, Map.class);
        Map<String, Object> data = MapUtils.getMap(lineMap, "body");
        String customId = MapUtils.getString(lineMap, "custom_id");

        return Task.builder()
                .taskId(taskId)
                .traceId(batch.getBatchId())
                .customId(customId)
                .status(TaskStatus.waiting.name())
                .queue(queueMeta.getQueue())
                .level(QueueLevel.L1.getLevel())
                .endpoint(batch.getEndpoint())
                .ak(batch.getAk())
                .expireTime(TimeUtils.toEpochMilli(batch.getExpiredAt()))
                .data(data)
                .responseMode(ResponseMode.batch.name())
                .build();
    }

    public void writeResult(QueueDB task, Map<String, Object> result) {
        String batchId = task.getBatchId();

        Map<String, Object> data = new HashMap<>();
        data.put("id", task.getTaskId());
        data.put("custom_id", task.getCustomId());
        data.put("response", result);

        String instanceId = Configs.INSTANCE_ID.replace(":", "_");
        String outputFileName = String.format("output_%s.jsonl", instanceId);
        Path outputPath = Configs.getBatchDir(batchId).resolve(outputFileName);
        FileUtils.FileWriter.writeToFile(outputPath.toString(), List.of(JsonUtils.toJson(data)));
    }

    public void stat(String batchId) {
        BatchDB batchDB = batchRepo.findBatchDB(batchId);
        Batch batch = batchRepo.toBatch(batchDB);
        if(BatchStatus.completed.name().equals(batch.getStatus())) {
            return;
        }

        if(batch.isCompleted()) {
            doFinalize(batchId, BatchStatus.completed.name());
        }
    }

    private void mergeFiles(BatchDB batchDB) {
        String batchId = batchDB.getBatchId();
        Path batchDir = Configs.getBatchDir(batchId);
        Path outputFile = Configs.getBatchOutputFile(batchId);
        Path errorFile = Configs.getBatchErrorFile(batchId);

        FileUtils.mergeFiles(batchDir, Configs.OUTPUT_PATTERN, outputFile);
        if(Files.exists(outputFile)) {
            String fileId = OpenapiUtils.uploadFile(outputFile, batchDB.getAk());
            batchDB.setOutputFileId(fileId);
        }

        FileUtils.mergeFiles(batchDir, Configs.ERROR_PATTERN, errorFile);
        if(Files.exists(errorFile)) {
            String errorFileId = OpenapiUtils.uploadFile(errorFile, batchDB.getAk());
            batchDB.setErrorFileId(errorFileId);
        }
    }

    private void writeErrors(String batchId, List<Task> tasks, BatchErrorType errorType) {
        if(tasks.isEmpty()) {
            return;
        }
        List<String> taskErrors = new ArrayList<>();

        for (Task task : tasks) {
            Map<String, String> error = new HashMap<>();
            error.put("code", errorType.getCode());
            error.put("message", errorType.getMessage());

            Map<String, Object> errorData = new HashMap<>();
            errorData.put("id", task.getTaskId());
            errorData.put("customId", task.getCustomId());
            errorData.put("error", error);
            taskErrors.add(JsonUtils.toJson(errorData));
        }

        String instanceId = Configs.INSTANCE_ID.replace(":", "_");
        String errorFileName = String.format("error_%s.jsonl", instanceId);
        Path errorPath = Configs.getBatchDir(batchId).resolve(errorFileName);
        FileUtils.FileWriter.writeToFile(errorPath.toString(), taskErrors);
    }

    private QueueMetadataDB getQueueMeta(String endpoint, String inputFileId, String queue) {
        if(StringUtils.isBlank(queue)) {
            queue = OpenapiUtils.peekFirstLine(inputFileId, BellaContext.getApikey().getApikey(),
                    firstLine -> {
                        Map lineMap = JsonUtils.fromJson(firstLine, Map.class);
                        Map<String, Object> dataMap = MapUtils.getMap(lineMap, "body");
                        return OpenapiUtils.exchangeQueueName(endpoint, dataMap, QueueLevel.L1.getLevel());
                    });
        }

        if(StringUtils.isBlank(queue)) {
            throw new IllegalStateException("Queue should not be empty");
        }
        return queueRepo.findMetadataByName(queue);
    }

}
