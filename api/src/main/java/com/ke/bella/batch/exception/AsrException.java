package com.ke.bella.batch.exception;

import lombok.Getter;

/**
 * ASR 模块基础异常类
 * 遵循原 bella-asr Python 项目的异常设计
 */
@Getter
public abstract class AsrException extends RuntimeException {

    private final int httpStatus;

    protected AsrException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    protected AsrException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
