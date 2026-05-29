package com.myagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.model.*;
import com.myagent.service.ChatService;
import com.myagent.service.ConversationService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {
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
        Message reply = chatService.chat(request.getConversationId(), request.getMessage());
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
        doStream(req, resp, chatService.chatStream(
                request.getConversationId(), request.getMessage()));
    }

    @PostMapping(value = "/chat/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void regenerate(@RequestBody ChatRequest request,
                           HttpServletRequest req,
                           HttpServletResponse resp) {
        doStream(req, resp, chatService.regenerateStream(
                request.getConversationId(), request.getMessage()));
    }

    private void doStream(HttpServletRequest req, HttpServletResponse resp, ChatService.StreamContext ctx) {
        // Start async — releases the servlet thread immediately
        AsyncContext asyncCtx = req.startAsync();
        asyncCtx.setTimeout(120000);

        try {
            ServletOutputStream out = resp.getOutputStream();

            StringBuilder fullContent = new StringBuilder();

            ctx.content().subscribe(
                    chunk -> {
                        fullContent.append(chunk);
                        try {
                            String sse = "data: " + objectMapper.writeValueAsString(
                                    Map.of("content", chunk)) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            asyncCtx.complete();
                        }
                    },
                    error -> {
                        if (fullContent.length() > 0) {
                            try { chatService.saveAssistantResponse(
                                    ctx.conversationId(), fullContent.toString());
                            } catch (Exception ignored) {}
                        }
                        try {
                            String sse = "data: " + objectMapper.writeValueAsString(
                                    Map.of("error", error.getMessage())) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            out.close();
                        } catch (IOException e) {}
                        asyncCtx.complete();
                    },
                    () -> {
                        try {
                            if (fullContent.length() > 0) {
                                chatService.saveAssistantResponse(
                                        ctx.conversationId(), fullContent.toString());
                            }
                            String sse = "data: " + objectMapper.writeValueAsString(
                                    Map.of("done", true)) + "\n\n";
                            out.write(sse.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            out.close();
                        } catch (IOException e) {}
                        asyncCtx.complete();
                    }
            );
        } catch (IOException e) {
            asyncCtx.complete();
        }
    }
}
