package com.ke.bella.batch.service.callback;

import com.ke.bella.batch.service.ITaskCallback;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractCallback implements ITaskCallback {

    protected final String taskId;
    protected final String queue;
    private final long startTime = System.currentTimeMillis();
    private final Timer ttftTimer;
    private final Timer ttltTimer;

    protected AbstractCallback(String taskId, String queue, MeterRegistry meterRegistry) {
        this.taskId = taskId;
        this.queue = queue;
        this.ttftTimer = Timer.builder("queue.task.ttft").tag("queue", queue).register(meterRegistry);
        this.ttltTimer = Timer.builder("queue.task.ttlt").tag("queue", queue).register(meterRegistry);
    }

    protected void recordTtft() {
        long ttft = System.currentTimeMillis() - startTime;
        log.info("Task [{}] TTFT={}ms", taskId, ttft);
        ttftTimer.record(ttft, TimeUnit.MILLISECONDS);
    }

    protected void recordTtlt() {
        long ttlt = System.currentTimeMillis() - startTime;
        log.info("Task [{}] TTLT={}ms", taskId, ttlt);
        ttltTimer.record(ttlt, TimeUnit.MILLISECONDS);
    }
}
