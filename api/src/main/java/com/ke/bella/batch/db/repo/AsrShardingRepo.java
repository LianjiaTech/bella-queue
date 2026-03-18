package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.tables.QueueSharding;
import com.ke.bella.batch.tables.pojos.QueueShardingDB;
import com.ke.bella.batch.tables.records.QueueShardingRecord;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static com.ke.bella.batch.Tables.QUEUE_SHARDING;
import static com.ke.bella.batch.db.IDGenerator.parseTimestamp;

@Component
@Slf4j
public class AsrShardingRepo implements BaseRepo {

    public static final String TABLE_NAME = "asr_tasks";
    private static final String QUEUE_TABLE = "asr";

    @Resource(name = "asrDslContext")
    private DSLContext db;

    @PostConstruct
    public void initSharding() {
        QueueShardingDB existing = db.selectFrom(QUEUE_SHARDING)
                .where(QUEUE_SHARDING.QUEUE_TABLE.eq(QUEUE_TABLE))
                .limit(1)
                .fetchOneInto(QueueShardingDB.class);
        if (existing != null) {
            return;
        }

        log.info("No ASR sharding found, initializing first shard");
        LocalDateTime keyTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String key = keyTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        saveSharding(key, keyTime, "");
        db.execute(createTableSql(key));
        log.info("ASR initial shard created - key={}", key);
    }

    public String findTargetSharding(String taskId) {
        LocalDateTime timestamp = parseTimestamp(taskId);

        QueueShardingDB shard = db.selectFrom(QueueSharding.QUEUE_SHARDING)
                .where(QueueSharding.QUEUE_SHARDING.QUEUE_TABLE.eq(QUEUE_TABLE))
                .and(QueueSharding.QUEUE_SHARDING.KEY_TIME.le(timestamp))
                .orderBy(QueueSharding.QUEUE_SHARDING.KEY_TIME.desc())
                .limit(1)
                .fetchOneInto(QueueShardingDB.class);

        return Optional.ofNullable(shard)
                .map(s -> s.getQueueTable() + "-" + s.getKey())
                .orElseThrow(() -> new IllegalStateException("No ASR sharding found for taskId: " + taskId));
    }

    @Transactional(transactionManager = "asrTransactionManager", rollbackFor = Exception.class)
    public void newSharding(String lastKey) {
        LocalDateTime keyTime = LocalDateTime.now().plusMinutes(10L).truncatedTo(ChronoUnit.SECONDS);
        String key = keyTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        QueueShardingRecord rec = db.selectFrom(QUEUE_SHARDING)
                .where(QUEUE_SHARDING.QUEUE_TABLE.eq(QUEUE_TABLE))
                .and(QUEUE_SHARDING.LAST_KEY.eq(lastKey))
                .forUpdate()
                .fetchOne();
        if (rec != null) {
            return;
        }

        saveSharding(key, keyTime, lastKey);
        db.execute(createTableSql(key));
        log.info("ASR new shard created - key={}, lastKey={}", key, lastKey);
    }

    public void increaseShardingCount(String shardingKey, long delta) {
        int idx = shardingKey.lastIndexOf("-");
        String queueTable = shardingKey.substring(0, idx);
        String key = shardingKey.substring(idx + 1);
        db.update(QUEUE_SHARDING)
                .set(QUEUE_SHARDING.COUNT, QUEUE_SHARDING.COUNT.plus(delta))
                .set(QUEUE_SHARDING.MTIME, LocalDateTime.now())
                .where(QUEUE_SHARDING.QUEUE_TABLE.eq(queueTable))
                .and(QUEUE_SHARDING.KEY.eq(key))
                .execute();
    }

    public List<QueueShardingDB> findExcessiveSharding() {
        QueueSharding t1 = QUEUE_SHARDING.as("t1");
        QueueSharding q2 = QUEUE_SHARDING.as("q2");

        var subquery = db.select(q2.ID.max().as("max_id"))
                .from(q2)
                .where(q2.QUEUE_TABLE.eq(QUEUE_TABLE))
                .groupBy(q2.QUEUE_TABLE)
                .asTable("t2");

        return db.select(t1.fields())
                .from(t1)
                .innerJoin(subquery)
                .on(t1.ID.eq(subquery.field("max_id", Long.class)))
                .where(t1.COUNT.ge(t1.MAX_COUNT))
                .fetchInto(QueueShardingDB.class);
    }

    private void saveSharding(String key, LocalDateTime keyTime, String lastKey) {
        QueueShardingRecord rec = db.newRecord(QUEUE_SHARDING);
        rec.setKey(key);
        rec.setKeyTime(keyTime);
        rec.setQueueTable(QUEUE_TABLE);
        rec.setLastKey(lastKey);
        fillCreatorInfo(rec);
        rec.insert();
    }

    private String createTableSql(String key) {
        return String.format("CREATE TABLE IF NOT EXISTS `%s_%s-%s` LIKE `%s`",
                TABLE_NAME, QUEUE_TABLE, key, TABLE_NAME);
    }
}
