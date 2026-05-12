package com.ke.bella.batch.service;

import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.enums.TaskStatus;
import com.ke.bella.batch.tables.pojos.QueueHeadDB;
import com.ke.bella.batch.tables.pojos.QueueMetadataDB;
import com.ke.bella.batch.utils.OpenapiUtils;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.cache.Cache;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class QueueServiceTest {

    @Mock
    private QueueRepo queueRepo;

    @Mock
    private BlockingQueue<Task> mockQueue;

    @InjectMocks
    private QueueService queueService;

    private Put putRequest;
    private Take takeRequest;
    private QueueMetadataDB queueMeta;

    @Before
    public void setUp() {
        putRequest = Put.builder()
                .endpoint("/v1/chat/completions")
                .queue("test-queue")
                .level(0)
                .data(Map.of("model", "test-model"))
                .responseMode("callback")
                .callbackUrl("http://callback.test")
                .build();

        takeRequest = Take.builder()
                .queues(Arrays.asList("test-queue:0", "test-queue:1"))
                .size(5)
                .strategy("round_robin")
                .build();

        queueMeta = new QueueMetadataDB();
        queueMeta.setId(1L);
        queueMeta.setQueue("test-queue");
    }

    @Test
    public void testPut_WithValidQueue_CallsRepo() {
        when(queueRepo.findMetadataByName("test-queue")).thenReturn(queueMeta);

        try {
            queueService.put(putRequest);
        } catch (Exception e) {
            //ignore
        }

        verify(queueRepo, atLeastOnce()).findMetadataByName("test-queue");
    }

    @Test
    public void testPut_EmptyQueueName_ThrowsException() {
        // Mock OpenapiUtils 返回空字符串
        try (var mockedStatic = mockStatic(OpenapiUtils.class)) {
            mockedStatic.when(() -> OpenapiUtils.exchangeQueueName(anyString(), any())).thenReturn("");

            Put emptyQueueRequest = Put.builder()
                    .endpoint("/v1/test/endpoint")
                    .queue("") // 空队列名
                    .level(0)
                    .data(Map.of("model", "test-model"))
                    .responseMode("callback")
                    .callbackUrl("http://callback.test")
                    .build();

            // 执行测试并验证异常
            try {
                queueService.put(emptyQueueRequest);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertEquals("Queue config cannot be found", e.getMessage());
            }
        }
    }

    @Test(expected = NullPointerException.class)
    public void testPut_QueueNotRegistered_ThrowsException() {
        when(queueRepo.findMetadataByName("test-queue")).thenReturn(null);
        queueService.put(putRequest);
    }

    @Test
    public void testTake_ValidRequest_ReturnsNotNull() {
        try {
            Map<String, List<Task>> result = queueService.take(takeRequest);
            assertNotNull(result);
        } catch (Exception e) {
            //ignore
        }
    }

    @Test
    public void testTake_PassesMinAgeSecondsToStrategy() throws Exception {
        // 构造 take 请求，设置 minAgeSeconds=30
        Take takeWithMinAge = Take.builder()
                .queues(Arrays.asList("test-queue:0"))
                .size(5)
                .strategy("round_robin")
                .minAgeSeconds(30L)
                .build();

        // mock QUEUE_CACHE，让 getQueue 返回 mock 的 RedisBlockingQueue
        java.lang.reflect.Field cacheField = QueueService.class.getDeclaredField("QUEUE_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, BlockingQueue<Task>> mockCache = mock(Cache.class);
        cacheField.set(queueService, mockCache);

        RedisBlockingQueue mockRedisQueue = mock(RedisBlockingQueue.class);
        when(mockCache.get(eq("test-queue:0"), any())).thenReturn(mockRedisQueue);
        // 队列返回空，不影响 minAgeSeconds 传递的验证
        when(mockRedisQueue.poll(30L)).thenReturn(null);

        queueService.take(takeWithMinAge);

        // 验证 poll(30L) 被调用，说明 minAgeSeconds 正确透传
        verify(mockRedisQueue, atLeastOnce()).poll(30L);
    }

    @Test
    public void testTake_ZeroMinAgeSecondsNoRestriction() throws Exception {
        Take takeNoMinAge = Take.builder()
                .queues(Arrays.asList("test-queue:0"))
                .size(5)
                .strategy("round_robin")
                .minAgeSeconds(0L)
                .build();

        java.lang.reflect.Field cacheField = QueueService.class.getDeclaredField("QUEUE_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, BlockingQueue<Task>> mockCache = mock(Cache.class);
        cacheField.set(queueService, mockCache);

        RedisBlockingQueue mockRedisQueue = mock(RedisBlockingQueue.class);
        when(mockCache.get(eq("test-queue:0"), any())).thenReturn(mockRedisQueue);
        when(mockRedisQueue.poll(0L)).thenReturn(null);

        queueService.take(takeNoMinAge);

        verify(mockRedisQueue, atLeastOnce()).poll(0L);
        // 确认没有用非零的 minAgeSeconds 调用
        verify(mockRedisQueue, never()).poll(longThat(v -> v > 0));
    }

    @Test
    public void testTake_InvalidStrategy_ThrowsException() {
        takeRequest.setStrategy("invalid-strategy");
        try {
            queueService.take(takeRequest);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("invalid-strategy"));
        }
    }

    @Test
    public void testCancel_Success() {
        // 创建符合新格式的taskId，使用level=1（离线任务）才会调用cancelTask
        String taskId = "TASK-1-1-C-250101120000-0001-000001";

        try {
            // Mock QueueRepo
            QueueMetadataDB mockQueueMetadata = new QueueMetadataDB();
            mockQueueMetadata.setId(1L);
            mockQueueMetadata.setQueue("test-queue");
            when(queueRepo.findMetadataById(1L)).thenReturn(mockQueueMetadata);

            // Mock QUEUE_CACHE using reflection
            java.lang.reflect.Field cacheField = QueueService.class.getDeclaredField("QUEUE_CACHE");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            com.google.common.cache.Cache<String, BlockingQueue<Task>> mockCache =
                    mock(com.google.common.cache.Cache.class);
            cacheField.set(queueService, mockCache);

            @SuppressWarnings("unchecked")
            BlockingQueue<Task> mockQueue = mock(BlockingQueue.class);
            when(mockQueue.remove(any(Task.class))).thenReturn(true);
            when(mockCache.get(eq("test-queue:1"), any())).thenReturn(mockQueue);

            queueService.cancel(taskId);

            // Verify interactions
            verify(queueRepo).findMetadataById(1L);
            verify(queueRepo).cancelTask(taskId);
            verify(mockCache).get(eq("test-queue:1"), any());

            // Verify queue removal
            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(mockQueue).remove(taskCaptor.capture());

            // Verify captured task properties
            Task capturedTask = taskCaptor.getValue();
            assertEquals(taskId, capturedTask.getTaskId());
            assertEquals("test-queue", capturedTask.getQueue());
            assertEquals(Integer.valueOf(1), capturedTask.getLevel());
            assertEquals("test-queue:1", capturedTask.getFullQueueName());
        } catch (Exception e) {
            fail("Test failed with exception: " + e.getMessage());
        }
    }

    @Test
    public void testCancel_QueueMetadataNotFound() {
        String taskId = "TASK-999-0-C-250101120000-0001-000001";

        // Mock queueRepo to return null
        when(queueRepo.findMetadataById(999L)).thenReturn(null);

        // Should not throw exception, just return silently
        queueService.cancel(taskId);

        verify(queueRepo).findMetadataById(999L);
        // Verify no interaction with queue cache since metadata is null
        verifyNoInteractions(mockQueue);
    }

    // ---- getBacklog tests ----

    private Cache<String, BlockingQueue<Task>> injectMockCache() throws Exception {
        java.lang.reflect.Field cacheField = QueueService.class.getDeclaredField("QUEUE_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, BlockingQueue<Task>> mockCache = mock(Cache.class);
        cacheField.set(queueService, mockCache);
        return mockCache;
    }

    @Test
    public void testGetBacklog_OnlineQueue_ReturnsRedisSize() throws Exception {
        Cache<String, BlockingQueue<Task>> mockCache = injectMockCache();
        RedisBlockingQueue mockRedisQueue = mock(RedisBlockingQueue.class);
        when(mockCache.get(eq("test-queue:0"), any())).thenReturn(mockRedisQueue);
        when(mockRedisQueue.size()).thenReturn(42);

        long backlog = queueService.getBacklog("test-queue:0");

        assertEquals(42L, backlog);
        verifyNoInteractions(queueRepo);
    }

    @Test
    public void testGetBacklog_OfflineQueue_ReturnsDbPluRedis() throws Exception {
        Cache<String, BlockingQueue<Task>> mockCache = injectMockCache();
        RedisBlockingQueue mockRedisQueue = mock(RedisBlockingQueue.class);
        when(mockCache.get(eq("test-queue:1"), any())).thenReturn(mockRedisQueue);
        when(mockRedisQueue.size()).thenReturn(10);

        QueueHeadDB head = new QueueHeadDB();
        head.setTotalPutCnt(100L);
        head.setTotalLoadedCnt(70L);
        when(queueRepo.findQueueHead("test-queue:1")).thenReturn(head);

        long backlog = queueService.getBacklog("test-queue:1");

        // (100 - 70) + 10 = 40
        assertEquals(40L, backlog);
    }

    @Test
    public void testGetBacklog_OfflineQueue_QueueHeadNull_ReturnsRedisOnly() throws Exception {
        Cache<String, BlockingQueue<Task>> mockCache = injectMockCache();
        RedisBlockingQueue mockRedisQueue = mock(RedisBlockingQueue.class);
        when(mockCache.get(eq("test-queue:1"), any())).thenReturn(mockRedisQueue);
        when(mockRedisQueue.size()).thenReturn(5);
        when(queueRepo.findQueueHead("test-queue:1")).thenReturn(null);

        long backlog = queueService.getBacklog("test-queue:1");

        assertEquals(5L, backlog);
    }

    @Test
    public void testGetBacklog_OfflineQueue_NegativeDbDiff_ClampedToZero() throws Exception {
        Cache<String, BlockingQueue<Task>> mockCache = injectMockCache();
        RedisBlockingQueue mockRedisQueue = mock(RedisBlockingQueue.class);
        when(mockCache.get(eq("test-queue:1"), any())).thenReturn(mockRedisQueue);
        when(mockRedisQueue.size()).thenReturn(3);

        QueueHeadDB head = new QueueHeadDB();
        head.setTotalPutCnt(50L);
        head.setTotalLoadedCnt(60L); // loaded > put (异常情况，DB diff 应被截断为 0)
        when(queueRepo.findQueueHead("test-queue:1")).thenReturn(head);

        long backlog = queueService.getBacklog("test-queue:1");

        // max(0, 50-60) + 3 = 0 + 3 = 3
        assertEquals(3L, backlog);
    }

    @Test
    public void testGetBacklog_OfflineQueue_EmptyRedisAllInDb() throws Exception {
        Cache<String, BlockingQueue<Task>> mockCache = injectMockCache();
        RedisBlockingQueue mockRedisQueue = mock(RedisBlockingQueue.class);
        when(mockCache.get(eq("test-queue:1"), any())).thenReturn(mockRedisQueue);
        when(mockRedisQueue.size()).thenReturn(0);

        QueueHeadDB head = new QueueHeadDB();
        head.setTotalPutCnt(200L);
        head.setTotalLoadedCnt(150L);
        when(queueRepo.findQueueHead("test-queue:1")).thenReturn(head);

        long backlog = queueService.getBacklog("test-queue:1");

        assertEquals(50L, backlog);
    }

    private Task createTestTask(String taskId, String queue, int level, String ak) {
        return Task.builder()
                .taskId(taskId)
                .queue(queue)
                .level(level)
                .ak(ak)
                .data(new HashMap<>())
                .status(TaskStatus.queued.name())
                .instanceId("test-instance")
                .startTime(System.currentTimeMillis())
                .responseMode("callback")
                .build();
    }
}
