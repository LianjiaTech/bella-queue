package com.ke.bella.batch.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.batch.TaskExecutor;
import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.tables.pojos.QueueShardingDB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class QueueTaskCountUpdater {

    @Resource
    QueueRepo queueRepo;

    private final Cache<String, AtomicLong> deltas = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .removalListener(notification -> {
                String shardingKey = (String) notification.getKey();
                AtomicLong atomicValue = (AtomicLong) notification.getValue();
                flushSingle(shardingKey, atomicValue);
            })
            .build();

    public void increase(String shardingKey) {
        increase(shardingKey, 1L);
    }

    public void increase(String shardingKey, Long delta) {
        try {
            deltas.get(shardingKey, () -> new AtomicLong(0)).addAndGet(delta);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void flush() {
        deltas.asMap().forEach(this::flushSingle);
    }


    private void flushSingle(String shardingKey, AtomicLong atomicValue) {
        long delta = atomicValue.get();
        if(delta > 0) {
            queueRepo.increaseShardingCount(shardingKey, delta);
            atomicValue.addAndGet(-delta);
        }
    }

    public void trySharding() {
        List<QueueShardingDB> shardingList = queueRepo.findAllExcessiveSharding();
        if(shardingList.isEmpty()) {
            return;
        }
        shardingList.forEach(sharding -> {
            TaskExecutor.submit(() -> queueRepo.newSharding(sharding.getQueueTable(), sharding.getKey()));
        });
    }
}
