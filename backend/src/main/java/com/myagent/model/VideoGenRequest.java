package com.myagent.model;

import java.util.List;

public class VideoGenRequest {

    private String prompt;
    private Integer duration = 5;        // video duration in seconds (5 or 10)
    private String aspectRatio = "16:9"; // 16:9, 9:16, 1:1
    private Integer seed = -1;
    private String firstFrameBase64;     // optional first-frame image (base64, no prefix)
    private String conversationId;       // associate with a conversation
    private Boolean subtitleEnabled = false;  // whether to burn subtitles into the video
    private Boolean generateAudio = true;       // whether to generate synchronized audio (1.5 pro)
    private Boolean narrateSubtitles = false;    // TTS voice reading subtitle text into video
    private List<SubtitleEntry> customSubtitles;  // user-defined subtitle entries with timing
    private String negativePrompt;     // negative prompt, comma-separated tags

    public VideoGenRequest() {}

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }

    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }

    public String getFirstFrameBase64() { return firstFrameBase64; }
    public void setFirstFrameBase64(String firstFrameBase64) { this.firstFrameBase64 = firstFrameBase64; }

    public Boolean getSubtitleEnabled() { return subtitleEnabled; }
    public void setSubtitleEnabled(Boolean subtitleEnabled) { this.subtitleEnabled = subtitleEnabled; }

    public Boolean getGenerateAudio() { return generateAudio; }
    public void setGenerateAudio(Boolean generateAudio) { this.generateAudio = generateAudio; }

    public Boolean getNarrateSubtitles() { return narrateSubtitles; }
    public void setNarrateSubtitles(Boolean narrateSubtitles) { this.narrateSubtitles = narrateSubtitles; }

    public List<SubtitleEntry> getCustomSubtitles() { return customSubtitles; }
    public void setCustomSubtitles(List<SubtitleEntry> customSubtitles) { this.customSubtitles = customSubtitles; }

    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
}
