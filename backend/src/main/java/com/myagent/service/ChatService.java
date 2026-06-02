package com.myagent.service;

import com.myagent.config.ChatClientRegistry;
import com.myagent.mapper.ConversationMapper;
import com.myagent.mapper.MessageMapper;
import com.myagent.model.Conversation;
import com.myagent.model.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.myagent.tool.ToolFunctions;
import com.myagent.tool.WebSearchTools;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Autowired(required = false)
    private SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    @Autowired
    private ToolFunctions toolFunctions;

    @Autowired
    private WebSearchTools webSearchTools;

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
        return getMcpTools();  // all MCP tools are filesystem tools now (web search is handled by WebSearchTools)
    }

    /**
     * Build a dynamic system prompt. For real-time queries (time/weather), pre-fetches
     * the data server-side and injects it directly — bypassing the model's unreliable
     * tool-calling in streaming mode. For web search queries, injects appropriate instructions.
     */
    private String buildDynamicSystemPrompt(String conversationId, String userMessage, boolean webSearch) {
        String base = buildSystemPrompt(conversationId);

        if (!webSearch && detectWebSearchNeed(userMessage)) {
            base += "\n\n【系统指令】用户的问题需要联网搜索才能准确回答，但用户尚未开启联网搜索功能。"
                  + "请友好地告知用户：这个问题需要联网搜索，请点击输入框左侧的地球图标开启联网搜索后再提问。"
                  + "绝对不要猜测或编造答案——没有联网搜索你无法获取这些实时信息。";
            log.info("Web search needed but disabled — prompting user to enable");
        }
        if (webSearch) {
            base += "\n\n【系统指令】用户已开启联网搜索。对于任何需要最新信息、实时数据的问题，" +
                    "必须调用 searchWeb 工具搜索，绝对不要使用对话历史中旧的搜索结果——那些数据已经过时。";
        }
        return base;
    }

    private String detectRequiredTool(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return null;
        String msg = userMessage;
        if (containsAny(msg,
                "几点", "几号", "几日", "几月", "哪天", "哪月",
                "日期", "星期几", "今天周几", "今天星期", "周几", "星期几",
                "什么时候", "啥时候", "今天是什么日子",
                "明天周几", "明天星期", "明天几号", "明天日期",
                "当前时间", "现在时间", "现在几点", "今天几", "今天日期",
                "现在是几", "什么时间", "是几点", "几点了", "几点钟",
                "what time", "what day", "current time", "today's date",
                "today is", "what's the date", "what date")) {
            return "getCurrentTime";
        }
        if (containsAny(msg, "天气", "气温", "温度", "下雨", "下雪")) {
            return "getWeather";
        }
        return null;
    }

    /**
     * Detect if the user's query likely needs web search to answer accurately.
     * Only triggers when webSearch toggle is off — tells the model to ask the user to enable it.
     */
    private boolean detectWebSearchNeed(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return false;
        String msg = userMessage;
        // News & current events
        if (containsAny(msg, "新闻", "最新", "最近发生", "热点", "热搜", "头条",
                "breaking", "latest news", "headline")) return true;
        // Finance & crypto
        if (containsAny(msg, "股价", "股票", "股市", "比特币", "以太坊", "加密货币",
                "大盘", "涨停", "跌停", "币价", "coin price",
                "stock price", "btc", "eth", "crypto", "nasdaq", "dow jones")) return true;
        // Sports & events
        if (containsAny(msg, "比分", "赛程", "赛事", "比赛结果", "欧冠", "英超", "NBA",
                "世界杯", "奥运会", "score", "game result", "match")) return true;
        // Real-time information that requires search
        if (containsAny(msg, "最新消息", "最近动态", "实时数据", "今天发生",
                "昨天发生", "最近发生", "刚刚发生", "突发", "地震")) return true;
        // Explicit search intent
        if (containsAny(msg, "搜索", "搜一下", "查一下", "帮我查", "帮我搜",
                "上网查", "网上查", "查查", "帮我找", "找一下")) return true;
        return false;
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

    /**
     * Build conversation history. Drops message pairs whose assistant reply contains stale
     * time/weather/search data — prevents the model from echoing a "[过时数据]" placeholder
     * or anchoring on old values. Dropping the whole pair (user + assistant) keeps history clean.
     */
    public List<org.springframework.ai.chat.messages.Message> buildHistory(
            List<Message> messages, String currentUserMessage, boolean webSearch) {
        String requiredTool = detectRequiredTool(currentUserMessage);
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
        for (Message m : messages) {
            if ("assistant".equals(m.getRole())) {
                if (shouldRedact(m.getContent(), requiredTool, webSearch)) {
                    // Also remove the preceding user message so the pair is fully dropped
                    if (!result.isEmpty() && result.get(result.size() - 1) instanceof UserMessage) {
                        result.remove(result.size() - 1);
                    }
                    log.info("Dropped stale message pair from history (tool={}, webSearch={})",
                            requiredTool, webSearch);
                    continue;
                }
                result.add(new AssistantMessage(m.getContent()));
            } else {
                result.add(new UserMessage(m.getContent()));
            }
        }
        return result;
    }

    private boolean shouldRedact(String content, String requiredTool, boolean webSearch) {
        // Redact stale time/weather data when user asks about them
        if (requiredTool != null && containsRealTimeData(content, requiredTool)) {
            return true;
        }
        // Redact stale search results when web search is enabled
        if (webSearch && containsSearchResults(content)) {
            return true;
        }
        return false;
    }

    private boolean containsSearchResults(String content) {
        if (content == null) return false;
        // Pattern: "搜索「...」的结果：" — our search result header
        if (content.contains("搜索「") && content.contains("」的结果")) return true;
        // Pattern: numbered list with URLs
        if (Pattern.compile("\\d+\\.\\s+\\*\\*.*?\\*\\*", Pattern.DOTALL).matcher(content).find()
                && content.contains("链接:")) return true;
        // Pattern: "Search results" or "Web search returned"
        if (content.contains("Search results") || content.contains("Web search returned")) return true;
        return false;
    }

    private boolean containsRealTimeData(String content, String requiredTool) {
        if (content == null) return false;
        if (requiredTool.contains("getCurrentTime")) {
            // Use Pattern.find() so multiline responses are handled correctly.
            // String.matches() would fail on any content with newlines because .* skips \n by default.
            if (Pattern.compile("\\d{1,2}[:：]\\d{2}").matcher(content).find()) return true;
            if (Pattern.compile("\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}").matcher(content).find()) return true;
            if (Pattern.compile("\\d{1,2}月\\d{1,2}[日号]").matcher(content).find()) return true;
            if (content.contains("CST") || content.contains("UTC") || content.contains("GMT")
                    || content.contains("时区") || content.contains("标准时间")) return true;
            if (Pattern.compile("[上中下]午\\d{1,2}[点时]").matcher(content).find()) return true;
            if (Pattern.compile("星期[一二三四五六日天]").matcher(content).find()) return true;
            if (Pattern.compile("20[2-9]\\d年").matcher(content).find()) return true;
        }
        if (requiredTool.contains("getWeather")) {
            if (Pattern.compile("\\d+\\s*[°度]").matcher(content).find()) return true;
            if (content.contains("天气") || content.contains("温度") || content.contains("气温")
                    || content.contains("湿度") || content.contains("风速") || content.contains("风向")) return true;
        }
        return false;
    }

    /**
     * Non-streaming: uses Spring AI with function calling.
     */
    @Transactional
    public Message chat(String conversationId, String userMessage, String model) {
        Conversation conv = getOrCreateConversation(conversationId, userMessage);
        insertMessage(conv.getId(), "user", userMessage);
        conv = conversationService.getConversation(conv.getId());

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages(), userMessage, false);
        var spec = selectClient(model).prompt()
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage, false))
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

    public StreamContext chatStream(String conversationId, String userMessage, String model,
                                    boolean webSearch, Double temperature, Integer maxTokens,
                                    String userId) {
        Conversation conv = conversationService.prepareForStream(conversationId, userMessage, userId);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages(), userMessage, webSearch);
        if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
            history.set(history.size() - 1, new UserMessage(userMessage));
        }
        var spec = selectClient(model).prompt()
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage, webSearch))
                .messages(history);
        applyOptions(spec, temperature, maxTokens);
        spec.toolCallbacks(getFileSystemTools());
        if (webSearch) {
            spec.tools(toolFunctions, webSearchTools);
        } else {
            spec.tools(toolFunctions);
        }
        Flux<ChatResponse> flux = spec.stream().chatResponse();
        return new StreamContext(conv.getId(), flux);
    }

    @Transactional
    public Message saveAssistantResponse(String conversationId, String content) {
        return saveAssistantResponse(conversationId, content, null, null);
    }

    @Transactional
    public Message saveAssistantResponse(String conversationId, String content, String reasoning) {
        return saveAssistantResponse(conversationId, content, reasoning, null);
    }

    @Transactional
    public Message saveAssistantResponse(String conversationId, String content, String reasoning,
                                          org.springframework.ai.chat.metadata.Usage usage) {
        Message saved = insertMessage(conversationId, "assistant", content);
        boolean needsUpdate = false;
        if (reasoning != null && !reasoning.isBlank()) {
            saved.setReasoning(reasoning);
            needsUpdate = true;
        }
        if (usage != null) {
            saved.setPromptTokens((int) usage.getPromptTokens());
            saved.setCompletionTokens((int) usage.getCompletionTokens());
            saved.setTotalTokens((int) usage.getTotalTokens());
            needsUpdate = true;
        }
        if (needsUpdate) {
            messageMapper.updateById(saved);
        }
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

    private void applyOptions(org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec spec,
                               Double temperature, Integer maxTokens) {
        if (temperature == null && maxTokens == null) return;
        var opts = org.springframework.ai.openai.OpenAiChatOptions.builder();
        if (temperature != null) opts.temperature(temperature);
        if (maxTokens != null) opts.maxTokens(maxTokens);
        spec.options(opts.build());
    }

    public StreamContext chatImageStream(String conversationId, String userMessage, String model,
                                         List<String> images, boolean webSearch,
                                         Double temperature, Integer maxTokens, String userId) {
        Conversation conv = conversationService.prepareForStream(conversationId, userMessage, userId);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages(), userMessage, webSearch);
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
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage, webSearch))
                .messages(history);
        applyOptions(spec, temperature, maxTokens);
        spec.toolCallbacks(getFileSystemTools());
        if (webSearch) {
            spec.tools(toolFunctions, webSearchTools);
        } else {
            spec.tools(toolFunctions);
        }
        Flux<ChatResponse> flux = spec.stream().chatResponse();
        return new StreamContext(conv.getId(), flux);
    }

    /** Save a partially-streamed assistant response as interrupted. */
    @Transactional
    public Message saveInterruptedResponse(String conversationId, String content, String reasoning) {
        Message saved = insertMessage(conversationId, "assistant", content != null ? content : "");
        saved.setInterrupted(true);
        if (reasoning != null && !reasoning.isBlank()) {
            saved.setReasoning(reasoning);
        }
        messageMapper.updateById(saved);
        return saved;
    }

    /** Append new continuation content to an existing message; set interrupted accordingly. */
    @Transactional
    public void appendToMessage(String messageId, String additionalContent, boolean interrupted) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) return;
        String existing = msg.getContent() != null ? msg.getContent() : "";
        msg.setContent(existing + additionalContent);
        msg.setInterrupted(interrupted);
        messageMapper.updateById(msg);
    }

    /** Build a stream that continues from a previously interrupted assistant message. */
    public StreamContext continueStream(String conversationId, String messageId, String model,
                                        Double temperature, Integer maxTokens) {
        Conversation conv = conversationService.getConversation(conversationId);
        List<Message> msgs = conv.getMessages();

        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        for (Message msg : msgs) {
            if ("user".equals(msg.getRole())) {
                history.add(new UserMessage(msg.getContent() != null ? msg.getContent() : ""));
            } else if ("assistant".equals(msg.getRole())) {
                history.add(new AssistantMessage(msg.getContent() != null ? msg.getContent() : ""));
            }
            if (msg.getId().equals(messageId)) break;
        }
        // Synthetic user message asking to continue — not persisted
        history.add(new UserMessage(
            "请继续你上面未完成的回答，从中断处接着输出，不要重复已经输出过的内容。"));

        var spec = selectClient(model).prompt()
                .system(buildSystemPrompt(conversationId))
                .messages(history);
        applyOptions(spec, temperature, maxTokens);
        spec.toolCallbacks(getFileSystemTools());
        spec.tools(toolFunctions);
        Flux<ChatResponse> flux = spec.stream().chatResponse();
        return new StreamContext(conversationId, flux);
    }

    public StreamContext regenerateStream(String conversationId, String userMessage, String model,
                                          boolean webSearch, Double temperature, Integer maxTokens) {
        Conversation conv = conversationService.prepareForRegenerate(conversationId, userMessage);

        List<org.springframework.ai.chat.messages.Message> history = buildHistory(conv.getMessages(), userMessage, webSearch);
        if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
            history.set(history.size() - 1, new UserMessage(userMessage));
        }
        var spec = selectClient(model).prompt()
                .system(buildDynamicSystemPrompt(conv.getId(), userMessage, webSearch))
                .messages(history);
        applyOptions(spec, temperature, maxTokens);
        spec.toolCallbacks(getFileSystemTools());
        if (webSearch) {
            spec.tools(toolFunctions, webSearchTools);
        } else {
            spec.tools(toolFunctions);
        }
        Flux<ChatResponse> flux = spec.stream().chatResponse();
        return new StreamContext(conv.getId(), flux);
    }

    public record StreamContext(String conversationId, Flux<ChatResponse> flux) {}
}
