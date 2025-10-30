package com.ke.bella.batch.enums;

import lombok.Getter;

@Getter
public enum BatchErrorType {
    PARSE_ERROR("parse_error", "Failed to parse request data.");

    private final String code;
    private final String message;

    BatchErrorType(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
