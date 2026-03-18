package com.ke.bella.batch.service;

import com.ke.bella.batch.enums.QueueLevel;
import com.ke.bella.batch.utils.EncryptUtils;
import com.ke.bella.batch.utils.JsonUtils;
import com.theokanning.openai.queue.Task;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisBlockingQueue implements BlockingQueue<Task> {

    private final Integer capacity;
    private final String queueName;
    @Getter
    private final JedisPool jedisPool;
    @Getter
    private final String taskMetadataKey;

    public RedisBlockingQueue(String queueName, Integer capacity, JedisPool jedisPool) {
        int finalCapacity = (capacity != null) ? capacity : Integer.MAX_VALUE;
        if(finalCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + finalCapacity);
        }

        this.queueName = queueName;
        this.capacity = finalCapacity;
        this.jedisPool = jedisPool;
        this.taskMetadataKey = queueName + ":metadata:";
    }

    @Override
    public boolean add(@NotNull Task task) {
        if(offer(task)) {
            return true;
        } else {
            throw new IllegalStateException("Queue full");
        }
    }

    @Override
    public boolean offer(@NotNull Task task) {
        long currentSize = size();
        if(currentSize >= capacity) {
            return false;
        }
        return enqueue(task);
    }

    @Override
    public Task remove() {
        return null;
    }

    @Override
    public Task poll() {
        long currentSize = size();
        if(currentSize == 0) {
            return null;
        }
        return dequeue();
    }

    @Override
    public Task element() {
        return null;
    }

    @Override
    public Task peek() {
        return null;
    }

    @Override
    public void put(@NotNull Task task) throws InterruptedException {

    }

    @Override
    public boolean offer(Task task, long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return false;
    }

    @NotNull
    @Override
    public Task take() throws InterruptedException {
        return null;
    }

    @Nullable
    @Override
    public Task poll(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return null;
    }

    @Override
    public int remainingCapacity() {
        return capacity - size();
    }

    @Override
    public boolean remove(Object o) {
        if(!(o instanceof Task)) {
            return false;
        }
        Task task = (Task) o;
        try {
            LuaManager.execute(jedisPool, "remove"
                    , List.of(queueName, task.getTaskId()));
            return true;
        } catch (Exception e) {
            log.error("Failed to remove task from Redis: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Task> tasks) {
        if(tasks.isEmpty() || tasks.size() > remainingCapacity()) {
            return false;
        }

        return batchEnqueue(tasks);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {
        try {
            LuaManager.execute(jedisPool, "clear", List.of(queueName));
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear queue: " + e.getMessage(), e);
        }
    }

    @Override
    public int size() {
        return Math.toIntExact(getRedisQueueSize());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @NotNull
    @Override
    public Iterator<Task> iterator() {
        return null;
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return null;
    }

    @Override
    public int drainTo(@NotNull Collection<? super Task> c) {
        return 0;
    }

    @Override
    public int drainTo(@NotNull Collection<? super Task> c, int maxElements) {
        return 0;
    }

    private long getRedisQueueSize() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.zcard(queueName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get queue size: " + e.getMessage(), e);
        }
    }

    private boolean enqueue(Task task) {
        task.setAk(EncryptUtils.encrypt(task.getAk()));

        try {
            List<String> args = List.of(
                    queueName,
                    task.getTaskId(),
                    JsonUtils.toJson(task),
                    String.valueOf(task.getStartTime()),
                    String.valueOf(capacity),
                    String.valueOf(QueueLevel.isOnlineQueue(queueName) ? Configs.ONLINE_QUEUE_TTL
                            : Configs.OFFLINE_QUEUE_TTL)
            );

            Object result = LuaManager.execute(jedisPool, getLuaModule(),
                    "enqueue", args);

            return (Long) result == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean batchEnqueue(Collection<? extends Task> tasks) {
        try (Jedis jedis = jedisPool.getResource()) {
            var pipeline = jedis.pipelined();
            int ttl = Configs.OFFLINE_QUEUE_TTL;
            for (Task task : tasks) {
                task.setAk(EncryptUtils.encrypt(task.getAk()));
                pipeline.setex(taskMetadataKey + task.getTaskId(), ttl, JsonUtils.toJson(task));
                pipeline.zadd(queueName, task.getStartTime(), task.getTaskId());
            }
            pipeline.expire(queueName, ttl);

            pipeline.sync();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Task dequeue() {
        try {
            Object result = LuaManager.execute(jedisPool, getLuaModule(),
                    "dequeue", List.of(queueName));
            if(result == null) {
                return null;
            }

            List<String> resultList = (List<String>) result;
            String taskId = resultList.get(0);
            String taskJson = resultList.get(1);

            if(taskJson == null) {
                log.warn("Task metadata not found for taskId: {} ", taskId);
                return null;
            }

            return parseTask(taskJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to dequeue task from Redis: " + e.getMessage(), e);
        }
    }

    public Task getTaskMetadata(String taskId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String taskJson = jedis.get(taskMetadataKey + taskId);
            if(taskJson == null) {
                return null;
            }
            return parseTask(taskJson);
        }
    }

    public void removeTaskMetadata(String taskId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(taskMetadataKey + taskId);
        }
    }

    private String getLuaModule() {
        if(QueueLevel.isOnlineQueue(queueName)) {
            return LuaManager.Module.online.name();
        } else {
            return LuaManager.Module.offline.name();
        }
    }

    public Task parseTask(String taskJson) {
        Task task = JsonUtils.fromJson(taskJson, Task.class);
        task.setAk(EncryptUtils.decrypt(task.getAk()));
        return task;
    }

}
