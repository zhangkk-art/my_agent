package com.myagent.controller;

import com.myagent.model.Storyboard;
import com.myagent.model.StoryboardShot;
import com.myagent.security.UserPrincipal;
import com.myagent.service.StoryboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/video-gen")
public class StoryboardController {

    private static final Logger log = LoggerFactory.getLogger(StoryboardController.class);
    private final StoryboardService storyboardService;

    public StoryboardController(StoryboardService storyboardService) {
        this.storyboardService = storyboardService;
    }

    /**
     * Generate a storyboard from a creative idea using LLM.
     */
    @PostMapping("/storyboard/generate")
    public ResponseEntity<?> generate(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            String idea = (String) body.get("idea");
            Integer shotCount = body.get("shotCount") != null ? ((Number) body.get("shotCount")).intValue() : 5;
            if (idea == null || idea.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "创意主题不能为空"));
            }
            if (shotCount < 1 || shotCount > 12) {
                return ResponseEntity.badRequest().body(Map.of("error", "镜头数量需在1-12之间"));
            }
            List<StoryboardShot> shots = storyboardService.generate(idea, shotCount);
            List<Map<String, Object>> result = shots.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sceneNote", s.getSceneNote());
                m.put("shotDescription", s.getShotDescription());
                m.put("cameraMovement", s.getCameraMovement());
                m.put("duration", s.getDuration());
                m.put("audioHint", s.getAudioHint());
                return m;
            }).toList();
            return ResponseEntity.ok(Map.of("shots", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Storyboard generation failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "分镜生成失败，请重试"));
        }
    }

    /**
     * Save a storyboard with its shots to the database.
     */
    @PostMapping("/storyboard")
    public ResponseEntity<?> save(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.get("title");
            String idea = (String) body.get("idea");
            String conversationId = (String) body.get("conversationId");
            Integer shotCount = body.get("shotCount") != null ? ((Number) body.get("shotCount")).intValue() : 5;
            if (title == null || title.isBlank()) title = idea != null ? (idea.length() > 50 ? idea.substring(0, 50) : idea) : "未命名分镜";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shotsData = (List<Map<String, Object>>) body.get("shots");
            if (shotsData == null || shotsData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "分镜脚本至少需要一个镜头"));
            }

            // Build storyboard
            Storyboard sb = new Storyboard();
            sb.setUserId(principal.getUserId());
            sb.setConversationId(conversationId);
            sb.setTitle(title);
            sb.setIdea(idea);
            sb.setShotCount(shotCount);

            // Build shots
            List<StoryboardShot> shots = new ArrayList<>();
            for (int i = 0; i < shotsData.size(); i++) {
                Map<String, Object> s = shotsData.get(i);
                StoryboardShot shot = new StoryboardShot();
                shot.setSceneNumber(i + 1);
                shot.setSceneNote((String) s.get("sceneNote"));
                shot.setShotDescription((String) s.get("shotDescription"));
                shot.setCameraMovement((String) s.get("cameraMovement"));
                shot.setDuration(s.get("duration") != null ? ((Number) s.get("duration")).intValue() : 5);
                shot.setAudioHint((String) s.get("audioHint"));
                shot.setSortOrder(i);
                shots.add(shot);
            }

            Storyboard saved = storyboardService.save(sb, shots);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", saved.getId());
            result.put("title", saved.getTitle());
            result.put("shotCount", shots.size());
            // Return actual saved shots with DB-assigned IDs
            List<Map<String, Object>> savedShots = shots.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", s.getId());
                m.put("storyboardId", s.getStoryboardId());
                m.put("sceneNumber", s.getSceneNumber());
                m.put("sceneNote", s.getSceneNote());
                m.put("shotDescription", s.getShotDescription());
                m.put("cameraMovement", s.getCameraMovement());
                m.put("duration", s.getDuration());
                m.put("audioHint", s.getAudioHint());
                m.put("sortOrder", s.getSortOrder());
                m.put("status", s.getStatus());
                m.put("taskId", s.getTaskId());
                return m;
            }).toList();
            result.put("shots", savedShots);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to save storyboard", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "保存失败: " + e.getMessage()));
        }
    }

    /**
     * Get a storyboard by ID, including all associated shots.
     */
    @GetMapping("/storyboard/{id}")
    public ResponseEntity<?> getStoryboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        try {
            Map<String, Object> data = storyboardService.getStoryboard(id, principal.getUserId());
            return ResponseEntity.ok(data);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a storyboard's title and replace all shots.
     */
    @PutMapping("/storyboard/{id}")
    public ResponseEntity<?> updateStoryboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            String title = (String) body.get("title");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shotsData = (List<Map<String, Object>>) body.get("shots");

            List<StoryboardShot> shots = new ArrayList<>();
            if (shotsData != null) {
                for (int i = 0; i < shotsData.size(); i++) {
                    Map<String, Object> s = shotsData.get(i);
                    StoryboardShot shot = new StoryboardShot();
                    shot.setSceneNumber(i + 1);
                    shot.setSceneNote((String) s.get("sceneNote"));
                    shot.setShotDescription((String) s.get("shotDescription"));
                    shot.setCameraMovement((String) s.get("cameraMovement"));
                    shot.setDuration(s.get("duration") != null ? ((Number) s.get("duration")).intValue() : 5);
                    shot.setAudioHint((String) s.get("audioHint"));
                    shot.setSortOrder(i);
                    shot.setStatus(s.get("status") != null ? (String) s.get("status") : "PENDING");
                    shot.setTaskId((String) s.get("taskId"));
                    shots.add(shot);
                }
            }

            storyboardService.updateStoryboard(id, title, shots, principal.getUserId());
            return ResponseEntity.ok(Map.of("updated", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a storyboard by ID.
     */
    @DeleteMapping("/storyboard/{id}")
    public ResponseEntity<?> deleteStoryboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        try {
            storyboardService.deleteStoryboard(id, principal.getUserId());
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all storyboards for a conversation.
     */
    @GetMapping("/storyboards")
    public ResponseEntity<List<Storyboard>> getStoryboards(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String conversationId) {
        return ResponseEntity.ok(storyboardService.getStoryboardsByConversation(conversationId, principal.getUserId()));
    }

    /**
     * Batch submit all PENDING shots in a storyboard for video generation.
     */
    @PostMapping("/storyboard/{id}/submit")
    public ResponseEntity<?> submitAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> params = body != null ? body : Map.of();
            List<Map<String, Object>> tasks = storyboardService.submitAll(id, params, principal.getUserId());
            if (tasks.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "没有可提交的镜头"));
            }
            return ResponseEntity.ok(Map.of("tasks", tasks));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit a single PENDING shot for video generation.
     */
    @PostMapping("/storyboard/{id}/shots/{shotId}/submit")
    public ResponseEntity<?> submitSingle(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @PathVariable String shotId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> params = body != null ? body : Map.of();
            Map<String, Object> task = storyboardService.submitSingle(id, shotId, params, principal.getUserId());
            return ResponseEntity.ok(task);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
