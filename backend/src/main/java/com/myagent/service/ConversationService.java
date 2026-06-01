package com.myagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.ConversationMapper;
import com.myagent.mapper.MessageMapper;
import com.myagent.model.Conversation;
import com.myagent.model.Message;
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

    public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    public List<Conversation> getAllConversations() {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .orderByDesc(Conversation::getPinned)
                        .orderByDesc(Conversation::getUpdatedAt));
        for (Conversation conv : conversations) {
            conv.setMessages(java.util.Collections.emptyList());
        }
        return conversations;
    }

    @Transactional
    public Conversation togglePin(String id) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        conv.setPinned(conv.getPinned() == null || !conv.getPinned());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return conv;
    }

    @Transactional
    public Conversation setFolder(String id, String folderName) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        conv.setFolderName(folderName == null || folderName.isBlank() ? null : folderName.trim());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return conv;
    }

    @Transactional
    public Conversation importConversation(String title, List<java.util.Map<String, String>> messages) {
        Conversation conv = createConversation(title != null && !title.isBlank() ? title : "导入的对话");
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
    public Conversation createConversation(String title) {
        String id = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(id, title);
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
                        .last("LIMIT " + MAX_MESSAGES));
        conversation.setMessages(messages);
        return conversation;
    }

    @Transactional
    public Conversation updateSystemPrompt(String id, String systemPrompt) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
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
    public Conversation touchConversation(String id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return conversation;
    }

    @Transactional
    public void deleteConversation(String id) {
        if (!conversationMapper.exists(new LambdaQueryWrapper<Conversation>().eq(Conversation::getId, id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, id));
        conversationMapper.deleteById(id);
    }

    @Transactional
    public Conversation prepareForStream(String conversationId, String userMessage) {
        Conversation conv;
        if (conversationId == null || conversationId.isEmpty()) {
            String title = userMessage != null && userMessage.length() > 10
                    ? userMessage.substring(0, 10) : userMessage;
            conv = createConversation(title != null ? title : "New Chat");
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

    /**
     * Delete oldest messages when a conversation exceeds the limit.
     * Uses a COUNT + SELECT + batch DELETE instead of N individual DELETEs.
     */
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
                        .last("LIMIT " + excess));
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

        // Reload after deleting the assistant message so the user-message scan
        // reflects the current DB state and finds the correct preceding user message.
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

    /**
     * Fork a conversation: copy all messages up to and including upToMessageId into a new conversation.
     */
    @Transactional
    public Conversation forkConversation(String conversationId, String upToMessageId) {
        Conversation original = conversationMapper.selectById(conversationId);
        if (original == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + conversationId);
        }

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

    /**
     * Generate a share token for a conversation. Returns the existing token if already shared.
     */
    @Transactional
    public String shareConversation(String id) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        if (conv.getShareToken() != null && !conv.getShareToken().isBlank()) {
            return conv.getShareToken();
        }
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        conv.setShareToken(token);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return token;
    }

    /**
     * Revoke a share token, making the conversation private again.
     */
    @Transactional
    public void revokeShare(String id) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found: " + id);
        }
        conv.setShareToken(null);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    /**
     * Get a conversation by its share token (public access).
     */
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
                        .last("LIMIT " + MAX_MESSAGES));
        conv.setMessages(messages);
        return conv;
    }
}
