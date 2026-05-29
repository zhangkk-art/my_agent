package com.myagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);

    private final Map<String, ChatClient> clients;
    private final ChatClient defaultClient;

    public ChatClientRegistry(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.ai.qwen.api-key}") String qwenApiKey,
            @Value("${app.ai.qwen.base-url}") String qwenBaseUrl,
            @Value("${app.ai.qwen.model}") String qwenModel) {

        log.info("Initializing ChatClientRegistry — qwenBaseUrl={}, qwenModel={}", qwenBaseUrl, qwenModel);

        // DeepSeek — auto-configured via spring.ai.openai.*
        ChatClient deepseekClient = chatClientBuilder.build();

        // Qwen — manually built from app.ai.qwen.*
        var qwenApi = OpenAiApi.builder()
                .baseUrl(qwenBaseUrl)
                .apiKey(qwenApiKey)
                .build();
        var qwenChatModel = OpenAiChatModel.builder()
                .openAiApi(qwenApi)
                .defaultOptions(OpenAiChatOptions.builder().model(qwenModel).build())
                .build();
        ChatClient qwenClient = ChatClient.create(qwenChatModel);

        this.clients = Map.of("deepseek", deepseekClient, "qwen", qwenClient);
        this.defaultClient = deepseekClient;
        log.info("ChatClientRegistry ready — registered providers: {}", clients.keySet());
    }

    public ChatClient select(String model) {
        ChatClient chosen = (model != null && clients.containsKey(model))
                ? clients.get(model)
                : defaultClient;
        log.info("selectClient('{}') → {}", model,
                chosen == defaultClient ? "deepseek(default)" : model);
        return chosen;
    }

    public ChatClient getDefault() {
        return defaultClient;
    }
}
