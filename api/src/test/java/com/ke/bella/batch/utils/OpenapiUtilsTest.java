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
    private RouteResult mockRouteResult;

    @Mock
    private File mockFile;

    private static final String TEST_APIKEY = "test-api-key-12345";
    private static final String TEST_CONSOLE_KEY = "test-console-key-12345";

    @Before
    public void setUp() {
        // Clear any previous static state
        ReflectionTestUtils.setField(OpenapiUtils.class, "openApiClient", null);
        ReflectionTestUtils.setField(OpenapiUtils.class, "openAiServiceFactory", null);

        // Initialize OpenapiUtils
        OpenapiUtils.initialize(mockOpenapiClient, mockOpenAiServiceFactory);

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
    public void testExchangeQueueName_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        when(mockRouteResult.getQueueName()).thenReturn("test-queue");
        when(mockOpenapiClient.route(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(mockRouteResult);

        String result = OpenapiUtils.exchangeQueueName("/v1/chat/completions", data);

        assertEquals("test-queue", result);
        verify(mockOpenapiClient).route(eq("/v1/chat/completions"), eq("gpt-4"),
                anyInt(), eq(TEST_APIKEY), eq(TEST_CONSOLE_KEY));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExchangeQueueName_EmptyModel() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "");

        OpenapiUtils.exchangeQueueName("/v1/chat/completions", data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExchangeQueueName_NullModel() {
        Map<String, Object> data = new HashMap<>();

        OpenapiUtils.exchangeQueueName("/v1/chat/completions", data);
    }

    @Test(expected = RuntimeException.class)
    public void testExchangeQueueName_NullRouteResult() {
        Map<String, Object> data = new HashMap<>();
        data.put("model", "gpt-4");

        when(mockOpenapiClient.route(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(null);

        // This should throw an exception because Guava cache doesn't allow null values
        OpenapiUtils.exchangeQueueName("/v1/chat/completions", data);
    }

    @Test
    public void testSaveStringAsFile_Success() {
        String testData = "test file content";
        String expectedFileId = "file-123";

        when(mockFile.getId()).thenReturn(expectedFileId);
        when(mockOpenAiServiceFactory.create(TEST_CONSOLE_KEY)).thenReturn(mockOpenAiService);
        when(mockOpenAiService.uploadFile(eq(Configs.FILE_API_PURPOSE), any(byte[].class), anyString()))
                .thenReturn(mockFile);

        String result = OpenapiUtils.saveStringAsFile(testData);

        assertEquals(expectedFileId, result);
        verify(mockOpenAiServiceFactory).create(TEST_CONSOLE_KEY);
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

        when(mockOpenAiServiceFactory.create(TEST_CONSOLE_KEY)).thenReturn(mockOpenAiService);
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

        when(mockOpenAiServiceFactory.create(TEST_CONSOLE_KEY)).thenReturn(mockOpenAiService);
        when(mockOpenAiService.retrieveFileContent(fileId)).thenReturn(mockResponseBody);

        OpenapiUtils.fetchStringFromFile(fileId);
    }

    @Test
    public void testDownload_Success() throws IOException {
        String fileId = "file-123";
        Path testPath = Paths.get("/tmp/test-file.txt");

        when(mockOpenAiServiceFactory.create()).thenReturn(mockOpenAiService);

        OpenapiUtils.download(fileId, testPath);

        verify(mockOpenAiService).retrieveFileContentAndSave(fileId, testPath);
    }

    @Test(expected = IllegalStateException.class)
    public void testDownload_IOException() throws IOException {
        String fileId = "file-123";
        Path testPath = Paths.get("/tmp/test-file.txt");

        when(mockOpenAiServiceFactory.create()).thenReturn(mockOpenAiService);
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
