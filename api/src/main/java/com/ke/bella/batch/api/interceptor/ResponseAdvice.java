package com.ke.bella.batch.api.interceptor;

import com.ke.bella.batch.api.BatchController;
import com.ke.bella.batch.api.ConsoleController;
import com.ke.bella.batch.api.QueueController;
import com.ke.bella.batch.api.protocol.BellaResponse;
import com.ke.bella.batch.utils.JsonUtils;
import com.ke.bella.openapi.BellaContext;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;

@RestControllerAdvice(assignableTypes = { QueueController.class, BatchController.class, ConsoleController.class })
@Slf4j
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NotNull MethodParameter returnType, @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        // 已通过@RestControllerAdvice的assignableTypes属性声明，此处无意义
        return true;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType, @NotNull MediaType selectedContentType,
            @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NotNull ServerHttpRequest request,
            ServerHttpResponse response) {
        try {
            response.getHeaders().add("Cache-Control", "no-cache");
            if(body instanceof BellaResponse) {
                response.setStatusCode(HttpStatus.valueOf(((BellaResponse) body).getCode()));
                return body;
            }

            if(body instanceof String) {
                return JsonUtils.toJson(body);
            }

            return body;
        } finally {
            BellaContext.clearAll();
        }
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public BellaResponse<?> exceptionHandler(Exception e) {
        int code = 500;
        String msg = e.getLocalizedMessage();
        if(e instanceof IllegalArgumentException
                || e instanceof DataIntegrityViolationException
                || e instanceof MethodArgumentNotValidException
                || e instanceof MaxUploadSizeExceededException) {
            code = 400;
        }

        if(e instanceof DataIntegrityViolationException) {
            msg = "非法的数据";
        }

        if(e instanceof MaxUploadSizeExceededException) {
            msg = e.getMessage();
        }

        if(code == 500) {
            log.warn(e.getMessage(), e);
        } else {
            log.info(e.getMessage());
        }

        BellaResponse<?> er = new BellaResponse<>();
        er.setCode(code);
        er.setTimestamp(System.currentTimeMillis());
        er.setMessage(msg);
        if(code == 500) {
            er.setStacktrace(stacktrace(e));
        }

        return er;
    }

    private static String stacktrace(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
