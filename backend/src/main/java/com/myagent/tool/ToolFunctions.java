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

    @Tool(description = "Get the current real weather for a specified city. MUST be called EVERY time the user asks about weather/temperature — never reuse weather data from conversation history, as weather changes constantly.")
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
                JsonNode current = root.path("current_condition").get(0);

                String tempC     = current.path("temp_C").asText();
                String feelsLike = current.path("FeelsLikeC").asText();
                String humidity  = current.path("humidity").asText();
                String weatherDesc = current.path("weatherDesc").get(0).path("value").asText();
                String windSpeed = current.path("windspeedKmph").asText();
                String windDir   = current.path("winddir16Point").asText();

                String result = String.format(
                        "City: %s\nTemperature: %s°C (feels like %s°C)\nWeather: %s\nHumidity: %s%%\nWind: %s km/h %s",
                        city, tempC, feelsLike, weatherDesc, humidity, windSpeed, windDir);

                JsonNode nearestArea = root.path("nearest_area").get(0);
                if (!nearestArea.isMissingNode()) {
                    String areaName = nearestArea.path("areaName").get(0).path("value").asText();
                    String country  = nearestArea.path("country").get(0).path("value").asText();
                    String region   = nearestArea.path("region").get(0).path("value").asText();
                    result = String.format(
                            "City: %s (%s, %s, %s)\nTemperature: %s°C (feels like %s°C)\nWeather: %s\nHumidity: %s%%\nWind: %s km/h %s",
                            city, areaName, region, country, tempC, feelsLike, weatherDesc, humidity, windSpeed, windDir);
                }
                return new WeatherResponse(result);
            } else {
                return new WeatherResponse("Unable to fetch weather for " + city + " (HTTP " + response.statusCode() + ")");
            }
        } catch (Exception e) {
            return new WeatherResponse("Failed to get weather: " + e.getMessage());
        }
    }

    public record TimeRequest(String timezone) {}
    public record TimeResponse(String datetime) {}
    public record WeatherRequest(String city) {}
    public record WeatherResponse(String weather) {}
}
