package com.ke.bella.batch.model.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 查询单个任务请求模型
 * 对应Python的QuerySingleTaskReq
 */
@Data
public class QuerySingleTaskRequest {

    @NotNull(message = "task_id不能为空")
    @Size(min = 1, max = 128, message = "task_id长度必须在1-128之间")
    @JsonProperty("task_id")
    private String taskId;
}
