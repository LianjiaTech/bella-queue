package com.ke.bella.batch.service;

import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.utils.TimeUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class QueueHeadUpdaterTest {

    @Mock
    private QueueRepo queueRepo;

    @InjectMocks
    private QueueHeadUpdater updater;

    private static final String TEST_QUEUE_NAME = "test-queue:0";
    private static final String TEST_SHARDING_KEY_1 = "shard-20250101120000";
    private static final String TEST_SHARDING_KEY_2 = "shard-20250101130000";

    @Before
    public void setUp() {
        // No specific setup needed for this test
    }

    @Test
    public void testUpdateWriteHead_NewQueue() {
        long lastWroteId = 12345L;

        updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, lastWroteId);
        updater.flushWriteHeads();

        verify(queueRepo).updateQueueHeadWroteId(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, lastWroteId);
    }

    @Test
    public void testUpdateWriteHead_SameShardingKeyHigherId() {
        long initialId = 100L;
        long updatedId = 200L;

        updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, initialId);
        updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, updatedId);
        updater.flushWriteHeads();

        verify(queueRepo).updateQueueHeadWroteId(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, updatedId);
    }

    @Test
    public void testUpdateWriteHead_SameShardingKeyLowerId() {
        long initialId = 200L;
        long lowerId = 100L;

        updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, initialId);
        updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, lowerId);
        updater.flushWriteHeads();

        // Should keep the higher ID
        verify(queueRepo).updateQueueHeadWroteId(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, initialId);
    }

    @Test
    public void testUpdateWriteHead_NewerShardingKey() {
        try (MockedStatic<TimeUtils> mockedTimeUtils = mockStatic(TimeUtils.class)) {
            LocalDateTime time1 = LocalDateTime.of(2025, 1, 1, 12, 0);
            LocalDateTime time2 = LocalDateTime.of(2025, 1, 1, 13, 0);

            mockedTimeUtils.when(() -> TimeUtils.parseTimestamp("20250101120000")).thenReturn(time1);
            mockedTimeUtils.when(() -> TimeUtils.parseTimestamp("20250101130000")).thenReturn(time2);

            updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, 100L);
            updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_2, 200L);
            updater.flushWriteHeads();

            // Should use the newer sharding key
            verify(queueRepo).updateQueueHeadWroteId(TEST_QUEUE_NAME, TEST_SHARDING_KEY_2, 200L);
        }
    }

    @Test
    public void testIncreasePutCnt() {
        updater.increasePutCnt(TEST_QUEUE_NAME, 5);
        updater.flushStats();

        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 5, 0, 0);
    }

    @Test
    public void testIncreaseLoadedCnt() {
        updater.increaseLoadedCnt(TEST_QUEUE_NAME, 3);
        updater.flushStats();

        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 0, 3, 0);
    }

    @Test
    public void testIncreaseCompletedCnt() {
        updater.increaseCompletedCnt(TEST_QUEUE_NAME, 7);
        updater.flushStats();

        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 0, 0, 7);
    }

    @Test
    public void testMixedStatUpdates() {
        updater.increasePutCnt(TEST_QUEUE_NAME, 10);
        updater.increaseLoadedCnt(TEST_QUEUE_NAME, 8);
        updater.increaseCompletedCnt(TEST_QUEUE_NAME, 5);
        updater.flushStats();

        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 10, 8, 5);
    }

    @Test
    public void testMultipleIncrementsBeforeFlush() {
        updater.increasePutCnt(TEST_QUEUE_NAME, 3);
        updater.increasePutCnt(TEST_QUEUE_NAME, 4);
        updater.increasePutCnt(TEST_QUEUE_NAME, 2);

        updater.increaseLoadedCnt(TEST_QUEUE_NAME, 1);
        updater.increaseLoadedCnt(TEST_QUEUE_NAME, 2);

        updater.increaseCompletedCnt(TEST_QUEUE_NAME, 1);

        updater.flushStats();

        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 9, 3, 1);
    }

    @Test
    public void testFlushStatsForSpecificQueue() {
        String queue1 = "queue1:0";
        String queue2 = "queue2:0";

        updater.increasePutCnt(queue1, 5);
        updater.increasePutCnt(queue2, 3);

        // Flush only queue1
        updater.flushStatsForQueue(queue1);

        verify(queueRepo).updateQueueStats(queue1, 5, 0, 0);
        verify(queueRepo, never()).updateQueueStats(eq(queue2), anyLong(), anyLong(), anyLong());

        // Now flush all remaining stats
        updater.flushStats();

        verify(queueRepo).updateQueueStats(queue2, 3, 0, 0);
    }

    @Test
    public void testFlushStatsForNonExistentQueue() {
        // Should not throw exception when flushing stats for a queue that doesn't exist
        updater.flushStatsForQueue("non-existent-queue");

        verify(queueRepo, never()).updateQueueStats(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    public void testZeroCountersNotFlushed() {
        // All counters remain at 0, should not update database
        updater.flushStats();

        verify(queueRepo, never()).updateQueueStats(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    public void testFlushAll() {
        // Setup some write head and stats data
        updater.updateWriteHead(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, 123L);
        updater.increasePutCnt(TEST_QUEUE_NAME, 5);

        // Flush all should flush both write heads and stats
        updater.flush();

        verify(queueRepo).updateQueueHeadWroteId(TEST_QUEUE_NAME, TEST_SHARDING_KEY_1, 123L);
        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 5, 0, 0);
    }

    @Test
    public void testCountersResetAfterFlush() {
        updater.increasePutCnt(TEST_QUEUE_NAME, 10);
        updater.flushStats();

        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 10, 0, 0);

        // Reset mocks and add more counts
        reset(queueRepo);
        updater.increasePutCnt(TEST_QUEUE_NAME, 5);
        updater.flushStats();

        // Should only flush the new count, not the previous one
        verify(queueRepo).updateQueueStats(TEST_QUEUE_NAME, 5, 0, 0);
    }

    @Test
    public void testMultipleQueues() {
        String queue1 = "queue1:0";
        String queue2 = "queue2:1";

        updater.updateWriteHead(queue1, TEST_SHARDING_KEY_1, 100L);
        updater.updateWriteHead(queue2, TEST_SHARDING_KEY_2, 200L);

        updater.increasePutCnt(queue1, 5);
        updater.increaseLoadedCnt(queue2, 3);

        updater.flush();

        verify(queueRepo).updateQueueHeadWroteId(queue1, TEST_SHARDING_KEY_1, 100L);
        verify(queueRepo).updateQueueHeadWroteId(queue2, TEST_SHARDING_KEY_2, 200L);
        verify(queueRepo).updateQueueStats(queue1, 5, 0, 0);
        verify(queueRepo).updateQueueStats(queue2, 0, 3, 0);
    }
}
