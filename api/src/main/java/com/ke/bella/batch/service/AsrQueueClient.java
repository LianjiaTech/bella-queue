package com.ke.bella.batch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.batch.exception.AsrQueueServiceException;
import com.ke.bella.batch.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * ASR队列客户端
 * 封装对外部bella-queue服务的HTTP调用
 *
 * 参考: bella-asr/app/services/queue_client.py
 */
@Component
@Slf4j
public class AsrQueueClient {

    @Resource(name = "asrRestTemplate")
    private RestTemplate restTemplate;

    @Value("${bella.asr.queue.service-host}")
    private String queueServiceHost;

    @Value("${bella.asr.queue.api-key}")
    private String queueApiKey;

    @Value("${bella.asr.queue.timeout:300}")
    private Integer defaultTimeout;

    @Value("${bella.asr.queue.retries:3}")
    private Integer maxRetries;

    /**
     * 提交任务到bella-queue服务
     *
     * @param endpoint 回调端点
     * @param queue 队列名称（通常是model名称）
     * @param data 任务数据
     * @param level 优先级
     * @param timeout 超时时间（秒）
     * @return 队列返回的task_id
     * @throws RuntimeException 如果提交失败
     */
    public String submitTask(String endpoint, String queue, Map<String, Object> data,
                             Integer level, Integer timeout) {
        String url = String.format("https://%s/v1/queue/put", queueServiceHost);

        // 构建请求payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("endpoint", endpoint);
        payload.put("queue", queue);
        payload.put("level", level != null ? level : 1);
        payload.put("data", data);
        payload.put("response_mode", "callback");
        payload.put("timeout", timeout != null ? timeout : defaultTimeout);

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + queueApiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        // 重试机制
        int attempt = 1;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                log.info("Submitting task to queue service - endpoint={}, queue={}, attempt={}/{}",
                        endpoint, queue, attempt, maxRetries);

                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        request,
                        String.class
                );

                // 解析响应
                JsonNode jsonResponse = JsonUtils.readTree(response.getBody());
                Integer code = jsonResponse.get("code").asInt();

                if (code != 200) {
                    throw new AsrQueueServiceException(
                        String.format("Queue service returned error code: %d", code)
                    );
                }

                JsonNode dataNode = jsonResponse.get("data");
                if (dataNode == null || dataNode.isNull()) {
                    throw new AsrQueueServiceException("No task_id in queue service response");
                }

                String taskId = dataNode.asText();
                log.info("Task submitted successfully - task_id={}, queue={}", taskId, queue);
                return taskId;

            } catch (HttpStatusCodeException e) {
                lastException = e;
                log.error("Queue service HTTP error - status={}, attempt={}/{}",
                        e.getStatusCode(), attempt, maxRetries, e);

                if (attempt == maxRetries) {
                    throw new AsrQueueServiceException(
                        String.format("Queue service error: %s", e.getStatusCode()),
                        e
                    );
                }

                // 指数退避
                try {
                    long sleepMs = (long) Math.pow(2, attempt - 1) * 1000;
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AsrQueueServiceException("Request interrupted during retry", ie);
                }
                attempt++;

            } catch (RestClientException e) {
                lastException = e;
                log.error("Queue service request failed - attempt={}/{}",
                        attempt, maxRetries, e);

                if (attempt == maxRetries) {
                    throw new AsrQueueServiceException(
                        String.format("Queue service request failed: %s", e.getMessage()),
                        e
                    );
                }

                // 指数退避
                try {
                    long sleepMs = (long) Math.pow(2, attempt - 1) * 1000;
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AsrQueueServiceException("Request interrupted during retry", ie);
                }
                attempt++;

            } catch (Exception e) {
                lastException = e;
                log.error("Unexpected error submitting task - attempt={}/{}",
                        attempt, maxRetries, e);

                if (attempt == maxRetries) {
                    throw new AsrQueueServiceException(
                        String.format("Unexpected error: %s", e.getMessage()),
                        e
                    );
                }

                // 指数退避
                try {
                    long sleepMs = (long) Math.pow(2, attempt - 1) * 1000;
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AsrQueueServiceException("Request interrupted during retry", ie);
                }
                attempt++;
            }
        }

        throw new AsrQueueServiceException(
            String.format("Failed to submit task after %d attempts", maxRetries),
            lastException
        );
    }
}
