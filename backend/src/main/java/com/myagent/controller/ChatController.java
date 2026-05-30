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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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
            StringBuilder fullReasoning = new StringBuilder();
            AtomicBoolean completed = new AtomicBoolean(false);

            ctx.flux().subscribe(
                    chatResponse -> {
                        if (completed.get()) return;
                        try {
                            // Extract reasoning and content from ChatResponse
                            Generation gen = chatResponse.getResult();
                            if (gen == null) return;
                            var output = gen.getOutput();
                            if (output == null) return;

                            String reasoning = extractReasoning(gen);
                            String content = output.getText();

                            if (reasoning != null && !reasoning.isEmpty()) {
                                fullReasoning.append(reasoning);
                                String sse = "data: " + objectMapper.writeValueAsString(
                                        Map.of("reasoning", reasoning)) + "\n\n";
                                out.write(sse.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            }
                            if (content != null && !content.isEmpty()) {
                                fullContent.append(content);
                                String sse = "data: " + objectMapper.writeValueAsString(
                                        Map.of("content", content)) + "\n\n";
                                out.write(sse.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            }
                        } catch (IOException e) {
                            log.warn("Client disconnected during stream for conversation {}", ctx.conversationId());
                            if (completed.compareAndSet(false, true)) {
                                asyncCtx.complete();
                            }
                        }
                    },
                    error -> {
                        if (fullContent.length() > 0 || fullReasoning.length() > 0) {
                            try {
                                chatService.saveAssistantResponse(ctx.conversationId(),
                                        fullContent.toString(),
                                        fullReasoning.length() > 0 ? fullReasoning.toString() : null);
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
                                        ctx.conversationId(),
                                        fullContent.toString(),
                                        fullReasoning.length() > 0 ? fullReasoning.toString() : null);
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

    /**
     * Try to extract reasoning/thinking content from a Generation.
     * Checks Generation metadata first, then ChatResponse-level metadata.
     * Returns null if no reasoning content is available (most models don't provide it).
     */
    private String extractReasoning(Generation gen) {
        if (gen == null) return null;
        // Check Generation metadata — Spring AI stores reasoningContent here
        if (gen.getMetadata() != null) {
            Object r = gen.getMetadata().get("reasoningContent");
            if (r != null && !r.toString().isEmpty()) {
                return r.toString();
            }
        }
        return null;
    }
}
