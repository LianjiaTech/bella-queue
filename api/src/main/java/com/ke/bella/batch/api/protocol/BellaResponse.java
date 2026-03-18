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

    /**
     * 创建成功响应
     */
    public static <T> BellaResponse<T> success(T data) {
        return BellaResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static <T> BellaResponse<T> error(int code, String message) {
        return BellaResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
