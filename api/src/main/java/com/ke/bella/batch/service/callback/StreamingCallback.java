package com.ke.bella.batch.service.callback;

import com.ke.bella.batch.service.QueueService;
import com.ke.bella.batch.utils.SseUtils;
import com.ke.bella.queue.TaskEvent;
import com.theokanning.openai.queue.Put;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class StreamingCallback extends AbstractCallback {
    private static final int DEFAULT_TIMEOUT_SECONDS = 600;
    private static final int MAX_TIMEOUT_SECONDS = 600;
    private final long timeout;
    @Getter
    private final SseEmitter emitter;
    private final QueueService qs;

    private volatile boolean completed = false;
    private volatile boolean firstPacketReceived = false;

    public StreamingCallback(String taskId, Put put, QueueService qs, MeterRegistry meterRegistry) {
        super(taskId, put.getQueue(), meterRegistry);
        this.timeout = put.getTimeout() <= 0 ? DEFAULT_TIMEOUT_SECONDS : Math.min(put.getTimeout(), MAX_TIMEOUT_SECONDS);
        this.emitter = SseUtils.createSse(this.timeout * 1000, taskId);
        this.qs = qs;

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
            recordTtlt();
            qs.removeTaskCallback(taskId);
            emitter.completeWithError(new RuntimeException("Task timeout"));
        } finally {
            qs.cancel(taskId);
        }
    }

    @Override
    public void onProgressEvent(TaskEvent.Progress.Payload event) {
        if(!completed) {
            if(!firstPacketReceived) {
                firstPacketReceived = true;
                recordTtft();
            }
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
            recordTtlt();
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to complete emitter for task [{}]", taskId, e);
            }
        }
    }
}
