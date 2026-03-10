package com.ke.bella.batch.utils;

import com.ke.bella.batch.service.Configs;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.route.RouteResult;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.queue.QueueMode;
import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OpenapiUtilsTest {

    @Mock
    private OpenapiClient mockOpenapiClient;

    @Mock
    private OpenAiServiceFactory mockOpenAiServiceFactory;

    @Mock
    private OpenAiService mockOpenAiService;

    @Mock
    private File mockFile;

    private static final String TEST_APIKEY = "test-api-key-12345";
    private static final String TEST_CONSOLE_KEY = "test-console-key-12345";

    @Before
    public void setUp() {
        // Clear any previous static instance
        ReflectionTestUtils.setField(OpenapiUtils.class, "INSTANCE", null);

        // Create OpenapiUtils instance (simulates Spring bean creation)
        new OpenapiUtils(mockOpenapiClient, mockOpenAiServiceFactory, mockOpenAiService);

        // Set up BellaContext
        BellaContext.setApikey(ApikeyInfo.builder().apikey(TEST_APIKEY).build());

        // Set up Configs
        Configs.OPENAPI_CONSOLE_KEY = TEST_CONSOLE_KEY;
        Configs.OPENAPI_HOST = "https://api.test-example.com";
        Configs.FILE_API_PURPOSE = "batch";

        // Clear cache before each test
        try {
            Object cache = ReflectionTestUtils.getField(OpenapiUtils.class, "QUEUE_CACHE");
            if(cache != null) {
                ReflectionTestUtils.invokeMethod(cache, "invalidateAll");
            }
        } catch (Exception e) {
            // Ignore if cache field is not accessible
        }
    }

    @Test
    public void testGetInstance() {
        OpenapiClient result = OpenapiUtils.getInstance();
        assertEquals(mockOpenapiClient, result);
    }

    @Test
    public void testExchangeQueueName_WithWorkerMode0() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        RouteResult route0 = mock(RouteResult.class);
        when(route0.getWorkerMode()).thenReturn(0);
        when(route0.getQueueName()).thenReturn("queue-mode-0");

        when(mockOpenapiClient.listAvailableChannels(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(java.util.Arrays.asList(route0));

        String result = OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 1);

        assertEquals("queue-mode-0", result);
        verify(mockOpenapiClient).listAvailableChannels(eq("/v1/chat/completions"), eq("gpt-4"),
                eq(QueueMode.ROUTE.getCode()), eq(TEST_APIKEY));
    }

    @Test
    public void testExchangeQueueName_Level1_WithWorkerMode2() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        RouteResult route2 = mock(RouteResult.class);
        when(route2.getWorkerMode()).thenReturn(2);
        when(route2.getQueueName()).thenReturn("queue-mode-2");

        when(mockOpenapiClient.listAvailableChannels(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(java.util.Arrays.asList(route2));

        String result = OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 1);

        assertEquals("queue-mode-2", result);
    }

    @Test
    public void testExchangeQueueName_Level1_WithWorkerMode1() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        RouteResult route1 = mock(RouteResult.class);
        when(route1.getWorkerMode()).thenReturn(1);
        when(route1.getQueueName()).thenReturn("queue-mode-1");

        when(mockOpenapiClient.listAvailableChannels(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(java.util.Arrays.asList(route1));

        String result = OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 1);

        assertEquals("queue-mode-1", result);
    }

    @Test
    public void testExchangeQueueName_Level0_WithWorkerMode1() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        RouteResult route1 = mock(RouteResult.class);
        when(route1.getWorkerMode()).thenReturn(1);
        when(route1.getQueueName()).thenReturn("queue-mode-1");

        when(mockOpenapiClient.listAvailableChannels(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(java.util.Arrays.asList(route1));

        String result = OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 0);

        assertEquals("queue-mode-1", result);
    }

    @Test
    public void testExchangeQueueName_Cache() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        RouteResult route0 = mock(RouteResult.class);
        when(route0.getWorkerMode()).thenReturn(0);
        when(route0.getQueueName()).thenReturn("cached-queue");

        when(mockOpenapiClient.listAvailableChannels(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(java.util.Arrays.asList(route0));

        String result1 = OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 0);
        String result2 = OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 0);

        assertEquals("cached-queue", result1);
        assertEquals("cached-queue", result2);
        verify(mockOpenapiClient, times(1)).listAvailableChannels(anyString(), anyString(), anyInt(), anyString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExchangeQueueName_EmptyModel() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "");

        OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExchangeQueueName_NullModel() {
        Map<String, Object> data = new HashMap<>();

        OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testExchangeQueueName_EmptyRouteResults() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        when(mockOpenapiClient.listAvailableChannels(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(java.util.Collections.emptyList());

        OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testExchangeQueueName_NoMatchingWorkerMode() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        RouteResult route3 = mock(RouteResult.class);
        when(route3.getWorkerMode()).thenReturn(3);
        when(route3.getQueueName()).thenReturn("queue-mode-3");

        when(mockOpenapiClient.listAvailableChannels(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(java.util.Arrays.asList(route3));

        OpenapiUtils.exchangeQueueName("/v1/chat/completions", data, 0);
    }

    @Test
    public void testSaveStringAsFile_Success() {
        String testData = "test file content";
        String expectedFileId = "file-123";

        when(mockFile.getId()).thenReturn(expectedFileId);
        when(mockOpenAiService.uploadFile(eq(Configs.FILE_API_PURPOSE), any(byte[].class), anyString()))
                .thenReturn(mockFile);

        String result = OpenapiUtils.saveStringAsFile(testData);

        assertEquals(expectedFileId, result);
        verify(mockOpenAiService).uploadFile(eq(Configs.FILE_API_PURPOSE),
                eq(testData.getBytes()), anyString());
    }

    @Test
    public void testFetchStringFromFile_Success() throws IOException {
        String fileId = "file-123";
        String expectedContent = "file content";
        byte[] contentBytes = expectedContent.getBytes();

        okhttp3.ResponseBody mockResponseBody = mock(okhttp3.ResponseBody.class);
        when(mockResponseBody.bytes()).thenReturn(contentBytes);

        when(mockOpenAiService.retrieveFileContent(fileId)).thenReturn(mockResponseBody);

        String result = OpenapiUtils.fetchStringFromFile(fileId);

        assertEquals(expectedContent, result);
        verify(mockOpenAiService).retrieveFileContent(fileId);
    }

    @Test
    public void testFetchStringFromFile_BlankFileId() {
        String result1 = OpenapiUtils.fetchStringFromFile("");
        String result2 = OpenapiUtils.fetchStringFromFile(null);
        String result3 = OpenapiUtils.fetchStringFromFile("   ");

        assertNull(result1);
        assertNull(result2);
        assertNull(result3);
    }

    @Test(expected = IllegalStateException.class)
    public void testFetchStringFromFile_IOException() throws IOException {
        String fileId = "file-123";

        okhttp3.ResponseBody mockResponseBody = mock(okhttp3.ResponseBody.class);
        when(mockResponseBody.bytes()).thenThrow(new IOException("Network error"));

        when(mockOpenAiService.retrieveFileContent(fileId)).thenReturn(mockResponseBody);

        OpenapiUtils.fetchStringFromFile(fileId);
    }

    @Test
    public void testDownload_Success() throws IOException {
        String fileId = "file-123";
        Path testPath = Paths.get("/tmp/test-file.txt");

        OpenapiUtils.download(fileId, testPath);

        verify(mockOpenAiService).retrieveFileContentAndSave(fileId, testPath);
    }

    @Test(expected = IllegalStateException.class)
    public void testDownload_IOException() throws IOException {
        String fileId = "file-123";
        Path testPath = Paths.get("/tmp/test-file.txt");

        doThrow(new IOException("Download failed")).when(mockOpenAiService)
                .retrieveFileContentAndSave(fileId, testPath);

        OpenapiUtils.download(fileId, testPath);
    }

    @Test
    public void testPeekFirstLine_Success() {
        String fileId = "file-123";
        String apikey = "test-key";
        String expectedLine = "first line content";

        try (MockedStatic<HttpUtils> mockedHttpUtils = mockStatic(HttpUtils.class)) {
            mockedHttpUtils.when(() -> HttpUtils.readFirstLine(anyString(), anyString()))
                    .thenReturn(expectedLine);

            String result = OpenapiUtils.peekFirstLine(fileId, apikey, line -> line);

            assertEquals(expectedLine, result);

            String expectedUrl = String.format("%s/v1/files/%s/content", Configs.OPENAPI_HOST, fileId);
            String expectedAuth = "Bearer " + apikey;
            mockedHttpUtils.verify(() -> HttpUtils.readFirstLine(expectedUrl, expectedAuth));
        }
    }

    @Test
    public void testPeekFirstLine_EmptyLine() {
        String fileId = "file-123";
        String apikey = "test-key";

        try (MockedStatic<HttpUtils> mockedHttpUtils = mockStatic(HttpUtils.class)) {
            mockedHttpUtils.when(() -> HttpUtils.readFirstLine(anyString(), anyString()))
                    .thenReturn("");

            String result = OpenapiUtils.peekFirstLine(fileId, apikey, line -> line);

            assertNull(result);
        }
    }

    @Test
    public void testPeekFirstLine_NullLine() {
        String fileId = "file-123";
        String apikey = "test-key";

        try (MockedStatic<HttpUtils> mockedHttpUtils = mockStatic(HttpUtils.class)) {
            mockedHttpUtils.when(() -> HttpUtils.readFirstLine(anyString(), anyString()))
                    .thenReturn(null);

            String result = OpenapiUtils.peekFirstLine(fileId, apikey, line -> line);

            assertNull(result);
        }
    }

    @Test
    public void testUploadFile_Success() {
        Path testPath = Paths.get("/tmp/test.txt");
        String apikey = "test-key";
        String expectedFileId = "file-456";

        when(mockFile.getId()).thenReturn(expectedFileId);
        when(mockOpenAiServiceFactory.create(apikey)).thenReturn(mockOpenAiService);
        when(mockOpenAiService.uploadFile(Configs.FILE_API_PURPOSE, testPath)).thenReturn(mockFile);

        try (MockedStatic<java.nio.file.Files> mockedFiles = mockStatic(java.nio.file.Files.class)) {
            mockedFiles.when(() -> java.nio.file.Files.exists(testPath)).thenReturn(true);

            String result = OpenapiUtils.uploadFile(testPath, apikey);

            assertEquals(expectedFileId, result);
            verify(mockOpenAiService).uploadFile(Configs.FILE_API_PURPOSE, testPath);
        }
    }

    @Test
    public void testUploadFile_NullPath() {
        String result = OpenapiUtils.uploadFile(null, "test-key");
        assertNull(result);
    }

    @Test
    public void testUploadFile_FileNotExists() {
        Path testPath = Paths.get("/tmp/nonexistent.txt");

        try (MockedStatic<java.nio.file.Files> mockedFiles = mockStatic(java.nio.file.Files.class)) {
            mockedFiles.when(() -> java.nio.file.Files.exists(testPath)).thenReturn(false);

            String result = OpenapiUtils.uploadFile(testPath, "test-key");

            assertNull(result);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testUploadFile_Exception() {
        Path testPath = Paths.get("/tmp/test.txt");
        String apikey = "test-key";

        when(mockOpenAiServiceFactory.create(apikey)).thenReturn(mockOpenAiService);
        when(mockOpenAiService.uploadFile(Configs.FILE_API_PURPOSE, testPath))
                .thenThrow(new IllegalStateException("Upload failed"));

        try (MockedStatic<java.nio.file.Files> mockedFiles = mockStatic(java.nio.file.Files.class)) {
            mockedFiles.when(() -> java.nio.file.Files.exists(testPath)).thenReturn(true);

            OpenapiUtils.uploadFile(testPath, apikey);
        }
    }
}
