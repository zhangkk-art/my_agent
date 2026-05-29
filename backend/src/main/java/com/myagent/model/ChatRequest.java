package com.myagent.model;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {
    private String conversationId;
    private String message;
    private String model;
    private List<String> images = new ArrayList<>();
    private boolean webSearch = false;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public boolean isWebSearch() { return webSearch; }
    public void setWebSearch(boolean webSearch) { this.webSearch = webSearch; }
}
