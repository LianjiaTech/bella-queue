package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.enums.BatchStatus;
import com.ke.bella.batch.enums.CompletionWindow;
import com.ke.bella.batch.service.QueueTaskCountUpdater;
import com.ke.bella.batch.tables.pojos.BatchDB;
import com.ke.bella.batch.tables.records.BatchRecord;
import com.ke.bella.batch.utils.JsonUtils;
import com.ke.bella.batch.utils.TimeUtils;
import com.ke.bella.openapi.BellaContext;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.batch.RequestCounts;
import com.theokanning.openai.queue.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ke.bella.batch.tables.Batch.BATCH;
import static com.ke.bella.batch.utils.EncryptUtils.decrypt;
import static com.ke.bella.batch.utils.EncryptUtils.encrypt;

@SuppressWarnings("all")
@Component
@Slf4j
public class BatchRepo implements BaseRepo {

    private static final String REDIS_KEY_BATCH_CANCEL_STSTUS = "bella-batch:cancel:";

    @Resource
    private DSLContext db;
    @Resource
    private QueueRepo queueRepo;
    @Resource
    private QueueTaskCountUpdater updator;
    @Resource
    private JedisPool jedisPool;

    @Transactional(rollbackFor = Exception.class)
    public Batch saveBatch(BatchRequest create, String batchId) {

        BatchRecord rec = db.newRecord(BATCH);

        rec.setBatchId(batchId);
        rec.setEndpoint(create.getEndpoint());
        rec.setAk(encrypt(BellaContext.getApikey().getApikey()));
        rec.setInputFileId(create.getInputFileId());
        rec.setCompletionWindow(create.getCompletionWindow());
        rec.setExpiredAt(CompletionWindow.calculateExpirationTime(LocalDateTime.now(), create.getCompletionWindow()));

        if(MapUtils.isNotEmpty(create.getMetadata())) {
            rec.setMatadata(JsonUtils.toJson(create.getMetadata()));
        }

        fillCreatorInfo(rec);

        db.insertInto(BATCH)
                .set(rec)
                .execute();

        return toBatch(rec.into(BatchDB.class));
    }

    public boolean setInprogress(String batchId) {
        int updated = db.update(BATCH)
                .set(BATCH.STATUS, BatchStatus.in_progress.name())
                .set(BATCH.IN_PROGRESS_AT, LocalDateTime.now())
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .and(BATCH.STATUS.eq(BatchStatus.validating.name()))
                .execute();
        return updated > 0;
    }

