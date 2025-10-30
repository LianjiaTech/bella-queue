package com.ke.bella.batch.api.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@ToString
public class BellaResponse<T> {
    private int code;
    private String message;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    private T data;
    private String stacktrace;
}
