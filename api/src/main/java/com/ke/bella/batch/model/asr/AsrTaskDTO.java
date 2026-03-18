package com.ke.bella.batch.model.asr;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * ASR任务数据传输对象
 * 对应数据库记录的映射
 */
@Data
public class AsrTaskDTO {

    private Long id;

    private String taskId;

    private String akCode;

    private String user;

    private String model;

    private String status;

    private String inputData;

    private String inputFileId;

    private String outputData;

    private String outputFileId;

    private String callbackUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
}
