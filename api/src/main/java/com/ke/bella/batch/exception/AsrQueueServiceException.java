package com.ke.bella.batch.exception;

/**
 * ASR 队列服务异常 (HTTP 502)
 * 对应 Python: QueueServiceException
 */
public class AsrQueueServiceException extends AsrException {

    public AsrQueueServiceException(String message) {
        super(message, 502);
    }

    public AsrQueueServiceException(String message, Throwable cause) {
        super(message, 502, cause);
    }
}
