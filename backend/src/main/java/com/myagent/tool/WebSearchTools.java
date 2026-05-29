package com.myagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Web search tool powered by Volcengine SearchInfinity API.
 * Only attached when the user enables the web search toggle in the frontend.
 */
@Component
public class WebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;
    private final String endpoint;

    public WebSearchTools(
            @Value("${app.websearch.api-key:}") String apiKey,
            @Value("${app.websearch.endpoint:https://api.volcengine.com/websearch/v1/query}") String endpoint) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        log.info("WebSearchTools initialized — endpoint={}, apiKey configured={}",
                endpoint, apiKey != null && !apiKey.isBlank());
    }

    @Tool(description = "搜索互联网获取实时信息。当用户询问的问题需要最新信息时使用此工具。" +
            "适用于：新闻事件、实时数据、最新动态、事实查询等需要联网获取的信息。" +
            "返回搜索结果列表，每项包含标题、URL和摘要。")
    public WebSearchResponse searchWeb(WebSearchRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return new WebSearchResponse("联网搜索未配置 API Key，请在环境变量 WEB_SEARCH_API_KEY 中设置火山引擎 SearchInfinity API Key。" +
                    "获取地址：https://console.volcengine.com/search-infinity/api-key");
        }

        try {
            String query = request.query();
            int count = Math.min(request.count() > 0 ? request.count() : 5, 20);

            // Build request body
            String body = objectMapper.writeValueAsString(new SearchApiRequest(query, count));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            log.info("WebSearch query='{}' count={}", query, count);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<SearchResultItem> items = parseResults(root, query);
                log.info("WebSearch returned {} results for '{}'", items.size(), query);
                return new WebSearchResponse(formatResults(items, query));
            } else if (response.statusCode() == 429) {
                return new WebSearchResponse("搜索请求过于频繁，请稍后再试。");
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.error("WebSearch API authentication failed: {}", response.body());
                return new WebSearchResponse("联网搜索 API Key 无效，请检查配置。");
            } else {
                log.warn("WebSearch API returned status {}: {}", response.statusCode(), response.body());
                return new WebSearchResponse("搜索请求失败 (HTTP " + response.statusCode() + ")，请稍后重试。");
            }
        } catch (Exception e) {
            log.error("WebSearch failed for '{}'", request.query(), e);
            return new WebSearchResponse("搜索出错: " + e.getMessage());
        }
    }

    /**
     * Parse the API response. Tries multiple known response formats.
     */
    private List<SearchResultItem> parseResults(JsonNode root, String query) {
        List<SearchResultItem> items = new ArrayList<>();

        // Format 1: { "data": { "items": [...] } }
        JsonNode itemsNode = root.at("/data/items");
        // Format 2: { "results": [...] }
        if (itemsNode.isMissingNode() || !itemsNode.isArray()) {
            itemsNode = root.path("results");
        }
        // Format 3: { "items": [...] }
        if (itemsNode.isMissingNode() || !itemsNode.isArray()) {
            itemsNode = root.path("items");
        }
        // Format 4: { "data": { "documents": [...] } }
        if (itemsNode.isMissingNode() || !itemsNode.isArray()) {
            itemsNode = root.at("/data/documents");
        }

        if (itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                String title = firstNonEmpty(
                        item, "title", "Title", "name", "Name");
                String url = firstNonEmpty(
                        item, "url", "URL", "link", "Link", "href");
                String snippet = firstNonEmpty(
                        item, "snippet", "Snippet", "content", "Content",
                        "summary", "Summary", "description", "Description",
                        "abstract", "Abstract", "text", "Text");

                if (title != null && !title.isBlank()) {
                    items.add(new SearchResultItem(title, url, snippet));
                }
            }
        }
        return items;
    }

    private String firstNonEmpty(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode field = node.path(name);
            if (!field.isMissingNode() && field.isTextual() && !field.asText().isBlank()) {
                return field.asText();
            }
        }
        return null;
    }

    private String formatResults(List<SearchResultItem> items, String query) {
        if (items.isEmpty()) {
            return "未找到与「" + query + "」相关的搜索结果。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("搜索「").append(query).append("」的结果：\n\n");
        for (int i = 0; i < items.size(); i++) {
            SearchResultItem item = items.get(i);
            sb.append(i + 1).append(". **").append(item.title()).append("**\n");
            if (item.url() != null && !item.url().isBlank()) {
                sb.append("   URL: ").append(item.url()).append("\n");
            }
            if (item.snippet() != null && !item.snippet().isBlank()) {
                sb.append("   ").append(item.snippet()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // --- DTOs ---

    public record WebSearchRequest(String query, int count) {}

    public record WebSearchResponse(String results) {}

    private record SearchResultItem(String title, String url, String snippet) {}

    /**
     * Request body for SearchInfinity API.
     */
    private record SearchApiRequest(String Query, int Count) {}
}
