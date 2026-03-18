package com.ke.bella.batch.model.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.*;

/**
 * ASR音频转写请求模型
 * 对应Python的AudioTranscriptionReq
 */
@Data
public class AudioTranscriptionRequest {

    @NotNull(message = "url不能为空")
    private String url;

    @NotNull(message = "model不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "model格式不正确")
    @Size(min = 1, max = 64, message = "model长度必须在1-64之间")
    private String model;

    @Size(max = 128, message = "user长度不能超过128")
    private String user = "";

    @JsonProperty("callback_url")
    private String callbackUrl;

    // ASR特定参数
    @JsonProperty("channel_number")
    @Min(value = 1, message = "channel_number最小为1")
    @Max(value = 16, message = "channel_number最大为16")
    private Integer channelNumber = 1;

    @JsonProperty("speaker_diarization")
    private Boolean speakerDiarization = false;

    @JsonProperty("speaker_number")
    @Min(value = 0, message = "speaker_number最小为0")
    @Max(value = 100, message = "speaker_number最大为100")
    private Integer speakerNumber = 0;

    @JsonProperty("hot_word")
    @Size(max = 1000, message = "hot_word长度不能超过1000")
    private String hotWord;

    @Size(max = 10, message = "language长度不能超过10")
    private String language = "cn";

    @Min(value = 0, message = "candidate最小为0")
    @Max(value = 10, message = "candidate最大为10")
    private Integer candidate = 0;

    @JsonProperty("audio_mode")
    @Size(max = 50, message = "audio_mode长度不能超过50")
    private String audioMode = "urlLink";

    @JsonProperty("standard_wav")
    @Min(value = 0, message = "standard_wav最小为0")
    @Max(value = 1, message = "standard_wav最大为1")
    private Integer standardWav = 0;

    @JsonProperty("language_type")
    @Min(value = 0, message = "language_type最小为0")
    @Max(value = 10, message = "language_type最大为10")
    private Integer languageType = 2;

    @JsonProperty("trans_mode")
    @Min(value = 0, message = "trans_mode最小为0")
    @Max(value = 10, message = "trans_mode最大为10")
    private Integer transMode = 2;

    @JsonProperty("eng_smoothproc")
    private Boolean engSmoothproc = true;

    @JsonProperty("eng_collogproc")
    private Boolean engCollogproc = false;

    @JsonProperty("eng_vad_mdn")
    @Min(value = 0, message = "eng_vad_mdn最小为0")
    @Max(value = 10, message = "eng_vad_mdn最大为10")
    private Integer engVadMdn = 1;

    @JsonProperty("eng_vad_margin")
    @Min(value = 0, message = "eng_vad_margin最小为0")
    @Max(value = 10, message = "eng_vad_margin最大为10")
    private Integer engVadMargin = 1;

    @JsonProperty("eng_rlang")
    @Min(value = 0, message = "eng_rlang最小为0")
    @Max(value = 10, message = "eng_rlang最大为10")
    private Integer engRlang = 1;

    @JsonProperty("vocab_id")
    @Size(max = 128, message = "vocab_id长度不能超过128")
    private String vocabId;

    @JsonProperty("sample_rate")
    @Min(value = 8000, message = "sample_rate最小为8000")
    @Max(value = 48000, message = "sample_rate最大为48000")
    private Integer sampleRate;

    @JsonProperty("enable_words")
    private Boolean enableWords = false;

    @JsonProperty("enable_vad")
    private Boolean enableVad = true;

    @JsonProperty("chunk_length")
    @Min(value = 1, message = "chunk_length最小为1")
    @Max(value = 60, message = "chunk_length最大为60")
    private Integer chunkLength = 10;

    @JsonProperty("enable_semantic_sentence_detection")
    private Boolean enableSemanticSentenceDetection = false;

    @JsonProperty("enable_punctuation_prediction")
    private Boolean enablePunctuationPrediction = false;

    @JsonProperty("max_end_silence")
    @Min(value = 0, message = "max_end_silence最小为0")
    @Max(value = 10000, message = "max_end_silence最大为10000")
    private Integer maxEndSilence;

    @JsonProperty("enable_itn")
    private Boolean enableItn = true;

    @JsonProperty("enable_ddc")
    private Boolean enableDdc = false;

    @JsonProperty("enable_channel_split")
    private Boolean enableChannelSplit = false;

    @JsonProperty("show_utterances")
    private Boolean showUtterances = true;

    @JsonProperty("vad_segment")
    private Boolean vadSegment = false;

    @JsonProperty("sensitive_words_filter")
    @Size(max = 1000, message = "sensitive_words_filter长度不能超过1000")
    private String sensitiveWordsFilter;

    @JsonProperty("boosting_table_name")
    @Size(max = 128, message = "boosting_table_name长度不能超过128")
    private String boostingTableName;

    @JsonProperty("embedding_model")
    @Size(max = 128, message = "embedding_model长度不能超过128")
    private String embeddingModel;

    @Size(max = 10000, message = "context长度不能超过10000")
    private String context;
}
