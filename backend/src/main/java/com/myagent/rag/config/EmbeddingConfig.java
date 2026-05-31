package com.myagent.rag.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Separate EmbeddingModel pointing to DashScope (Aliyun).
 * Uses the OpenAI-compatible endpoint for text-embedding-v2 (1536 dims).
 */
@Configuration
public class EmbeddingConfig {

    @Value("${app.ai.qwen.api-key}")
    private String apiKey;

    @Value("${app.ai.qwen.base-url}")
    private String baseUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model("text-embedding-v2")
                        .build());
    }
}
