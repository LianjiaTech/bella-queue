package com.ke.bella.batch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.batch.db.repo.AsrTaskRepo;
import com.ke.bella.batch.exception.AsrQueueServiceException;
import com.ke.bella.batch.exception.AsrTaskNotFoundException;
import com.ke.bella.batch.model.asr.*;
import com.ke.bella.batch.utils.HttpUtils;
import com.ke.bella.batch.utils.JsonUtils;
import com.ke.bella.batch.utils.OpenapiUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ASR业务逻辑服务
 * 协调ASR任务的完整生命周期
 *
 * 参考: bella-asr/app/services/transcription.py
 */
@Service
@Slf4j
public class AsrService {

    @Resource
    private AsrTaskRepo taskRepo;

    @Resource
    private AsrQueueClient queueClient;

    @Resource(name = "asrRestTemplate")
    private RestTemplate restTemplate;

    private static final java.util.function.Predicate<String> DATA_TOO_LARGE = input -> input.length() * 3 > 65535;

    /**
     * 创建转写任务
     *
     * @param request 转写请求
     * @param authorization 认证头（可选）
     * @return 转写响应
     */
    public AudioTranscriptionResponse createTranscription(AudioTranscriptionRequest request,
                                                          String authorization) {
        log.info("Received transcription request - model={}, user={}, url={}",
                request.getModel(), request.getUser(), maskUrl(request.getUrl()));

        try {
            // 1. 转换请求为Map（用于队列数据）
            Map<String, Object> taskData = convertRequestToMap(request);

            // 2. 提交到bella-queue服务
            String queueTaskId = queueClient.submitTask(
                    Configs.ASR_QUEUE_ENDPOINT,
                    request.getModel(),
                    taskData,
                    Configs.ASR_QUEUE_DEFAULT_LEVEL,
                    Configs.ASR_QUEUE_DEFAULT_TIMEOUT
            );

            log.info("Task submitted to queue - queue_task_id={}, model={}", queueTaskId, request.getModel());

            // 3. 获取AK Code
            String akCode = fetchAkCodeBySha(authorization);
            if (akCode == null || akCode.isEmpty()) {
                log.warn("Failed to fetch ak_code - queue_task_id={}, user={}", queueTaskId, request.getUser());
            }

            // 4. 准备数据库保存数据
            String inputJson = JsonUtils.toJson(taskData);

            Map<String, Object> dbTaskData = new HashMap<>();
            dbTaskData.put("taskId", queueTaskId);
            dbTaskData.put("akCode", akCode);
            dbTaskData.put("user", request.getUser());
            dbTaskData.put("model", request.getModel());
            dbTaskData.put("status", "pending");
            dbTaskData.put("callbackUrl", request.getCallbackUrl());

            // 5. 检查数据大小，如果超过限制则上传到文件服务
            if (DATA_TOO_LARGE.test(inputJson)) {
                log.info("Input data too large, uploading to file service - queue_task_id={}, size={}",
                        queueTaskId, inputJson.length());
                String fileId = OpenapiUtils.saveStringAsFile(inputJson);
                dbTaskData.put("inputFileId", fileId);
                dbTaskData.put("inputData", "");
            } else {
                dbTaskData.put("inputData", inputJson);
            }

            // 6. 保存到数据库（通过 taskId 时间戳确定性路由到分片）
            taskRepo.saveTask(queueTaskId, dbTaskData);

            log.info("Task created successfully - task_id={}, user={}, model={}",
                    queueTaskId, request.getUser(), request.getModel());

            return new AudioTranscriptionResponse(queueTaskId);

        } catch (AsrQueueServiceException e) {
            // 重新抛出队列服务异常（502）
            throw e;
        } catch (Exception e) {
            log.error("Failed to create transcription - model={}, user={}",
                    request.getModel(), request.getUser(), e);
            // 通用异常统一处理为 500（由 AsrResponseAdvice 捕获）
            throw new RuntimeException("Failed to create transcription: " + e.getMessage(), e);
        }
    }

