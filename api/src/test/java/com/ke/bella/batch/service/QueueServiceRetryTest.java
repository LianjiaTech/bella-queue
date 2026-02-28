package com.ke.bella.batch.service;

import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.tables.pojos.QueueDB;
import com.theokanning.openai.queue.Task;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试 QueueService 中的重试相关逻辑
 * 注意：这些测试使用反射调用私有方法
 */
public class QueueServiceRetryTest {

    /**
     * 测试 reEnqueueTask 方法 - 首次重试场景
     * 验证逻辑： 1. 任务存在且未过期 2. retry 键不存在（首次重试） 3. 应该设置 retry:taskId = 1
     */
    @Test
    public void testReEnqueueTask_FirstRetry() throws Exception {
        // 准备测试数据
        String taskId = "TASK-1-1-C-260302120000-0001-000001";

        // Mock 对象
        QueueRepo queueRepo = mock(QueueRepo.class);
        Jedis jedis = mock(Jedis.class);
        BlockingQueue<Task> mockQueue = mock(BlockingQueue.class);

        // Mock 数据库返回任务
        QueueDB queueDB = createMockQueueDB(taskId, LocalDateTime.now().plusHours(1));
        when(queueRepo.findTask(taskId)).thenReturn(queueDB);

        // Mock 解析任务
        Task task = createMockTask(taskId, "test-queue", 1);
        when(queueRepo.parseTask(queueDB)).thenReturn(task);

        // Mock Redis 操作 - 首次重试，retry 键不存在
        when(jedis.get("retry:" + taskId)).thenReturn(null);

        // 创建 QueueService 实例并注入依赖
        QueueService queueService = createQueueServiceWithMocks(queueRepo, mockQueue);

        // 调用私有方法
        Method method = QueueService.class.getDeclaredMethod("reEnqueueTask", String.class, Jedis.class);
        method.setAccessible(true);
        method.invoke(queueService, taskId, jedis);

        // 验证任务被重新入队
        verify(mockQueue).add(task);

        // 验证重试次数被设置为 1
        verify(jedis).setex(eq("retry:" + taskId), anyLong(), eq("1"));
    }

    /**
     * 测试 reEnqueueTask 方法 - 第二次重试场景
     * 验证逻辑： 1. 任务存在且未过期 2. retry 键已存在且值为 1 3. 应该更新 retry:taskId = 2
     */
    @Test
    public void testReEnqueueTask_SecondRetry() throws Exception {
        String taskId = "TASK-1-1-C-260302120000-0001-000001";

        QueueRepo queueRepo = mock(QueueRepo.class);
        Jedis jedis = mock(Jedis.class);
        BlockingQueue<Task> mockQueue = mock(BlockingQueue.class);

        QueueDB queueDB = createMockQueueDB(taskId, LocalDateTime.now().plusHours(1));
        when(queueRepo.findTask(taskId)).thenReturn(queueDB);

        Task task = createMockTask(taskId, "test-queue", 1);
        when(queueRepo.parseTask(queueDB)).thenReturn(task);

        // Mock Redis 操作 - 第二次重试，retry 键值为 1
        when(jedis.get("retry:" + taskId)).thenReturn("1");

        QueueService queueService = createQueueServiceWithMocks(queueRepo, mockQueue);

        Method method = QueueService.class.getDeclaredMethod("reEnqueueTask", String.class, Jedis.class);
        method.setAccessible(true);
        method.invoke(queueService, taskId, jedis);

        verify(mockQueue).add(task);
        verify(jedis).setex(eq("retry:" + taskId), anyLong(), eq("2"));
    }

    /**
     * 测试 reEnqueueTask 方法 - 任务不存在
     * 验证逻辑： 1. 数据库返回 null 2. 应该抛出 IllegalStateException
     */
    @Test
    public void testReEnqueueTask_TaskNotFound() throws Exception {
        String taskId = "TASK-1-1-C-260302120000-0001-000001";

        QueueRepo queueRepo = mock(QueueRepo.class);
        Jedis jedis = mock(Jedis.class);

        when(queueRepo.findTask(taskId)).thenReturn(null);

        QueueService queueService = createQueueServiceWithMocks(queueRepo, null);

        Method method = QueueService.class.getDeclaredMethod("reEnqueueTask", String.class, Jedis.class);
        method.setAccessible(true);

        try {
            method.invoke(queueService, taskId, jedis);
            fail("Should throw IllegalStateException");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(e.getCause().getMessage().contains("Task not found"));
        }
    }

