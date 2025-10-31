package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.enums.QueueLevel;
import com.ke.bella.batch.enums.TaskStatus;
import com.ke.bella.batch.service.FullQueueName;
import com.ke.bella.batch.service.QueueTaskCountUpdater;
import com.ke.bella.batch.service.BatchCompleteCountUpdater;
import com.ke.bella.batch.service.QueueHeadUpdater;
import com.ke.bella.batch.tables.Queue;
import com.ke.bella.batch.tables.QueueSharding;
import com.ke.bella.batch.tables.pojos.QueueDB;
import com.ke.bella.batch.tables.pojos.QueueHeadDB;
import com.ke.bella.batch.tables.pojos.QueueMetadataDB;
import com.ke.bella.batch.tables.pojos.QueueShardingDB;
import com.ke.bella.batch.tables.records.QueueHeadRecord;
import com.ke.bella.batch.tables.records.QueueMetadataRecord;
import com.ke.bella.batch.tables.records.QueueRecord;
import com.ke.bella.batch.tables.records.QueueShardingRecord;
import com.ke.bella.batch.utils.EncryptUtils;
import com.ke.bella.batch.utils.JsonUtils;
import com.ke.bella.batch.utils.OpenapiUtils;
import com.ke.bella.batch.utils.TimeUtils;
import com.ke.bella.openapi.BellaContext;
import com.theokanning.openai.queue.Register;
import com.theokanning.openai.queue.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.ke.bella.batch.Tables.QUEUE;
import static com.ke.bella.batch.Tables.QUEUE_HEAD;
import static com.ke.bella.batch.Tables.QUEUE_SHARDING;
import static com.ke.bella.batch.db.IDGenerator.parseLevel;
import static com.ke.bella.batch.db.IDGenerator.parseLevelFromBatchId;
import static com.ke.bella.batch.db.IDGenerator.parseQueueId;
import static com.ke.bella.batch.db.IDGenerator.parseQueueIdFromBatchId;
import static com.ke.bella.batch.db.IDGenerator.parseResponseMode;
import static com.ke.bella.batch.db.IDGenerator.parseTimestamp;
import static com.ke.bella.batch.db.IDGenerator.parseTimestampFromBatchId;
import static com.ke.bella.batch.tables.QueueMetadata.QUEUE_METADATA;
import static com.ke.bella.batch.utils.OpenapiUtils.fetchStringFromFile;
import static org.jooq.impl.DSL.row;

@SuppressWarnings("all")
@Component
@Slf4j
public class QueueRepo implements BaseRepo {

    @Resource
    private DSLContext db;

    @Resource
    private QueueTaskCountUpdater updator;

    @Resource
    private BatchCompleteCountUpdater batchCompleteCountUpdater;

    @Resource
    private QueueHeadUpdater queueHeadUpdater;

    private static final Predicate<String> DATA_TOO_LARGE = input -> input.length() * 3 > 65535;

    @Value("${bella.queue.load.batch.size:100}")
    private int loadTaskBatchSize;

    @Autowired
    private BatchRepo batchRepo;

    @Autowired
    private TraceTaskShardingMappingRepo traceRepo;

    @Autowired
    private JedisPool jedisPool;

    private DSLContext db(String shardingKey) {
        return DSLContextHolder.get(shardingKey, db);
    }

    @Cacheable(value = "queueMetadata", key = "#queueName")
    public QueueMetadataDB findMetadataByName(String queueName) {
        return db.selectFrom(QUEUE_METADATA)
                .where(QUEUE_METADATA.QUEUE.eq(queueName))
                .fetchOneInto(QueueMetadataDB.class);
    }

