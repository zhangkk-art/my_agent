package com.myagent.rag.controller;

import com.myagent.model.ChatRequest;
import com.myagent.rag.model.KnowledgeDocument;
import com.myagent.rag.service.KnowledgeService;
import com.myagent.service.ChatService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final ChatService chatService;

    public KnowledgeController(KnowledgeService knowledgeService, ChatService chatService) {
        this.knowledgeService = knowledgeService;
        this.chatService = chatService;
    }

    @PostMapping("/documents")
    public KnowledgeDocument upload(@RequestParam("file") MultipartFile file) throws Exception {
        return knowledgeService.uploadDocument(file);
    }

    @GetMapping("/documents")
    public List<KnowledgeDocument> listDocuments() {
        return knowledgeService.listDocuments();
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) throws Exception {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * RAG streaming chat — search knowledge base, inject context, stream answer.
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void ragChat(@RequestBody ChatRequest request,
                        HttpServletRequest req,
                        HttpServletResponse resp) throws Exception {
        // 1. Search knowledge base
        List<KnowledgeService.SearchResult> results = knowledgeService.search(request.getMessage());

        // 2. Build context from search results
        StringBuilder contextBuilder = new StringBuilder();
        if (!results.isEmpty()) {
            contextBuilder.append("根据知识库中的以下相关片段回答问题：\n\n");
            for (int i = 0; i < results.size(); i++) {
                KnowledgeService.SearchResult r = results.get(i);
                contextBuilder.append("[来源").append(i + 1).append(": ").append(r.docName()).append("]\n");
                contextBuilder.append(r.text()).append("\n\n");
            }
            contextBuilder.append("请基于以上片段回答用户问题。如果片段中没有相关信息，请如实说明。\n");
            contextBuilder.append("在回答中引用来源时使用编号标注，例如 [来源1]、[来源2]。");
        }

        // 3. Build citations metadata for frontend
        List<Map<String, Object>> citations = results.stream()
                .map(r -> Map.<String, Object>of(
                        "docName", r.docName(),
                        "text", r.text(),
                        "score", Math.round(r.score() * 1000.0) / 1000.0))
                .toList();

        // 4. Stream with RAG context
        String augmentedMessage = contextBuilder.isEmpty()
                ? request.getMessage()
                : contextBuilder + "\n\n用户问题：" + request.getMessage();

        ChatService.StreamContext ctx = chatService.chatStream(
                request.getConversationId(), augmentedMessage,
                request.getModel(), request.isWebSearch(), null, null);

        // Stream the response, appending citations at the end
        AsyncContext asyncCtx = req.startAsync();
        asyncCtx.setTimeout(120000);
        ServletOutputStream out = resp.getOutputStream();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        ctx.flux().subscribe(
                chatResponse -> {
                    try {
                        var gen = chatResponse.getResult();
                        if (gen != null && gen.getOutput() != null) {
                            String content = gen.getOutput().getText();
                            if (content != null && !content.isEmpty()) {
                                String sse = "data: " + mapper.writeValueAsString(
                                        Map.of("content", content)) + "\n\n";
                                out.write(sse.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            }
                        }
                    } catch (Exception ignored) {}
                },
                error -> {
                    try {
                        String sse = "data: " + mapper.writeValueAsString(
                                Map.of("error", error.getMessage())) + "\n\n";
                        out.write(sse.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (Exception ignored) {}
                    asyncCtx.complete();
                },
                () -> {
                    try {
                        // Send done with citations
                        Map<String, Object> done = new java.util.HashMap<>();
                        done.put("done", true);
                        if (!citations.isEmpty()) {
                            done.put("citations", citations);
                        }
                        String sse = "data: " + mapper.writeValueAsString(done) + "\n\n";
                        out.write(sse.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (Exception ignored) {}
                    asyncCtx.complete();
                }
        );
    }
}
