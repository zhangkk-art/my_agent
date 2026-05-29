package com.myagent.service;

import com.myagent.config.ChatClientRegistry;
import com.myagent.mapper.ConversationMapper;
import com.myagent.mapper.MessageMapper;
import com.myagent.model.Conversation;
import com.myagent.model.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.myagent.tool.ToolFunctions;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Autowired(required = false)
    private SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    @Autowired
    private ToolFunctions toolFunctions;

    private final ChatClientRegistry clientRegistry;
    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final String baseSystemPrompt;

    public ChatService(ChatClientRegistry clientRegistry,
                       ConversationService conversationService,
                       ConversationMapper conversationMapper,
                       MessageMapper messageMapper) {
        this.clientRegistry = clientRegistry;
        this.conversationService = conversationService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.baseSystemPrompt = loadSystemPrompt();
    }

    private ToolCallback[] getMcpTools() {
        return mcpToolCallbackProvider != null
                ? mcpToolCallbackProvider.getToolCallbacks()
                : new ToolCallback[0];
    }

    private ToolCallback[] getFileSystemTools() {
        return Arrays.stream(getMcpTools())
                .filter(t -> !isWebSearchTool(t))
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback[] getWebSearchToolCallbacks() {
        return Arrays.stream(getMcpTools())
                .filter(this::isWebSearchTool)
                .toArray(ToolCallback[]::new);
    }

    private boolean isWebSearchTool(ToolCallback t) {
        try {
            String name = t.getToolDefinition().name();
            return name != null && (name.startsWith("brave") || name.contains("web_search"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Build a dynamic system prompt. For real-time queries (time/weather),
     * appends a forceful override instruction that tells the model the
     * conversation history data is stale and the tool MUST be called.
     */
    private String buildDynamicSystemPrompt(String conversationId, String userMessage) {
        String base = buildSystemPrompt(conversationId);
        String requiredTool = detectRequiredTool(userMessage);
        if (requiredTool != null) {
            base += "\n\n【最高优先级指令 — 立即执行】\n"
                  + "对话历史中可能包含过时的时间或天气数据，这些数据现在是错误的。\n"
                  + "你必须立即调用 " + requiredTool + " 工具获取当前真实数据，\n"
                  + "绝对禁止使用对话历史中的任何时间/天气信息来回答。\n"
                  + "THIS OVERRIDE TAKES PRIORITY OVER CONVERSATION HISTORY.";
            log.info("Real-time query detected — injecting tool-force prompt for: {}", requiredTool);
        }
        return base;
    }

    private String detectRequiredTool(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return null;
        String msg = userMessage;
        if (containsAny(msg, "几点", "几号", "日期", "星期几", "今天周几", "今天星期",
                "当前时间", "现在时间", "现在几点", "今天几", "今天日期",
                "what time", "what day", "current time", "today's date")) {
            return "getCurrentTime";
        }
        if (containsAny(msg, "天气", "气温", "下雨", "下雪")) {
            return "getWeather";
        }
        return null;
    }

    private boolean containsAny(String msg, String... keywords) {
        String lower = msg.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private ChatClient selectClient(String model) {
        return clientRegistry.select(model);
    }

    private String loadSystemPrompt() {
        try {
            return new ClassPathResource("system-prompt.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("system-prompt.txt not found, AI will run without system instructions");
            return "";
        }
    }

    public Conversation getOrCreateConversation(String conversationId, String userMessage) {
        if (conversationId == null || conversationId.isEmpty()) {
            String title = userMessage != null && userMessage.length() > 10
                    ? userMessage.substring(0, 10) : userMessage;
            return conversationService.createConversation(title != null ? title : "New Chat");
        }
        return conversationService.getConversation(conversationId);
    }

    private Message insertMessage(String conversationId, String role, String content) {
        Message message = new Message(
                UUID.randomUUID().toString(),
                role,
                content,
                conversationId
        );
        messageMapper.insert(message);
        conversationService.trimMessages(conversationId);
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setUpdatedAt(java.time.LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
        return message;
    }

    public List<org.springframework.ai.chat.messages.Message> buildHistory(List<Message> messages) {
        return messages.stream()
                .map(m -> {
                    if ("user".equals(m.getRole())) {
                        return new UserMessage(m.getContent());
                    } else {
                        return new AssistantMessage(m.getContent());
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Non-streaming: uses Spring AI with function calling.
     */
    @Transactional
    public Message chat(String conversationId, String userMessage, String model) {
        Conversation conv = getOrCreateConversation(conversationId, userMessage);
        insertMessage(conv.getId(), "user", userMessage);
        conv = conversationService.getConversation(conv.getId());

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages());
        var spec = selectClient(model).prompt()
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage))
                .messages(history);
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
        String replyContent = spec.call().content();

        return insertMessage(conv.getId(), "assistant", replyContent);
    }

    private String buildSystemPrompt(String conversationId) {
        String base = baseSystemPrompt;
        if (conversationId != null) {
            Conversation conv = conversationMapper.selectById(conversationId);
            if (conv != null && conv.getSystemPrompt() != null && !conv.getSystemPrompt().isBlank()) {
                base = conv.getSystemPrompt();
            }
        }
        return base;
    }

    public StreamContext chatStream(String conversationId, String userMessage, String model, boolean webSearch) {
        Conversation conv = conversationService.prepareForStream(conversationId, userMessage);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages());
        if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
            history.set(history.size() - 1, new UserMessage(userMessage));
        }
        var spec = selectClient(model).prompt()
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage))
                .messages(history);
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
        if (webSearch) {
            spec.toolCallbacks(getWebSearchToolCallbacks());
        }
        Flux<String> content = spec.stream().content().onErrorResume(e -> Flux.empty());
        return new StreamContext(conv.getId(), content);
    }

    @Transactional
    public Message saveAssistantResponse(String conversationId, String content) {
        Message saved = insertMessage(conversationId, "assistant", content);
        // After the first exchange (1 user + 1 assistant), generate a title asynchronously
        Conversation conv = conversationService.getConversation(conversationId);
        if (conv.getMessages().size() == 2) {
            List<Message> msgs = conv.getMessages();
            String userContent = msgs.get(0).getContent();
            String assistantContent = msgs.get(1).getContent();
            CompletableFuture.runAsync(() -> generateTitle(conversationId, userContent, assistantContent));
        }
        return saved;
    }

    private void generateTitle(String conversationId, String userContent, String assistantContent) {
        try {
            String prompt = String.format(
                "根据以下对话内容，生成一个简洁的中文标题。要求：不超过15个字，不加引号和书名号，直接输出标题文字。\n\n用户：%s\n助手：%s",
                userContent.substring(0, Math.min(150, userContent.length())),
                assistantContent.substring(0, Math.min(300, assistantContent.length()))
            );
            String title = clientRegistry.getDefault().prompt().user(prompt).call().content();
            if (title != null && !title.isBlank()) {
                title = title.trim().replaceAll("[\"'《》【】<>]", "").trim();
                if (title.length() > 20) title = title.substring(0, 20);
                conversationService.renameConversation(conversationId, title);
                log.debug("Generated title for conversation {}: {}", conversationId, title);
            }
        } catch (Exception e) {
            log.warn("Failed to generate title for conversation {}", conversationId, e);
        }
    }

    public StreamContext chatImageStream(String conversationId, String userMessage, String model, List<String> images, boolean webSearch) {
        Conversation conv = conversationService.prepareForStream(conversationId, userMessage);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages());
        List<Media> mediaList = new ArrayList<>();
        for (String img : images) {
            if (img.startsWith("data:image/")) {
                String mimeType = img.substring(5, img.indexOf(";"));
                String base64 = img.substring(img.indexOf(",") + 1);
                byte[] decoded = java.util.Base64.getMimeDecoder().decode(base64);
                mediaList.add(new Media(MimeTypeUtils.parseMimeType(mimeType),
                        new ByteArrayResource(decoded)));
            }
        }
        var multimodalMsg = UserMessage.builder()
                .text(userMessage)
                .media(mediaList)
                .build();
        if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
            history.set(history.size() - 1, multimodalMsg);
        } else {
            history.add(multimodalMsg);
        }

        var spec = selectClient(model).prompt()
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage))
                .messages(history);
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
        if (webSearch) {
            spec.toolCallbacks(getWebSearchToolCallbacks());
        }
        Flux<String> content = spec.stream().content().onErrorResume(e -> Flux.empty());
        return new StreamContext(conv.getId(), content);
    }

    public StreamContext regenerateStream(String conversationId, String userMessage, String model, boolean webSearch) {
        Conversation conv = conversationService.prepareForRegenerate(conversationId, userMessage);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages());
        if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
            history.set(history.size() - 1, new UserMessage(userMessage));
        }
        var spec = selectClient(model).prompt()
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage))
                .messages(history);
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
        if (webSearch) {
            spec.toolCallbacks(getWebSearchToolCallbacks());
        }
        Flux<String> content = spec.stream().content().onErrorResume(e -> Flux.empty());
        return new StreamContext(conv.getId(), content);
    }

    public record StreamContext(String conversationId, Flux<String> content) {}
}
