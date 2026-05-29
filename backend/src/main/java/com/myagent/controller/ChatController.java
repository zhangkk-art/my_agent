package com.myagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.model.*;
import com.myagent.service.ChatService;
import com.myagent.service.ConversationService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, ConversationService conversationService) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        Message reply = chatService.chat(request.getConversationId(), request.getMessage(), request.getModel());
        Conversation conversation = conversationService.getConversation(reply.getConversationId());
        return Map.of(
                "conversationId", reply.getConversationId(),
                "message", reply,
                "conversation", conversation
        );
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chatStream(@RequestBody ChatRequest request,
                           HttpServletRequest req,
                           HttpServletResponse resp) {
        log.info("chatStream — model='{}' webSearch={}", request.getModel(), request.isWebSearch());
        doStream(req, resp, chatService.chatStream(
                request.getConversationId(), request.getMessage(), request.getModel(), request.isWebSearch()));
    }

    @PostMapping(value = "/chat/image", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chatImage(@RequestBody ChatRequest request,
                          HttpServletRequest req,
                          HttpServletResponse resp) {
        doStream(req, resp, chatService.chatImageStream(
                request.getConversationId(), request.getMessage(),
                request.getModel(), request.getImages(), request.isWebSearch()));
    }

    @PostMapping(value = "/chat/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void regenerate(@RequestBody ChatRequest request,
                           HttpServletRequest req,
                           HttpServletResponse resp) {
        doStream(req, resp, chatService.regenerateStream(
                request.getConversationId(), request.getMessage(), request.getModel(), request.isWebSearch()));
    }

    private void doStream(HttpServletRequest req, HttpServletResponse resp, ChatService.StreamContext ctx) {
        AsyncContext asyncCtx = req.startAsync();
        asyncCtx.setTimeout(120000);

        try {
            ServletOutputStream out = resp.getOutputStream();
            StringBuilder fullContent = new StringBuilder();
            AtomicBoolean completed = new AtomicBoolean(false);

            ctx.content().subscribe(
                    chunk -> {
                        if (completed.get()) return;
                        fullContent.append(chunk);
                        try {
                            String sse = "data: " + objectMapper.writeValueAsString(
                                    Map.of("content", chunk)) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            log.warn("Client disconnected during stream for conversation {}", ctx.conversationId());
                            if (completed.compareAndSet(false, true)) {
                                asyncCtx.complete();
                            }
                        }
                    },
                    error -> {
                        if (fullContent.length() > 0) {
                            try {
                                chatService.saveAssistantResponse(ctx.conversationId(), fullContent.toString());
                            } catch (Exception e) {
                                log.error("Failed to save partial assistant response for conversation {}", ctx.conversationId(), e);
                            }
                        }
                        try {
                            String sse = "data: " + objectMapper.writeValueAsString(
                                    Map.of("error", error.getMessage() != null ? error.getMessage() : "Unknown error")) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            log.warn("Failed to send error SSE for conversation {}", ctx.conversationId());
                        }
                        if (completed.compareAndSet(false, true)) {
                            asyncCtx.complete();
                        }
                    },
                    () -> {
                        try {
                            String messageId = null;
                            if (fullContent.length() > 0) {
                                Message saved = chatService.saveAssistantResponse(
                                        ctx.conversationId(), fullContent.toString());
                                messageId = saved.getId();
                            }
                            Map<String, Object> doneData = new HashMap<>();
                            doneData.put("done", true);
                            if (messageId != null) doneData.put("messageId", messageId);
                            String sse = "data: " + objectMapper.writeValueAsString(doneData) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            log.warn("Failed to send done SSE for conversation {}", ctx.conversationId());
                        } catch (Exception e) {
                            log.error("Failed to save assistant response for conversation {}", ctx.conversationId(), e);
                        }
                        if (completed.compareAndSet(false, true)) {
                            asyncCtx.complete();
                        }
                    }
            );
        } catch (IOException e) {
            log.error("Failed to initialize SSE stream for conversation {}", ctx.conversationId(), e);
            asyncCtx.complete();
        }
    }
}
