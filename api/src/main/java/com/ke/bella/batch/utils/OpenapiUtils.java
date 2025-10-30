package com.ke.bella.batch.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.batch.service.Configs;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.route.RouteResult;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.queue.QueueMode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
public class OpenapiUtils {

    static OpenapiClient openApiClient;

    static OpenAiServiceFactory openAiServiceFactory;

    private static final Cache<String, String> QUEUE_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build();

    public synchronized static void initialize(OpenapiClient openApiClient, OpenAiServiceFactory openAiServiceFactory) {
        if(OpenapiUtils.openApiClient == null) {
            OpenapiUtils.openApiClient = openApiClient;
        }
        if(OpenapiUtils.openAiServiceFactory == null) {
            OpenapiUtils.openAiServiceFactory = openAiServiceFactory;
        }
    }

    public static OpenapiClient getInstance() {
        return openApiClient;
    }

    @SneakyThrows
    public static String exchangeQueueName(String endpoint, Map<String, Object> data) {
        String model = MapUtils.getString(data, "model");
        if(StringUtils.isBlank(model)) {
            throw new IllegalArgumentException("model must not be empty");
        }

        return QUEUE_CACHE.get(endpoint + ":" + model, () -> {
            RouteResult routeResult = getInstance().route(
                    endpoint, model, QueueMode.ROUTE.getCode()
                    , BellaContext.getApikey().getApikey()
                    , Configs.OPENAPI_CONSOLE_KEY);
            return Optional.ofNullable(routeResult)
                    .map(RouteResult::getQueueName)
                    .orElse(null);
        });
    }

    private static final Charset FILE_CHARSET = StandardCharsets.UTF_8;

    public static String saveStringAsFile(String data) {
        String fileName = UUID.randomUUID() + ".txt";
        return openAiServiceFactory.create(Configs.OPENAPI_CONSOLE_KEY)
                .uploadFile(Configs.FILE_API_PURPOSE, data.getBytes(), fileName).getId();
    }

    public static String fetchStringFromFile(String fileId) {
        if(StringUtils.isBlank(fileId)) {
            return null;
        }
        byte[] fileContent;
        try {
            fileContent = openAiServiceFactory.create(Configs.OPENAPI_CONSOLE_KEY).retrieveFileContent(fileId).bytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new String(fileContent, FILE_CHARSET);
    }

    @SneakyThrows
    public static void download(String fileId, Path path) {
        try {
            openAiServiceFactory.create().retrieveFileContentAndSave(fileId, path);
        } catch (IOException e) {
            log.error("Failed to download file after all retry attempts: {} to {}", fileId, path, e);
            throw new IllegalStateException("Failed to download file: " + fileId, e);
        }
    }

    //todo: 下沉到openai4j
    @SneakyThrows
    public static <T> T peekFirstLine(String fileId, String apikey, Function<String, T> processor) {
        String fileUrl = String.format("%s/v1/files/%s/content", Configs.OPENAPI_HOST, fileId);
        String authorization = "Bearer " + apikey;

        return Optional.ofNullable(HttpUtils.readFirstLine(fileUrl, authorization))
                .filter(line -> !line.isEmpty())
                .map(processor)
                .orElse(null);
    }

    @SneakyThrows
    public static String uploadFile(Path filePath, String apikey) {
        if(filePath == null || !Files.exists(filePath)) {
            return null;
        }
        try {
            return openAiServiceFactory.create(apikey).uploadFile(Configs.FILE_API_PURPOSE, filePath).getId();
        } catch (IllegalStateException e) {
            log.error("Failed to upload file after all retry attempts: {}", filePath, e);
            throw new IllegalStateException("Failed to upload file: " + filePath, e);
        }
    }

}
