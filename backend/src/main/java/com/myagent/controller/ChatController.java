package com.myagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.model.*;
import com.myagent.security.UserPrincipal;
import com.myagent.service.ChatService;
import com.myagent.service.ConversationService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.Disposable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.metadata.Usage;

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
    public void chatStream(@AuthenticationPrincipal UserPrincipal principal,
                           @RequestBody ChatRequest request,
                           HttpServletRequest req,
                           HttpServletResponse resp) {
        log.info("chatStream — model='{}' webSearch={}", request.getModel(), request.isWebSearch());
        doStream(req, resp, chatService.chatStream(
                request.getConversationId(), request.getMessage(), request.getModel(),
                request.isWebSearch(), request.getTemperature(), request.getMaxTokens(),
                principal.getUserId()));
    }

    @PostMapping(value = "/chat/image", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chatImage(@AuthenticationPrincipal UserPrincipal principal,
                          @RequestBody ChatRequest request,
                          HttpServletRequest req,
                          HttpServletResponse resp) {
        doStream(req, resp, chatService.chatImageStream(
                request.getConversationId(), request.getMessage(), request.getModel(),
                request.getImages(), request.isWebSearch(),
                request.getTemperature(), request.getMaxTokens(),
                principal.getUserId()));
    }

    @PostMapping(value = "/chat/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void continueGeneration(@RequestBody ChatRequest request,
                                   HttpServletRequest req,
                                   HttpServletResponse resp) {
        doStreamAppend(req, resp,
                chatService.continueStream(request.getConversationId(), request.getMessageId(),
                        request.getModel(), request.getTemperature(), request.getMaxTokens()),
                request.getMessageId());
    }

    @PostMapping(value = "/chat/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void regenerate(@RequestBody ChatRequest request,
                           HttpServletRequest req,
                           HttpServletResponse resp) {
        doStream(req, resp, chatService.regenerateStream(
                request.getConversationId(), request.getMessage(), request.getModel(),
                request.isWebSearch(), request.getTemperature(), request.getMaxTokens()));
    }

    private void doStream(HttpServletRequest req, HttpServletResponse resp, ChatService.StreamContext ctx) {
        AsyncContext asyncCtx = req.startAsync();
        asyncCtx.setTimeout(120000);

        try {
            ServletOutputStream out = resp.getOutputStream();
            StringBuilder fullContent = new StringBuilder();
            StringBuilder fullReasoning = new StringBuilder();
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<Usage> lastUsage = new AtomicReference<>();

            AtomicReference<Disposable> subRef = new AtomicReference<>();
            subRef.set(ctx.flux().subscribe(
                    chatResponse -> {
                        if (completed.get()) return;
                        try {
                            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                                lastUsage.set(chatResponse.getMetadata().getUsage());
                            }
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
                            dispose(subRef);
                            if (completed.compareAndSet(false, true)) {
                                asyncCtx.complete();
                            }
                        }
                    },
                    error -> {
                        dispose(subRef);
                        if (fullContent.length() > 0 || fullReasoning.length() > 0) {
                            try {
                                chatService.saveAssistantResponse(ctx.conversationId(),
                                        fullContent.toString(),
                                        fullReasoning.length() > 0 ? fullReasoning.toString() : null,
                                        lastUsage.get());
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
                                        fullReasoning.length() > 0 ? fullReasoning.toString() : null,
                                        lastUsage.get());
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
            ));

            asyncCtx.addListener(new AsyncListener() {
                @Override public void onTimeout(AsyncEvent event) { dispose(subRef); }
                @Override public void onError(AsyncEvent event) { dispose(subRef); }
                @Override public void onComplete(AsyncEvent event) {}
                @Override public void onStartAsync(AsyncEvent event) {}
            });
        } catch (IOException e) {
            log.error("Failed to initialize SSE stream for conversation {}", ctx.conversationId(), e);
            asyncCtx.complete();
        }
    }

    private void doStreamAppend(HttpServletRequest req, HttpServletResponse resp,
                                  ChatService.StreamContext ctx, String appendToMessageId) {
        AsyncContext asyncCtx = req.startAsync();
        asyncCtx.setTimeout(120000);
        try {
            ServletOutputStream out = resp.getOutputStream();
            StringBuilder addedContent = new StringBuilder();
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<Disposable> subRef = new AtomicReference<>();

            subRef.set(ctx.flux().subscribe(
                    chatResponse -> {
                        if (completed.get()) return;
                        try {
                            Generation gen = chatResponse.getResult();
                            if (gen == null) return;
                            var output = gen.getOutput();
                            if (output == null) return;
                            String content = output.getText();
                            if (content != null && !content.isEmpty()) {
                                addedContent.append(content);
                                String sse = "data: " + objectMapper.writeValueAsString(
                                        Map.of("content", content)) + "\n\n";
                                out.write(sse.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            }
                        } catch (IOException e) {
                            log.warn("Client disconnected during continue for message {}", appendToMessageId);
                            if (addedContent.length() > 0) {
                                try { chatService.appendToMessage(appendToMessageId, addedContent.toString(), true); }
                                catch (Exception ex) { log.error("Failed to save partial continuation", ex); }
                            }
                            dispose(subRef);
                            if (completed.compareAndSet(false, true)) asyncCtx.complete();
                        }
                    },
                    error -> {
                        dispose(subRef);
                        if (addedContent.length() > 0) {
                            try { chatService.appendToMessage(appendToMessageId, addedContent.toString(), true); }
                            catch (Exception e) { log.error("Failed to save error continuation", e); }
                        }
                        try {
                            String sse = "data: " + objectMapper.writeValueAsString(
                                    Map.of("error", error.getMessage() != null ? error.getMessage() : "Unknown error")) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException ignored) {}
                        if (completed.compareAndSet(false, true)) asyncCtx.complete();
                    },
                    () -> {
                        try {
                            if (addedContent.length() > 0) {
                                chatService.appendToMessage(appendToMessageId, addedContent.toString(), false);
                            }
                            Map<String, Object> doneData = new HashMap<>();
                            doneData.put("done", true);
                            doneData.put("messageId", appendToMessageId);
                            String sse = "data: " + objectMapper.writeValueAsString(doneData) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException ignored) {
                        } catch (Exception e) {
                            log.error("Failed to append continuation to message {}", appendToMessageId, e);
                        }
                        if (completed.compareAndSet(false, true)) asyncCtx.complete();
                    }
            ));

            asyncCtx.addListener(new AsyncListener() {
                @Override public void onTimeout(AsyncEvent event) { dispose(subRef); }
                @Override public void onError(AsyncEvent event) { dispose(subRef); }
                @Override public void onComplete(AsyncEvent event) {}
                @Override public void onStartAsync(AsyncEvent event) {}
            });
        } catch (IOException e) {
            log.error("Failed to initialize continue SSE stream for message {}", appendToMessageId, e);
            asyncCtx.complete();
        }
    }

    private static void dispose(AtomicReference<Disposable> ref) {
        Disposable d = ref.get();
        if (d != null && !d.isDisposed()) d.dispose();
    }

    private String extractReasoning(Generation gen) {
        if (gen == null) return null;
        if (gen.getMetadata() != null) {
            Object r = gen.getMetadata().get("reasoningContent");
            if (r != null && !r.toString().isEmpty()) return r.toString();
        }
        return null;
    }
}