    public void setFailed(String batchId) {
        db.update(BATCH)
                .set(BATCH.STATUS, BatchStatus.failed.name())
                .set(BATCH.FAILED_AT, LocalDateTime.now())
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .and(BATCH.STATUS.in(BatchStatus.validating.name(), BatchStatus.in_progress.name()))
                .execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public void flush(String batchId, List<Task> tasks, List<Task> failed) {
        Batch batch = findBatch(batchId);
        if(BatchStatus.cancelled.name().equals(batch.getStatus())) {
            return;
        }

        if(!tasks.isEmpty()) {
            String shardingKey = queueRepo.saveTasks(tasks, batchId);
            updator.increase(shardingKey, Long.valueOf(tasks.size()));
        }

        db.update(BATCH)
                .set(BATCH.REQUEST_COUNTS_TOTAL, BATCH.REQUEST_COUNTS_TOTAL
                        .plus(tasks.size() + failed.size()))
                .set(BATCH.IMPORT_COUNTS_FAILED, BATCH.IMPORT_COUNTS_FAILED
                        .plus(failed.size()))
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .execute();
    }

    public void writeProgress(String batchId, int delta) {
        int affectedRows = db.update(BATCH)
                .set(BATCH.REQUEST_COUNTS_COMPLETED, BATCH.REQUEST_COUNTS_COMPLETED
                        .plus(delta))
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .and(BATCH.STATUS.eq(BatchStatus.in_progress.name()))
                .execute();
        if(affectedRows == 0) {
            log.warn("Failed to update progress for batch {}, possibly status changed", batchId);
        }
    }

    public boolean completeBatch(String batchId) {
        int updated = db.update(BATCH)
                .set(BATCH.STATUS, BatchStatus.completed.name())
                .set(BATCH.COMPLETED_AT, LocalDateTime.now())
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .and(BATCH.STATUS.eq(BatchStatus.finalizing.name()))
                .execute();
        return updated > 0;
    }

    public boolean expireBatch(String batchId) {
        int updated = db.update(BATCH)
                .set(BATCH.STATUS, BatchStatus.expired.name())
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .and(BATCH.STATUS.eq(BatchStatus.finalizing.name()))
                .execute();
        return updated > 0;
    }

    public boolean cancelBatch(String batchId) {
        int updated = db.update(BATCH)
                .set(BATCH.STATUS, BatchStatus.cancelled.name())
                .set(BATCH.MTIME, LocalDateTime.now())
                .set(BATCH.MU_NAME, BellaContext.getOperator().getUserName())
                .set(BATCH.MUID, BellaContext.getOperator().getUserId())
                .set(BATCH.CANCELLED_AT, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .and(BATCH.STATUS.in(BatchStatus.finalizing.name()))
                .execute();

        return updated > 0;
    }

    public void updateFileIds(String batchId, String outputFileId, String errorFileId) {
        db.update(BATCH)
                .set(BATCH.OUTPUT_FILE_ID, outputFileId)
                .set(BATCH.ERROR_FILE_ID, errorFileId)
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .execute();
    }

    public Batch findBatch(String batchId) {
        return Optional.ofNullable(findBatchDB(batchId))
                .map(this::toBatch)
                .orElse(null);
    }

    public BatchDB findBatchDB(String batchId) {
        BatchDB batchDB = db.selectFrom(BATCH)
                .where(BATCH.BATCH_ID.eq(batchId))
                .fetchOneInto(BatchDB.class);

        return Optional.ofNullable(batchDB)
                .map(batch -> {
                    batch.setAk(decrypt(batch.getAk()));
                    return batch;
                })
                .orElse(null);
    }

    public List<Batch> findBatches(String after, int limit) {
        return findBatchDBList(after, limit)
                .stream()
                .map(this::toBatch)
                .collect(Collectors.toList());
    }

    private List<BatchDB> findBatchDBList(String after, int limit) {
        var select = db.selectFrom(BATCH);
        Condition condition = DSL.trueCondition();

        if(after != null && !after.isEmpty()) {
            condition = condition.and(BATCH.BATCH_ID.gt(after));
        }

        return select.where(condition)
                .orderBy(BATCH.CTIME.asc())
                .limit(limit)
                .fetchInto(BatchDB.class)
                .stream()
                .peek(batchDB -> batchDB.setAk(decrypt(batchDB.getAk())))
                .collect(Collectors.toList());
    }

    public List<BatchDB> findValidatingBatches(int limit) {
        return db.selectFrom(BATCH)
                .where(BATCH.STATUS.eq(BatchStatus.validating.name()))
                .orderBy(BATCH.CTIME.desc())
                .limit(limit)
                .fetchInto(BatchDB.class)
                .stream()
                .peek(batchDB -> batchDB.setAk(decrypt(batchDB.getAk())))
                .collect(Collectors.toList());
    }

    public Batch toBatch(BatchDB batchDB) {
        Batch batch = new com.theokanning.openai.batch.Batch();
        batch.setId(batchDB.getBatchId());
        batch.setObject("batch");
        batch.setEndpoint(batchDB.getEndpoint());
        batch.setInputFileId(batchDB.getInputFileId());
        batch.setCompletionWindow(batchDB.getCompletionWindow());
        batch.setStatus(batchDB.getStatus());
        batch.setOutputFileId(batchDB.getOutputFileId());
        batch.setErrorFileId(batchDB.getErrorFileId());

        batch.setCreatedAt(TimeUtils.toEpochMilli(batchDB.getCtime()));
        batch.setInProgressAt(TimeUtils.toEpochMilli(batchDB.getInProgressAt()));
        batch.setCompletedAt(TimeUtils.toEpochMilli(batchDB.getCompletedAt()));
        batch.setFailedAt(TimeUtils.toEpochMilli(batchDB.getFailedAt()));
        batch.setExpiredAt(TimeUtils.toEpochMilli(batchDB.getExpiredAt()));
        batch.setCancellingAt(TimeUtils.toEpochMilli(batchDB.getCancellingAt()));
        batch.setCancelledAt(TimeUtils.toEpochMilli(batchDB.getCancelledAt()));
        batch.setFinalizingAt(TimeUtils.toEpochMilli(batchDB.getFinalizingAt()));

        RequestCounts requestCounts = new RequestCounts();
        requestCounts.setTotal(orZero(batchDB.getRequestCountsTotal()));
        requestCounts.setCompleted(orZero(batchDB.getRequestCountsCompleted()));
        requestCounts.setFailed(orZero(batchDB.getRequestCountsFailed()) + orZero(batchDB.getImportCountsFailed()));
        batch.setRequestCounts(requestCounts);

        return batch;
    }

    private Integer orZero(Long value) {
        return value != null ? value.intValue() : 0;
    }

    public List<String> findExpiredBatches() {
        return db.selectFrom(BATCH)
                .where(BATCH.STATUS.in(BatchStatus.validating.name(), BatchStatus.in_progress.name()))
                .and(BATCH.EXPIRED_AT.lessThan(LocalDateTime.now()))
                .fetch(BATCH.BATCH_ID);
    }

    public boolean setFinalizing(String batchId) {
        int updated = db.update(BATCH)
                .set(BATCH.STATUS, BatchStatus.finalizing.name())
                .set(BATCH.FINALIZING_AT, LocalDateTime.now())
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .and(BATCH.STATUS.in(BatchStatus.validating.name(), BatchStatus.in_progress.name()))
                .execute();
        return updated > 0;
    }

    public void incrementFailedCount(String batchId, int size) {
        db.update(BATCH)
                .set(BATCH.REQUEST_COUNTS_FAILED, BATCH.REQUEST_COUNTS_FAILED.plus(size))
                .set(BATCH.MTIME, LocalDateTime.now())
                .where(BATCH.BATCH_ID.eq(batchId))
                .execute();
    }

}
