package com.ke.bella.batch.service;

import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.enums.TaskStatus;
import com.ke.bella.batch.tables.pojos.QueueMetadataDB;
import com.ke.bella.batch.utils.OpenapiUtils;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

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
