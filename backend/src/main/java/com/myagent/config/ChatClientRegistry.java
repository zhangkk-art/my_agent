package com.myagent.config;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

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

        log.info("Initializing ChatClientRegistry \u2014 qwenBaseUrl={}, qwenModel={}", qwenBaseUrl, qwenModel);

        // DeepSeek \u2014 auto-configured via spring.ai.openai.*
        ChatClient deepseekClient = chatClientBuilder.build();

        // Qwen \u2014 manually built from app.ai.qwen.*
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
        log.info("ChatClientRegistry ready \u2014 registered providers: {}", clients.keySet());
    }

    public ChatClient select(String model) {
        ChatClient chosen = (model != null && clients.containsKey(model))
                ? clients.get(model)
                : defaultClient;
        log.info("selectClient(\u2018{}\u2019) \u2192 {}", model,
                chosen == defaultClient ? "deepseek(default)" : model);
        return chosen;
    }

    public ChatClient getDefault() {
        return defaultClient;
    }

    /**
     * Dedicated executor for background AI tasks (e.g. title generation).
     * Avoids blocking the common ForkJoinPool with synchronous API calls.
     */
    @Bean
    public ExecutorService titleGenerationExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "title-gen");
            t.setDaemon(true);
            return t;
        });
    }
}
