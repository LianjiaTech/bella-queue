package com.ke.bella.batch.api.interceptor;

import com.ke.bella.batch.api.AsrController;
import com.ke.bella.batch.exception.AsrException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ASR Controller 专用异常处理器
 * 遵循原 bella-asr Python 项目的响应格式:{"error": "message"}
 *
 * 不使用 BellaResponse,保持与 Python 版本 API 的兼容性
 */
@RestControllerAdvice(assignableTypes = AsrController.class)
@Slf4j
public class AsrResponseAdvice {

    /**
     * 处理 ASR 自定义异常
     * 对应 Python: ASRSchedulerException 子类
     */
    @ExceptionHandler(AsrException.class)
    public ResponseEntity<Map<String, Object>> handleAsrException(AsrException e) {
        int status = e.getHttpStatus();
        String message = e.getMessage();

        // 日志记录(与 Python 版本保持一致)
        if (status >= 500) {
            log.error("ASR error - status={}, message={}", status, message, e);
        } else if (status == 404) {
            log.warn("ASR error - status={}, message={}", status, message);
        } else {
            log.info("ASR error - status={}, message={}", status, message);
        }

        // 返回简洁的错误响应:{"error": "message"}
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);

        return ResponseEntity.status(status).body(body);
    }

    /**
     * 处理参数验证异常 (JSR-303)
     * 对应 Python: validation_exception_handler
     * HTTP 422 Unprocessable Entity
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        log.info("Validation error - fieldErrors={}", e.getBindingResult().getFieldErrorCount());

        // 构建详细的验证错误信息
        List<Map<String, Object>> details = new ArrayList<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            Map<String, Object> errorDetail = new HashMap<>();
            errorDetail.put("type", "value_error");
            errorDetail.put("loc", List.of("body", error.getField()));
            errorDetail.put("msg", error.getDefaultMessage());
            errorDetail.put("input", error.getRejectedValue());
            details.add(errorDetail);
        }

        // 返回 {"error": "Validation error", "details": [...]}
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validation error");
        body.put("details", details);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * 处理 IllegalArgumentException
     * 对应 Python: value_error_handler
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.info("Illegal argument - message={}", e.getMessage());

        Map<String, Object> body = new HashMap<>();
        // 只返回通用消息，详细错误记录在日志中，避免泄露内部信息
        body.put("error", "Invalid request parameter");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 处理所有未捕获的异常
     * 对应 Python: 路由层的 except Exception
     * HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error in ASR controller", e);

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Internal server error");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
