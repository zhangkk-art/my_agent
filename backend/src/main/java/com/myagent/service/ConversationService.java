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
                        .orderByDesc(Conversation::getUpdatedAt));
        for (Conversation conv : conversations) {
            conv.setMessages(messageMapper.selectList(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getConversationId, conv.getId())
                            .orderByAsc(Message::getCreatedAt)
                            .last("LIMIT " + MAX_MESSAGES)));
        }
        return conversations;
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
        Conversation conversation = getConversation(id);
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
     */
    @Transactional
    public void trimMessages(String conversationId) {
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getCreatedAt));
        if (messages.size() > MAX_MESSAGES) {
            for (int i = MAX_MESSAGES; i < messages.size(); i++) {
                messageMapper.deleteById(messages.get(i).getId());
            }
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
}
