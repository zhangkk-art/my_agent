package com.myagent.controller;

import com.myagent.mapper.MessageMapper;
import com.myagent.model.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageMapper messageMapper;

    public MessageController(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
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
