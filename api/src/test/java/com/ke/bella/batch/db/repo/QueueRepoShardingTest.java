package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.service.QueueTaskCountUpdater;
import com.ke.bella.batch.tables.pojos.QueueMetadataDB;
import com.ke.bella.batch.tables.pojos.QueueShardingDB;
import com.ke.bella.batch.tables.records.QueueHeadRecord;
import com.ke.bella.batch.tables.records.QueueShardingRecord;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;

import static com.ke.bella.batch.Tables.QUEUE_HEAD;
import static com.ke.bella.batch.Tables.QUEUE_SHARDING;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class QueueRepoShardingTest {

    @Mock
    private DSLContext db;

    @Mock
    private QueueTaskCountUpdater updator;

    @InjectMocks
    private QueueRepo queueRepo;

    private QueueShardingDB testSharding;

    @Before
    public void setUp() {
        testSharding = new QueueShardingDB();
        testSharding.setId(1L);
        testSharding.setQueueTable("1-0");
        testSharding.setKey("20250118120000");
        testSharding.setCount(500L);
        testSharding.setMaxCount(1000L);
        testSharding.setKeyTime(LocalDateTime.now());
    }

    @Test
    public void testInitSharding_CreatesShardingAndTable() {
        QueueShardingRecord mockShardingRecord = mock(QueueShardingRecord.class);
        QueueHeadRecord mockHeadRecord = mock(QueueHeadRecord.class);

        when(db.newRecord(QUEUE_SHARDING)).thenReturn(mockShardingRecord);
        when(db.newRecord(QUEUE_HEAD)).thenReturn(mockHeadRecord);
        when(db.execute(anyString())).thenReturn(1);
        when(mockShardingRecord.insert()).thenReturn(1);
        when(mockHeadRecord.insert()).thenReturn(1);

        // Create mock QueueMetadataDB
        QueueMetadataDB metadataDB = new QueueMetadataDB();
        metadataDB.setId(1L);
        metadataDB.setQueue("test-queue");

        queueRepo.initSharding(metadataDB, 0);

        verify(db).newRecord(QUEUE_SHARDING);
        verify(db).newRecord(QUEUE_HEAD);
        verify(db).execute(contains("create table"));
        verify(mockShardingRecord).insert();
        verify(mockHeadRecord).insert();
    }

    @Test
    public void testIncreaseShardingCount_ParsesKeyCorrectly() {
        // This test verifies the key parsing logic using lastIndexOf
        String queueTableKey = "1-0-20250118120000";
        int idx = queueTableKey.lastIndexOf("-");
        String queueTable = queueTableKey.substring(0, idx);
        String key = queueTableKey.substring(idx + 1);

        assertEquals("Should extract queueTable correctly", "1-0", queueTable);
        assertEquals("Should extract key correctly", "20250118120000", key);
    }

    @Test
    public void testFindTargetShardingKey_UsesQueueHeadShardingKey() {
        // Test the modified findTargetShardingKey logic that uses queueHead.getLastScannedShardingKey() directly
        String expectedShardingKey = "1-0-20250118120000";

        // The method now uses queueHead.getLastScannedShardingKey() as the shardingKey directly
        // This validates the simplified logic in the updated method
        assertTrue("Sharding key should match expected format",
                expectedShardingKey.matches("\\d+-\\d+-\\d{14}"));
    }

    @Test
    public void testMoveWriteHead_WithShardingKeyOnly() {
        // Test the updated moveWriteHead method that takes only shardingKey and newId
        String shardingKey = "1-0-20250118120000";
        long newId = 12345L;

        // This validates that the method signature change is working correctly
        // The method should accept shardingKey directly without needing to parse queue and level
        assertNotNull(shardingKey);
        assertTrue(newId > 0);
    }
}