    @Cacheable(value = "queueMetadata", key = "#queueId")
    public QueueMetadataDB findMetadataById(long queueId) {
        return db.selectFrom(QUEUE_METADATA)
                .where(QUEUE_METADATA.ID.eq(queueId))
                .fetchOneInto(QueueMetadataDB.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public String saveTask(Task task) {
        String shardingKey = findTargetSharding(task.getTaskId());

        QueueRecord record = buildQueueRecord(task);
        record = db(shardingKey).insertInto(Queue.QUEUE)
                .set(record)
                .returning(Queue.QUEUE.ID)
                .fetchOne();
        if(record == null) {
            throw new IllegalStateException("Failed to save task: " + task.getTaskId());
        }

        if(StringUtils.isNotBlank(task.getTraceId())) {
            traceRepo.saveMapping(task.getTraceId(), shardingKey);
        }
        QueueRecord finalRecord = record;
        queueHeadUpdater.updateWriteHead(task.getFullQueueName(), shardingKey, finalRecord.getId());
        queueHeadUpdater.increasePutCnt(task.getFullQueueName(), 1L);

        return shardingKey;
    }

    @Transactional(rollbackFor = Exception.class)
    public String saveTasks(List<Task> tasks, String batchId) {
        String shardingKey = findTargetShardingByBatchId(batchId);

        List<QueueRecord> records = tasks.stream()
                .map(task -> {
                    QueueRecord record = buildQueueRecord(task);
                    record.setBatchId(batchId);
                    return record;
                })
                .collect(Collectors.toList());

        Result<QueueRecord> result = db(shardingKey)
                .insertInto(QUEUE, QUEUE.TASK_ID, QUEUE.CUSTOM_ID, QUEUE.QUEUE_, QUEUE.ENDPOINT, QUEUE.AK, QUEUE.BATCH_ID,
                        QUEUE.INPUT_DATA, QUEUE.INPUT_FILE_ID, QUEUE.OUTPUT_DATA, QUEUE.CALLBACK_URL,
                        QUEUE.CUID, QUEUE.MUID, QUEUE.CU_NAME, QUEUE.MU_NAME, QUEUE.CTIME, QUEUE.MTIME, QUEUE.EXPIRED_AT, QUEUE.TRACE_ID)
                .valuesOfRows(records
                        .stream()
                        .map(r -> row(r.getTaskId(), r.getCustomId(), r.getQueue(), r.getEndpoint(), r.getAk(), r.getBatchId(),
                                r.getInputData(), r.getInputFileId(), r.getOutputData(), r.getCallbackUrl(),
                                r.getCuid(), r.getMuid(), r.getCuName(), r.getMuName(), r.getCtime(), r.getMtime(), r.getExpiredAt(), r.getTraceId()))
                        .collect(Collectors.toList()))
                .returning(QUEUE.ID)
                .fetch();
        if(result.isEmpty()) {
            throw new IllegalStateException("Failed to save tasks for batch: " + batchId);
        }

        String traceId = tasks.get(0).getTraceId();
        if(StringUtils.isNotBlank(traceId)) {
            traceRepo.saveMapping(traceId, shardingKey);
        }

        long maxId = result.getValues(QUEUE.ID).stream().max(Long::compareTo).orElse(0L);
        Task firstTask = tasks.get(0);
        queueHeadUpdater.updateWriteHead(firstTask.getFullQueueName(), shardingKey, maxId);
        queueHeadUpdater.increasePutCnt(firstTask.getFullQueueName(), tasks.size());

        return shardingKey;
    }

    private void switchWroteSharding(String queueTable, String lastKey, String newKey) {
        String lastShardingKey = String.format("%s-%s", queueTable, lastKey);
        String shardingKey = String.format("%s-%s", queueTable, newKey);

        db.update(QUEUE_HEAD)
                .set(QUEUE_HEAD.LAST_WROTE_SHARDING_KEY, shardingKey)
                .set(QUEUE_HEAD.LAST_WROTE_ID, 0L)
                .set(QUEUE_HEAD.MTIME, LocalDateTime.now())
                .where(QUEUE_HEAD.LAST_WROTE_SHARDING_KEY.eq(lastShardingKey))
                .execute();
    }

    private QueueRecord buildQueueRecord(Task task) {
        QueueRecord rec = Queue.QUEUE.newRecord();
        rec.setTaskId(task.getTaskId());
        rec.setEndpoint(task.getEndpoint());
        rec.setQueue(task.getQueue());
        rec.setAk(EncryptUtils.encrypt(task.getAk()));
        rec.setOutputData(StringUtils.EMPTY);
        rec.setExpiredAt(TimeUtils.fromEpochMilli(task.getExpireTime()));

        if(StringUtils.isNotBlank(task.getCustomId())) {
            rec.setCustomId(task.getCustomId());
        }

        if(StringUtils.isNotBlank(task.getCallbackUrl())) {
            rec.setCallbackUrl(task.getCallbackUrl());
        } else {
            rec.setCallbackUrl(StringUtils.EMPTY);
        }

        String inputData = JsonUtils.toJson(task.getData());
        if(DATA_TOO_LARGE.test(inputData)) {
            String fileId = OpenapiUtils.saveStringAsFile(inputData);
            rec.setInputFileId(fileId);
            rec.setInputData(StringUtils.EMPTY);
        } else {
            rec.setInputFileId(StringUtils.EMPTY);
            rec.setInputData(inputData);
        }

        if(StringUtils.isNotBlank(task.getTraceId())) {
            rec.setTraceId(task.getTraceId());
        } else {
            rec.setTraceId(StringUtils.EMPTY);
        }

        fillCreatorInfo(rec);

        return rec;
    }

    public void cancelTask(String taskId) {
        String shardingKey = findTargetSharding(taskId);

        db(shardingKey).update(Queue.QUEUE)
                .set(Queue.QUEUE.STATUS, TaskStatus.cancelled.name())
                .set(Queue.QUEUE.MTIME, LocalDateTime.now())
                .where(Queue.QUEUE.TASK_ID.eq(taskId))
                .and(Queue.QUEUE.STATUS.in(TaskStatus.waiting.name(), TaskStatus.queued.name()))
                .execute();
    }

    public void cancelTasks(String shardingKey, String traceId) {
        db(shardingKey).update(Queue.QUEUE)
                .set(Queue.QUEUE.STATUS, TaskStatus.cancelled.name())
                .set(Queue.QUEUE.MTIME, LocalDateTime.now())
                .set(Queue.QUEUE.MUID, BellaContext.getOperator().getUserId())
                .set(Queue.QUEUE.MU_NAME, BellaContext.getOperator().getUserName())
                .where(Queue.QUEUE.TRACE_ID.eq(traceId))
                .and(Queue.QUEUE.STATUS.in(TaskStatus.waiting.name(), TaskStatus.queued.name()))
                .execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(QueueDB task, Map<String, Object> result) {
        String taskId = task.getTaskId();
        String shardingKey = findTargetSharding(taskId);

        QueueRecord rec = Queue.QUEUE.newRecord();
        rec.setStatus(TaskStatus.succeeded.name());
        String resultJson = JsonUtils.toJson(result);
        if(DATA_TOO_LARGE.test(resultJson)) {
            String fileId = OpenapiUtils.saveStringAsFile(resultJson);
            rec.setOutputFileId(fileId);
        } else {
            rec.setOutputData(resultJson);
        }

        fillUpdatorInfo(rec);

        int resultCount = db(shardingKey).update(Queue.QUEUE)
                .set(rec)
                .where(Queue.QUEUE.TASK_ID.eq(taskId))
                .and(Queue.QUEUE.STATUS.eq(TaskStatus.queued.name()))
                .and(Queue.QUEUE.EXPIRED_AT.ge(LocalDateTime.now()))
                .execute();

        if(resultCount == 0) {
            return false;
        }
        if(StringUtils.isNotBlank(task.getBatchId())) {
            batchCompleteCountUpdater.increaseCompleteCount(task.getBatchId(), 1);
        }
        return true;
    }

    public QueueDB findTask(String taskId) {
        String shardingKey = findTargetSharding(taskId);

        return db(shardingKey).selectFrom(Queue.QUEUE)
                .where(Queue.QUEUE.TASK_ID.eq(taskId))
                .fetchOneInto(QueueDB.class);
    }

    private Task parseTask(QueueDB queueDB) {
        String taskId = queueDB.getTaskId();

        Task task = Task.builder()
                .taskId(taskId)
                .traceId(queueDB.getTraceId())
                .batchId(queueDB.getBatchId())
                .ak(EncryptUtils.decrypt(queueDB.getAk()))
                .endpoint(queueDB.getEndpoint())
                .queue(queueDB.getQueue())
                .level(parseLevel(taskId))
                .data(parseInputData(queueDB))
                .result(parseOutputData(queueDB))
                .status(queueDB.getStatus())
                .startTime(TimeUtils.toEpochMilli(queueDB.getCtime()))
                .expireTime(TimeUtils.toEpochMilli(queueDB.getExpiredAt()))
                .callbackUrl(queueDB.getCallbackUrl())
                .responseMode(parseResponseMode(taskId).name())
                .build();

        if(TaskStatus.succeeded.name().equals(queueDB.getStatus())) {
            long completedTime = TimeUtils.toEpochMilli(queueDB.getMtime());
            task.setCompletedTime(completedTime);
            task.setRunningTime(completedTime - task.getStartTime());
        }

        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public void register(Register register) {
        QueueMetadataRecord rec = db.newRecord(QUEUE_METADATA);
        rec.setQueue(register.getQueue());
        rec.setEndpoint(register.getEndpoint());
        fillCreatorInfo(rec);

        QueueMetadataDB metadataDB = db.insertInto(QUEUE_METADATA)
                .set(rec)
                .returning()
                .fetchOne()
                .into(QueueMetadataDB.class);
        if(metadataDB == null) {
            throw new IllegalStateException("Failed to register queue: " + register.getQueue());
        }

        Arrays.stream(QueueLevel.values())
                .filter(level -> level.isOffline())
                .forEach(level -> initSharding(metadataDB, level.getLevel()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void initSharding(QueueMetadataDB metadataDB, int level) {
        LocalDateTime keyTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String key = keyTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String queueTable = String.format("%d-%d", metadataDB.getId(), level);

        saveSharding(key, keyTime, queueTable, "");
        initQueueHead(metadataDB, level, key);
        db.execute(createTableSql(queueTable, key));
    }

    @Transactional(rollbackFor = Exception.class)
    public void newSharding(String queueTable, String lastKey) {
        LocalDateTime keyTime = LocalDateTime.now().plusMinutes(10L).truncatedTo(ChronoUnit.SECONDS);
        String key = keyTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        QueueShardingRecord rec = db.selectFrom(QUEUE_SHARDING)
                .where(QUEUE_SHARDING.QUEUE_TABLE.eq(queueTable))
                .and(QUEUE_SHARDING.LAST_KEY.eq(lastKey))
                .forUpdate()
                .fetchOne();
        if(rec != null) {
            return;
        }

        switchWroteSharding(queueTable, lastKey, key);
        saveSharding(key, keyTime, queueTable, lastKey);
        db.execute(createTableSql(queueTable, key));
    }

    private String createTableSql(String queueTable, String key) {
        return String.format("create table `%s_%s-%s` like `%s`", QUEUE.getName(), queueTable, key, QUEUE.getName());
    }

    private void saveSharding(String key, LocalDateTime keyTime, String queueTable, String lastKey) {
        QueueShardingRecord rec = db.newRecord(QUEUE_SHARDING);
        rec.setKey(key);
        rec.setKeyTime(keyTime);
        rec.setQueueTable(queueTable);
        rec.setLastKey(lastKey);
        fillCreatorInfo(rec);

        rec.insert();
    }

    private String findTargetSharding(String taskId) {
        String queueTable = parseQueueId(taskId) + "-" + parseLevel(taskId);
        LocalDateTime timestamp = parseTimestamp(taskId);
        return findTargetSharding(queueTable, timestamp);
    }

    public String findTargetShardingByBatchId(String batchId) {
        String queueTable = parseQueueIdFromBatchId(batchId) + "-" + parseLevelFromBatchId(batchId);
        return findTargetSharding(queueTable, parseTimestampFromBatchId(batchId));
    }

    private String findTargetSharding(String queueTable, LocalDateTime timestamp) {
        QueueShardingDB shard = db.selectFrom(QueueSharding.QUEUE_SHARDING)
                .where(QueueSharding.QUEUE_SHARDING.QUEUE_TABLE.eq(queueTable))
                .and(QueueSharding.QUEUE_SHARDING.KEY_TIME.le(timestamp))
                .orderBy(QueueSharding.QUEUE_SHARDING.KEY_TIME.desc())
                .limit(1)
                .fetchOneInto(QueueShardingDB.class);

        return Optional.ofNullable(shard)
                .map(s -> s.getQueueTable() + "-" + s.getKey())
                .orElseThrow(() -> new IllegalStateException("No sharding found for"));
    }

    private Map parseInputData(QueueDB queueDB) {
        return parseData(queueDB.getInputData(),
                fetchStringFromFile(queueDB.getInputFileId()), Map.class);
    }

    private String parseOutputData(QueueDB queueDB) {
        return parseData(queueDB.getOutputData(),
                fetchStringFromFile(queueDB.getOutputFileId()), String.class);
    }

    private <T> T parseData(String originData, String fileData, Class<T> clazz) {
        String data = StringUtils.isNotBlank(fileData) ? fileData : originData;
        if(StringUtils.isBlank(data)) {
            return null;
        }

        if(clazz == String.class) {
            return (T) data;
        }

        return JsonUtils.fromJson(data, clazz);
    }

    public void increaseShardingCount(String queueTableKey, long delta) {
        int idx = queueTableKey.lastIndexOf("-");
        String queueTable = queueTableKey.substring(0, idx);
        String key = queueTableKey.substring(idx + 1);
        db.update(QUEUE_SHARDING)
                .set(QUEUE_SHARDING.COUNT, QUEUE_SHARDING.COUNT.plus(delta))
                .set(QUEUE_SHARDING.MTIME, LocalDateTime.now())
                .where(QUEUE_SHARDING.QUEUE_TABLE.eq(queueTable))
                .and(QUEUE_SHARDING.KEY.eq(key))
                .execute();
    }

    public List<QueueShardingDB> findAllExcessiveSharding() {
        QueueSharding t1 = QUEUE_SHARDING.as("t1");
        QueueSharding q2 = QUEUE_SHARDING.as("q2");

        var subquery = db.select(q2.ID.max().as("max_id"))
                .from(q2)
                .groupBy(q2.QUEUE_TABLE)
                .asTable("t2");

        return db.select(t1.fields())
                .from(t1)
                .innerJoin(subquery)
                .on(t1.ID.eq(subquery.field("max_id", Long.class)))
                .where(t1.COUNT.ge(t1.MAX_COUNT))
                .fetchInto(QueueShardingDB.class);
    }

    public void initQueueHead(QueueMetadataDB queueMetadata, int level, String key) {
        String shardingKey = String.format("%d-%d-%s", queueMetadata.getId(), level, key);

        QueueHeadRecord rec = db.newRecord(QUEUE_HEAD);
        rec.setQueue(queueMetadata.getQueue());
        rec.setLevel(level);
        rec.setLastWroteShardingKey(shardingKey);
        rec.setLastWroteId(0L);
        rec.setLastScannedShardingKey(shardingKey);
        rec.setLastScannedId(0L);
        fillCreatorInfo(rec);

        if(rec.insert() == 0) {
            throw new IllegalStateException("Failed to save queue head for queue: " + queueMetadata.getQueue() + ", level: " + level);
        }
    }

    public QueueHeadDB findQueueHead(String fullQueueNameStr) {
        FullQueueName fullQueueName = FullQueueName.valueOf(fullQueueNameStr);
        String queueName = fullQueueName.getQueueName();
        int level = fullQueueName.getLevel();

        return db.selectFrom(QUEUE_HEAD)
                .where(QUEUE_HEAD.QUEUE.eq(queueName))
                .and(QUEUE_HEAD.LEVEL.eq(level))
                .fetchOneInto(QueueHeadDB.class);
    }

    private int collectTasks(String shardingKey, long lastScannedId, int limit,
            List<QueueDB> allTasks, Map<String, List<Long>> tasksBySharding) {
        List<QueueDB> tasks = pullTasks(shardingKey, lastScannedId, limit);
        if(!tasks.isEmpty()) {
            allTasks.addAll(tasks);
            tasksBySharding.put(shardingKey, tasks.stream().map(QueueDB::getId).collect(Collectors.toList()));
        }
        return tasks.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public void loadTasks(String fullQueueName, BlockingQueue<Task> queue) {
        QueueHeadDB queueHead = findQueueHead(fullQueueName);
        List<QueueDB> allTasks = new ArrayList<>();
        Map<String, List<Long>> tasksBySharding = new HashMap<>();

        String currentSharding = queueHead.getLastScannedShardingKey();
        collectTasks(currentSharding, queueHead.getLastScannedId(), loadTaskBatchSize, allTasks, tasksBySharding);

        if(allTasks.size() < loadTaskBatchSize) {
            String nextSharding = findNextSharding(queueHead);
            int remaining = loadTaskBatchSize - allTasks.size();
            if(nextSharding != null && collectTasks(nextSharding, 0L, remaining, allTasks, tasksBySharding) > 0) {
                currentSharding = nextSharding;
            }
        }

        if(allTasks.isEmpty()) {
            return;
        }

        List<Task> queueTasks = allTasks.parallelStream()
                .map(this::parseTask)
                .filter(task -> !task.isExpire() && !TaskStatus.cancelled.name().equals(task.getStatus()))
                .collect(Collectors.toList());

        if(queueTasks.isEmpty() || queue.addAll(queueTasks)) {
            tasksBySharding.forEach(this::batchUpdateToQueued);
            long lastScannedId = allTasks.get(allTasks.size() - 1).getId();
            moveScanHead(queueHead.getId(), currentSharding, lastScannedId);
            queueHeadUpdater.increaseLoadedCnt(fullQueueName, allTasks.size());
        }
    }

    public boolean hasMoreTask(String fullQueueNameStr) {
        FullQueueName fullQueueName = FullQueueName.valueOf(fullQueueNameStr);
        String queueName = fullQueueName.getQueueName();
        int level = fullQueueName.getLevel();

        QueueHeadDB queueHead = db.selectFrom(QUEUE_HEAD)
                .where(QUEUE_HEAD.QUEUE.eq(queueName))
                .and(QUEUE_HEAD.LEVEL.eq(level))
                .and(QUEUE_HEAD.LAST_WROTE_SHARDING_KEY.eq(QUEUE_HEAD.LAST_SCANNED_SHARDING_KEY))
                .and(QUEUE_HEAD.LAST_SCANNED_ID.ge(QUEUE_HEAD.LAST_WROTE_ID))
                .fetchOneInto(QueueHeadDB.class);
        return queueHead == null;
    }

    public String findNextSharding(QueueHeadDB queueHead) {
        QueueMetadataDB metadata = findMetadataByName(queueHead.getQueue());
        String queueTable = String.format("%d-%d", metadata.getId(), queueHead.getLevel());
        String currentKey = queueHead.getLastScannedShardingKey().substring(queueHead.getLastScannedShardingKey().lastIndexOf("-") + 1);

        QueueShardingDB nextSharding = db.selectFrom(QUEUE_SHARDING)
                .where(QUEUE_SHARDING.QUEUE_TABLE.eq(queueTable))
                .and(QUEUE_SHARDING.LAST_KEY.eq(currentKey))
                .fetchOneInto(QueueShardingDB.class);

        return Optional.ofNullable(nextSharding)
                .map(sharding -> String.format("%s-%s", queueTable, sharding.getKey()))
                .orElse(null);
    }

    public List<QueueDB> pullTasks(String shardingKey, long lastScannedId, int limit) {
        return db(shardingKey).selectFrom(QUEUE)
                .where(QUEUE.ID.gt(lastScannedId))
                .orderBy(QUEUE.ID.asc())
                .limit(limit)
                .fetchInto(QueueDB.class);
    }

    private void batchUpdateToQueued(String shardingKey, List<Long> enqueuedTasks) {
        db(shardingKey).update(QUEUE)
                .set(QUEUE.STATUS, TaskStatus.queued.name())
                .set(QUEUE.MTIME, LocalDateTime.now())
                .where(QUEUE.ID.in(enqueuedTasks))
                .and(QUEUE.STATUS.eq(TaskStatus.waiting.name()))
                .execute();
    }

    private void moveScanHead(long queueHeadId, String shardingKey, long lastScannedId) {
        db.update(QUEUE_HEAD)
                .set(QUEUE_HEAD.LAST_SCANNED_ID, lastScannedId)
                .set(QUEUE_HEAD.LAST_SCANNED_SHARDING_KEY, shardingKey)
                .set(QUEUE_HEAD.MTIME, LocalDateTime.now())
                .where(QUEUE_HEAD.ID.eq(queueHeadId))
                .execute();
    }

    public void updateQueueStats(String fullQueueName, long putDelta, long loadedDelta, long completedDelta) {
        FullQueueName queueName = FullQueueName.valueOf(fullQueueName);

        db.update(QUEUE_HEAD)
                .set(QUEUE_HEAD.MTIME, LocalDateTime.now())
                .set(QUEUE_HEAD.TOTAL_PUT_CNT, QUEUE_HEAD.TOTAL_PUT_CNT.plus(putDelta))
                .set(QUEUE_HEAD.TOTAL_LOADED_CNT, QUEUE_HEAD.TOTAL_LOADED_CNT.plus(loadedDelta))
                .set(QUEUE_HEAD.TOTAL_COMPLETED_CNT, QUEUE_HEAD.TOTAL_COMPLETED_CNT.plus(completedDelta))
                .where(QUEUE_HEAD.QUEUE.eq(queueName.getQueueName()))
                .and(QUEUE_HEAD.LEVEL.eq(queueName.getLevel()))
                .execute();
    }

    public void moveScanHeadToLatest(String fullQueueName) {
        QueueHeadDB queueHead = findQueueHead(fullQueueName);
        if(queueHead == null) {
            throw new IllegalArgumentException("Queue head not found for: " + fullQueueName);
        }

        Long currentUserId = BellaContext.getOperator().getUserId();
        if(!currentUserId.equals(queueHead.getCuid())) {
            throw new IllegalArgumentException("Access denied. You can only reset queues created by yourself");
        }

        db.update(QUEUE_HEAD)
                .set(QUEUE_HEAD.LAST_SCANNED_SHARDING_KEY, QUEUE_HEAD.LAST_WROTE_SHARDING_KEY)
                .set(QUEUE_HEAD.LAST_SCANNED_ID, QUEUE_HEAD.LAST_WROTE_ID)
                .set(QUEUE_HEAD.TOTAL_LOADED_CNT, QUEUE_HEAD.TOTAL_PUT_CNT)
                .set(QUEUE_HEAD.MUID, currentUserId)
                .set(QUEUE_HEAD.MU_NAME, BellaContext.getOperator().getUserName())
                .set(QUEUE_HEAD.MTIME, LocalDateTime.now())
                .where(QUEUE_HEAD.ID.eq(queueHead.getId()))
                .execute();
    }

    public List<QueueHeadDB> findAllQueueHeads() {
        return db.selectFrom(QUEUE_HEAD)
                .fetchInto(QueueHeadDB.class);
    }

    public int updateQueueHeadWroteId(String fullQueueName, String wroteShardingKey, long lastWroteId) {
        FullQueueName queueName = FullQueueName.valueOf(fullQueueName);
        return db.update(QUEUE_HEAD)
                .set(QUEUE_HEAD.LAST_WROTE_ID, lastWroteId)
                .set(QUEUE_HEAD.MTIME, LocalDateTime.now())
                .where(QUEUE_HEAD.QUEUE.eq(queueName.getQueueName()))
                .and(QUEUE_HEAD.LEVEL.eq(queueName.getLevel()))
                .and(QUEUE_HEAD.LAST_WROTE_SHARDING_KEY.eq(wroteShardingKey))
                .and(QUEUE_HEAD.LAST_WROTE_ID.lt(lastWroteId))
                .execute();
    }
}