    /**
     * 测试 reEnqueueTask 方法 - 任务已过期
     * 验证逻辑： 1. 任务的 expireTime 已经过期 2. 应该抛出 IllegalStateException
     */
    @Test
    public void testReEnqueueTask_TaskExpired() throws Exception {
        String taskId = "TASK-1-1-C-260302120000-0001-000001";

        QueueRepo queueRepo = mock(QueueRepo.class);
        Jedis jedis = mock(Jedis.class);

        QueueDB queueDB = createMockQueueDB(taskId, LocalDateTime.now().minusHours(1));
        when(queueRepo.findTask(taskId)).thenReturn(queueDB);

        Task task = createMockTask(taskId, "test-queue", 1);
        task.setExpireTime(System.currentTimeMillis() - 3600000); // 1小时前过期
        when(queueRepo.parseTask(queueDB)).thenReturn(task);

        QueueService queueService = createQueueServiceWithMocks(queueRepo, null);

        Method method = QueueService.class.getDeclaredMethod("reEnqueueTask", String.class, Jedis.class);
        method.setAccessible(true);

        try {
            method.invoke(queueService, taskId, jedis);
            fail("Should throw IllegalStateException");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(e.getCause().getMessage().contains("Task expired"));
        }
    }

    /**
     * 测试 unTrackTask 方法 - 正常清理
     * 验证逻辑： 1. 调用 unTrackTask 2. 应该删除所有追踪键
     */
    @Test
    public void testUnTrackTask_Success() throws Exception {
        String taskId = "TASK-1-1-C-260302120000-0001-000001";

        Jedis jedis = mock(Jedis.class);
        redis.clients.jedis.JedisPool jedisPool = mock(redis.clients.jedis.JedisPool.class);
        when(jedisPool.getResource()).thenReturn(jedis);

        QueueService queueService = new QueueService();
        injectField(queueService, "jedisPool", jedisPool);

        Method method = QueueService.class.getDeclaredMethod("unTrackTask", String.class);
        method.setAccessible(true);
        method.invoke(queueService, taskId);

        // 验证删除了所有追踪键
        verify(jedis).del(
                eq("timeout:" + taskId),
                eq("retry:" + taskId),
                eq("max_retry:" + taskId)
        );

        // 验证 Jedis 资源被关闭
        verify(jedis).close();
    }

    // ============== 辅助方法 ==============

    private QueueService createQueueServiceWithMocks(QueueRepo queueRepo, BlockingQueue<Task> mockQueue) throws Exception {
        QueueService queueService = new QueueService();
        injectField(queueService, "queueRepo", queueRepo);

        if(mockQueue != null) {
            // 注入 QUEUE_CACHE
            java.lang.reflect.Field cacheField = QueueService.class.getDeclaredField("QUEUE_CACHE");
            cacheField.setAccessible(true);

            @SuppressWarnings("unchecked")
            com.google.common.cache.Cache<String, BlockingQueue<Task>> cache =
                    com.google.common.cache.CacheBuilder.newBuilder().build();

            cache.put("test-queue:1", mockQueue);
            cacheField.set(queueService, cache);
        }

        return queueService;
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = QueueService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private QueueDB createMockQueueDB(String taskId, LocalDateTime expiredAt) {
        QueueDB queueDB = new QueueDB();
        queueDB.setTaskId(taskId);
        queueDB.setQueue("test-queue");
        queueDB.setExpiredAt(expiredAt);
        queueDB.setStatus("queued");
        return queueDB;
    }

    private Task createMockTask(String taskId, String queue, int level) {
        Task task = new Task();
        task.setTaskId(taskId);
        task.setQueue(queue);
        task.setLevel(level);
        task.setExpireTime(System.currentTimeMillis() + 3600000); // 1小时后过期
        task.setData(new HashMap<>());
        return task;
    }
}
