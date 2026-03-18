package com.ke.bella.batch.model.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * ASR回调请求模型
 * 对应Python的ASRTranscribeCallbackRequest
 */
@Data
public class AsrCallbackRequest {

    private String task = "transcribe";

    @JsonProperty("task_id")
    private String taskId = "";

    private String language = "zh";

    private Float duration = 0.0f;

    private String text = "";

    private List<Word> words;

    private List<Segment> segments;

    @JsonProperty("statusCode")
    private String statusCode = "";

    @JsonProperty("statusText")
    private String statusText = "";

    @JsonProperty("solveTime")
    private Integer solveTime = 0;

    @JsonProperty("num_speakers")
    private Integer numSpeakers = 0;

    @JsonProperty("speaker_embeddings")
    private Map<String, List<Float>> speakerEmbeddings;

    private String user = "";

    private String error = "";

    /**
     * Word模型
     */
    @Data
    public static class Word {
        private String word = "";
        private Float start = 0.0f;
        private Float end = 0.0f;
        @JsonProperty("channel_id")
        private Integer channelId = 0;
        @JsonProperty("speaker_id")
        private Integer speakerId = 0;
    }

    /**
     * Segment模型
     */
    @Data
    public static class Segment {
        private Integer id = 0;
        private Integer seek = 0;
        private Float start = 0.0f;
        private Float end = 0.0f;
        private String text = "";
        private List<Integer> tokens;
        private Float temperature = 0.0f;
        @JsonProperty("avg_logprob")
        private Float avgLogprob = 0.0f;
        @JsonProperty("compression_ratio")
        private Float compressionRatio = 0.0f;
        @JsonProperty("no_speech_prob")
        private Float noSpeechProb = 0.0f;
        @JsonProperty("channel_id")
        private Integer channelId = 0;
        @JsonProperty("speaker_id")
        private Integer speakerId = 0;
        private Float confidence = 0.0f;
        private List<Float> embedding;
    }
}
