package com.ke.bella.batch.service.callback;

import com.google.common.collect.Maps;
import com.ke.bella.batch.service.ITaskCallback;
import com.ke.bella.batch.service.QueueService;
import com.ke.bella.queue.TaskEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;

@Slf4j
public class BlockingCallback implements ITaskCallback {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_TIMEOUT_SECONDS = 300;

    private final long timeout;
    private final String taskId;
    private final QueueService qs;
    @Getter
    private final DeferredResult<ResponseEntity<?>> deferredResult;

    public final static String STATUS_CODE = "status_code";
    public final static String BODY = "body";

    public BlockingCallback(String taskId, QueueService qs, long timeout) {
        this.taskId = taskId;
        this.qs = qs;
        this.timeout = timeout <= 0 ? DEFAULT_TIMEOUT_SECONDS : Math.min(timeout, MAX_TIMEOUT_SECONDS);
        this.deferredResult = new DeferredResult<>(this.timeout * 1000L);

        this.deferredResult.onTimeout(() -> onTimeout(taskId));
        this.deferredResult.onError((ex) -> {
            log.warn("DeferredResult error for task [{}]", taskId, ex);
            qs.removeTaskCallback(taskId);
            qs.cancel(taskId);
        });
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void onTimeout(String taskId) {
        log.info("Task [{}] blocking timeout.", taskId);
        qs.removeTaskCallback(taskId);
        qs.cancel(taskId);
        deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build());
    }

    @Override
    public void onCompletionEvent(TaskEvent.Completion.Payload event) {
        Map<String, Object> result = event.getResult();
        Integer statusCode = MapUtils.getInteger(result, STATUS_CODE, 200);
        Object payload = MapUtils.getObject(result, BODY, Maps.newHashMap());
        deferredResult.setResult(ResponseEntity.status(statusCode).body(payload));
    }
}
