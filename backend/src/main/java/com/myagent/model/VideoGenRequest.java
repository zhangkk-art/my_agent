package com.myagent.model;

public class VideoGenRequest {

    private String prompt;
    private Integer frames = 121;
    private String aspectRatio = "16:9";
    private Integer seed = -1;
    private String firstFrameBase64;

    public VideoGenRequest() {}

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public Integer getFrames() { return frames; }
    public void setFrames(Integer frames) { this.frames = frames; }

    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }

    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }

    public String getFirstFrameBase64() { return firstFrameBase64; }
    public void setFirstFrameBase64(String firstFrameBase64) { this.firstFrameBase64 = firstFrameBase64; }
}
