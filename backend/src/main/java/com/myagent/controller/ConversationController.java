package com.myagent.controller;

import com.myagent.model.Conversation;
import com.myagent.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<Conversation> getAllConversations() {
        return conversationService.getAllConversations();
    }

    @PostMapping
    public Conversation createConversation(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.getOrDefault("title", "New Chat") : "New Chat";
        if (title.length() > 50) {
            title = title.substring(0, 50);
        }
        return conversationService.createConversation(title);
    }

    @GetMapping("/{id}")
    public Conversation getConversation(@PathVariable String id) {
        return conversationService.getConversation(id);
    }

    @PutMapping("/{id}")
    public Conversation renameConversation(@PathVariable String id, @RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Untitled");
        return conversationService.renameConversation(id, title);
    }

    @PutMapping("/{id}/system-prompt")
    public Conversation updateSystemPrompt(@PathVariable String id, @RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("systemPrompt", "");
        return conversationService.updateSystemPrompt(id, prompt.isBlank() ? null : prompt);
    }

    @PatchMapping("/{id}/touch")
    public Conversation touchConversation(@PathVariable String id) {
        return conversationService.touchConversation(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    public Map<String, String> shareConversation(@PathVariable String id) {
        String token = conversationService.shareConversation(id);
        return Map.of("shareToken", token);
    }

    @DeleteMapping("/{id}/share")
    public ResponseEntity<Void> revokeShare(@PathVariable String id) {
        conversationService.revokeShare(id);
        return ResponseEntity.noContent().build();
    }
}

// Shared conversation — public endpoint (no /api prefix to avoid proxy)
@RestController
@RequestMapping("/api/shared")
class SharedConversationController {
    private final ConversationService conversationService;

    SharedConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{token}")
    public Conversation getSharedConversation(@PathVariable String token) {
        return conversationService.getSharedConversation(token);
    }
}
