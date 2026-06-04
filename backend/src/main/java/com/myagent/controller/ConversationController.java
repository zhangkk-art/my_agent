package com.myagent.controller;

import com.myagent.model.Conversation;
import com.myagent.security.UserPrincipal;
import com.myagent.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public List<Conversation> getAllConversations(@AuthenticationPrincipal UserPrincipal principal) {
        return conversationService.getAllConversations(principal.getUserId());
    }

    @PostMapping
    public Conversation createConversation(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.getOrDefault("title", "New Chat") : "New Chat";
        if (title.length() > 50) title = title.substring(0, 50);
        return conversationService.createConversation(title, principal.getUserId());
    }

    @GetMapping("/{id}")
    public Conversation getConversation(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable String id) {
        return conversationService.getConversation(id, principal.getUserId());
    }

    @PutMapping("/{id}")
    public Conversation renameConversation(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable String id,
                                           @RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Untitled");
        return conversationService.renameConversation(id, title, principal.getUserId());
    }

    @PutMapping("/{id}/system-prompt")
    public Conversation updateSystemPrompt(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable String id,
                                           @RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("systemPrompt", "");
        return conversationService.updateSystemPrompt(id, prompt.isBlank() ? null : prompt, principal.getUserId());
    }

    @PatchMapping("/{id}/touch")
    public Conversation touchConversation(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable String id) {
        return conversationService.touchConversation(id, principal.getUserId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String id) {
        conversationService.deleteConversation(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fork")
    public Conversation forkConversation(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable String id,
                                          @RequestBody Map<String, String> body) {
        return conversationService.forkConversation(id, body.get("messageId"), principal.getUserId());
    }

    @PatchMapping("/{id}/pin")
    public Conversation togglePin(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable String id) {
        return conversationService.togglePin(id, principal.getUserId());
    }

    @PatchMapping("/{id}/folder")
    public Conversation setFolder(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable String id,
                                   @RequestBody Map<String, String> body) {
        return conversationService.setFolder(id, body.get("folderName"), principal.getUserId());
    }

    @PostMapping("/import")
    public Conversation importConversation(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, String>> messages =
                (java.util.List<java.util.Map<String, String>>) body.get("messages");
        return conversationService.importConversation(title,
                messages != null ? messages : java.util.List.of(),
                principal.getUserId());
    }

    @PostMapping("/{id}/share")
    public Map<String, String> shareConversation(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String id) {
        String token = conversationService.shareConversation(id, principal.getUserId());
        return Map.of("shareToken", token);
    }

    @DeleteMapping("/{id}/share")
    public ResponseEntity<Void> revokeShare(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable String id) {
        conversationService.revokeShare(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}

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