    /**
     * 查询单个任务
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    public QuerySingleTaskResponse querySingleTranscription(String taskId) {
        log.info("Query single transcription request - task_id={}", taskId);

        AsrTaskDTO task = taskRepo.findByTaskId(taskId);

        if (task == null) {
            log.warn("Task not found - task_id={}", taskId);
            throw new AsrTaskNotFoundException(taskId);
        }

        // 构建响应
        QuerySingleTaskResponse response = new QuerySingleTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setStatus(task.getStatus());
        response.setUser(task.getUser());
        response.setModel(task.getModel());
        response.setAkCode(task.getAkCode());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        response.setCompletedAt(task.getCompletedAt());
        response.setInputFileId(task.getInputFileId());
        response.setOutputFileId(task.getOutputFileId());
        response.setCallbackUrl(task.getCallbackUrl());

        // 解析JSON数据（优先从文件服务获取）
        try {
            String inputData = resolveData(task.getInputData(), task.getInputFileId());
            if (StringUtils.isNotBlank(inputData)) {
                response.setInputData(JsonUtils.fromJson(inputData, Map.class));
            }
        } catch (Exception e) {
            log.error("Failed to parse input_data - task_id={}", taskId, e);
        }

        try {
            String outputData = resolveData(task.getOutputData(), task.getOutputFileId());
            if (StringUtils.isNotBlank(outputData)) {
                response.setOutputData(JsonUtils.fromJson(outputData, Map.class));
            }
        } catch (Exception e) {
            log.error("Failed to parse output_data - task_id={}", taskId, e);
        }

        log.info("Query single transcription result - task_id={}, status={}", taskId, task.getStatus());

        return response;
    }

    /**
     * 批量查询任务
     *
     * @param taskIds 任务ID列表
     * @return 任务结果列表
     */
    public List<Map<String, Object>> queryTranscriptions(List<String> taskIds) {
        log.info("Query transcriptions request - task_ids={}, count={}", taskIds, taskIds.size());

        List<AsrTaskDTO> tasks = taskRepo.findByTaskIds(taskIds);
        Map<String, AsrTaskDTO> taskMap = tasks.stream()
                .collect(Collectors.toMap(AsrTaskDTO::getTaskId, task -> task));

        List<Map<String, Object>> results = new ArrayList<>();

        for (String taskId : taskIds) {
            AsrTaskDTO task = taskMap.get(taskId);

            if (task == null) {
                log.warn("Task not found - task_id={}", taskId);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Task not found");
                errorResult.put("task_id", taskId);
                results.add(errorResult);
                continue;
            }

            try {
                String outputData = resolveData(task.getOutputData(), task.getOutputFileId());
                if (StringUtils.isNotBlank(outputData)) {
                    Map<String, Object> resultData = JsonUtils.fromJson(outputData, Map.class);
                    results.add(resultData);
                } else {
                    Map<String, Object> statusResult = new HashMap<>();
                    statusResult.put("status", task.getStatus());
                    statusResult.put("task_id", taskId);
                    statusResult.put("message", "Task processing or no result available");
                    results.add(statusResult);
                }
            } catch (Exception e) {
                log.error("Failed to parse output_data - task_id={}", taskId, e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Invalid output data format");
                errorResult.put("task_id", taskId);
                results.add(errorResult);
            }
        }

        log.info("Query transcriptions result - count={}, found={}", results.size(), tasks.size());

        return results;
    }

    /**
     * 处理回调
     *
     * @param callbackData 回调数据
     */
    public void handleCallback(AsrCallbackRequest callbackData) {
        log.info("Received ASR callback - task_id={}, status={}, user={}",
                callbackData.getTaskId(), callbackData.getStatusCode(), callbackData.getUser());

        try {
            // 1. 查询任务以获取callback_url、ak_code和model
            AsrTaskDTO task = taskRepo.findByTaskId(callbackData.getTaskId());

            if (task == null) {
                log.error("Task not found in database during callback - task_id={}", callbackData.getTaskId());
                throw new AsrTaskNotFoundException(callbackData.getTaskId());
            }

            // 2. 幂等性检查：任务已经处于终态
            if ("completed".equals(task.getStatus()) || "failed".equals(task.getStatus())) {
                log.warn("Task already in final state - task_id={}, status={}",
                         callbackData.getTaskId(), task.getStatus());
                return;  // 直接返回，不处理
            }

            String callbackUrl = task.getCallbackUrl();
            String akCode = task.getAkCode();
            String model = task.getModel();

            log.info("Retrieved from DB - task_id={}, callback_url={}, ak_code={}, model={}",
                    callbackData.getTaskId(), callbackUrl, akCode, model);

            // 3. 准备输出数据
            String outputJson = JsonUtils.toJson(callbackData);
            String status = (callbackData.getError() == null || callbackData.getError().isEmpty()) ?
                    "completed" : "failed";

            String outputData = null;
            String outputFileId = null;

            // 4. 检查输出大小
            if (DATA_TOO_LARGE.test(outputJson)) {
                log.info("Output data too large, uploading to file service - task_id={}, size={}",
                        callbackData.getTaskId(), outputJson.length());
                outputFileId = OpenapiUtils.saveStringAsFile(outputJson);
                outputData = "";
            } else {
                outputData = outputJson;
            }

            // 5. 更新数据库（带乐观锁）
            boolean updated = taskRepo.updateTaskOutput(
                    callbackData.getTaskId(),
                    status,
                    outputData,
                    outputFileId
            );

            if (!updated) {
                log.error("Failed to update task output - task_id={}, possibly already completed",
                         callbackData.getTaskId());
                throw new RuntimeException("Update failed, task may be already completed");
            }

            log.info("Callback saved to DB - task_id={}, status={}", callbackData.getTaskId(), status);

            // 7. 用量上报（仅对completed状态的任务，失败不影响主流程）
            if ("completed".equals(status) && akCode != null && !akCode.isEmpty() && model != null) {
                try {
                    String akSha = fetchAkShaByCode(akCode);
                    if (callbackData.getDuration() != null && callbackData.getDuration() > 0) {
                        reportUsage(callbackData.getTaskId(), akSha, model, callbackData.getDuration());
                    } else {
                        log.warn("Duration is zero or negative, skipping usage report - task_id={}, duration={}",
                                callbackData.getTaskId(), callbackData.getDuration());
                    }
                } catch (Exception e) {
                    // 用量上报失败不影响主流程
                    log.error("Error during usage reporting - task_id={}", callbackData.getTaskId(), e);
                }
            } else {
                if (!"completed".equals(status)) {
                    log.info("Task not completed, skipping usage report - task_id={}, status={}",
                            callbackData.getTaskId(), status);
                } else if (akCode == null || akCode.isEmpty()) {
                    log.warn("Missing ak_code, skipping usage report - task_id={}", callbackData.getTaskId());
                } else if (model == null) {
                    log.warn("Missing model, skipping usage report - task_id={}", callbackData.getTaskId());
                }
            }

            // 8. 转发回调到用户服务（失败不影响主流程）
            if (callbackUrl != null && !callbackUrl.isEmpty()) {
                try {
                    log.info("Forwarding callback - task_id={}, callback_url={}", callbackData.getTaskId(), callbackUrl);
                    forwardCallback(callbackUrl, callbackData);
                    log.info("Callback forwarded successfully - task_id={}, callback_url={}",
                            callbackData.getTaskId(), callbackUrl);
                } catch (Exception e) {
                    // 回调转发失败不影响主流程
                    log.error("Failed to forward callback - task_id={}, callback_url={}",
                            callbackData.getTaskId(), callbackUrl, e);
                }
            } else {
                log.info("No callback_url configured for task - task_id={}, skipping callback forwarding",
                        callbackData.getTaskId());
            }

        } catch (Exception e) {
            log.error("Error handling callback - task_id={}", callbackData.getTaskId(), e);
        }
    }

    /**
     * 上报用量到计费系统
     */
    private void reportUsage(String taskId, String akSha, String model, Float usage) {
        if (usage == null || usage <= 0) {
            log.warn("Invalid usage value for billing report - task_id={}, usage={}", taskId, usage);
            return;
        }

        if (model == null || model.isEmpty()) {
            log.warn("Missing model for billing report - task_id={}", taskId);
            return;
        }

        // 构建请求数据
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("endpoint", Configs.ASR_USAGE_REPORT_ENDPOINT);
        reportData.put("bellaTraceId", taskId);
        reportData.put("akSha", akSha != null ? akSha : "");
        reportData.put("usage", Math.max(0, usage.intValue()));
        reportData.put("model", model);

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + Configs.ASR_USAGE_REPORT_AK);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(reportData, headers);

        // 重试机制
        for (int attempt = 0; attempt <= Configs.ASR_USAGE_REPORT_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        Configs.ASR_USAGE_REPORT_URL,
                        HttpMethod.POST,
                        request,
                        String.class
                );

                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Usage report successful - task_id={}, model={}, usage={}",
                            taskId, model, usage);
                    return;
                } else {
                    if (attempt == Configs.ASR_USAGE_REPORT_RETRIES) {
                        log.error("Usage report failed after {} attempts - task_id={}, status={}",
                                attempt + 1, taskId, response.getStatusCode());
                    } else {
                        log.warn("Usage report attempt {} failed - task_id={}, status={}, retrying...",
                                attempt + 1, taskId, response.getStatusCode());
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    }
                }

            } catch (Exception e) {
                if (attempt == Configs.ASR_USAGE_REPORT_RETRIES) {
                    log.error("Usage report failed after {} attempts - task_id={}",
                            attempt + 1, taskId, e);
                    return;
                } else {
                    log.warn("Usage report attempt {} failed - task_id={}, retrying...",
                            attempt + 1, taskId, e);
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * 转发回调到用户服务
     */
    private void forwardCallback(String callbackUrl, AsrCallbackRequest callbackData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AsrCallbackRequest> request = new HttpEntity<>(callbackData, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                callbackUrl,
                HttpMethod.POST,
                request,
                String.class
        );

        response.getStatusCode();  // 触发异常（如果不是2xx）
    }

    /**
     * 获取AK Code（通过SHA）
     */
    private String fetchAkCodeBySha(String authorization) {
        if (authorization == null || authorization.isEmpty() || !authorization.startsWith("Bearer ")) {
            return null;
        }

        try {
            String akSha = authorization.substring(7);  // 移除 "Bearer "

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + Configs.ASR_OPENAPI_AK);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    Configs.ASR_OPENAPI_FETCH_BY_SHA_URL + "?akSha=" + akSha,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = JsonUtils.readTree(response.getBody());
                JsonNode dataNode = jsonResponse.get("data");
                if (dataNode != null && dataNode.has("code")) {
                    return dataNode.get("code").asText();
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch ak_code by sha", e);
        }

        return null;
    }

    /**
     * 获取AK SHA（通过Code）
     */
    private String fetchAkShaByCode(String akCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + Configs.ASR_OPENAPI_AK);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    Configs.ASR_OPENAPI_FETCH_BY_CODE_URL + "?code=" + akCode,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = JsonUtils.readTree(response.getBody());
                JsonNode dataNode = jsonResponse.get("data");
                if (dataNode != null && dataNode.has("sha")) {
                    return dataNode.get("sha").asText();
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch ak_sha by code", e);
        }

        return null;
    }

    /**
     * 解析数据：优先从文件服务获取，否则使用内联数据
     */
    private String resolveData(String inlineData, String fileId) {
        String fileData = OpenapiUtils.fetchStringFromFile(fileId);
        return StringUtils.isNotBlank(fileData) ? fileData : inlineData;
    }

    /**
     * 转换请求为Map
     */
    private Map<String, Object> convertRequestToMap(AudioTranscriptionRequest request) {
        String json = JsonUtils.toJson(request);
        return JsonUtils.fromJson(json, Map.class);
    }

    /**
     * 遮蔽URL敏感信息（用于日志）
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + parsedUrl.getPath();
        } catch (Exception e) {
            return url;
        }
    }
}
