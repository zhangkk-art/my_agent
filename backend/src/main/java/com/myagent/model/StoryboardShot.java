package com.myagent.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("storyboard_shots")
public class StoryboardShot {

    @TableId
    private String id;
    private String storyboardId;
    private Integer sceneNumber;
    private String sceneNote;
    private String shotDescription;
    private String cameraMovement;
    private Integer duration;
    private String audioHint;
    private Integer sortOrder;
    private String status;
    private String taskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public StoryboardShot() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoryboardId() { return storyboardId; }
    public void setStoryboardId(String storyboardId) { this.storyboardId = storyboardId; }

    public Integer getSceneNumber() { return sceneNumber; }
    public void setSceneNumber(Integer sceneNumber) { this.sceneNumber = sceneNumber; }

    public String getSceneNote() { return sceneNote; }
    public void setSceneNote(String sceneNote) { this.sceneNote = sceneNote; }

    public String getShotDescription() { return shotDescription; }
    public void setShotDescription(String shotDescription) { this.shotDescription = shotDescription; }

    public String getCameraMovement() { return cameraMovement; }
    public void setCameraMovement(String cameraMovement) { this.cameraMovement = cameraMovement; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getAudioHint() { return audioHint; }
    public void setAudioHint(String audioHint) { this.audioHint = audioHint; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
