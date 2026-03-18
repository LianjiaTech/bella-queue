package com.ke.bella.batch.exception;

/**
 * ASR 任务未找到异常 (HTTP 404)
 * 对应 Python: TaskNotFoundException
 */
public class AsrTaskNotFoundException extends AsrException {

    public AsrTaskNotFoundException(String taskId) {
        super(String.format("Task %s not found", taskId), 404);
    }
}
