package com.myagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.mapper.VideoGenTaskMapper;
import com.myagent.model.VideoGenRequest;
import com.myagent.model.VideoGenTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class VideoGenService {

    private static final Logger log = LoggerFactory.getLogger(VideoGenService.class);

    private final VideoGenTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final Path storagePath;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();

    public VideoGenService(
            VideoGenTaskMapper taskMapper,
            @Value("${app.ark.api-key:}") String apiKey,
            @Value("${app.ark.endpoint:https://ark.cn-beijing.volces.com/api/v3}") String endpoint,
            @Value("${app.ark.model:doubao-seedance-1-0-pro-250528}") String model,
            @Value("${app.video.storage.path:./data/videos}") String storagePath) {
        this.taskMapper = taskMapper;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.storagePath = Paths.get(storagePath);
        try {
            Files.createDirectories(this.storagePath);
        } catch (IOException e) {
            log.error("Failed to create video storage directory: {}", this.storagePath, e);
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Submit a video generation task via Ark API.
     */
    @Transactional
    public VideoGenTask submitTask(VideoGenRequest request, String userId) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }

        VideoGenTask task = new VideoGenTask();
        task.setId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setPrompt(request.getPrompt());
        task.setReqKey(model);
        task.setConversationId(request.getConversationId());
        task.setDuration(request.getDuration() != null ? request.getDuration() : 5);
        task.setAspectRatio(request.getAspectRatio() != null ? request.getAspectRatio() : "16:9");
        task.setSeed(request.getSeed() != null ? request.getSeed() : -1);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        try {
            // Build Ark API request body
            List<Map<String, Object>> content = new ArrayList<>();

            // If first frame image provided, add it first
            if (request.getFirstFrameBase64() != null && !request.getFirstFrameBase64().isBlank()) {
                Map<String, Object> imagePart = new LinkedHashMap<>();
                imagePart.put("type", "image_url");
                Map<String, String> imageUrl = new LinkedHashMap<>();
                imageUrl.put("url", "data:image/png;base64," + request.getFirstFrameBase64());
                imagePart.put("image_url", imageUrl);
                content.add(imagePart);
            }

            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", request.getPrompt());
            content.add(textPart);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("content", content);
            body.put("duration", task.getDuration());
            body.put("ratio", task.getAspectRatio());
            if (task.getSeed() != -1) {
                body.put("seed", task.getSeed());
            }

            String payload = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/contents/generations/tasks"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.info("Ark submit response: HTTP {} body={}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                Object arkTaskId = result.get("id");
                if (arkTaskId != null) {
                    task.setTaskId(arkTaskId.toString());
                    task.setStatus("SUBMITTED");
                    log.info("Ark task submitted: localId={}, arkTaskId={}", task.getId(), task.getTaskId());
                } else {
                    task.setStatus("FAILED");
                    task.setErrorMessage("Ark API返回异常: " + response.body());
                    log.error("Ark submit failed: no id in response: {}", response.body());
                }
            } else {
                task.setStatus("FAILED");
                task.setErrorMessage("Ark API返回HTTP " + response.statusCode() + ": " + response.body());
                log.error("Ark submit failed with HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage("提交失败: " + e.getMessage());
            log.error("Failed to submit Ark task", e);
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    /**
     * Poll task status from Ark API and update DB.
     */
    @Transactional
    public VideoGenTask pollTask(String taskId) {
        VideoGenTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new NoSuchElementException("Task not found: " + taskId);
        }

        // Only poll if in non-terminal state
        if ("SUCCEEDED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            return task;
        }

        if (task.getTaskId() == null) {
            return task;
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/contents/generations/tasks/" + task.getTaskId()))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                String status = result.get("status") != null ? result.get("status").toString() : null;

                if ("succeeded".equals(status)) {
                    // Extract video URL from content
                    String videoUrl = null;
                    Object contentObj = result.get("content");
                    if (contentObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                        Object vu = contentMap.get("video_url");
                        if (vu != null) videoUrl = vu.toString();
                    }

                    if (videoUrl != null && !videoUrl.isBlank()) {
                        task.setOriginalVideoUrl(videoUrl);
                        task.setStatus("SUCCEEDED");
                        log.info("Ark task {} succeeded, starting async download from {}", task.getId(), videoUrl);
                        // Download asynchronously to avoid blocking the poll response
                        final String url = videoUrl;
                        final String tid = task.getId();
                        downloadExecutor.submit(() -> {
                            try {
                                String localPath = downloadVideo(url, tid);
                                VideoGenTask t = taskMapper.selectById(tid);
                                if (t != null) {
                                    t.setVideoPath(localPath);
                                    t.setUpdatedAt(LocalDateTime.now());
                                    taskMapper.updateById(t);
                                }
                                log.info("Video downloaded for task {}: {}", tid, localPath);
                            } catch (Exception e) {
                                log.error("Async download failed for task {}", tid, e);
                            }
                        });
                    } else {
                        task.setStatus("FAILED");
                        task.setErrorMessage("Ark返回成功但未包含视频URL");
                        log.warn("Ark task {} succeeded but no video_url in response: {}", task.getId(), response.body());
                    }
                } else if ("failed".equals(status)) {
                    task.setStatus("FAILED");
                    Object errMsg = result.get("error");
                    task.setErrorMessage(errMsg != null ? errMsg.toString() : "生成失败");
                    log.warn("Ark task {} failed: {}", task.getId(), task.getErrorMessage());
                } else if ("queued".equals(status)) {
                    task.setStatus("SUBMITTED");
                } else if ("running".equals(status)) {
                    task.setStatus("PROCESSING");
                }
            } else {
                log.warn("Ark poll returned HTTP {} for task {}", response.statusCode(), task.getId());
            }
        } catch (Exception e) {
            log.error("Failed to poll Ark task {}", task.getId(), e);
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    private String downloadVideo(String videoUrl, String taskId) throws IOException, InterruptedException {
        String fileName = taskId + ".mp4";
        Path targetPath = storagePath.resolve(fileName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(videoUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            Files.copy(response.body(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Video downloaded: {} -> {}", videoUrl, targetPath);
            return targetPath.toString();
        } else {
            throw new IOException("Failed to download video, HTTP " + response.statusCode());
        }
    }

    public List<VideoGenTask> getUserTasks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        List<VideoGenTask> tasks = taskMapper.selectByMap(params);
        tasks.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return tasks;
    }

    public List<VideoGenTask> getConversationTasks(String conversationId) {
        Map<String, Object> params = new HashMap<>();
        params.put("conversation_id", conversationId);
        List<VideoGenTask> tasks = taskMapper.selectByMap(params);
        tasks.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        return tasks;
    }

    public VideoGenTask getTask(String taskId) {
        return pollTask(taskId);
    }

    @Transactional
    public void deleteTask(String taskId) {
        VideoGenTask task = taskMapper.selectById(taskId);
        if (task == null) return;

        if (task.getVideoPath() != null) {
            try {
                Files.deleteIfExists(Path.of(task.getVideoPath()));
            } catch (IOException e) {
                log.warn("Failed to delete video file: {}", task.getVideoPath(), e);
            }
        }

        taskMapper.deleteById(taskId);
    }

    @Transactional
    public void updateConversationId(String taskId, String conversationId) {
        VideoGenTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new NoSuchElementException("Task not found: " + taskId);
        }
        task.setConversationId(conversationId);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public Path getVideoPath(String taskId) {
        VideoGenTask task = taskMapper.selectById(taskId);
        if (task == null || task.getVideoPath() == null) {
            return null;
        }
        Path path = Path.of(task.getVideoPath());
        if (Files.exists(path)) {
            return path;
        }
        return null;
    }
}
