package com.ke.bella.batch.api;

import com.ke.bella.batch.model.asr.*;
import com.ke.bella.batch.service.AsrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ASR REST API控制器
 * 提供ASR音频转写相关的HTTP端点
 *
 * 参考: bella-asr/app/main.py
 */
@RestController
@RequestMapping("/v1/audio")
@Slf4j
@Validated
public class AsrController {

    @Resource
    private AsrService asrService;

    @Value("${bella.asr.callback.secret:}")
    private String callbackSecret;

    /**
     * 创建ASR转写任务
     * POST /v1/audio/transcriptions/file
     *
     * @param request 转写请求
     * @param authorization 可选的认证头
     * @return 转写响应（包含task_id）
     */
    @PostMapping("/transcriptions/file")
    public AudioTranscriptionResponse createTranscription(
            @Valid @RequestBody AudioTranscriptionRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        log.info("Received create transcription request - model={}, user={}",
                request.getModel(), request.getUser());

        return asrService.createTranscription(request, authorization);
    }

    /**
     * 批量查询任务结果
     * POST /v1/audio/transcriptions/file/result
     *
     * @param request 查询请求（包含task_ids列表）
     * @param authorization 可选的认证头
     * @return 任务结果列表
     */
    @PostMapping("/transcriptions/file/result")
    public List<Map<String, Object>> queryTranscriptions(
            @Valid @RequestBody QueryTasksRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        log.info("Received query transcriptions request - count={}", request.getTaskIds().size());

        return asrService.queryTranscriptions(request.getTaskIds());
    }

    /**
     * 查询单个任务详情
     * POST /v1/audio/transcriptions/query
     *
     * @param request 查询请求（包含task_id）
     * @return 任务详情
     */
    @PostMapping("/transcriptions/query")
    public QuerySingleTaskResponse querySingleTranscription(
            @Valid @RequestBody QuerySingleTaskRequest request) {

        log.info("Received query single transcription request - task_id={}", request.getTaskId());

        return asrService.querySingleTranscription(request.getTaskId());
    }

    /**
     * 接收Worker回调
     * POST /v1/audio/callback
     *
     * @param callbackData 回调数据
     * @param signature 回调签名（从Header中获取）
     * @return 处理结果
     */
    @PostMapping("/callback")
    public Map<String, String> receiveCallback(
            @RequestBody AsrCallbackRequest callbackData,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        log.info("Received callback - task_id={}, status={}",
                callbackData.getTaskId(), callbackData.getStatusCode());

        // 验证签名（如果配置了secret）
        if (callbackSecret != null && !callbackSecret.isEmpty()) {
            if (signature == null || signature.isEmpty()) {
                log.warn("Missing signature in callback - task_id={}", callbackData.getTaskId());
                throw new IllegalArgumentException("Missing signature");
            }

            // TODO: 需要获取原始请求体进行签名验证
            // 暂时跳过签名验证
        }

        // 处理回调
        asrService.handleCallback(callbackData);

        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");

        return result;
    }

    /**
     * 健康检查端点
     * GET /v1/audio/health
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("service", "bella-asr");
        return status;
    }
}
