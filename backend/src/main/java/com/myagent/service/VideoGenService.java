package com.myagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.mapper.VideoGenTaskMapper;
import com.myagent.model.VideoGenRequest;
import com.myagent.model.VideoGenTask;
import com.myagent.util.IamV4Signer;
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

@Service
public class VideoGenService {

    private static final Logger log = LoggerFactory.getLogger(VideoGenService.class);

    private final VideoGenTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final IamV4Signer signer;
    private final String endpoint;
    private final String version;
    private final Path storagePath;

    public VideoGenService(
            VideoGenTaskMapper taskMapper,
            @Value("${app.jimeng.api.access-key:}") String accessKey,
            @Value("${app.jimeng.api.secret-key:}") String secretKey,
            @Value("${app.jimeng.api.endpoint:https://visual.volcengineapi.com}") String endpoint,
            @Value("${app.jimeng.api.region:cn-north-1}") String region,
            @Value("${app.jimeng.api.service:cv}") String service,
            @Value("${app.jimeng.api.version:2022-08-31}") String version,
            @Value("${app.video.storage.path:./data/videos}") String storagePath) {
        this.taskMapper = taskMapper;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.signer = new IamV4Signer(accessKey, secretKey, region, service);
        this.endpoint = endpoint;
        this.version = version;
        this.storagePath = Paths.get(storagePath);
        try {
            Files.createDirectories(this.storagePath);
        } catch (IOException e) {
            log.error("Failed to create video storage directory: {}", this.storagePath, e);
        }
    }

    /**
     * Check if API key is configured.
     */
    public boolean isConfigured() {
        return signer != null;
    }

    /**
     * Submit a video generation task to Jimeng API.
     */
    @Transactional
    public VideoGenTask submitTask(VideoGenRequest request, String userId) {
        // Validate
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }

        VideoGenTask task = new VideoGenTask();
        task.setId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setPrompt(request.getPrompt());
        task.setReqKey("jimeng_ti2v_v30_pro");
        task.setFrames(request.getFrames() != null ? request.getFrames() : 121);
        task.setAspectRatio(request.getAspectRatio() != null ? request.getAspectRatio() : "16:9");
        task.setSeed(request.getSeed() != null ? request.getSeed() : -1);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.insert(task);

