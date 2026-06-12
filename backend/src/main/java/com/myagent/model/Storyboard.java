package com.myagent.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("storyboards")
public class Storyboard {

    @TableId
    private String id;
    private String userId;
    private String conversationId;
    private String title;
    private String idea;
    private Integer shotCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Storyboard() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIdea() { return idea; }
    public void setIdea(String idea) { this.idea = idea; }

    public Integer getShotCount() { return shotCount; }
    public void setShotCount(Integer shotCount) { this.shotCount = shotCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
