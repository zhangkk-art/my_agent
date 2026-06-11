package com.myagent.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("video_gen_tasks")
public class VideoGenTask {

    @TableId
    private String id;
    private String userId;
    private String conversationId;
    private String prompt;
    private String reqKey;
    private Integer duration;
    private String aspectRatio;
    private Integer seed;
    private String firstFrameUrl;
    private String taskId;
    private String status;
    private String videoPath;
    private String originalVideoUrl;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VideoGenTask() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getReqKey() { return reqKey; }
    public void setReqKey(String reqKey) { this.reqKey = reqKey; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }

    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }

    public String getFirstFrameUrl() { return firstFrameUrl; }
    public void setFirstFrameUrl(String firstFrameUrl) { this.firstFrameUrl = firstFrameUrl; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }

    public String getOriginalVideoUrl() { return originalVideoUrl; }
    public void setOriginalVideoUrl(String originalVideoUrl) { this.originalVideoUrl = originalVideoUrl; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
