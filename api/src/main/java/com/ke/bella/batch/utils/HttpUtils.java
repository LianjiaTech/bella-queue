package com.ke.bella.batch.utils;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategy;
import com.ke.bella.batch.service.Configs;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

@Slf4j
@SuppressWarnings("all")
public class HttpUtils {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = createDefaultClient();

    private HttpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static OkHttpClient createDefaultClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(Configs.TASK_THREAD_NUMS);
        dispatcher.setMaxRequestsPerHost(Configs.TASK_THREAD_NUMS);

        return new OkHttpClient.Builder()
                .addInterceptor(logging)
                .dispatcher(dispatcher)
                .connectionPool(new ConnectionPool(Configs.TASK_THREAD_NUMS, 60, TimeUnit.SECONDS))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @SneakyThrows
    public static int postJson(String url, Object data) {
        String json = JsonUtils.toJson(data);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            return response.code();
        } catch (IOException e) {
            log.error("HTTP POST request failed for url: {}", url, e);
            throw e;
        }
    }

    public static void postWithRetry(String url, Object data) {
        postWithRetry(url, data, DEFAULT_RETRYER);
    }

    public static void postWithRetry(String url, Object data, Retryer<Void> retryer) {
        try {
            retryer.call(() -> {
                int responseCode = postJson(url, data);
                if(responseCode < 200 || responseCode >= 300) {
                    String errorMsg = String.format("post failed with HTTP %d for URL: %s", responseCode, url);
                    log.warn(errorMsg);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("HTTP POST request failed after retries for URL: {}", url, e);
        }
    }

    public static final Retryer<Void> DEFAULT_RETRYER = createRetryer(4, new long[] { 2, 5, 10 });

    public static Retryer<Void> createRetryer(int maxAttempts, long[] delaySeconds) {
        return RetryerBuilder.<Void>newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .retryIfExceptionOfType(ConnectException.class)
                .retryIfExceptionOfType(SocketTimeoutException.class)
                .withWaitStrategy(createWaitStrategy(delaySeconds))
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxAttempts))
                .build();
    }

    private static WaitStrategy createWaitStrategy(long[] delaySeconds) {
        return attemptHistory -> {
            int attemptIndex = Math.min((int) attemptHistory.getAttemptNumber() - 1, delaySeconds.length - 1);
            return TimeUnit.SECONDS.toMillis(delaySeconds[attemptIndex]);
        };
    }

    @SneakyThrows
    public static String readFirstLine(String url, String authorization) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", authorization)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if(!response.isSuccessful()) {
                throw new IOException("HTTP Error: " + response.code());
            }

            try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
                return reader.readLine();
            }
        }
    }
}
