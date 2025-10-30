package com.ke.bella.batch.api;

import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.service.QueueHeadUpdater;
import com.ke.bella.batch.service.QueueService;
import com.ke.bella.batch.tables.pojos.QueueHeadDB;
import com.theokanning.openai.queue.Task;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ConsoleControllerTest {

    @Mock
    private QueueService queueService;

    @Mock
    private QueueRepo queueRepo;

    @Mock
    private QueueHeadUpdater queueHeadUpdater;

    @InjectMocks
    private ConsoleController consoleController;

    private static final String TEST_FULL_QUEUE_NAME = "test-queue:0";

    @Before
    public void setUp() {
        // No specific setup needed
    }

    @Test
    public void testFlushHeadToLatest_Success() {
        BlockingQueue<Task> mockQueue = new LinkedBlockingQueue<>();
        when(queueService.getQueue(TEST_FULL_QUEUE_NAME)).thenReturn(mockQueue);

        String result = consoleController.flushHeadToLatest(TEST_FULL_QUEUE_NAME);

        assertEquals(TEST_FULL_QUEUE_NAME, result);
        verify(queueHeadUpdater).flushStatsForQueue(TEST_FULL_QUEUE_NAME);
        verify(queueRepo).moveScanHeadToLatest(TEST_FULL_QUEUE_NAME);
        verify(queueService).getQueue(TEST_FULL_QUEUE_NAME);
    }

    @Test
    public void testFlushHeadToLatest_ClearsQueue() {
        BlockingQueue<Task> mockQueue = mock(BlockingQueue.class);
        when(queueService.getQueue(TEST_FULL_QUEUE_NAME)).thenReturn(mockQueue);

        consoleController.flushHeadToLatest(TEST_FULL_QUEUE_NAME);

        verify(mockQueue).clear();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFlushHeadToLatest_NullQueueName() {
        consoleController.flushHeadToLatest(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFlushHeadToLatest_EmptyQueueName() {
        consoleController.flushHeadToLatest("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFlushHeadToLatest_BlankQueueName() {
        consoleController.flushHeadToLatest("   ");
    }

    @Test
    public void testFlushHeadToLatest_WithQueueContainingTasks() {
        BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

        // Add some mock tasks to the queue
        Task task1 = createMockTask("task1");
        Task task2 = createMockTask("task2");
        queue.offer(task1);
        queue.offer(task2);

        when(queueService.getQueue(TEST_FULL_QUEUE_NAME)).thenReturn(queue);

        // Verify queue has tasks before clearing
        assertEquals(2, queue.size());

        String result = consoleController.flushHeadToLatest(TEST_FULL_QUEUE_NAME);

        assertEquals(TEST_FULL_QUEUE_NAME, result);
        assertEquals(0, queue.size()); // Queue should be cleared

        verify(queueHeadUpdater).flushStatsForQueue(TEST_FULL_QUEUE_NAME);
        verify(queueRepo).moveScanHeadToLatest(TEST_FULL_QUEUE_NAME);
    }

    @Test
    public void testGetAllQueueStats_Success() {
        List<QueueHeadDB> expectedStats = Arrays.asList(
                createMockQueueHeadDB("queue1:0", 100L, 80L, 75L),
                createMockQueueHeadDB("queue2:1", 50L, 40L, 35L)
        );

        when(queueRepo.findAllQueueHeads()).thenReturn(expectedStats);

        List<QueueHeadDB> result = consoleController.getAllQueueStats();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedStats, result);
        verify(queueRepo).findAllQueueHeads();
    }

    @Test
    public void testGetAllQueueStats_EmptyResult() {
        List<QueueHeadDB> emptyList = new ArrayList<>();
        when(queueRepo.findAllQueueHeads()).thenReturn(emptyList);

        List<QueueHeadDB> result = consoleController.getAllQueueStats();

        assertNotNull(result);
        assertEquals(0, result.size());
        verify(queueRepo).findAllQueueHeads();
    }

    @Test
    public void testGetAllQueueStats_NullResult() {
        when(queueRepo.findAllQueueHeads()).thenReturn(null);

        List<QueueHeadDB> result = consoleController.getAllQueueStats();

        assertNull(result);
        verify(queueRepo).findAllQueueHeads();
    }

    @Test
    public void testFlushHeadToLatest_ServiceThrowsException() {
        when(queueService.getQueue(TEST_FULL_QUEUE_NAME)).thenThrow(new RuntimeException("Queue not found"));

        try {
            consoleController.flushHeadToLatest(TEST_FULL_QUEUE_NAME);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Queue not found", e.getMessage());
        }

        // Verify that other operations were still called before the exception
        verify(queueHeadUpdater).flushStatsForQueue(TEST_FULL_QUEUE_NAME);
        verify(queueRepo).moveScanHeadToLatest(TEST_FULL_QUEUE_NAME);
    }

    @Test
    public void testFlushHeadToLatest_RepoThrowsException() {
        doThrow(new RuntimeException("Database error")).when(queueRepo).moveScanHeadToLatest(TEST_FULL_QUEUE_NAME);

        try {
            consoleController.flushHeadToLatest(TEST_FULL_QUEUE_NAME);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Database error", e.getMessage());
        }

        // Verify that flush stats was still called
        verify(queueHeadUpdater).flushStatsForQueue(TEST_FULL_QUEUE_NAME);
        verify(queueRepo).moveScanHeadToLatest(TEST_FULL_QUEUE_NAME);
        // QueueService should not be called due to exception
        verify(queueService, never()).getQueue(anyString());
    }

    private Task createMockTask(String taskId) {
        return Task.builder()
                .taskId(taskId)
                .queue("test-queue")
                .level(0)
                .build();
    }

    private QueueHeadDB createMockQueueHeadDB(String fullQueueName, Long putCnt, Long loadedCnt, Long completedCnt) {
        QueueHeadDB queueHead = new QueueHeadDB();
        // Extract queue and level from fullQueueName (format: "queue:level")
        String[] parts = fullQueueName.split(":");
        queueHead.setQueue(parts[0]);
        queueHead.setLevel(parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
        queueHead.setTotalPutCnt(putCnt);
        queueHead.setTotalLoadedCnt(loadedCnt);
        queueHead.setTotalCompletedCnt(completedCnt);
        return queueHead;
    }
}
