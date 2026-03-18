package com.ke.bella.batch.model.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 批量查询任务请求模型
 * 对应Python的QueryTasksReq
 */
@Data
public class QueryTasksRequest {

    @NotNull(message = "task_ids不能为空")
    @Size(min = 1, max = 50, message = "task_ids数量必须在1-50之间")
    @JsonProperty("task_ids")
    private List<String> taskIds;
}
