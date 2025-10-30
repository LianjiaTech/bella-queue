package com.ke.bella.batch.service;

import com.ke.bella.queue.TaskEvent;

public interface ITaskCallback {

    default void onProgressEvent(TaskEvent.Progress.Payload event) {
    }

    default void onCompletionEvent(TaskEvent.Completion.Payload event) {
    }

    default long getTimeout() {
        return 0L;
    }

    default boolean isTimeout() {
        return false;
    }

    default void onTimeout(String taskId) {
    }

}
