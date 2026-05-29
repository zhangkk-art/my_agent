package com.myagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.ConversationMapper;
import com.myagent.mapper.MessageMapper;
import com.myagent.model.Conversation;
import com.myagent.model.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public MessageController(MessageMapper messageMapper, ConversationMapper conversationMapper) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        List<Message> messages = messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .like(Message::getContent, q.trim())
                .orderByDesc(Message::getCreatedAt)
                .last("LIMIT 30")
        );
        return messages.stream().map(m -> {
            Conversation conv = conversationMapper.selectById(m.getConversationId());
            Map<String, Object> r = new HashMap<>();
            r.put("messageId", m.getId());
            r.put("conversationId", m.getConversationId());
            r.put("conversationTitle", conv != null ? conv.getTitle() : "Unknown");
            r.put("role", m.getRole());
            r.put("content", m.getContent());
            r.put("createdAt", m.getCreatedAt());
            return r;
        }).collect(Collectors.toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Message> updateMessage(@PathVariable String id, @RequestBody Map<String, String> body) {
        Message message = messageMapper.selectById(id);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }
        message.setContent(body.getOrDefault("content", message.getContent()));
        messageMapper.updateById(message);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        Message message = messageMapper.selectById(id);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }
        messageMapper.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
