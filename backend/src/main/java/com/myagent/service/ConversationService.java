package com.myagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.ConversationMapper;
import com.myagent.mapper.MessageMapper;
import com.myagent.model.Conversation;
import com.myagent.model.Message;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private static final int MAX_MESSAGES = 100;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final CacheManager cacheManager;

    public ConversationService(ConversationMapper conversationMapper,
                               MessageMapper messageMapper,
                               CacheManager cacheManager) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.cacheManager = cacheManager;
    }

    public List<Conversation> getAllConversations(String userId) {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getPinned)
                        .orderByDesc(Conversation::getUpdatedAt));
        for (Conversation conv : conversations) {
            conv.setMessages(java.util.Collections.emptyList());
        }
        return conversations;
    }

    @Transactional
    public Conversation togglePin(String id, String userId) {
        Conversation conv = getOwnedConversation(id, userId);
        conv.setPinned(conv.getPinned() == null || !conv.getPinned());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return conv;
    }

    @Transactional
    public Conversation setFolder(String id, String folderName, String userId) {
        Conversation conv = getOwnedConversation(id, userId);
        conv.setFolderName(folderName == null || folderName.isBlank() ? null : folderName.trim());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return conv;
    }

    @Transactional
    public Conversation importConversation(String title, List<java.util.Map<String, String>> messages, String userId) {
        Conversation conv = createConversation(title != null && !title.isBlank() ? title : "导入的对话", userId);
        for (java.util.Map<String, String> m : messages) {
            String role = m.getOrDefault("role", "user");
            String content = m.getOrDefault("content", "");
            if (!content.isBlank() && (role.equals("user") || role.equals("assistant"))) {
                Message msg = new Message(UUID.randomUUID().toString(), role, content, conv.getId());
                messageMapper.insert(msg);
            }
        }
        return getConversation(conv.getId());
    }

    @Transactional
    public Conversation createConversation(String title, String userId) {
        String id = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(id, title);
        conversation.setUserId(userId);
        conversationMapper.insert(conversation);
        return conversation;
    }

    public Conversation getConversation(String id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, id)
                        .orderByAsc(Message::getCreatedAt)
                        .last(safeLimit(MAX_MESSAGES)));
        conversation.setMessages(messages);
        return conversation;
    }

    public Conversation getConversation(String id, String userId) {
        Conversation conv = getOwnedConversation(id, userId);
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, id)
                        .orderByAsc(Message::getCreatedAt)
                        .last(safeLimit(MAX_MESSAGES)));
        conv.setMessages(messages);
        return conv;
    }

    private Conversation getOwnedConversation(String id, String userId) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        if (userId != null && conv.getUserId() != null && !userId.equals(conv.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return conv;
    }

    @Transactional
    public Conversation updateSystemPrompt(String id, String systemPrompt, String userId) {
        Conversation conv = getOwnedConversation(id, userId);
        conv.setSystemPrompt(systemPrompt);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return conv;
    }

    @Transactional
    public Conversation renameConversation(String id, String title) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        conversation.setTitle(title);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return conversation;
    }

    @Transactional
    public Conversation renameConversation(String id, String title, String userId) {
        Conversation conversation = getOwnedConversation(id, userId);
        conversation.setTitle(title);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return conversation;
    }

    @Transactional
    public Conversation touchConversation(String id, String userId) {
        Conversation conversation = getOwnedConversation(id, userId);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return conversation;
    }

    @Transactional
    public void deleteConversation(String id, String userId) {
        Conversation conv = getOwnedConversation(id, userId);
        messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, conv.getId()));
        conversationMapper.deleteById(id);
    }

    @Transactional
    public Conversation prepareForStream(String conversationId, String userMessage, String userId) {
        Conversation conv;
        if (conversationId == null || conversationId.isEmpty()) {
            String title = userMessage != null && userMessage.length() > 10
                    ? userMessage.substring(0, 10) : userMessage;
            conv = createConversation(title != null ? title : "New Chat", userId);
        } else {
            conv = getConversation(conversationId);
        }

        Message userMsg = new Message(
                UUID.randomUUID().toString(),
                "user",
                userMessage,
                conv.getId()
        );
        messageMapper.insert(userMsg);

        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);

        return getConversation(conv.getId());
    }

    @Transactional
    public void trimMessages(String conversationId) {
        long count = messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId));
        if (count <= MAX_MESSAGES) return;

        int excess = (int) (count - MAX_MESSAGES);
        List<Message> oldest = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt)
                        .last(safeLimit(excess)));
        if (!oldest.isEmpty()) {
            List<String> ids = oldest.stream()
                    .map(Message::getId)
                    .collect(java.util.stream.Collectors.toList());
            messageMapper.deleteBatchIds(ids);
        }
    }

    @Transactional
    public Conversation prepareForRegenerate(String conversationId, String userMessage) {
        Conversation conv = getConversation(conversationId);
        List<Message> messages = conv.getMessages();

        if (messages == null || messages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot regenerate: conversation has no messages");
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).getRole())) {
                messageMapper.deleteById(messages.get(i).getId());
                break;
            }
        }

        messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt));
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                messageMapper.deleteById(messages.get(i).getId());
                break;
            }
        }

        Message userMsg = new Message(
                UUID.randomUUID().toString(),
                "user",
                userMessage,
                conv.getId()
        );
        messageMapper.insert(userMsg);

        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);

        return getConversation(conv.getId());
    }

    @Transactional
    public Conversation forkConversation(String conversationId, String upToMessageId, String userId) {
        Conversation original = getOwnedConversation(conversationId, userId);

        List<Message> allMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt));

        List<Message> toCopy = new ArrayList<>();
        for (Message m : allMessages) {
            toCopy.add(m);
            if (m.getId().equals(upToMessageId)) break;
        }

        String newId = UUID.randomUUID().toString();
        Conversation forked = new Conversation(newId, original.getTitle() + " [分支]");
        forked.setSystemPrompt(original.getSystemPrompt());
        forked.setUserId(userId);
        conversationMapper.insert(forked);

        for (Message m : toCopy) {
            Message copy = new Message(UUID.randomUUID().toString(), m.getRole(), m.getContent(), newId);
            copy.setReasoning(m.getReasoning());
            copy.setPromptTokens(m.getPromptTokens());
            copy.setCompletionTokens(m.getCompletionTokens());
            copy.setTotalTokens(m.getTotalTokens());
            messageMapper.insert(copy);
        }

        return getConversation(newId);
    }

    @Transactional
    public String shareConversation(String id, String userId) {
        Conversation conv = getOwnedConversation(id, userId);
        if (conv.getShareToken() != null && !conv.getShareToken().isBlank()) {
            return conv.getShareToken();
        }
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        conv.setShareToken(token);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return token;
    }

    @Transactional
    public void revokeShare(String id, String userId) {
        Conversation conv = getOwnedConversation(id, userId);
        if (conv.getShareToken() != null) {
            Cache cache = cacheManager.getCache("shared_conversations");
            if (cache != null) cache.evict(conv.getShareToken());
        }
        conv.setShareToken(null);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Cacheable(value = "shared_conversations", key = "#token")
    public Conversation getSharedConversation(String token) {
        List<Conversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getShareToken, token));
        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shared conversation not found or has been revoked");
        }
        Conversation conv = list.get(0);
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conv.getId())
                        .orderByAsc(Message::getCreatedAt)
                        .last(safeLimit(MAX_MESSAGES)));
        conv.setMessages(messages);
        return conv;
    }

    private static String safeLimit(int n) {
        if (n <= 0 || n > 1000) {
            throw new IllegalArgumentException("Invalid limit: " + n);
        }
        return "LIMIT " + n;
    }
}
