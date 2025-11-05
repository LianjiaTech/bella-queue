package com.ke.bella.batch.service;

import com.ke.bella.openapi.server.BellaServerContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class Configs {

    public static String INSTANCE_ID = String.format("%s:%s"
            , BellaServerContextHolder.getIp()
            , BellaServerContextHolder.getPort());

    public static int ONLINE_QUEUE_CAPACITY = 10000;
    public static int OFFLINE_QUEUE_CAPACITY = 1000;

    public static String OPENAPI_HOST;
    public static String OPENAPI_CONSOLE_KEY;

    public static String SECRET_KEY;
    public static final int TASK_THREAD_NUMS = 1000;

    public static String BATCH_FILE_BASE_PATH;
    public static final String OUTPUT_MERGED_FILE_PREFIX = "output_";
    public static final String OUTPUT_MERGED_FILE_SUFFIX = ".jsonl";
    public static final String OUTPUT_PATTERN = "output_*.jsonl";
    public static final String ERROR_MERGED_FILE_PREFIX = "errors_";
    public static final String ERROR_MERGED_FILE_SUFFIX = ".jsonl";
    public static final String ERROR_PATTERN = "error_*.jsonl";

    public static int BATCH_THREAD_SIZE = 5;

    public static String FILE_API_PURPOSE = "batch";

    @Value("${bella.openapi.host}")
    public void setOpenapiHost(String openapiHost) {
        OPENAPI_HOST = openapiHost;
    }

    @Value("${bella.openapi.apikey}")
    public void setOpenapiConsoleKey(String openapiConsoleKey) {
        OPENAPI_CONSOLE_KEY = openapiConsoleKey;
    }

    @Value("${bella.queue.secretKey}")
    public void setSecretKey(String secretKey) {
        SECRET_KEY = secretKey;
    }

    @Value("${bella.queue.batch.threadSize:5}")
    public void setBatchThreadSize(Integer batchThreadSize) {
        BATCH_THREAD_SIZE = batchThreadSize;
    }

    @Value("${bella.queue.batch.file.path:/tmp/bella-queue/batch}")
    public void setBatchFileBasePath(String batchFileBasePath) {
        BATCH_FILE_BASE_PATH = batchFileBasePath;
    }

    @Value("${bella.queue.online.capacity:10000}")
    public void setOnlineQueueCapacity(Integer onlineQueueCapacity) {
        ONLINE_QUEUE_CAPACITY = onlineQueueCapacity;
    }

    @Value("${bella.queue.offline.capacity:1000}")
    public void setOfflineQueueCapacity(Integer offlineQueueCapacity) {
        OFFLINE_QUEUE_CAPACITY = offlineQueueCapacity;
    }

    public static Path getBatchBasePath() {
        return Paths.get(BATCH_FILE_BASE_PATH);
    }

    public static Path getBatchDir(String batchId) {
        return getBatchBasePath().resolve(batchId);
    }

    public static Path getBatchInputFile(String batchId) {
        return getBatchDir(batchId).resolve(batchId + ".jsonl");
    }

    public static Path getBatchErrorFile(String batchId) {
        return getBatchDir(batchId).resolve(ERROR_MERGED_FILE_PREFIX + batchId + ERROR_MERGED_FILE_SUFFIX);
    }

    public static Path getBatchOutputFile(String batchId) {
        return getBatchDir(batchId).resolve(OUTPUT_MERGED_FILE_PREFIX + batchId + OUTPUT_MERGED_FILE_SUFFIX);
    }
}
