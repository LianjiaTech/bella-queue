package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.tables.pojos.TraceTaskShardingMappingDB;
import com.ke.bella.batch.tables.records.TraceTaskShardingMappingRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;

import static com.ke.bella.batch.Tables.TRACE_TASK_SHARDING_MAPPING;

@SuppressWarnings("all")
@Component
public class TraceTaskShardingMappingRepo implements BaseRepo {
    @Resource
    private DSLContext db;
    @Resource
    private QueueRepo queueRepo;
    @Resource
    private BatchRepo batchRepo;

    public void saveMapping(String traceId, String shardingKey) {
        TraceTaskShardingMappingDB existing = findByTraceIdAndShardingKey(traceId, shardingKey);
        if(existing != null) {
            return;
        }

        TraceTaskShardingMappingRecord rec = db.newRecord(TRACE_TASK_SHARDING_MAPPING);
        rec.setTraceId(traceId);
        rec.setShardingKey(shardingKey);

        db.insertInto(TRACE_TASK_SHARDING_MAPPING)
                .set(rec)
                .onDuplicateKeyIgnore()
                .execute();
    }

    public TraceTaskShardingMappingDB findByTraceIdAndShardingKey(String traceId, String shardingKey) {
        return db.selectFrom(TRACE_TASK_SHARDING_MAPPING)
                .where(TRACE_TASK_SHARDING_MAPPING.TRACE_ID.eq(traceId)
                        .and(TRACE_TASK_SHARDING_MAPPING.SHARDING_KEY.eq(shardingKey)))
                .fetchOneInto(TraceTaskShardingMappingDB.class);
    }

    public List<TraceTaskShardingMappingDB> findByTraceId(String traceId) {
        return db.selectFrom(TRACE_TASK_SHARDING_MAPPING)
                .where(TRACE_TASK_SHARDING_MAPPING.TRACE_ID.eq(traceId))
                .fetchInto(TraceTaskShardingMappingDB.class);
    }
}
