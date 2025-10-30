package com.ke.bella.batch.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.utils.TimeUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class QueueHeadUpdater {

    @Resource
    @Lazy
    private QueueRepo queueRepo;

    private final ConcurrentHashMap<String, WritePointer> writePointers = new ConcurrentHashMap<>();

    private final Cache<String, Map<String, AtomicLong>> deltas = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .removalListener(notification -> {
                String fullQueueName = (String) notification.getKey();
                @SuppressWarnings("unchecked")
                Map<String, AtomicLong> counters = (Map<String, AtomicLong>) notification.getValue();
                flushStat(fullQueueName, counters);
            })
            .build();

    private static final String PUT_CNT = "put";
    private static final String LOADED_CNT = "loaded";
    private static final String COMPLETED_CNT = "completed";

    public void updateWriteHead(String fullQueueName, String shardingKey, long lastWroteId) {
        writePointers.compute(fullQueueName, (key, currentPointer) -> {
            if(currentPointer == null || isNewerSharding(shardingKey, currentPointer.wroteShardingKey)) {
                return new WritePointer(shardingKey, new AtomicLong(lastWroteId));
            } else if(shardingKey.equals(currentPointer.wroteShardingKey)) {
                currentPointer.wroteId.updateAndGet(current -> Math.max(current, lastWroteId));
            }
            return currentPointer;
        });
    }

    private boolean isNewerSharding(String newShardingKey, String currentShardingKey) {
        LocalDateTime newTime = extractTimestamp(newShardingKey);
        LocalDateTime currentTime = extractTimestamp(currentShardingKey);
        return newTime.isAfter(currentTime);
    }

    private LocalDateTime extractTimestamp(String shardingKey) {
        int lastDashIndex = shardingKey.lastIndexOf('-');
        String timestampStr = shardingKey.substring(lastDashIndex + 1);
        return TimeUtils.parseTimestamp(timestampStr);
    }

    private void flushWriteHead(String fullQueueName, WritePointer value) {
        queueRepo.updateQueueHeadWroteId(fullQueueName, value.wroteShardingKey, value.wroteId.get());
    }

    public void flush() {
        flushWriteHeads();
        flushStats();
    }

    public void flushWriteHeads() {
        writePointers.forEach(this::flushWriteHead);
    }

    public void flushStats() {
        deltas.asMap().forEach(this::flushStat);
    }

    public void increasePutCnt(String fullQueueName, long delta) {
        increase(fullQueueName, PUT_CNT, delta);
    }

    public void increaseLoadedCnt(String fullQueueName, long delta) {
        increase(fullQueueName, LOADED_CNT, delta);
    }

    public void increaseCompletedCnt(String fullQueueName, long delta) {
        increase(fullQueueName, COMPLETED_CNT, delta);
    }

    private void increase(String fullQueueName, String type, long delta) {
        try {
            Map<String, AtomicLong> counters = deltas.get(fullQueueName, () -> {
                Map<String, AtomicLong> map = new HashMap<>();
                map.put(PUT_CNT, new AtomicLong(0));
                map.put(LOADED_CNT, new AtomicLong(0));
                map.put(COMPLETED_CNT, new AtomicLong(0));
                return map;
            });
            counters.get(type).addAndGet(delta);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void flushStatsForQueue(String fullQueueName) {
        Map<String, AtomicLong> counters = deltas.getIfPresent(fullQueueName);
        if(counters != null) {
            flushStat(fullQueueName, counters);
        }
    }

    private void flushStat(String fullQueueName, Map<String, AtomicLong> counters) {
        long putDelta = counters.get(PUT_CNT).get();
        long loadedDelta = counters.get(LOADED_CNT).get();
        long completedDelta = counters.get(COMPLETED_CNT).get();

        if(putDelta > 0 || loadedDelta > 0 || completedDelta > 0) {
            queueRepo.updateQueueStats(fullQueueName, putDelta, loadedDelta, completedDelta);
            counters.get(PUT_CNT).addAndGet(-putDelta);
            counters.get(LOADED_CNT).addAndGet(-loadedDelta);
            counters.get(COMPLETED_CNT).addAndGet(-completedDelta);
        }
    }

    @AllArgsConstructor
    private static class WritePointer {
        final String wroteShardingKey;
        final AtomicLong wroteId;
    }

}
