package com.myagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.config.ChatClientRegistry;
import com.myagent.mapper.VideoGenTaskMapper;
import com.myagent.model.SubtitleEntry;
import com.myagent.model.VideoGenRequest;
import com.myagent.model.VideoGenTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final String ffmpegPath;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private final ChatClientRegistry clientRegistry;
    private final ExecutorService titleExecutor;

    public VideoGenService(
            VideoGenTaskMapper taskMapper,
            @Value("${app.ark.api-key:}") String apiKey,
            @Value("${app.ark.endpoint:https://ark.cn-beijing.volces.com/api/v3}") String endpoint,
            @Value("${app.ark.model:doubao-seedance-1-0-pro-fast-251015}") String model,
            @Value("${app.video.storage.path:./data/videos}") String storagePath,
            @Value("${app.video.ffmpeg.path:ffmpeg}") String ffmpegPath,
            ChatClientRegistry clientRegistry,
            @Qualifier("titleGenerationExecutor") ExecutorService titleExecutor) {
        this.taskMapper = taskMapper;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.storagePath = Paths.get(storagePath);
        this.ffmpegPath = ffmpegPath;
        this.clientRegistry = clientRegistry;
        this.titleExecutor = titleExecutor;
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
        return submitTask(request, userId, null);
    }

    /**
     * Submit a video generation task with optional storyboard association.
     */
    @Transactional
    public VideoGenTask submitTask(VideoGenRequest request, String userId, String storyboardId) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }

        VideoGenTask task = new VideoGenTask();
        task.setId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setPrompt(request.getPrompt());
        task.setReqKey(model);
        task.setConversationId(request.getConversationId());
        task.setStoryboardId(storyboardId);
        task.setDuration(request.getDuration() != null ? request.getDuration() : 5);
        // Seedance 1.0 pro fast: duration must be 2-12 seconds
        int duration = task.getDuration();
        if (duration < 2) task.setDuration(2);
        if (duration > 12) task.setDuration(12);
        task.setAspectRatio(request.getAspectRatio() != null ? request.getAspectRatio() : "16:9");
        task.setSeed(request.getSeed() != null ? request.getSeed() : -1);
        task.setSubtitleEnabled(request.getSubtitleEnabled() != null && request.getSubtitleEnabled());
        task.setGenerateAudio(request.getGenerateAudio() == null || request.getGenerateAudio());  // default true
        task.setNarrateSubtitles(request.getNarrateSubtitles() != null && request.getNarrateSubtitles());
        // Serialize custom subtitles to JSON for storage
        if (request.getCustomSubtitles() != null && !request.getCustomSubtitles().isEmpty()) {
            try {
                task.setCustomSubtitles(objectMapper.writeValueAsString(request.getCustomSubtitles()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("Failed to serialize custom subtitles", e);
            }
        }
        // Store negative prompt tags
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) {
            task.setNegativePrompt(request.getNegativePrompt());
        }
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // Async: generate a human-readable title via LLM (single-shot tasks only)
        if (storyboardId == null) {
            final String taskId = task.getId();
            final String taskPrompt = task.getPrompt();
            titleExecutor.submit(() -> generateTaskTitle(taskId, taskPrompt));
        }

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
            // Truncate prompt if too long (API limit ~500 Chinese chars / ~1000 English words)
            String prompt = request.getPrompt();
            if (prompt != null && prompt.length() > 500) {
                prompt = prompt.substring(0, 500);
                log.warn("Prompt truncated to 500 chars for task {}", task.getId());
            }
            // Append negative prompt as suffix if present
            String fullPrompt = prompt;
            if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) {
                fullPrompt = prompt + "。Avoid: " + request.getNegativePrompt();
            }
            textPart.put("text", fullPrompt);
            content.add(textPart);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("content", content);
            body.put("duration", task.getDuration());
            body.put("ratio", task.getAspectRatio());
            body.put("generate_audio", task.getGenerateAudio());
            if (task.getSeed() != -1) {
                body.put("seed", task.getSeed());
            }

            String payload = objectMapper.writeValueAsString(body);
            log.info("Ark submit payload: {}", payload);

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

    /**
     * Post-process a generated video: burn subtitles and/or TTS narration.
     * Called on-demand AFTER video generation succeeds, so users can decide
     * whether to apply subtitles/narration after seeing the result.
     */
    @Transactional
    public VideoGenTask applyPostProcessing(String taskId, boolean subtitleEnabled,
                                            boolean narrateSubtitles,
                                            List<SubtitleEntry> customSubtitles) {
        VideoGenTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new NoSuchElementException("任务不存在: " + taskId);
        }
        if (!"SUCCEEDED".equals(task.getStatus())) {
            throw new IllegalArgumentException("视频尚未生成完成，无法添加字幕/配音");
        }
        if (task.getVideoPath() == null) {
            throw new IllegalArgumentException("视频文件尚未就绪");
        }

        task.setSubtitleEnabled(subtitleEnabled);
        task.setNarrateSubtitles(narrateSubtitles);
        if (customSubtitles != null && !customSubtitles.isEmpty()) {
            try {
                task.setCustomSubtitles(objectMapper.writeValueAsString(customSubtitles));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("Failed to serialize custom subtitles for post-processing", e);
            }
        }

        try {
            // Burn subtitles first, then narrate on top
            if (subtitleEnabled) {
                processSubtitles(task);
            }
            if (narrateSubtitles) {
                processNarration(task);
            }
        } catch (Exception e) {
            log.error("Post-processing failed for task {}", taskId, e);
            throw new RuntimeException("处理失败: " + e.getMessage(), e);
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    /**
     * Generate SRT subtitle file from prompt text, burn into video via FFmpeg.
     */
    private void processSubtitles(VideoGenTask task) throws IOException, InterruptedException {
        Path videoPath = Path.of(task.getVideoPath());
        if (!Files.exists(videoPath)) {
            log.warn("Video file not found for subtitle processing: {}", task.getVideoPath());
            return;
        }

        // Generate SRT file (custom subtitles or auto-generated from prompt)
        List<SubtitleEntry> customEntries = parseCustomSubtitles(task.getCustomSubtitles());
        Path srtPath;
        if (customEntries != null && !customEntries.isEmpty()) {
            srtPath = generateSrtFromEntries(customEntries, task.getId());
        } else {
            srtPath = generateSrt(task.getPrompt(), task.getId(), task.getDuration());
        }
        task.setSubtitlePath(srtPath.toString());
        log.info("SRT generated for task {}: {}", task.getId(), srtPath);

        // Burn subtitles into video using FFmpeg
        Path outputPath = storagePath.resolve(task.getId() + "_subtitled.mp4");
        burnSubtitles(videoPath, srtPath, outputPath);

        // Replace original video with subtitled version
        Files.move(outputPath, videoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Subtitles burned into video for task {}", task.getId());

        // Clean up SRT file (subtitles are now burned in)
        try {
            Files.deleteIfExists(srtPath);
            task.setSubtitlePath(null);
        } catch (IOException e) {
            log.warn("Failed to delete SRT file: {}", srtPath, e);
        }
    }

    /**
     * Generate TTS voice audio from prompt and mix into video via FFmpeg.
     * Uses Windows SAPI via PowerShell for speech synthesis.
     */
    private void processNarration(VideoGenTask task) throws IOException, InterruptedException {
        Path videoPath = Path.of(task.getVideoPath());
        if (!Files.exists(videoPath)) {
            log.warn("Video file not found for TTS narration: {}", task.getVideoPath());
            return;
        }

        // Generate TTS WAV audio — use custom subtitle text if available
        String narrationText = task.getPrompt();
        List<SubtitleEntry> customEntries = parseCustomSubtitles(task.getCustomSubtitles());
        if (customEntries != null && !customEntries.isEmpty()) {
            narrationText = customEntries.stream()
                    .map(SubtitleEntry::getText)
                    .reduce((a, b) -> a + "。" + b).orElse(task.getPrompt());
        }
        Path wavPath = generateTtsAudio(narrationText, task.getId());
        if (wavPath == null) {
            log.warn("TTS audio generation returned null for task {}", task.getId());
            return;
        }
        log.info("TTS audio generated for task {}: {}", task.getId(), wavPath);

        // Mix TTS audio with video's audio track
        Path outputPath = storagePath.resolve(task.getId() + "_narrated.mp4");
        mixAudioWithVideo(videoPath, wavPath, outputPath);

        // Replace original with narrated version
        Files.move(outputPath, videoPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("TTS narration mixed into video for task {}", task.getId());

        // Clean up TTS WAV file
        try {
            Files.deleteIfExists(wavPath);
        } catch (IOException e) {
            log.warn("Failed to delete TTS WAV: {}", wavPath, e);
        }
    }

    /**
     * Generate WAV audio from text using Windows SAPI via PowerShell.
     */
    private Path generateTtsAudio(String text, String taskId) throws IOException, InterruptedException {
        Path wavPath = storagePath.resolve(taskId + "_tts.wav");

        // Escape special characters for PowerShell
        String safeText = text.replace("\"", "\\\"")
                .replace("'", "''")
                .replace("\n", " ")
                .replace("\r", "");

        // PowerShell script using Windows SAPI Chinese voice
        String psScript = "Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "try { $s.SelectVoice('Microsoft Huihui Desktop') } catch {}; "
                + "$s.SetOutputToWaveFile('" + wavPath.toString().replace("\\", "\\\\") + "'); "
                + "$s.Speak('" + safeText + "'); "
                + "$s.Dispose()";

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-Command", psScript);
        pb.redirectErrorStream(true);

        log.info("Running TTS PowerShell for task {}", taskId);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0 || !Files.exists(wavPath) || Files.size(wavPath) < 100) {
            log.error("TTS generation failed, exit={}, output={}", exitCode, output.trim());
            return null;
        }
        log.info("TTS WAV generated: {} ({} bytes)", wavPath, Files.size(wavPath));
        return wavPath;
    }

    /**
     * Mix TTS narration audio into video using FFmpeg amix filter.
     * Lowers original audio volume (background music) and overlays TTS voice.
     */
    private void mixAudioWithVideo(Path videoPath, Path wavPath, Path outputPath)
            throws IOException, InterruptedException {
        // amix: mix original audio [0:a] with TTS audio [1:a]
        // Lower original audio to 30% so TTS voice is clear
        String filter = "[0:a]volume=0.3[a0];[1:a]volume=1.5[a1];[a0][a1]amix=inputs=2:duration=first";

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", videoPath.toString(),
                "-i", wavPath.toString(),
                "-filter_complex", filter,
                "-c:v", "copy",
                "-y",
                outputPath.toString()
        );
        pb.redirectErrorStream(true);

        log.info("Running FFmpeg audio mix for narration: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("FFmpeg audio mix failed with exit {}: {}", exitCode, output);
            throw new IOException("FFmpeg audio mixing failed with exit code " + exitCode);
        }
        log.info("FFmpeg audio mix completed for {}", videoPath);
    }

    /**
     * Parse custom subtitles JSON string to list of SubtitleEntry.
     */
    private List<SubtitleEntry> parseCustomSubtitles(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.core.type.TypeReference<List<SubtitleEntry>> ref =
                    new com.fasterxml.jackson.core.type.TypeReference<>() {};
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            log.warn("Failed to parse custom subtitles JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate SRT file from user-defined subtitle entries.
     */
    private Path generateSrtFromEntries(List<SubtitleEntry> entries, String taskId) throws IOException {
        StringBuilder srt = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            SubtitleEntry e = entries.get(i);
            srt.append(i + 1).append("\n");
            srt.append(formatSrtTime(e.getStartSec())).append(" --> ")
               .append(formatSrtTime(e.getEndSec())).append("\n");
            srt.append(e.getText()).append("\n");
            srt.append("\n");
        }
        Path srtPath = storagePath.resolve(taskId + ".srt");
        Files.writeString(srtPath, srt.toString());
        return srtPath;
    }

    /**
     * Generate an SRT subtitle file from prompt text (auto mode).
     * Splits text by punctuation and distributes evenly across video duration.
     */
    private Path generateSrt(String prompt, String taskId, int durationSeconds) throws IOException {
        // Split prompt into clauses by Chinese and English punctuation
        String[] rawClauses = prompt.split("[。！？\\n.!?]+");
        java.util.List<String> clauses = new java.util.ArrayList<>();
        for (String clause : rawClauses) {
            String trimmed = clause.trim();
            if (trimmed.isEmpty()) continue;

            // Further split long clauses by comma/semicolon
            if (trimmed.length() > 30) {
                String[] subClauses = trimmed.split("[，,；;]+");
                for (String sc : subClauses) {
                    String st = sc.trim();
                    if (!st.isEmpty()) {
                        clauses.add(st);
                    }
                }
            } else {
                clauses.add(trimmed);
            }
        }

        // If still empty, use the whole prompt as one subtitle
        if (clauses.isEmpty()) {
            clauses.add(prompt.trim());
        }

        int count = clauses.size();
        double segmentDuration = (double) durationSeconds / count;
        // Ensure minimum 1 second per subtitle
        if (segmentDuration < 1.0) {
            segmentDuration = 1.0;
        }

        StringBuilder srt = new StringBuilder();
        for (int i = 0; i < count; i++) {
            double startSec = i * segmentDuration;
            double endSec = Math.min((i + 1) * segmentDuration, durationSeconds);

            srt.append(i + 1).append("\n");
            srt.append(formatSrtTime(startSec)).append(" --> ").append(formatSrtTime(endSec)).append("\n");
            srt.append(clauses.get(i)).append("\n");
            srt.append("\n");
        }

        Path srtPath = storagePath.resolve(taskId + ".srt");
        Files.writeString(srtPath, srt.toString());
        return srtPath;
    }

    /**
     * Format seconds to SRT timestamp: HH:MM:SS,mmm
     */
    private String formatSrtTime(double totalSeconds) {
        int hours = (int) totalSeconds / 3600;
        int minutes = ((int) totalSeconds % 3600) / 60;
        int seconds = (int) totalSeconds % 60;
        int millis = (int) ((totalSeconds - (int) totalSeconds) * 1000);
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    /**
     * Burn subtitles into video using FFmpeg.
     */
    private void burnSubtitles(Path videoPath, Path srtPath, Path outputPath)
            throws IOException, InterruptedException {
        // Convert to absolute path with forward slashes
        String absPath = srtPath.toAbsolutePath().toString().replace('\\', '/');

        // FFmpeg subtitles filter: wrap path in single quotes to handle spaces,
        // escape colons for filter graph parsing, FontSize only (avoid &H color codes)
        // Format: subtitles='C\:/path/with spaces/file.srt':force_style=FontSize=18
        String subtitleFilter = "subtitles='" + absPath.replace(":", "\\:") + "':force_style=FontSize=18";

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", videoPath.toString(),
                "-vf", subtitleFilter,
                "-c:a", "copy",
                "-y",
                outputPath.toString()
        );
        pb.redirectErrorStream(true);

        log.info("Running FFmpeg: {}", String.join(" ", pb.command()));
        Process process = pb.start();

        // Read output for logging
        String output;
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            output = sb.toString();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("FFmpeg failed with exit code {}: {}", exitCode, output);
            throw new IOException("FFmpeg subtitle burning failed with exit code " + exitCode);
        }
        log.info("FFmpeg subtitle burning completed for {}", videoPath);
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

    /**
     * Async: generate a short human-readable title from the video prompt via LLM.
     */
    private void generateTaskTitle(String taskId, String prompt) {
        try {
            ChatClient client = clientRegistry.getDefault();
            String systemPrompt = "你是一个标题生成助手。根据用户的视频提示词，生成一个简洁的中文标题（不超过15个字）。只输出标题，不要任何额外文字或标点。";
            String title = client.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();
            if (title != null && !title.isBlank()) {
                title = title.trim().replaceAll("[\"「」『』\"']", "");
                if (title.length() > 20) title = title.substring(0, 20);
                VideoGenTask task = taskMapper.selectById(taskId);
                if (task != null) {
                    task.setTitle(title);
                    task.setUpdatedAt(LocalDateTime.now());
                    taskMapper.updateById(task);
                    log.info("Title generated for task {}: {}", taskId, title);
                }
            }
        } catch (Exception e) {
            log.warn("Title generation failed for task {}: {}", taskId, e.getMessage());
        }
    }
}
