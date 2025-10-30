package com.ke.bella.batch.service;

import com.ke.bella.batch.db.repo.BatchRepo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

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
}
