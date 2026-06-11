package com.myagent.controller;

import com.myagent.model.VideoGenRequest;
import com.myagent.model.VideoGenTask;
import com.myagent.security.UserPrincipal;
import com.myagent.service.VideoGenService;
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

    public VideoGenController(VideoGenService videoGenService) {
        this.videoGenService = videoGenService;
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
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + ".mp4\"")
                    .body(resource);
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
}
