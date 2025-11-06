package com.ke.bella.batch.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.batch.db.repo.BatchRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class BatchCompleteCountUpdater {

    @Resource
    BatchRepo batchRepo;

    @Resource
    BatchService bs;

    private final Cache<String, AtomicLong> deltas = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .removalListener(notification -> {
                String batchId = (String) notification.getKey();
                AtomicLong counter = (AtomicLong) notification.getValue();
                flushCounter(batchId, counter);
            })
            .build();

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public void remove(String batchId) {
        deltas.invalidate(batchId);
        lockMap.remove(batchId);
    }

    public void increaseCompleteCount(String batchId, int delta) {
        try {
            ReentrantLock lock = lockMap.computeIfAbsent(batchId, k -> new ReentrantLock());
            lock.lock();
            try {
                AtomicLong counter = deltas.get(batchId, () -> new AtomicLong(0));
                counter.addAndGet(delta);
            } finally {
                lock.unlock();
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void flush() {
        deltas.asMap().forEach(this::flushCounter);
    }

    private void flushCounter(String batchId, AtomicLong counter) {
        ReentrantLock lock = lockMap.computeIfAbsent(batchId, k -> new ReentrantLock());
        lock.lock();
        try {
            long delta = counter.get();

            if(delta > 0) {
                batchRepo.writeProgress(batchId, (int) delta);
                counter.addAndGet(-delta);

                bs.stat(batchId);
            }
        } finally {
            lock.unlock();
        }
    }
}
