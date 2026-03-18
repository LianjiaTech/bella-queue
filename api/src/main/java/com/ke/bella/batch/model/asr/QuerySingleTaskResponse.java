package com.ke.bella.batch.model.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 查询单个任务响应模型
 * 对应Python的QuerySingleTaskResp
 */
@Data
public class QuerySingleTaskResponse {

    @JsonProperty("task_id")
    private String taskId;

    private String status;

    private String user;

    private String model;

    @JsonProperty("ak_code")
    private String akCode;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("completed_at")
    private LocalDateTime completedAt;

    @JsonProperty("input_data")
    private Map<String, Object> inputData;

    @JsonProperty("input_file_id")
    private String inputFileId;

    @JsonProperty("output_data")
    private Map<String, Object> outputData;

    @JsonProperty("output_file_id")
    private String outputFileId;

    @JsonProperty("callback_url")
    private String callbackUrl;
}
