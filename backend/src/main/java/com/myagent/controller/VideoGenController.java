package com.myagent.controller;

import com.myagent.model.VideoGenRequest;
import com.myagent.model.VideoGenTask;
import com.myagent.model.VideoPromptTemplate;
import com.myagent.security.UserPrincipal;
import com.myagent.service.VideoGenService;
import com.myagent.service.VideoPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/video-gen")
public class VideoGenController {


    private static final Logger log = LoggerFactory.getLogger(VideoGenController.class);

    private final VideoGenService videoGenService;
    private final VideoPromptService promptService;

    public VideoGenController(VideoGenService videoGenService, VideoPromptService promptService) {
        this.videoGenService = videoGenService;
        this.promptService = promptService;
    }

    /**
     * Submit a video generation task.
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody VideoGenRequest request) {
        try {
            VideoGenTask task = videoGenService.submitTask(request, principal.getUserId());
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", task.getId());
            result.put("status", task.getStatus());
            result.put("taskId", task.getTaskId());
            result.put("createdAt", task.getCreatedAt());
            if (task.getErrorMessage() != null) {
                result.put("error", task.getErrorMessage());
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to submit video generation task", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "提交失败: " + e.getMessage()));
        }
    }

    /**
     * Get all tasks for the current user.
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<VideoGenTask>> getTasks(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<VideoGenTask> tasks = videoGenService.getUserTasks(principal.getUserId());
        return ResponseEntity.ok(tasks);
    }

    /**
     * Get video tasks for a specific conversation.
     */
    @GetMapping("/conversations/{conversationId}/tasks")
    public ResponseEntity<List<VideoGenTask>> getConversationTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String conversationId) {
        List<VideoGenTask> tasks = videoGenService.getConversationTasks(conversationId);
        // Filter to only return tasks owned by the current user
        tasks = tasks.stream()
                .filter(t -> t.getUserId().equals(principal.getUserId()))
                .toList();
        return ResponseEntity.ok(tasks);
    }

    /**
     * Get a single task (also polls Jimeng for status update).
     */
    @GetMapping("/tasks/{id}")
    public ResponseEntity<?> getTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        try {
            VideoGenTask task = videoGenService.getTask(id);
            // Verify ownership
            if (!task.getUserId().equals(principal.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "无权访问此任务"));
            }
            return ResponseEntity.ok(task);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update the conversation ID for a task (used when conversation is created after submission).
     */
    @PatchMapping("/tasks/{id}/conversation")
    public ResponseEntity<?> updateConversationId(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        try {
            VideoGenTask task = videoGenService.getTask(id);
            if (!task.getUserId().equals(principal.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "无权修改此任务"));
            }
            String conversationId = body.get("conversationId");
            videoGenService.updateConversationId(id, conversationId);
            return ResponseEntity.ok(Map.of("updated", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a task and its local video file.
     */
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<?> deleteTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        try {
            VideoGenTask task = videoGenService.getTask(id);
            if (!task.getUserId().equals(principal.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "无权删除此任务"));
            }
            videoGenService.deleteTask(id);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Stream the generated video file for playback.
     */
    @GetMapping("/tasks/{id}/video")
    public ResponseEntity<?> getVideo(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        try {
            VideoGenTask task = videoGenService.getTask(id);
            if (!task.getUserId().equals(principal.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "无权访问此视频"));
            }
            if (!"SUCCEEDED".equals(task.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "视频尚未生成完成"));
            }
            Path videoPath = videoGenService.getVideoPath(id);
            if (videoPath == null) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(videoPath);
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + ".mp4\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes");
            try {
                builder.contentLength(resource.contentLength());
            } catch (Exception ignored) {
                // contentLength may throw IOException, continue without it
            }
            return builder.body(resource);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Check if Jimeng API is configured.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("configured", videoGenService.isConfigured()));
    }

    // ── Prompt Engineering endpoints ──

    @PostMapping("/prompt/enhance")
    public ResponseEntity<?> enhancePrompt(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        try {
            String prompt = body.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "提示词不能为空"));
            }
            VideoPromptService.EnhanceResult result = promptService.enhance(prompt);
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("enhanced", result.enhanced());
            if (result.suggestedNegative() != null) {
                resp.put("suggestedNegative", result.suggestedNegative());
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Prompt enhancement failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "增强失败: " + e.getMessage()));
        }
    }

    @PostMapping("/prompt/translate")
    public ResponseEntity<?> translatePrompt(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        try {
            String prompt = body.get("prompt");
            String target = body.get("target");
            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "提示词不能为空"));
            }
            String translated = promptService.translate(prompt, target);
            return ResponseEntity.ok(Map.of("translated", translated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Prompt translation failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "翻译失败: " + e.getMessage()));
        }
    }

    @GetMapping("/prompt/templates")
    public ResponseEntity<List<VideoPromptTemplate>> getPromptTemplates(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(promptService.getTemplates());
    }

    @PostMapping("/prompt/templates")
    public ResponseEntity<?> createPromptTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            String content = body.get("content");
            String category = body.get("category");
            if (name == null || name.isBlank() || content == null || content.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "名称和内容不能为空"));
            }
            VideoPromptTemplate t = promptService.createTemplate(name, content, category);
            return ResponseEntity.ok(t);
        } catch (Exception e) {
            log.error("Failed to create video prompt template", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "创建失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/prompt/templates/{id}")
    public ResponseEntity<?> deletePromptTemplate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        try {
            promptService.deleteTemplate(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete video prompt template", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }
}
