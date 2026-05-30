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
 * Web search tool powered by Bocha AI Search API.
 * Only attached when the user enables the web search toggle in the frontend.
 *
 * Register at https://open.bochaai.com to get a free API key (1000 free calls for new users).
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
            @Value("${app.websearch.endpoint:https://api.bochaai.com/v1/web-search}") String endpoint) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        log.info("WebSearchTools initialized — endpoint={}, apiKey configured={}",
                endpoint, apiKey != null && !apiKey.isBlank());
    }

    @Tool(description = "搜索互联网获取实时信息。当用户询问的问题需要最新信息时使用此工具。" +
            "参数query为搜索关键词。适用于：新闻事件、实时数据、最新动态、事实查询等需要联网获取的信息。" +
            "返回搜索结果列表，每项包含标题、URL和摘要。")
    public WebSearchResponse searchWeb(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return new WebSearchResponse(
                    "联网搜索未配置 API Key。请到 https://open.bochaai.com 注册获取免费 API Key（新用户1000次免费），" +
                    "然后设置环境变量 WEB_SEARCH_API_KEY=你的key。");
        }

        try {
            int count = 10;  // default result count

            // Bocha API — use oneWeek freshness to prioritize recent content
            var body = new BochaSearchRequest(query, count, "oneWeek");

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            log.info("WebSearch query='{}' count={}", query, count);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<SearchResultItem> items = parseBochaResults(root);
                log.info("WebSearch returned {} results for '{}'", items.size(), query);
                return new WebSearchResponse(formatResults(items, query));
            } else if (response.statusCode() == 429) {
                return new WebSearchResponse("搜索请求过于频繁，请稍后再试。");
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.error("WebSearch API authentication failed: {}", response.body());
                return new WebSearchResponse("联网搜索 API Key 无效，请到 https://open.bochaai.com 重新获取。");
            } else {
                log.warn("WebSearch API returned status {}: {}", response.statusCode(), response.body());
                return new WebSearchResponse("搜索请求失败 (HTTP " + response.statusCode() + ")，请稍后重试。");
            }
        } catch (Exception e) {
            log.error("WebSearch failed for '{}'", query, e);
            return new WebSearchResponse("搜索出错: " + e.getMessage());
        }
    }

    /**
     * Parse Bocha API response. Tries multiple known response formats.
     * Bocha returns: { "code": 200, "data": { "webPages": { "value": [...] } } }
     * or: { "webPages": { "value": [...] } }
     */
    private List<SearchResultItem> parseBochaResults(JsonNode root) {
        List<SearchResultItem> items = new ArrayList<>();

        // Unwrap "data" wrapper if present
        JsonNode data = root.path("data");
        if (!data.isMissingNode() && data.isObject()) {
            root = data;
        }

        // Try webPages.value (standard format)
        JsonNode webPages = root.path("webPages");
        JsonNode value = webPages.path("value");

        // Fallback: try "pages", "results", "items" at top level
        if (!value.isArray() || value.isEmpty()) {
            value = root.path("pages");
        }
        if (!value.isArray() || value.isEmpty()) {
            value = root.path("results");
        }
        if (!value.isArray() || value.isEmpty()) {
            value = root.path("items");
        }

        if (value.isArray()) {
            for (JsonNode item : value) {
                String title = firstNonEmpty(item,
                        "name", "title", "Title", "displayUrl");
                String url = firstNonEmpty(item,
                        "url", "Url", "link", "Link", "displayUrl");
                String snippet = firstNonEmpty(item,
                        "summary", "snippet", "Snippet", "description",
                        "content", "text", "abstract");

                if (!title.isBlank()) {
                    String siteName = item.path("siteName").asText();
                    if (!siteName.isBlank()) {
                        snippet = "[" + siteName + "] " + snippet;
                    }
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
        return "";
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
                sb.append("   链接: ").append(item.url()).append("\n");
            }
            if (item.snippet() != null && !item.snippet().isBlank()) {
                sb.append("   ").append(item.snippet()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // --- DTOs ---

    public record WebSearchResponse(String results) {}

    private record SearchResultItem(String title, String url, String snippet) {}

    /**
     * Request body for Bocha AI Search API.
     */
    private record BochaSearchRequest(String query, int count, String freshness) {}
}
