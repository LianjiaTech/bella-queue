package com.ke.bella.batch.service;

import com.ke.bella.batch.db.repo.BatchRepo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchCompleteCountUpdaterTest {

    @Mock
    private BatchRepo batchRepo;

    @Mock
    private BatchService batchService;

    @InjectMocks
    private BatchCompleteCountUpdater updater;

    private static final String TEST_BATCH_ID = "batch-123";

    @Before
    public void setUp() {
        // Inject the mocked BatchService
        ReflectionTestUtils.setField(updater, "bs", batchService);
    }

    @Test
    public void testIncreaseCompleteCount_SingleIncrement() {
        updater.increaseCompleteCount(TEST_BATCH_ID, 1);

        // Flush should trigger the counter to be written
        updater.flush();

        verify(batchRepo).writeProgress(TEST_BATCH_ID, 1);
        verify(batchService).stat(TEST_BATCH_ID);
    }

    @Test
    public void testIncreaseCompleteCount_MultipleIncrements() {
        updater.increaseCompleteCount(TEST_BATCH_ID, 5);
        updater.increaseCompleteCount(TEST_BATCH_ID, 3);
        updater.increaseCompleteCount(TEST_BATCH_ID, 2);

        updater.flush();

        verify(batchRepo).writeProgress(TEST_BATCH_ID, 10);
        verify(batchService).stat(TEST_BATCH_ID);
    }

    @Test
    public void testIncreaseCompleteCount_ZeroDelta() {
        updater.increaseCompleteCount(TEST_BATCH_ID, 0);

        updater.flush();

        // Should not write progress for zero delta
        verify(batchRepo, never()).writeProgress(anyString(), anyInt());
        verify(batchService, never()).stat(anyString());
    }

    @Test
    public void testIncreaseCompleteCount_NegativeDelta() {
        // First add some positive count
        updater.increaseCompleteCount(TEST_BATCH_ID, 5);

        // Then subtract
        updater.increaseCompleteCount(TEST_BATCH_ID, -3);

        updater.flush();

        verify(batchRepo).writeProgress(TEST_BATCH_ID, 2);
        verify(batchService).stat(TEST_BATCH_ID);
    }

    @Test
    public void testRemove_ClearsBatchFromCache() {
        updater.increaseCompleteCount(TEST_BATCH_ID, 5);

        // Remove should trigger flush for this batch
        updater.remove(TEST_BATCH_ID);

        verify(batchRepo).writeProgress(TEST_BATCH_ID, 5);
        verify(batchService).stat(TEST_BATCH_ID);

        // After remove, flush should not process this batch again
        updater.flush();

        // Verify no additional calls
        verify(batchRepo, times(1)).writeProgress(TEST_BATCH_ID, 5);
        verify(batchService, times(1)).stat(TEST_BATCH_ID);
    }

    @Test
    public void testMultipleBatches() {
        String batchId1 = "batch-1";
        String batchId2 = "batch-2";

        updater.increaseCompleteCount(batchId1, 3);
        updater.increaseCompleteCount(batchId2, 7);

        updater.flush();

        verify(batchRepo).writeProgress(batchId1, 3);
        verify(batchRepo).writeProgress(batchId2, 7);
        verify(batchService).stat(batchId1);
        verify(batchService).stat(batchId2);
    }

    @Test
    public void testFlush_WithoutAnyIncrements() {
        updater.flush();

        // Should not make any calls to repo or service
        verify(batchRepo, never()).writeProgress(anyString(), anyInt());
        verify(batchService, never()).stat(anyString());
    }

    @Test
    public void testIncreaseCompleteCount_AfterFlushAndMoreIncrements() {
        // First batch of increments
        updater.increaseCompleteCount(TEST_BATCH_ID, 5);
        updater.flush();

        verify(batchRepo).writeProgress(TEST_BATCH_ID, 5);
        verify(batchService).stat(TEST_BATCH_ID);

        // Reset mocks to verify next batch independently
        reset(batchRepo, batchService);

        // Second batch of increments
        updater.increaseCompleteCount(TEST_BATCH_ID, 3);
        updater.flush();

        verify(batchRepo).writeProgress(TEST_BATCH_ID, 3);
        verify(batchService).stat(TEST_BATCH_ID);
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Simulate concurrent access from multiple threads
        int numThreads = 10;
        int incrementsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    updater.increaseCompleteCount(TEST_BATCH_ID, 1);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        updater.flush();

        // Should have accumulated all increments
        verify(batchRepo).writeProgress(TEST_BATCH_ID, numThreads * incrementsPerThread);
        verify(batchService).stat(TEST_BATCH_ID);
    }

    @Test
    public void testConcurrentAccessWithExpectedActualComparison() throws InterruptedException {
        int numThreads = 20;
        int incrementsPerThread = 50;
        int deltaPerIncrement = 2;
        
        // Expected total
        int expectedTotal = numThreads * incrementsPerThread * deltaPerIncrement;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(numThreads);
        AtomicInteger actualCallCount = new AtomicInteger(0);
        
        // Submit all tasks
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < incrementsPerThread; j++) {
                        updater.increaseCompleteCount(TEST_BATCH_ID, deltaPerIncrement);
                        actualCallCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion with timeout
        boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
        assertTrue("All threads should complete within timeout", completed);
        
        executor.shutdown();
        
        // Flush to trigger database write
        updater.flush();
        
        // Verify expected vs actual
        int expectedCallCount = numThreads * incrementsPerThread;
        assertEquals("All increment calls should be made", expectedCallCount, actualCallCount.get());
        
        // Verify the total written to database matches expected
        verify(batchRepo).writeProgress(TEST_BATCH_ID, expectedTotal);
        verify(batchService).stat(TEST_BATCH_ID);
    }

    @Test
    public void testConcurrentAccessMultipleBatches() throws InterruptedException {
        String[] batchIds = {"batch-1", "batch-2", "batch-3"};
        int numThreadsPerBatch = 5;
        int incrementsPerThread = 20;
        int deltaPerIncrement = 3;
        
        // Expected values for each batch
        int expectedPerBatch = numThreadsPerBatch * incrementsPerThread * deltaPerIncrement;
        
        ExecutorService executor = Executors.newFixedThreadPool(batchIds.length * numThreadsPerBatch);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(batchIds.length * numThreadsPerBatch);
        
        // Submit tasks for each batch
        for (String batchId : batchIds) {
            for (int i = 0; i < numThreadsPerBatch; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < incrementsPerThread; j++) {
                            updater.increaseCompleteCount(batchId, deltaPerIncrement);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }
        }
        
        startLatch.countDown();
        assertTrue("All threads should complete", completeLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        updater.flush();
        
        // Verify each batch got the expected total
        for (String batchId : batchIds) {
            verify(batchRepo).writeProgress(batchId, expectedPerBatch);
            verify(batchService).stat(batchId);
        }
    }

    @Test
    public void testConcurrentIncrementAndFlush() throws InterruptedException {
        int numIncrementThreads = 5;
        int incrementsPerThread = 200;
        
        AtomicLong totalFlushed = new AtomicLong(0);
        
        // Mock to capture flushed values
        doAnswer(invocation -> {
            int delta = invocation.getArgument(1);
            totalFlushed.addAndGet(delta);
            return null;
        }).when(batchRepo).writeProgress(eq(TEST_BATCH_ID), anyInt());
        
        ExecutorService executor = Executors.newFixedThreadPool(numIncrementThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(numIncrementThreads);
        
        // Increment threads - each thread does a fixed number of increments
        for (int i = 0; i < numIncrementThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        updater.increaseCompleteCount(TEST_BATCH_ID, 1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        
        // Wait for all increment threads to complete
        assertTrue("All threads should complete within timeout", 
                   completeLatch.await(30, TimeUnit.SECONDS));
        
        executor.shutdown();
        
        // Single final flush to ensure all increments are processed
        updater.flush();
        
        // Expected total should match actual total since all increments are done before flush
        int expectedTotal = numIncrementThreads * incrementsPerThread;
        assertEquals("Total flushed should equal total incremented", 
                     expectedTotal, totalFlushed.get());
    }
}
