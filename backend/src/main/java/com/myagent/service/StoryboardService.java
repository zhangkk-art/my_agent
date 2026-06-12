package com.myagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.config.ChatClientRegistry;
import com.myagent.mapper.StoryboardMapper;
import com.myagent.mapper.StoryboardShotMapper;
import com.myagent.mapper.VideoGenTaskMapper;
import com.myagent.model.Storyboard;
import com.myagent.model.StoryboardShot;
import com.myagent.model.VideoGenRequest;
import com.myagent.model.VideoGenTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StoryboardService {

    private static final Logger log = LoggerFactory.getLogger(StoryboardService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位专业的视频导演和分镜师，擅长将创意构思转化为可执行的分镜脚本。

            根据用户的创意主题，生成 {SHOT_COUNT} 个镜头的分镜脚本。每个镜头需包含：

            1. shotDescription（画面描述）：详细描述画面内容、光线、色调、构图、主体动作（中文，50-150字）
            2. cameraMovement（运镜方式）：推镜 / 拉镜 / 摇镜 / 跟镜 / 固定 / 升降
            3. duration（时长建议）：2~12 秒之间的整数
            4. sceneNote（场景备注）：简短的场景标识（5-10字）
            5. audioHint（音效提示）：该镜头的音效或配乐建议

            【连贯性要求——非常重要】：
            - 所有镜头必须围绕同一主题/故事展开，形成清晰的叙事线：开端 → 发展 → 高潮 → 结尾
            - 相邻镜头之间要有明确的承接关系（如：上一镜的落点衔接下一镜的起点、主体动作的延续、空间位置的递进）
            - 视觉风格保持统一（色调、光线方向、画面质感不能突变）
            - 景别变化要有节奏（如：远景建立场景 → 中景交代主体 → 特写强调情绪 → 远景收尾）
            - 运镜方向避免连续重复（如不要连续两个推镜），相邻镜头运镜应有变化
            - 首镜快速建立场景氛围，末镜给出明确的结束感（淡出/远去/定格）
            - 画面描述需包含镜头语言（景别、角度）和视觉风格（光线、色调）
            - 严格输出 JSON 数组，不要任何额外文字或 markdown 标记

            用户创意：{USER_IDEA}

            输出格式：
            [{"sceneNote": "...", "shotDescription": "...", "cameraMovement": "...", "duration": 5, "audioHint": "..."}]
            """;

    private final ChatClientRegistry clientRegistry;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardShotMapper storyboardShotMapper;
    private final VideoGenService videoGenService;
    private final VideoGenTaskMapper videoGenTaskMapper;
    private final ObjectMapper objectMapper;
    private final String ffmpegPath;
    private final Path storagePath;

    public StoryboardService(ChatClientRegistry clientRegistry,
                             StoryboardMapper storyboardMapper,
                             StoryboardShotMapper storyboardShotMapper,
                             VideoGenService videoGenService,
                             VideoGenTaskMapper videoGenTaskMapper,
                             @Value("${app.video.ffmpeg.path:ffmpeg}") String ffmpegPath,
                             @Value("${app.video.storage.path:./data/videos}") String storagePath) {
        this.clientRegistry = clientRegistry;
        this.storyboardMapper = storyboardMapper;
        this.storyboardShotMapper = storyboardShotMapper;
        this.videoGenService = videoGenService;
        this.videoGenTaskMapper = videoGenTaskMapper;
        this.ffmpegPath = ffmpegPath;
        this.storagePath = Paths.get(storagePath);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate a storyboard from the user's creative idea using DeepSeek LLM.
     * Returns a list of StoryboardShot objects (not yet persisted).
     */
    public List<StoryboardShot> generate(String idea, int shotCount) {
        if (idea == null || idea.isBlank()) {
            throw new IllegalArgumentException("创意主题不能为空");
        }
        if (shotCount < 1 || shotCount > 12) {
            throw new IllegalArgumentException("镜头数量需在1-12之间");
        }

        String systemPrompt = SYSTEM_PROMPT
                .replace("{SHOT_COUNT}", String.valueOf(shotCount))
                .replace("{USER_IDEA}", idea);

        try {
            String raw = clientRegistry.getDefault().prompt()
                    .system(systemPrompt)
                    .user(idea)
                    .call()
                    .content();

            if (raw == null || raw.isBlank()) {
                throw new RuntimeException("LLM 返回空结果，分镜生成失败，请重试");
            }

            log.info("LLM storyboard raw response length: {}", raw.length());

            // Strip markdown code fences if present
            String json = raw.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf("\n") + 1;
                int end = json.lastIndexOf("```");
                if (end > start) {
                    json = json.substring(start, end).trim();
                }
            }

            // Parse JSON array
            List<Map<String, Object>> rawShots = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            if (rawShots == null || rawShots.isEmpty()) {
                throw new RuntimeException("LLM 返回的分镜数据为空，分镜生成失败，请重试");
            }

            List<StoryboardShot> shots = new ArrayList<>();
            for (int i = 0; i < rawShots.size(); i++) {
                Map<String, Object> rawShot = rawShots.get(i);
                StoryboardShot shot = new StoryboardShot();
                shot.setSceneNumber(i + 1);
                shot.setSortOrder(i);
                shot.setStatus("PENDING");

                String shotDescription = getStringField(rawShot, "shotDescription");
                if (shotDescription == null || shotDescription.isBlank()) {
                    log.warn("Shot {} missing shotDescription, skipping", i + 1);
                    continue;
                }
                shot.setShotDescription(shotDescription);
                shot.setCameraMovement(getStringField(rawShot, "cameraMovement"));
                shot.setSceneNote(getStringField(rawShot, "sceneNote"));
                shot.setAudioHint(getStringField(rawShot, "audioHint"));

                Object durationObj = rawShot.get("duration");
                if (durationObj instanceof Number) {
                    int duration = ((Number) durationObj).intValue();
                    if (duration < 2) duration = 2;
                    if (duration > 12) duration = 12;
                    shot.setDuration(duration);
                } else {
                    shot.setDuration(5);
                }

                shots.add(shot);
            }

            if (shots.isEmpty()) {
                throw new RuntimeException("LLM 返回的分镜数据无效，分镜生成失败，请重试");
            }

            log.info("Storyboard generated: {} shots from idea ({} chars)", shots.size(), idea.length());
            return shots;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Storyboard generation failed", e);
            throw new RuntimeException("分镜生成失败，请重试: " + e.getMessage(), e);
        }
    }

    /**
     * Save a storyboard and all its shots to the database.
     */
    @Transactional
    public Storyboard save(Storyboard storyboard, List<StoryboardShot> shots) {
        if (storyboard == null) {
            throw new IllegalArgumentException("分镜信息不能为空");
        }
        if (shots == null || shots.isEmpty()) {
            throw new IllegalArgumentException("分镜脚本至少需要一个镜头");
        }

        LocalDateTime now = LocalDateTime.now();
        storyboard.setId(UUID.randomUUID().toString());
        if (storyboard.getTitle() == null || storyboard.getTitle().isBlank()) {
            storyboard.setTitle("未命名分镜");
        }
        storyboard.setShotCount(shots.size());
        storyboard.setCreatedAt(now);
        storyboard.setUpdatedAt(now);
        storyboardMapper.insert(storyboard);

        for (int i = 0; i < shots.size(); i++) {
            StoryboardShot shot = shots.get(i);
            shot.setId(UUID.randomUUID().toString());
            shot.setStoryboardId(storyboard.getId());
            shot.setSceneNumber(i + 1);
            shot.setSortOrder(i);
            if (shot.getStatus() == null || shot.getStatus().isBlank()) {
                shot.setStatus("PENDING");
            }
            if (shot.getDuration() == null) {
                shot.setDuration(5);
            }
            shot.setCreatedAt(now);
            shot.setUpdatedAt(now);
            storyboardShotMapper.insert(shot);
        }

        log.info("Storyboard saved: id={}, title={}, shots={}",
                storyboard.getId(), storyboard.getTitle(), shots.size());
        return storyboard;
    }

    /**
     * Get a storyboard by ID, including all associated shots.
     * Returns a map with "storyboard" and "shots" keys.
     * Verifies ownership via userId parameter.
     */
    public Map<String, Object> getStoryboard(String id, String userId) {
        Storyboard storyboard = storyboardMapper.selectById(id);
        if (storyboard == null) {
            throw new NoSuchElementException("分镜不存在");
        }
        if (userId != null && !userId.equals(storyboard.getUserId())) {
            throw new SecurityException("无权访问此分镜");
        }

        List<StoryboardShot> shots = storyboardShotMapper.selectByMap(
                Map.of("storyboard_id", id));
        shots.sort(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storyboard", storyboard);
        result.put("shots", shots);
        return result;
    }

    /**
     * Update a storyboard's title and replace all shots.
     * Verifies ownership via userId.
     */
    @Transactional
    public void updateStoryboard(String id, String title, List<StoryboardShot> shots, String userId) {
        Storyboard storyboard = storyboardMapper.selectById(id);
        if (storyboard == null) {
            throw new NoSuchElementException("分镜不存在");
        }
        if (userId != null && !userId.equals(storyboard.getUserId())) {
            throw new SecurityException("无权访问此分镜");
        }

        if (title != null && !title.isBlank()) {
            storyboard.setTitle(title);
        }
        storyboard.setUpdatedAt(LocalDateTime.now());

        if (shots != null && !shots.isEmpty()) {
            // Delete old shots
            storyboardShotMapper.deleteByMap(Map.of("storyboard_id", id));

            // Insert new shots
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < shots.size(); i++) {
                StoryboardShot shot = shots.get(i);
                shot.setId(UUID.randomUUID().toString());
                shot.setStoryboardId(id);
                shot.setSceneNumber(i + 1);
                shot.setSortOrder(i);
                if (shot.getStatus() == null || shot.getStatus().isBlank()) {
                    shot.setStatus("PENDING");
                }
                if (shot.getDuration() == null) {
                    shot.setDuration(5);
                }
                shot.setCreatedAt(now);
                shot.setUpdatedAt(now);
                storyboardShotMapper.insert(shot);
            }
            storyboard.setShotCount(shots.size());
        }

        storyboardMapper.updateById(storyboard);
        log.info("Storyboard updated: id={}, title={}", id, storyboard.getTitle());
    }

    /**
     * Delete a storyboard by ID. Cascade delete handled by DB foreign key.
     */
    @Transactional
    public void deleteStoryboard(String id, String userId) {
        Storyboard storyboard = storyboardMapper.selectById(id);
        if (storyboard == null) {
            throw new NoSuchElementException("分镜不存在: " + id);
        }
        if (!storyboard.getUserId().equals(userId)) {
            throw new SecurityException("无权访问此分镜");
        }
        storyboardMapper.deleteById(id);
        log.info("Storyboard deleted: id={}", id);
    }

    /**
     * Get all storyboards for a given conversation and user.
     */
    public List<Storyboard> getStoryboardsByConversation(String conversationId, String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("conversation_id", conversationId);
        params.put("user_id", userId);
        List<Storyboard> storyboards = storyboardMapper.selectByMap(params);
        storyboards.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return storyboards;
    }

    /**
     * Submit all PENDING shots in a storyboard for video generation.
     * Returns a list of maps with shotId, taskId, and status.
     */
    @Transactional
    public List<Map<String, Object>> submitAll(String storyboardId, Map<String, Object> commonParams, String userId) {
        Storyboard storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null) {
            throw new NoSuchElementException("分镜不存在");
        }
        if (userId != null && !userId.equals(storyboard.getUserId())) {
            throw new SecurityException("无权访问此分镜");
        }

        List<StoryboardShot> shots = storyboardShotMapper.selectByMap(
                Map.of("storyboard_id", storyboardId));
        shots.sort(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0));

        List<StoryboardShot> pendingShots = shots.stream()
                .filter(s -> "PENDING".equals(s.getStatus()))
                .toList();

        if (pendingShots.isEmpty()) {
            throw new IllegalArgumentException("没有可提交的镜头");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (StoryboardShot shot : pendingShots) {
            try {
                VideoGenRequest request = buildVideoGenRequest(shot, commonParams, storyboard.getConversationId());
                VideoGenTask task = videoGenService.submitTask(request, userId, storyboardId);

                shot.setTaskId(task.getId());
                shot.setStatus("SUBMITTED");
                shot.setUpdatedAt(now);
                storyboardShotMapper.updateById(shot);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("shotId", shot.getId());
                result.put("taskId", task.getId());
                result.put("status", "SUBMITTED");
                results.add(result);

                log.info("Shot submitted: shotId={}, taskId={}", shot.getId(), task.getId());
            } catch (Exception e) {
                log.error("Failed to submit shot: shotId={}", shot.getId(), e);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("shotId", shot.getId());
                result.put("taskId", null);
                result.put("status", "FAILED");
                result.put("error", e.getMessage());
                results.add(result);
            }
        }

        log.info("Storyboard submitAll complete: storyboardId={}, submitted={}/{}",
                storyboardId, results.stream().filter(r -> "SUBMITTED".equals(r.get("status"))).count(),
                pendingShots.size());
        return results;
    }

    /**
     * Submit a single PENDING shot for video generation.
     */
    @Transactional
    public Map<String, Object> submitSingle(String storyboardId, String shotId,
                                            Map<String, Object> commonParams, String userId) {
        Storyboard storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null) {
            throw new NoSuchElementException("分镜不存在");
        }
        if (userId != null && !userId.equals(storyboard.getUserId())) {
            throw new SecurityException("无权访问此分镜");
        }

        StoryboardShot shot = storyboardShotMapper.selectById(shotId);
        if (shot == null) {
            throw new NoSuchElementException("镜头不存在");
        }
        if (!storyboardId.equals(shot.getStoryboardId())) {
            throw new IllegalArgumentException("镜头不属于该分镜");
        }
        if (!"PENDING".equals(shot.getStatus())) {
            throw new IllegalArgumentException("该镜头已提交，无法重复提交");
        }

        VideoGenRequest request = buildVideoGenRequest(shot, commonParams, storyboard.getConversationId());
        VideoGenTask task = videoGenService.submitTask(request, userId);

        shot.setTaskId(task.getId());
        shot.setStatus("SUBMITTED");
        shot.setUpdatedAt(LocalDateTime.now());
        storyboardShotMapper.updateById(shot);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shotId", shot.getId());
        result.put("taskId", task.getId());
        result.put("status", "SUBMITTED");

        log.info("Single shot submitted: shotId={}, taskId={}", shot.getId(), task.getId());
        return result;
    }

    /**
     * Merge all SUCCEEDED shots of a storyboard into a single video using FFmpeg xfade transitions.
     * Returns the merged video file path.
     */
    public Path mergeStoryboardVideos(String storyboardId, String userId) throws IOException, InterruptedException {
        Storyboard storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null) {
            throw new NoSuchElementException("分镜不存在");
        }
        if (userId != null && !userId.equals(storyboard.getUserId())) {
            throw new SecurityException("无权访问此分镜");
        }

        List<StoryboardShot> shots = storyboardShotMapper.selectByMap(
                Map.of("storyboard_id", storyboardId));
        shots.sort(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0));

        // Filter to only shots whose task has actually succeeded
        // (The shot.status in DB may be stale — always check the real VideoGenTask status)
        List<StoryboardShot> succeeded = new ArrayList<>();
        for (StoryboardShot shot : shots) {
            if (shot.getTaskId() == null) continue;
            if ("SUCCEEDED".equals(shot.getStatus())) {
                succeeded.add(shot);
                continue;
            }
            // Fallback: check actual task status in case shot.status was never synced from frontend
            VideoGenTask task = videoGenTaskMapper.selectById(shot.getTaskId());
            if (task != null && "SUCCEEDED".equals(task.getStatus()) && task.getVideoPath() != null) {
                // Sync shot status to DB so subsequent queries are accurate
                shot.setStatus("SUCCEEDED");
                shot.setUpdatedAt(LocalDateTime.now());
                storyboardShotMapper.updateById(shot);
                succeeded.add(shot);
            }
        }

        if (succeeded.isEmpty()) {
            throw new IllegalArgumentException("没有已完成的镜头可合并");
        }

        // Only one shot — return its video directly, no merge needed
        if (succeeded.size() == 1) {
            VideoGenTask task = videoGenTaskMapper.selectById(succeeded.get(0).getTaskId());
            if (task == null || task.getVideoPath() == null) {
                throw new IllegalArgumentException("镜头视频文件尚未下载完成");
            }
            Path p = Path.of(task.getVideoPath());
            if (!Files.exists(p)) {
                throw new IllegalArgumentException("镜头视频文件不存在");
            }
            return p;
        }

        // Collect video paths and durations
        List<Path> videoPaths = new ArrayList<>();
        List<Double> offsets = new ArrayList<>();
        double currentOffset = 0;
        double transitionDuration = 1.0; // 1-second crossfade

        for (StoryboardShot shot : succeeded) {
            VideoGenTask task = videoGenTaskMapper.selectById(shot.getTaskId());
            if (task == null || task.getVideoPath() == null) {
                log.warn("Skipping shot {} — video not ready", shot.getId());
                continue;
            }
            Path p = Path.of(task.getVideoPath());
            if (!Files.exists(p)) {
                log.warn("Skipping shot {} — video file missing: {}", shot.getId(), task.getVideoPath());
                continue;
            }
            videoPaths.add(p);
            offsets.add(currentOffset);
            currentOffset += (shot.getDuration() != null ? shot.getDuration() : 5);
            currentOffset -= transitionDuration; // overlap for crossfade
        }

        if (videoPaths.size() < 2) {
            // Fallback: not enough valid videos after filtering
            if (videoPaths.isEmpty()) {
                throw new IllegalArgumentException("没有可用的视频文件进行合并");
            }
            return videoPaths.get(0);
        }

        // Determine target resolution (default 16:9 at 720p)
        int width = 1280;
        int height = 720;

        // Build FFmpeg xfade filter chain
        // Step 1: normalize each input [0:v],[1:v],... → [v0],[v1],...
        StringBuilder filterComplex = new StringBuilder();
        for (int i = 0; i < videoPaths.size(); i++) {
            filterComplex.append(String.format(
                "[%d:v]scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2,setpts=PTS-STARTPTS[v%d]; ",
                i, width, height, width, height, i));
        }

        // Step 2: chain xfade transitions
        String prevLabel = "v0";
        for (int i = 1; i < videoPaths.size(); i++) {
            double offset = offsets.get(i);
            String outLabel = (i == videoPaths.size() - 1) ? "fvout" : "fv" + i;
            filterComplex.append(String.format(
                "[%s][v%d]xfade=transition=fade:duration=%.1f:offset=%.1f[%s]",
                prevLabel, i, transitionDuration, offset, outLabel));
            if (i < videoPaths.size() - 1) {
                filterComplex.append("; ");
            }
            prevLabel = outLabel;
        }

        // Output path
        Path mergedPath = storagePath.resolve(storyboardId + "_merged.mp4");

        // Build command
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        for (Path vp : videoPaths) {
            cmd.add("-i");
            cmd.add(vp.toString());
        }
        cmd.add("-filter_complex");
        cmd.add(filterComplex.toString());
        cmd.add("-map");
        cmd.add("[fvout]");
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("fast");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-y");
        cmd.add(mergedPath.toString());

        log.info("FFmpeg merge: videoCount={}, mergedPath={}", videoPaths.size(), mergedPath);
        log.debug("FFmpeg command: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output for logging
        String ffmpegOutput;
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            ffmpegOutput = sb.toString();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("FFmpeg merge failed: exit={}, output={}", exitCode, ffmpegOutput);
            throw new IOException("视频合并失败 (FFmpeg exit " + exitCode + ")");
        }

        log.info("FFmpeg merge completed: {} videos → {} ({} bytes)",
                videoPaths.size(), mergedPath, Files.size(mergedPath));
        return mergedPath;
    }

    /**
     * Build a VideoGenRequest from a StoryboardShot and common parameters.
     */
    private VideoGenRequest buildVideoGenRequest(StoryboardShot shot, Map<String, Object> commonParams,
                                                  String conversationId) {
        VideoGenRequest request = new VideoGenRequest();
        request.setPrompt(shot.getShotDescription());
        request.setConversationId(conversationId);
        request.setDuration(shot.getDuration() != null ? shot.getDuration() : 5);

        if (commonParams != null) {
            if (commonParams.containsKey("aspectRatio")) {
                request.setAspectRatio(commonParams.get("aspectRatio").toString());
            }
            if (commonParams.containsKey("negativePrompt")) {
                Object np = commonParams.get("negativePrompt");
                if (np != null) request.setNegativePrompt(np.toString());
            }
            if (commonParams.containsKey("subtitleEnabled")) {
                Object se = commonParams.get("subtitleEnabled");
                request.setSubtitleEnabled(se instanceof Boolean ? (Boolean) se : Boolean.valueOf(se.toString()));
            }
            if (commonParams.containsKey("generateAudio")) {
                Object ga = commonParams.get("generateAudio");
                request.setGenerateAudio(ga instanceof Boolean ? (Boolean) ga : Boolean.valueOf(ga.toString()));
            }
            if (commonParams.containsKey("narrateSubtitles")) {
                Object ns = commonParams.get("narrateSubtitles");
                request.setNarrateSubtitles(ns instanceof Boolean ? (Boolean) ns : Boolean.valueOf(ns.toString()));
            }
            if (commonParams.containsKey("seed")) {
                Object seed = commonParams.get("seed");
                if (seed instanceof Number) {
                    request.setSeed(((Number) seed).intValue());
                }
            }
        }

        // Default aspect ratio if not specified
        if (request.getAspectRatio() == null) {
            request.setAspectRatio("16:9");
        }

        return request;
    }

    /**
     * Safely extract a string field from a map.
     */
    private String getStringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
