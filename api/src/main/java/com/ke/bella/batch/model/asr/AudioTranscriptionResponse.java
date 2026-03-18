package com.ke.bella.batch.model.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ASR音频转写响应模型
 * 对应Python的AudioTranscriptionResp
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioTranscriptionResponse {

    @JsonProperty("task_id")
    private String taskId;
}
