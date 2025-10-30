package com.ke.bella.batch.api;

import com.ke.bella.batch.db.repo.BatchRepo;
import com.ke.bella.batch.enums.BatchStatus;
import com.ke.bella.batch.enums.CompletionWindow;
import com.ke.bella.batch.service.BatchService;
import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchControllerTest {

    @Mock
    private BatchService batchService;

    @Mock
    private BatchRepo batchRepo;

    @InjectMocks
    private BatchController batchController;

    private BatchRequest createRequest;
    private Batch batchDetail;
    private ListSearchParameters retrieveRequest;

    @Before
    public void setUp() {
        createRequest = BatchRequest.builder()
                .endpoint("/api/test")
                .inputFileId("file123")
                .completionWindow("24h")
                .build();

        batchDetail = new Batch();
        batchDetail.setId("batch123");
        batchDetail.setStatus(BatchStatus.validating.name());
        batchDetail.setEndpoint("/api/test");
        batchDetail.setInputFileId("file123");
        batchDetail.setCompletionWindow("24h");

        retrieveRequest = new ListSearchParameters();
        retrieveRequest.setLimit(20);
        retrieveRequest.setAfter("");
    }

    @Test
    public void testCreateBatch() {
        when(batchService.create(any(BatchRequest.class), isNull())).thenReturn(batchDetail);

        Batch result = batchController.create(createRequest, null);

        assertNotNull(result);
        assertEquals("batch123", result.getId());
        verify(batchService).create(createRequest, null);
    }

    @Test
    public void testCreateBatchWithQueueHeader() {
        when(batchService.create(any(BatchRequest.class), eq("test-queue"))).thenReturn(batchDetail);

        Batch result = batchController.create(createRequest, "test-queue");

        assertNotNull(result);
        assertEquals("batch123", result.getId());
        verify(batchService).create(createRequest, "test-queue");
    }

    @Test
    public void testCreateBatchWithEmptyQueueHeader() {
        when(batchService.create(any(BatchRequest.class), eq(""))).thenReturn(batchDetail);

        Batch result = batchController.create(createRequest, "");

        assertNotNull(result);
        assertEquals("batch123", result.getId());
        verify(batchService).create(createRequest, "");
    }

    @Test(expected = NullPointerException.class)
    public void testCreateBatchWithNullCompletionWindow() {
        BatchRequest invalidRequest = new BatchRequest();
        invalidRequest.setEndpoint("/api/test");
        invalidRequest.setInputFileId("file123");
        invalidRequest.setCompletionWindow(null);

        batchController.create(invalidRequest, null);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateBatchWithNullInputFileId() {
        BatchRequest invalidRequest = new BatchRequest();
        invalidRequest.setEndpoint("/api/test");
        invalidRequest.setInputFileId(null);
        invalidRequest.setCompletionWindow("24h");

        batchController.create(invalidRequest, null);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateBatchWithNullEndpoint() {
        BatchRequest invalidRequest = new BatchRequest();
        invalidRequest.setEndpoint(null);
        invalidRequest.setInputFileId("file123");
        invalidRequest.setCompletionWindow("24h");

        batchController.create(invalidRequest, null);
    }

    @Test
    public void testCreateBatchWithInvalidCompletionWindow() {
        BatchRequest requestWithInvalidWindow = BatchRequest.builder()
                .endpoint("/api/test")
                .inputFileId("file123")
                .completionWindow("invalid")
                .build();

        when(batchService.create(any(BatchRequest.class), isNull())).thenReturn(batchDetail);

        Batch result = batchController.create(requestWithInvalidWindow, null);

        assertNotNull(result);
        assertEquals(CompletionWindow.DEFAULT, requestWithInvalidWindow.getCompletionWindow());
        verify(batchService).create(requestWithInvalidWindow, null);
    }

    @Test
    public void testRetrieveBatch() {
        String batchId = "batch123";
        when(batchRepo.findBatch(batchId)).thenReturn(batchDetail);

        Batch result = batchController.retrieve(batchId);

        assertNotNull(result);
        assertEquals("batch123", result.getId());
        verify(batchRepo).findBatch(batchId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRetrieveBatchWithNullId() {
        batchController.retrieve(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRetrieveBatchWithEmptyId() {
        batchController.retrieve("");
    }

    @Test
    public void testCancelBatch() {
        String batchId = "batch123";
        Batch cancelledBatch = new Batch();
        cancelledBatch.setId(batchId);
        cancelledBatch.setStatus(BatchStatus.cancelled.name());

        when(batchService.cancel(batchId)).thenReturn(cancelledBatch);

        Batch result = batchController.cancel(batchId);

        assertNotNull(result);
        assertEquals("batch123", result.getId());
        assertEquals(BatchStatus.cancelled.name(), result.getStatus());
        verify(batchService).cancel(batchId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCancelBatchWithNullId() {
        batchController.cancel(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCancelBatchWithEmptyId() {
        batchController.cancel("");
    }

    @Test
    public void testListBatchesWithResults() {
        Batch batch1 = new Batch();
        batch1.setId("batch1");
        batch1.setStatus(BatchStatus.completed.name());
        Batch batch2 = new Batch();
        batch2.setId("batch2");
        batch2.setStatus(BatchStatus.in_progress.name());
        Batch batch3 = new Batch();
        batch3.setId("batch3");
        batch3.setStatus(BatchStatus.validating.name());
        List<Batch> mockBatches = Arrays.asList(batch1, batch2, batch3);

        when(batchRepo.findBatches("", 21)).thenReturn(mockBatches);

        OpenAiResponse<Batch> result = batchController.list(retrieveRequest);

        assertNotNull(result);
        assertEquals(3, result.getData().size());
        assertFalse(result.isHasMore());
        verify(batchRepo).findBatches("", 21);
    }

    @Test
    public void testListBatchesWithMoreResults() {
        Batch batch1 = new Batch();
        batch1.setId("batch1");
        Batch batch2 = new Batch();
        batch2.setId("batch2");
        Batch batch3 = new Batch();
        batch3.setId("batch3");
        List<Batch> mockBatches = Arrays.asList(batch1, batch2, batch3);

        ListSearchParameters limitedRequest = new ListSearchParameters();
        limitedRequest.setLimit(2);
        limitedRequest.setAfter("");

        when(batchRepo.findBatches("", 3)).thenReturn(mockBatches);

        OpenAiResponse<Batch> result = batchController.list(limitedRequest);

        assertNotNull(result);
        assertEquals(2, result.getData().size());
        assertTrue(result.isHasMore());
        verify(batchRepo).findBatches("", 3);
    }

    @Test
    public void testListBatchesEmpty() {
        when(batchRepo.findBatches("", 21)).thenReturn(Collections.emptyList());

        OpenAiResponse<Batch> result = batchController.list(retrieveRequest);

        assertNotNull(result);
        // When empty, the controller returns a new OpenAiResponse() with null data
        assertTrue(result.getData() == null || result.getData().isEmpty());
        assertFalse(result.isHasMore());
        verify(batchRepo).findBatches("", 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testListBatchesWithInvalidLimit() {
        ListSearchParameters invalidRequest = new ListSearchParameters();
        invalidRequest.setLimit(0);
        invalidRequest.setAfter("");

        batchController.list(invalidRequest);
    }
}
