package com.ke.bella.batch.service.callback;

import com.google.common.collect.Maps;
import com.ke.bella.batch.service.ITaskCallback;
import com.ke.bella.batch.service.QueueService;
import com.ke.bella.queue.TaskEvent;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("all")
public class BlockingCallback implements ITaskCallback {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_TIMEOUT_SECONDS = 300;

    private final Map<String, Object> data = new LinkedHashMap<>();
    private final long timeout;
    private final String taskId;
    private final long startTime;
    private final QueueService qs;

    public final static String STATUS_CODE = "status_code";
    public final static String BODY = "body";

    public BlockingCallback(String taskId, QueueService qs, long timeout) {
        this.taskId = taskId;
        this.qs = qs;
        this.timeout = timeout <= 0 ? DEFAULT_TIMEOUT_SECONDS : Math.min(timeout, MAX_TIMEOUT_SECONDS);
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public boolean isTimeout() {
        return System.currentTimeMillis() - startTime >= timeout * 1000;
    }

    @Override
    public void onTimeout(String taskId) {
        synchronized(data) {
            qs.cancel(taskId);
            data.put(STATUS_CODE, HttpStatus.REQUEST_TIMEOUT.value());
        }
    }

    @Override
    public void onCompletionEvent(TaskEvent.Completion.Payload event) {
        synchronized(data) {
            Map<String, Object> result = event.getResult();
            data.put(STATUS_CODE, MapUtils.getInteger(result, STATUS_CODE));
            data.put(BODY, MapUtils.getMap(result, BODY, Maps.newHashMap()));
        }
    }

    public Map<String, Object> getResult() {
        while (!isTimeout()) {
            synchronized(data) {
                if(!data.isEmpty()) {
                    return data;
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        onTimeout(taskId);
        return data;
    }
}
