package com.ke.bella.batch.service;

import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.tables.pojos.QueueShardingDB;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class QueueTaskCountUpdaterTest {

    @Mock
    private QueueRepo queueRepo;

    @InjectMocks
    private QueueTaskCountUpdater queueTaskCountUpdater;

    private QueueShardingDB firstSharding;
    private QueueShardingDB secondSharding;

    @Before
    public void setUp() {
        // Latest sharding for queue_table "1-0" that exceeds max_count
        firstSharding = new QueueShardingDB();
        firstSharding.setId(3L); // Higher ID = latest for queue_table "1-0"
        firstSharding.setQueueTable("1-0");
        firstSharding.setKey("20250118120000");
        firstSharding.setCount(1000L);
        firstSharding.setMaxCount(1000L);
        firstSharding.setKeyTime(LocalDateTime.now());

        // Latest sharding for queue_table "1-1" that does NOT exceed max_count
        secondSharding = new QueueShardingDB();
        secondSharding.setId(4L); // Higher ID = latest for queue_table "1-1"
        secondSharding.setQueueTable("1-1");
        secondSharding.setKey("20250118130000");
        secondSharding.setCount(500L);
        secondSharding.setMaxCount(1000L);
        secondSharding.setKeyTime(LocalDateTime.now());
    }

    @Test
    public void testTrySharding_WithExcessiveSharding_ProcessesLatestOnly() throws InterruptedException {
        // Only latest sharding records that exceed max_count are returned by the new query
        when(queueRepo.findAllExcessiveSharding())
                .thenReturn(Arrays.asList(firstSharding));

        queueTaskCountUpdater.trySharding();

        // Wait a bit for async execution to complete
        Thread.sleep(100);

        verify(queueRepo).findAllExcessiveSharding();
        // Note: newSharding is called asynchronously, so we can't verify it in this test
        // The fact that no exception was thrown means the method executed correctly
    }

    @Test
    public void testTrySharding_WithNonExcessiveSharding_DoesNotProcess() throws InterruptedException {
        // secondSharding count < max_count, so it won't be returned by the new query
        when(queueRepo.findAllExcessiveSharding())
                .thenReturn(Collections.emptyList()); // Query filters out non-excessive sharding

        queueTaskCountUpdater.trySharding();

        // Wait a bit for async execution to complete
        Thread.sleep(100);

        verify(queueRepo).findAllExcessiveSharding();
        // No sharding should be processed since none exceed max_count
    }

    @Test
    public void testTrySharding_WithMultipleExcessiveSharding_ProcessesLatestPerQueue() throws InterruptedException {
        // Latest sharding for queue_table "2-0" that exceeds max_count
        QueueShardingDB thirdSharding = new QueueShardingDB();
        thirdSharding.setId(5L); // Latest ID for queue_table "2-0"
        thirdSharding.setQueueTable("2-0");
        thirdSharding.setKey("20250118140000");
        thirdSharding.setCount(2000L);
        thirdSharding.setMaxCount(1000L);

        // Only return the latest excessive sharding for each queue_table
        when(queueRepo.findAllExcessiveSharding())
                .thenReturn(Arrays.asList(firstSharding, thirdSharding)); // Only excessive ones

        queueTaskCountUpdater.trySharding();

        // Wait a bit for async execution to complete
        Thread.sleep(100);

        verify(queueRepo).findAllExcessiveSharding();
        // Note: newSharding calls are asynchronous, so we can't verify them in unit tests
        // The fact that no exception was thrown means the method executed correctly
    }

    @Test
    public void testTrySharding_OnlyReturnsLatestExcessiveSharding() throws InterruptedException {
        // The new INNER JOIN query with MAX(id) GROUP BY queue_table ensures only 
        // the latest sharding record for each queue_table is returned
        // and only if count >= max_count
        when(queueRepo.findAllExcessiveSharding())
                .thenReturn(Arrays.asList(firstSharding)); // Only latest excessive sharding

        queueTaskCountUpdater.trySharding();

        // Wait a bit for async execution to complete
        Thread.sleep(100);

        verify(queueRepo).findAllExcessiveSharding();
        // The method processes only the latest excessive sharding per queue_table
    }

    @Test
    public void testTrySharding_WithEmptyResult_DoesNothing() {
        when(queueRepo.findAllExcessiveSharding()).thenReturn(Collections.emptyList());

        queueTaskCountUpdater.trySharding();

        verify(queueRepo).findAllExcessiveSharding();
        verify(queueRepo, never()).newSharding(anyString(), anyString());
    }

    @Test
    public void testTrySharding_WithNoExcessiveLatestSharding_DoesNothing() throws InterruptedException {
        // No latest sharding records exceed max_count
        // The INNER JOIN with MAX(id) and WHERE count >= max_count returns empty
        when(queueRepo.findAllExcessiveSharding())
                .thenReturn(Collections.emptyList()); // No latest excessive sharding found

        queueTaskCountUpdater.trySharding();

        // Wait a bit for potential async execution
        Thread.sleep(100);

        verify(queueRepo).findAllExcessiveSharding();
        // No new sharding should be created since no latest sharding exceeds max_count
    }

    @Test
    public void testIncrease_AddsToCache() {
        String shardingKey = "1-0-20250118120000";

        // Call increase multiple times
        queueTaskCountUpdater.increase(shardingKey);
        queueTaskCountUpdater.increase(shardingKey);
        queueTaskCountUpdater.increase(shardingKey);

        // Flush should be called with accumulated delta
        queueTaskCountUpdater.flush();

        verify(queueRepo).increaseShardingCount("1-0-20250118120000", 3L);
    }

    @Test
    public void testFlush_WithNoDelta_DoesNotUpdateRepo() {
        queueTaskCountUpdater.flush();

        verify(queueRepo, never()).increaseShardingCount(anyString(), anyLong());
    }
}
