package com.myagent.model;

public class VideoGenRequest {

    private String prompt;
    private Integer duration = 5;        // video duration in seconds (5 or 10)
    private String aspectRatio = "16:9"; // 16:9, 9:16, 1:1
    private Integer seed = -1;
    private String firstFrameBase64;     // optional first-frame image (base64, no prefix)
    private String conversationId;       // associate with a conversation

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
}
