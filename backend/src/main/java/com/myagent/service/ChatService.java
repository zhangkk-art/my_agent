package com.myagent.service;

import com.myagent.mapper.ConversationMapper;
import com.myagent.mapper.MessageMapper;
import com.myagent.model.Conversation;
import com.myagent.model.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final String baseSystemPrompt;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ConversationService conversationService,
                       ConversationMapper conversationMapper,
                       MessageMapper messageMapper) {
        this.chatClient = chatClientBuilder.build();
        this.conversationService = conversationService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.baseSystemPrompt = loadSystemPrompt();
    }

    private String loadSystemPrompt() {
        try {
            return new ClassPathResource("system-prompt.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
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
    public Message chat(String conversationId, String userMessage) {
        Conversation conv = getOrCreateConversation(conversationId, userMessage);
        insertMessage(conv.getId(), "user", userMessage);
        conv = conversationService.getConversation(conv.getId());

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages());
        String replyContent = chatClient.prompt().messages(history)
                .functions("getCurrentTime", "getWeather")
                .call().content();

        return insertMessage(conv.getId(), "assistant", replyContent);
    }

    // ── Streaming: function calling not supported in Spring AI 1.0.0-M6 ──
    // Workaround: call functions directly and inject results as history context.

    private String nowText() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private String buildSystemPrompt() {
        return baseSystemPrompt + "\n\n当前时间：" + nowText();
    }

    private String withTime(String message) {
        return "[Current time: " + nowText() + "]\n" + message;
    }

    public StreamContext chatStream(String conversationId, String userMessage) {
        Conversation conv = conversationService.prepareForStream(conversationId, userMessage);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages());
        // Replace last user message with time-augmented version
        if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
            history.set(history.size() - 1, new UserMessage(withTime(userMessage)));
        }
        Flux<String> content = chatClient.prompt()
                .system(buildSystemPrompt())
                .messages(history)
                .stream().content()
                .onErrorResume(e -> Flux.empty());
        return new StreamContext(conv.getId(), content);
    }

    @Transactional
    public void saveAssistantResponse(String conversationId, String content) {
        insertMessage(conversationId, "assistant", content);
    }

    public StreamContext regenerateStream(String conversationId, String userMessage) {
        Conversation conv = conversationService.prepareForRegenerate(conversationId, userMessage);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages());
        // Replace last user message with time-augmented version
        if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
            history.set(history.size() - 1, new UserMessage(withTime(userMessage)));
        }
        Flux<String> content = chatClient.prompt()
                .system(buildSystemPrompt())
                .messages(history)
                .stream().content()
                .onErrorResume(e -> Flux.empty());
        return new StreamContext(conv.getId(), content);
    }

    public record StreamContext(String conversationId, Flux<String> content) {}
}
