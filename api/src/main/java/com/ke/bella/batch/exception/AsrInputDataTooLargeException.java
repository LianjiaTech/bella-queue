package com.ke.bella.batch.exception;

/**
 * ASR 输入数据过大异常 (HTTP 400)
 * 对应 Python: InputDataTooLargeException
 */
public class AsrInputDataTooLargeException extends AsrException {

    public AsrInputDataTooLargeException(int size, int maxSize) {
        super(String.format("Input size %d exceeds maximum allowed size %d", size, maxSize), 400);
    }
}
