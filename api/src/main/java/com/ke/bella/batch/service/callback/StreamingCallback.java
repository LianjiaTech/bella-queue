package com.ke.bella.batch.service.callback;

import com.ke.bella.batch.service.ITaskCallback;
import com.ke.bella.batch.service.QueueService;
import com.ke.bella.batch.utils.SseUtils;
import com.ke.bella.queue.TaskEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class StreamingCallback implements ITaskCallback {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private final long timeout;
    @Getter
    private final SseEmitter emitter;
    private final QueueService qs;
    private final String taskId;

    private volatile boolean completed = false;

    public StreamingCallback(String taskId, QueueService qs, int timeout) {
        this.timeout = timeout <= 0 ? DEFAULT_TIMEOUT_SECONDS : Math.min(timeout, MAX_TIMEOUT_SECONDS);
        this.emitter = SseUtils.createSse(this.timeout * 1000, taskId);
        this.qs = qs;
        this.taskId = taskId;

        this.emitter.onError((ex) -> {
            if(!completed) {
                completed = true;
                log.warn("Task [{}] emitter error, cancelling task", taskId, ex);
                qs.removeTaskCallback(taskId);
                qs.cancel(taskId);
            }
        });
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void onTimeout(String taskId) {
        try {
            log.info("Task [{}] streaming timeout, closing emitter", taskId);
            qs.removeTaskCallback(taskId);
            emitter.completeWithError(new RuntimeException("Task timeout"));
        } finally {
            qs.cancel(taskId);
        }
    }

    @Override
    public void onProgressEvent(TaskEvent.Progress.Payload event) {
        if(!completed) {
            SseUtils.send(emitter, SseEmitter.event()
                    .id(event.getEventId())
                    .name(event.getEventName())
                    .data(event.getEventData()));
        } else {
            log.warn("Task [{}] already completed, ignoring progress event [{}]", taskId, event.getEventId());
        }
    }

    @Override
    public void onCompletionEvent(TaskEvent.Completion.Payload event) {
        if(!completed) {
            completed = true;
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to complete emitter for task [{}]", taskId, e);
            }
        }
    }
}
