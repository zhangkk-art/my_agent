package com.myagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.ConversationMapper;
import com.myagent.mapper.MessageMapper;
import com.myagent.model.Conversation;
import com.myagent.model.Message;
import com.myagent.security.UserPrincipal;
import com.myagent.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ChatService chatService;

    public MessageController(MessageMapper messageMapper, ConversationMapper conversationMapper, ChatService chatService) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.chatService = chatService;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestParam String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        // Join via conversation to filter by owner
        List<Conversation> userConvs = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, principal.getUserId())
                        .select(Conversation::getId, Conversation::getTitle));
        if (userConvs.isEmpty()) return List.of();

        Map<String, String> convTitleMap = new HashMap<>();
        userConvs.forEach(c -> convTitleMap.put(c.getId(), c.getTitle()));

        List<String> convIds = userConvs.stream().map(Conversation::getId).collect(Collectors.toList());
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .in(Message::getConversationId, convIds)
                        .like(Message::getContent, q.trim())
                        .orderByDesc(Message::getCreatedAt)
                        .last(safeLimit(30)));

        return messages.stream().map(m -> {
            Map<String, Object> r = new HashMap<>();
            r.put("messageId", m.getId());
            r.put("conversationId", m.getConversationId());
            r.put("conversationTitle", convTitleMap.getOrDefault(m.getConversationId(), "Unknown"));
            r.put("role", m.getRole());
            r.put("content", m.getContent());
            r.put("createdAt", m.getCreatedAt());
            return r;
        }).collect(Collectors.toList());
    }


    private void checkMessageOwnership(String messageId, String userId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found: " + messageId);
        }
        Conversation conv = conversationMapper.selectById(message.getConversationId());
        if (conv == null || conv.getUserId() == null || !conv.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void checkConversationOwnership(String conversationId, String userId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || conv.getUserId() == null || !conv.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
    @PutMapping("/{id}")
    public ResponseEntity<Message> updateMessage(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String id,
                                                  @RequestBody Map<String, String> body) {
        checkMessageOwnership(id, principal.getUserId());
        Message message = messageMapper.selectById(id);
        if (message == null) return ResponseEntity.notFound().build();
        message.setContent(body.getOrDefault("content", message.getContent()));
        messageMapper.updateById(message);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String id) {
        checkMessageOwnership(id, principal.getUserId());
        Message message = messageMapper.selectById(id);
        if (message == null) return ResponseEntity.notFound().build();
        messageMapper.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/star")
    public ResponseEntity<Message> toggleStar(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String id) {
        checkMessageOwnership(id, principal.getUserId());
        Message message = messageMapper.selectById(id);
        if (message == null) return ResponseEntity.notFound().build();
        message.setStarred(message.getStarred() == null || !message.getStarred());
        messageMapper.updateById(message);
        return ResponseEntity.ok(message);
    }

    @PatchMapping("/{id}/rating")
    public ResponseEntity<Message> setRating(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String id,
                                              @RequestBody Map<String, Object> body) {
        checkMessageOwnership(id, principal.getUserId());
        Message message = messageMapper.selectById(id);
        if (message == null) return ResponseEntity.notFound().build();
        Object r = body.get("rating");
        message.setRating(r != null ? ((Number) r).intValue() : null);
        messageMapper.updateById(message);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/save-partial")
    public ResponseEntity<Message> savePartial(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestBody Map<String, String> body) {
        String conversationId = body.get("conversationId");
        String content = body.get("content");
        String reasoning = body.get("reasoning");
        if (conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        checkConversationOwnership(conversationId, principal.getUserId());
        Message saved = chatService.saveInterruptedResponse(conversationId, content, reasoning);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/interrupted")
    public ResponseEntity<Message> markInterrupted(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String id,
                                                    @RequestBody Map<String, String> body) {
        checkMessageOwnership(id, principal.getUserId());
        Message message = messageMapper.selectById(id);
        if (message == null) return ResponseEntity.notFound().build();
        String content = body.get("content");
        if (content != null) message.setContent(content);
        message.setInterrupted(true);
        messageMapper.updateById(message);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/starred")
    public List<Map<String, Object>> getStarred(@AuthenticationPrincipal UserPrincipal principal) {
        List<Conversation> userConvs = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, principal.getUserId())
                        .select(Conversation::getId, Conversation::getTitle));
        if (userConvs.isEmpty()) return List.of();

        Map<String, String> convTitleMap = new HashMap<>();
        userConvs.forEach(c -> convTitleMap.put(c.getId(), c.getTitle()));

        List<String> convIds = userConvs.stream().map(Conversation::getId).collect(Collectors.toList());
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .in(Message::getConversationId, convIds)
                        .eq(Message::getStarred, true)
                        .orderByDesc(Message::getCreatedAt)
                        .last(safeLimit(50)));

        return messages.stream().map(m -> {
            Map<String, Object> r = new HashMap<>();
            r.put("messageId", m.getId());
            r.put("conversationId", m.getConversationId());
            r.put("conversationTitle", convTitleMap.getOrDefault(m.getConversationId(), "Unknown"));
            r.put("role", m.getRole());
            r.put("content", m.getContent());
            return r;
        }).collect(Collectors.toList());
    }

    private static String safeLimit(int n) {
        if (n <= 0 || n > 1000) {
            throw new IllegalArgumentException("Invalid limit: " + n);
        }
        return "LIMIT " + n;
    }
}