        try {
            // Build Jimeng API request body
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("req_key", task.getReqKey());
            body.put("prompt", task.getPrompt());
            body.put("frames", task.getFrames());
            body.put("aspect_ratio", task.getAspectRatio());
            body.put("seed", task.getSeed());
            body.put("return_url", true);

            // If first frame image provided, add binary_data_base64
            if (request.getFirstFrameBase64() != null && !request.getFirstFrameBase64().isBlank()) {
                body.put("binary_data_base64", List.of(request.getFirstFrameBase64()));
            }

            String payload = objectMapper.writeValueAsString(body);
            String query = "Action=CVSync2AsyncSubmitTask&Version=" + version;
            String host = URI.create(endpoint).getHost();
            String path = "/";

            String xDate = signer.getXDate();
            String authorization = signer.sign("POST", path, query, payload, host);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "?" + query))
                    .header("Content-Type", "application/json")
                    .header("X-Date", xDate)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null && data.get("task_id") != null) {
                    task.setTaskId(data.get("task_id").toString());
                    task.setStatus("SUBMITTED");
                    log.info("Jimeng task submitted: taskId={}, jimengTaskId={}", task.getId(), task.getTaskId());
                } else {
                    task.setStatus("FAILED");
                    task.setErrorMessage("即梦API返回异常: " + response.body());
                    log.error("Jimeng submit failed: {}", response.body());
                }
            } else {
                task.setStatus("FAILED");
                task.setErrorMessage("即梦API返回HTTP " + response.statusCode() + ": " + response.body());
                log.error("Jimeng submit failed with HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage("提交失败: " + e.getMessage());
            log.error("Failed to submit Jimeng task", e);
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    /**
     * Poll task status from Jimeng API and update DB.
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
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("req_key", task.getReqKey());
            body.put("task_id", task.getTaskId());

            String payload = objectMapper.writeValueAsString(body);
            String query = "Action=CVSync2AsyncGetResult&Version=" + version;
            String host = URI.create(endpoint).getHost();
            String path = "/";

            String xDate = signer.getXDate();
            String authorization = signer.sign("POST", path, query, payload, host);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "?" + query))
                    .header("Content-Type", "application/json")
                    .header("X-Date", xDate)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null) {
                    String status = data.get("status") != null ? data.get("status").toString() : null;
                    if ("done".equals(status)) {
                        // Task succeeded
                        @SuppressWarnings("unchecked")
                        Map<String, Object> videoInfo = (Map<String, Object>) data.get("video");
                        // videoInfo might be null or in different shapes; try common patterns
                        String videoUrl = null;
                        if (videoInfo != null) {
                            if (videoInfo.get("video_url") != null) {
                                videoUrl = videoInfo.get("video_url").toString();
                            } else if (data.get("video_url") != null) {
                                videoUrl = data.get("video_url").toString();
                            }
                        }
                        // Fallback: check data directly
                        if (videoUrl == null && data.get("video_url") != null) {
                            videoUrl = data.get("video_url").toString();
                        }

                        if (videoUrl != null && !videoUrl.isBlank()) {
                            task.setOriginalVideoUrl(videoUrl);
                            // Download video to local storage
                            String localPath = downloadVideo(videoUrl, task.getId());
                            task.setVideoPath(localPath);
                            task.setStatus("SUCCEEDED");
                            log.info("Jimeng task {} succeeded, video saved to {}", task.getId(), localPath);
                        } else {
                            task.setStatus("FAILED");
                            task.setErrorMessage("即梦返回成功但未包含视频URL");
                            log.warn("Jimeng task {} done but no video URL in response: {}", task.getId(), response.body());
                        }
                    } else if ("failed".equals(status)) {
                        task.setStatus("FAILED");
                        task.setErrorMessage(data.get("message") != null ? data.get("message").toString() : "生成失败");
                        log.warn("Jimeng task {} failed: {}", task.getId(), task.getErrorMessage());
                    } else if ("not_found".equals(status) || "expired".equals(status)) {
                        task.setStatus("FAILED");
                        task.setErrorMessage("即梦任务已过期或不存在");
                    } else {
                        // queuing or running
                        task.setStatus("PROCESSING");
                    }
                }
            } else {
                log.warn("Jimeng poll returned HTTP {} for task {}", response.statusCode(), task.getId());
            }
        } catch (Exception e) {
            log.error("Failed to poll Jimeng task {}", task.getId(), e);
            // Don't mark as failed on transient poll errors
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    /**
     * Download video from URL and save to local storage.
     */
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

    /**
     * Get all tasks for a user, ordered by creation time descending.
     */
    public List<VideoGenTask> getUserTasks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        List<VideoGenTask> tasks = taskMapper.selectByMap(params);
        tasks.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return tasks;
    }

    /**
     * Get a single task by ID. Also polls Jimeng if in non-terminal state.
     */
    public VideoGenTask getTask(String taskId) {
        return pollTask(taskId);
    }

    /**
     * Delete a task and its local video file.
     */
    @Transactional
    public void deleteTask(String taskId) {
        VideoGenTask task = taskMapper.selectById(taskId);
        if (task == null) return;

        // Delete local video file
        if (task.getVideoPath() != null) {
            try {
                Files.deleteIfExists(Path.of(task.getVideoPath()));
            } catch (IOException e) {
                log.warn("Failed to delete video file: {}", task.getVideoPath(), e);
            }
        }

        taskMapper.deleteById(taskId);
    }

    /**
     * Get the local video file path for playback.
     */
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
