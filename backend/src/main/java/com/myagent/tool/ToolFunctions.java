package com.myagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ToolFunctions {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Tool(description = "Get the current real date and time. MUST be called EVERY time the user asks about time/date/today/now — never reuse time values from conversation history, as they become stale immediately.")
    public TimeResponse getCurrentTime(TimeRequest request) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        try {
            if (request.timezone() != null && !request.timezone().isBlank()) {
                zone = ZoneId.of(request.timezone());
            }
        } catch (Exception ignored) {}
        ZonedDateTime now = ZonedDateTime.now(zone);
        return new TimeResponse(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
    }

    @Tool(description = "获取指定城市的天气信息，包括当前天气和未来几天预报。每次用户询问天气/温度时都必须调用此工具——绝不能复用对话历史中的天气数据。支持查询今天、明天及未来几天的天气。")
    public WeatherResponse getWeather(WeatherRequest request) {
        try {
            String city = request.city() != null ? request.city() : "Beijing";
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = "https://wttr.in/" + encodedCity + "?format=j1";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                StringBuilder result = new StringBuilder();

                // --- Current conditions ---
                JsonNode current = root.path("current_condition").get(0);
                if (!current.isMissingNode()) {
                    String tempC     = current.path("temp_C").asText();
                    String feelsLike = current.path("FeelsLikeC").asText();
                    String humidity  = current.path("humidity").asText();
                    String weatherDesc = current.path("weatherDesc").get(0).path("value").asText();
                    String windSpeed = current.path("windspeedKmph").asText();
                    String windDir   = current.path("winddir16Point").asText();

                    JsonNode nearestArea = root.path("nearest_area").get(0);
                    if (!nearestArea.isMissingNode()) {
                        String areaName = nearestArea.path("areaName").get(0).path("value").asText();
                        String country  = nearestArea.path("country").get(0).path("value").asText();
                        String region   = nearestArea.path("region").get(0).path("value").asText();
                        result.append(String.format(
                                "城市: %s (%s, %s, %s)\n", city, areaName, region, country));
                    } else {
                        result.append("城市: ").append(city).append("\n");
                    }
                    result.append(String.format(
                            "【当前天气】\n温度: %s°C (体感 %s°C)\n天气: %s\n湿度: %s%%\n风速: %s km/h %s\n",
                            tempC, feelsLike, weatherDesc, humidity, windSpeed, windDir));
                }

                // --- Multi-day forecast ---
                JsonNode weather = root.path("weather");
                if (weather.isArray() && weather.size() > 0) {
                    result.append("\n【未来天气预报】\n");
                    for (JsonNode day : weather) {
                        String date = day.path("date").asText();
                        String minTemp = day.path("mintempC").asText();
                        String maxTemp = day.path("maxtempC").asText();
                        String avgTemp = day.path("avgtempC").asText();
                        JsonNode hourly = day.path("hourly");
                        // Get midday (index 4-6, around 12pm) weather description as representative
                        String dayDesc = "";
                        if (hourly.isArray() && hourly.size() > 4) {
                            JsonNode midDay = hourly.get(Math.min(4, hourly.size() - 1));
                            dayDesc = midDay.path("weatherDesc").get(0).path("value").asText();
                        }
                        result.append(String.format(
                                "%s: %s ~ %s°C (平均 %s°C)%s\n",
                                date, minTemp, maxTemp, avgTemp,
                                dayDesc.isEmpty() ? "" : ", " + dayDesc));
                    }
                }

                return new WeatherResponse(result.toString().trim());
            } else {
                return new WeatherResponse("无法获取 " + city + " 的天气 (HTTP " + response.statusCode() + ")");
            }
        } catch (Exception e) {
            return new WeatherResponse("获取天气失败: " + e.getMessage());
        }
    }

    public record TimeRequest(String timezone) {}
    public record TimeResponse(String datetime) {}
    public record WeatherRequest(String city) {}
    public record WeatherResponse(String weather) {}
}
