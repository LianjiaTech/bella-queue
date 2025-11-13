package com.ke.bella.batch.service;

import com.ke.bella.batch.RedisMesh;
import com.ke.bella.batch.db.repo.BatchRepo;
import com.ke.bella.batch.db.repo.QueueRepo;
import com.ke.bella.batch.enums.BatchStatus;
import com.ke.bella.batch.tables.pojos.BatchDB;
import com.ke.bella.batch.tables.pojos.QueueDB;
import com.ke.bella.batch.tables.pojos.QueueMetadataDB;
import com.ke.bella.batch.utils.FileUtils;
import com.ke.bella.batch.utils.OpenapiUtils;
import com.ke.bella.batch.db.IDGenerator;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.queue.TaskWrapper;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.batch.RequestCounts;
import com.theokanning.openai.queue.Task;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchServiceTest {

    @Mock
    private BatchRepo batchRepo;

    @Mock
    private QueueRepo queueRepo;

    @Mock
    private RedisMesh redisMesh;

    @Mock
    private BatchCompleteCountUpdater batchCompleteCountUpdater;

    @Mock 
    private QueueService queueService;

    @InjectMocks
    private BatchService batchService;

    @Mock
    private QueueDB queueDB;

    private BatchRequest createRequest;
    private Batch batchDetail;
    private QueueMetadataDB queueMetadata;
    private BatchDB batchDB;

    @Before
    public void setUpBatchService() {
        BellaContext.setApikey(ApikeyInfo.builder().apikey("test-ak").build());

        // Set maxSplittingBatches field using reflection to fix test failure
        ReflectionTestUtils.setField(batchService, "maxSplittingBatches", 500);

        createRequest = BatchRequest.builder()
                .endpoint("/api/test")
                .inputFileId("file123")
                .completionWindow("24h")
                .build();

        queueMetadata = new QueueMetadataDB();
        queueMetadata.setId(1L);
        queueMetadata.setQueue("test-queue");

        batchDetail = new Batch();
        batchDetail.setId("batch123");
        batchDetail.setStatus(BatchStatus.validating.name());
        RequestCounts requestCounts = new RequestCounts();
        requestCounts.setTotal(100);
        requestCounts.setCompleted(0);
        requestCounts.setFailed(0);
        batchDetail.setRequestCounts(requestCounts);

        batchDB = new BatchDB();
        batchDB.setBatchId("batch123");
        batchDB.setEndpoint("/api/test");
        batchDB.setInputFileId("file123");
        batchDB.setAk("test-ak");
        batchDB.setRequestCountsTotal(100L);
    }

    @Test
    public void testCreateBatchWithQueueHeader() {
        try (MockedStatic<IDGenerator> mockedIDGenerator = mockStatic(IDGenerator.class)) {
            mockedIDGenerator.when(() -> IDGenerator.newQueueBatchId(anyLong(), anyInt())).thenReturn("batch123");
            
            when(queueRepo.findMetadataByName("specified-queue")).thenReturn(queueMetadata);
            when(batchRepo.saveBatch(any(BatchRequest.class), anyString())).thenReturn(batchDetail);

            Batch result = batchService.create(createRequest, "specified-queue");

            assertNotNull(result);
            assertEquals("batch123", result.getId());
            verify(batchRepo).saveBatch(eq(createRequest), anyString());
            verify(queueRepo).findMetadataByName("specified-queue");
            verify(queueRepo, never()).findMetadataByName("test-queue");
        }
    }

    @Test
    public void testCreateBatch() {
        try (MockedStatic<OpenapiUtils> mockedOpenapiUtils = mockStatic(OpenapiUtils.class);
             MockedStatic<IDGenerator> mockedIDGenerator = mockStatic(IDGenerator.class)) {
            mockedOpenapiUtils.when(() -> OpenapiUtils.peekFirstLine(anyString(), anyString(), any()))
                    .thenReturn("test-queue");
            mockedIDGenerator.when(() -> IDGenerator.newQueueBatchId(anyLong(), anyInt())).thenReturn("batch123");

            when(queueRepo.findMetadataByName("test-queue")).thenReturn(queueMetadata);
            when(batchRepo.saveBatch(any(BatchRequest.class), anyString())).thenReturn(batchDetail);

            Batch result = batchService.create(createRequest, null);

            assertNotNull(result);
            assertEquals("batch123", result.getId());
            verify(batchRepo).saveBatch(eq(createRequest), anyString());
            verify(queueRepo).findMetadataByName("test-queue");
        }
    }

    @Test
    public void testCancelBatch() {
        String batchId = "batch123";
        when(batchRepo.findBatch(batchId)).thenReturn(batchDetail);

        Batch result = batchService.cancel(batchId);

        assertNotNull(result);
        assertEquals("batch123", result.getId());
        verify(batchRepo).findBatch(batchId);
        // Note: cancelBatch is called asynchronously in doFinalize, not directly in cancel()
    }

    @Test
    public void testSplitBatchWhenSetInprogressFails() {
        String batchId = "batch123";
        
        // Create mock TaskWrapper
        Task mockTask = mock(Task.class);
        TaskWrapper mockTaskWrapper = mock(TaskWrapper.class);
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("batchId", batchId);
        
        // Create mock BatchDB
        BatchDB mockBatchDB = mock(BatchDB.class);
        when(mockBatchDB.getAk()).thenReturn("test-apikey");
        
        // Create ApikeyInfo using builder
        ApikeyInfo mockApikeyInfo = ApikeyInfo.builder()
                .userId(123L)
                .ownerName("testUser")
                .apikey("test-apikey")
                .build();
        
        when(mockTaskWrapper.getTask()).thenReturn(mockTask);
        when(mockTask.getData()).thenReturn(taskData);
        when(batchRepo.findBatchDB(batchId)).thenReturn(mockBatchDB);
        when(batchRepo.setInprogress(batchId)).thenReturn(false);
        
        try (MockedStatic<OpenapiUtils> mockedOpenapiUtils = mockStatic(OpenapiUtils.class);
             MockedStatic<IDGenerator> mockedIDGenerator = mockStatic(IDGenerator.class)) {
            
            OpenapiClient mockOpenapiClient = mock(OpenapiClient.class);
            mockedOpenapiUtils.when(OpenapiUtils::getInstance).thenReturn(mockOpenapiClient);
            when(mockOpenapiClient.whoami(anyString())).thenReturn(mockApikeyInfo);
            
            mockedIDGenerator.when(() -> IDGenerator.parseQueueIdFromBatchId(batchId)).thenReturn(1L);
            when(queueRepo.findMetadataById(1L)).thenReturn(queueMetadata);
            
            batchService.split(mockTaskWrapper);
            
            verify(batchRepo).findBatchDB(batchId);
            verify(batchRepo).setInprogress(batchId);
            verify(mockTaskWrapper).markComplete(any());
        }
    }

    @Test
    public void testWriteResult() {
        try (MockedStatic<Configs> mockedConfigs = mockStatic(Configs.class)) {
            mockedConfigs.when(() -> Configs.getBatchDir(anyString())).thenReturn(java.nio.file.Paths.get("/tmp/test-batch"));

            when(queueDB.getTaskId()).thenReturn("task123");
            when(queueDB.getBatchId()).thenReturn("batch123");
            when(queueDB.getCustomId()).thenReturn("custom123");

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("data", "test data");

            batchService.writeResult(queueDB, result);

            verify(queueDB, atLeastOnce()).getTaskId();
            verify(queueDB, atLeastOnce()).getBatchId();
            verify(queueDB, atLeastOnce()).getCustomId();
        }
    }

    @Test
    public void testStatWhenBatchCompleted() {
        String batchId = "batch123";
        Batch completedBatch = new Batch();
        completedBatch.setId(batchId);
        completedBatch.setStatus(BatchStatus.completed.name());

        when(batchRepo.findBatchDB(batchId)).thenReturn(batchDB);
        when(batchRepo.toBatch(batchDB)).thenReturn(completedBatch);

        batchService.stat(batchId);

        verify(batchRepo).findBatchDB(batchId);
        verify(batchRepo).toBatch(batchDB);
    }

    @Test
    public void testStatWhenBatchNotCompleted() {
        String batchId = "batch123";
        Batch inProgressBatch = new Batch();
        inProgressBatch.setId(batchId);
        inProgressBatch.setStatus(BatchStatus.in_progress.name());
        RequestCounts requestCounts = new RequestCounts();
        requestCounts.setTotal(100);
        requestCounts.setCompleted(50);
        requestCounts.setFailed(0);
        inProgressBatch.setRequestCounts(requestCounts);

        when(batchRepo.findBatchDB(batchId)).thenReturn(batchDB);
        when(batchRepo.toBatch(batchDB)).thenReturn(inProgressBatch);

        batchService.stat(batchId);

        verify(batchRepo).findBatchDB(batchId);
        verify(batchRepo).toBatch(batchDB);
    }

    @Test
    public void testStatWhenBatchIsCompletedButStatusNotSet() {
        String batchId = "batch123";
        BatchDB batchDB = new BatchDB();
        batchDB.setBatchId(batchId);
        batchDB.setAk("test-ak");

        Batch readyBatch = new Batch();
        readyBatch.setId(batchId);
        readyBatch.setStatus(BatchStatus.in_progress.name());
        RequestCounts requestCounts = new RequestCounts();
        requestCounts.setTotal(100);
        requestCounts.setCompleted(100);
        requestCounts.setFailed(0);
        readyBatch.setRequestCounts(requestCounts);

        when(batchRepo.findBatchDB(batchId)).thenReturn(batchDB);
        when(batchRepo.toBatch(batchDB)).thenReturn(readyBatch);

        when(batchRepo.setFinalizing(batchId)).thenReturn(true);
        when(batchRepo.completeBatch(batchId)).thenReturn(true);

        try (MockedStatic<Configs> mockedConfigs = mockStatic(Configs.class);
                MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class);
                MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {

            mockedConfigs.when(() -> Configs.getBatchDir(batchId)).thenReturn(java.nio.file.Paths.get("/tmp/test-batch"));
            mockedConfigs.when(() -> Configs.getBatchOutputFile(batchId)).thenReturn(java.nio.file.Paths.get("/tmp/output"));
            mockedConfigs.when(() -> Configs.getBatchErrorFile(batchId)).thenReturn(java.nio.file.Paths.get("/tmp/error"));
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);

            batchService.stat(batchId);

            verify(batchRepo, times(2)).findBatchDB(batchId);
            verify(batchRepo).toBatch(batchDB);
            verify(batchRepo).setFinalizing(batchId);
            verify(batchRepo).completeBatch(batchId);
            verify(batchCompleteCountUpdater).remove(batchId);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateBatchWhenQueueNotFound() {
        try (MockedStatic<OpenapiUtils> mockedOpenapiUtils = mockStatic(OpenapiUtils.class)) {
            mockedOpenapiUtils.when(() -> OpenapiUtils.peekFirstLine(anyString(), anyString(), any()))
                    .thenReturn(null);

            batchService.create(createRequest, null);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateBatchWhenQueueIsEmpty() {
        try (MockedStatic<OpenapiUtils> mockedOpenapiUtils = mockStatic(OpenapiUtils.class)) {
            mockedOpenapiUtils.when(() -> OpenapiUtils.peekFirstLine(anyString(), anyString(), any()))
                    .thenReturn("");

            batchService.create(createRequest, "");
        }
    }
}
