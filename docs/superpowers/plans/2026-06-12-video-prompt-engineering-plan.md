# 视频生成智能提示词工程 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为视频生成面板增加 LLM 驱动的 prompt 增强/翻译、模板库、反向提示词功能

**Architecture:** 新增 VideoPromptService 调用 DeepSeek 进行 prompt 增强和翻译，新增 video_prompt_templates 表管理模板，在 VideoGenController 中增加 4 个 REST 端点，前端 VideoGenPanel 增加工具栏和反向提示词区域

**Spec:** docs/superpowers/specs/2026-06-12-video-prompt-engineering-design.md

**Tech Stack:** Java 17, Spring Boot 3.5.x, MyBatis-Plus, Spring AI (ChatClient), Vue 3

---

## File Structure

**Created:**
- `backend/src/main/java/com/myagent/model/VideoPromptTemplate.java` — 模板实体
- `backend/src/main/java/com/myagent/mapper/VideoPromptTemplateMapper.java` — MyBatis-Plus Mapper
- `backend/src/main/java/com/myagent/service/VideoPromptService.java` — 增强、翻译、模板 CRUD

**Modified:**
- `backend/src/main/java/com/myagent/controller/VideoGenController.java` — 新增 4 个 prompt 端点
- `backend/src/main/java/com/myagent/config/SchemaMigration.java` — 建表 + 预设数据
- `backend/src/main/resources/schema.sql` — 新增 video_prompt_templates DDL
- `backend/src/main/java/com/myagent/model/VideoGenRequest.java` — 新增 negativePrompt 字段
- `backend/src/main/java/com/myagent/service/VideoGenService.java` — submitTask 中处理 negativePrompt
- `frontend/src/api/index.js` — 新增 5 个 API 函数
- `frontend/src/components/VideoGenPanel.vue` — 工具栏 + 反向提示词 + 模板选择器 + 翻译弹窗

---

### Task 1: Create VideoPromptTemplate entity

**Files:**
- Create: `backend/src/main/java/com/myagent/model/VideoPromptTemplate.java`

- [ ] **Step 1: Write the entity class**

```java
package com.myagent.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("video_prompt_templates")
public class VideoPromptTemplate {

    @TableId
    private String id;
    private String name;
    private String content;
    private String category;
    private Integer sortOrder;
    private Boolean isPreset;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VideoPromptTemplate() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsPreset() { return isPreset; }
    public void setIsPreset(Boolean isPreset) { this.isPreset = isPreset; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/myagent/model/VideoPromptTemplate.java
git commit -m "feat: add VideoPromptTemplate entity"
```

---

### Task 2: Create VideoPromptTemplateMapper

**Files:**
- Create: `backend/src/main/java/com/myagent/mapper/VideoPromptTemplateMapper.java`

- [ ] **Step 1: Write the Mapper interface**

```java
package com.myagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.model.VideoPromptTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoPromptTemplateMapper extends BaseMapper<VideoPromptTemplate> {
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/myagent/mapper/VideoPromptTemplateMapper.java
git commit -m "feat: add VideoPromptTemplateMapper"
```

---

### Task 3: Create VideoPromptService

**Files:**
- Create: `backend/src/main/java/com/myagent/service/VideoPromptService.java`

- [ ] **Step 1: Write the service class with enhance, translate, and template CRUD**

```java
package com.myagent.service;

import com.myagent.config.ChatClientRegistry;
import com.myagent.mapper.VideoPromptTemplateMapper;
import com.myagent.model.VideoPromptTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class VideoPromptService {

    private static final Logger log = LoggerFactory.getLogger(VideoPromptService.class);

    private final ChatClientRegistry clientRegistry;
    private final VideoPromptTemplateMapper templateMapper;

    public VideoPromptService(ChatClientRegistry clientRegistry,
                              VideoPromptTemplateMapper templateMapper) {
        this.clientRegistry = clientRegistry;
        this.templateMapper = templateMapper;
    }

    /**
     * Enhance a user's brief prompt into a detailed video-generation prompt using LLM.
     * @return EnhanceResult containing the enhanced prompt and suggested negative tags.
     */
    public EnhanceResult enhance(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }

        String systemPrompt = """
                你是一位资深的AI视频生成提示词工程师，擅长为即梦/Seedance等视频模型编写高质量提示词。

                请将用户提供的简短描述扩展为详细的视频生成提示词。扩展时需包含以下要素（如适用）：
                1. 镜头语言：景别（特写/中景/远景）、镜头角度（俯拍/仰拍/平视）、运镜方式（推拉摇移跟）
                2. 光线与色调：光源方向、色温（暖/冷/中性）、对比度、影调风格
                3. 主体与动作：主体外观特征、动作节奏（缓慢流畅/快速激烈）
                4. 场景与氛围：环境描述、天气/时间、情绪基调
                5. 风格：写实、电影感、动漫、赛博朋克等

                要求：
                - 使用中文输出（适配即梦 Seedance 模型）
                - 控制在 200 字以内
                - 直接输出最终提示词，不要解释
                - 同时给出 3-5 个建议的反向提示词（逗号分隔），放在 [NEGATIVE] 标记之后

                用户输入：%s

                输出格式：
                {增强后的提示词}
                [NEGATIVE] {反向提示词1}, {反向提示词2}, ...
                """.formatted(userPrompt);

        try {
            String raw = clientRegistry.getDefault().prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (raw == null || raw.isBlank()) {
                throw new RuntimeException("LLM 返回空结果");
            }

            // Parse the response: split on [NEGATIVE] marker
            String enhanced = raw;
            String suggestedNegative = "";
            int negIdx = raw.indexOf("[NEGATIVE]");
            if (negIdx >= 0) {
                enhanced = raw.substring(0, negIdx).trim();
                suggestedNegative = raw.substring(negIdx + "[NEGATIVE]".length()).trim();
            }

            log.info("Prompt enhanced: {} chars -> {} chars, suggested negative: {}",
                    userPrompt.length(), enhanced.length(), suggestedNegative);
            return new EnhanceResult(enhanced, suggestedNegative.isEmpty() ? null : suggestedNegative);
        } catch (Exception e) {
            log.error("Prompt enhancement failed", e);
            throw new RuntimeException("增强失败: " + e.getMessage(), e);
        }
    }

    /**
     * Translate/polish a prompt. target: "en" / "zh" / "auto"
     */
    public String translate(String prompt, String target) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }

        String systemPrompt = switch (target != null ? target : "auto") {
            case "en" -> "将以下中文精准翻译为英文视频生成提示词，保持原意的同时让表达更符合AI视频模型的习惯用语。只输出英文结果，不要解释。";
            case "zh" -> "将以下英文视频生成提示词翻译为中文，保持专业术语的准确性。只输出中文结果，不要解释。";
            default -> "将以下中英混杂的视频生成提示词统一润色为地道的中文表达，保持所有要素（镜头、光线、动作、风格）不丢失。只输出润色结果，不要解释。";
        };

        try {
            String result = clientRegistry.getDefault().prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();

            if (result == null || result.isBlank()) {
                throw new RuntimeException("LLM 返回空结果");
            }
            log.info("Prompt translated: target={}, {} chars -> {} chars", target, prompt.length(), result.length());
            return result.trim();
        } catch (Exception e) {
            log.error("Prompt translation failed", e);
            throw new RuntimeException("翻译失败: " + e.getMessage(), e);
        }
    }

    /**
     * Get all templates, ordered by category then sortOrder.
     */
    public List<VideoPromptTemplate> getTemplates() {
        List<VideoPromptTemplate> all = templateMapper.selectList(null);
        all.sort(Comparator.comparing(VideoPromptTemplate::getCategory)
                .thenComparing(VideoPromptTemplate::getSortOrder));
        return all;
    }

    /**
     * Create a user-defined template.
     */
    @Transactional
    public VideoPromptTemplate createTemplate(String name, String content, String category) {
        VideoPromptTemplate t = new VideoPromptTemplate();
        t.setId(UUID.randomUUID().toString());
        t.setName(name);
        t.setContent(content);
        t.setCategory(category != null ? category : "custom");
        t.setSortOrder(100);
        t.setIsPreset(false);
        t.setCreatedAt(java.time.LocalDateTime.now());
        t.setUpdatedAt(java.time.LocalDateTime.now());
        templateMapper.insert(t);
        return t;
    }

    /**
     * Delete a template. Preset templates (isPreset=true) cannot be deleted.
     */
    @Transactional
    public void deleteTemplate(String id) {
        VideoPromptTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new NoSuchElementException("模板不存在: " + id);
        }
        if (Boolean.TRUE.equals(t.getIsPreset())) {
            throw new IllegalArgumentException("预设模板不可删除");
        }
        templateMapper.deleteById(id);
    }

    /**
     * Result of prompt enhancement.
     */
    public record EnhanceResult(String enhanced, String suggestedNegative) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/myagent/service/VideoPromptService.java
git commit -m "feat: add VideoPromptService with enhance, translate, template CRUD"
```

---

### Task 4: Add video_prompt_templates table + preset data

**Files:**
- Modify: `backend/src/main/resources/schema.sql` — append DDL at end
- Modify: `backend/src/main/java/com/myagent/config/SchemaMigration.java` — add table creation + preset insertion

- [ ] **Step 1: Add DDL to schema.sql**

Append to the end of `schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS video_prompt_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50) DEFAULT 'general',
    sort_order INT DEFAULT 0,
    is_preset BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Use Edit to append after the last `CREATE TABLE` statement in schema.sql.

- [ ] **Step 2: Add table creation + preset insertion in SchemaMigration**

In `SchemaMigration.java`, add after the existing `video_gen_tasks` column migrations (after line 105 "custom_subtitles" addColumnIfMissing block):

```java
        // ── Video prompt templates table ──
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS video_prompt_templates (" +
            "  id VARCHAR(36) PRIMARY KEY," +
            "  name VARCHAR(100) NOT NULL," +
            "  content TEXT NOT NULL," +
            "  category VARCHAR(50) DEFAULT 'general'," +
            "  sort_order INT DEFAULT 0," +
            "  is_preset BOOLEAN NOT NULL DEFAULT FALSE," +
            "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        // Seed preset templates only if table is empty
        Integer templateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_prompt_templates", Integer.class);
        if (templateCount != null && templateCount == 0) {
            String[][] presets = {
                {"电影大片", "cinematic",
                 "Cinematic masterpiece, shallow depth of field, golden hour lighting, 8K quality, anamorphic lens, film grain texture, dramatic color grading, slow motion camera movement, epic composition"},
                {"日系动漫", "anime",
                 "Studio Ghibli inspired animation style, vibrant cel shading, soft pastel color palette, gentle breeze animating hair and clothing, dreamlike atmosphere, cherry blossom petals floating, warm sunlight streaming through leaves"},
                {"商业广告", "commercial",
                 "Professional product commercial shot, studio lighting setup, clean minimalist background, 60fps smooth slow motion, macro lens capturing fine details, elegant presentation, premium quality feel"},
                {"写实纪录片", "realistic",
                 "Photorealistic documentary style, natural ambient lighting, handheld camera with subtle movement, shallow depth of field on subject, detailed textures and imperfections, authentic atmosphere"},
                {"国风美学", "chinese",
                 "中国古典风格，水墨意境，留白构图，丝绸飘动，烟雾缭绕，金色与朱红色调，意境深远，含蓄典雅，如诗如画"},
                {"赛博朋克", "cyberpunk",
                 "Cyberpunk cityscape at night, neon lights reflecting on wet streets, holographic advertisements flickering, volumetric fog rolling through alleyways, blue and magenta color palette, high contrast lighting, rain droplets"}
            };
            for (int i = 0; i < presets.length; i++) {
                jdbcTemplate.update(
                    "INSERT INTO video_prompt_templates (id, name, content, category, sort_order, is_preset, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, TRUE, NOW(), NOW())",
                    java.util.UUID.randomUUID().toString(),
                    presets[i][0], presets[i][1], presets[i][2], i);
            }
            log.info("Schema migration: seeded {} video prompt templates", presets.length);
        }
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/schema.sql backend/src/main/java/com/myagent/config/SchemaMigration.java
git commit -m "feat: add video_prompt_templates table with preset data"
```

---

### Task 5: Add negativePrompt to VideoGenRequest + handle in VideoGenService

**Files:**
- Modify: `backend/src/main/java/com/myagent/model/VideoGenRequest.java` — add negativePrompt field
- Modify: `backend/src/main/java/com/myagent/service/VideoGenService.java` — append negative prompt in submitTask

- [ ] **Step 1: Add negativePrompt field to VideoGenRequest**

In `VideoGenRequest.java`, add after the `customSubtitles` field (after line 16):

```java
    private String negativePrompt;     // negative prompt, comma-separated tags

    // ... (add getter/setter alongside existing ones)

    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
```

- [ ] **Step 2: Handle negativePrompt in VideoGenService.submitTask**

In `VideoGenService.java`, after setting `customSubtitles` (after line 103-104), add:

```java
        // Build negative prompt from user input — appended to the main prompt
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) {
            task.setNegativePrompt(request.getNegativePrompt());
        }
```

Then in the prompt construction section (around line 128, where prompt is truncated), modify to append negative prompt as suffix:

In the section where `textPart.put("text", prompt)` is set, change from:
```java
            textPart.put("text", prompt);
```
to:
```java
            // Append negative prompt as suffix if present
            String fullPrompt = prompt;
            if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) {
                fullPrompt = prompt + "。Avoid: " + request.getNegativePrompt();
            }
            textPart.put("text", fullPrompt);
```

Also add `negativePrompt` field to `VideoGenTask.java`:

```java
    private String negativePrompt;  // negative prompt tags, comma-separated

    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
```

And add column migration in SchemaMigration:
```java
        addColumnIfMissing("video_gen_tasks", "negative_prompt",
                "ALTER TABLE video_gen_tasks ADD COLUMN negative_prompt TEXT NULL DEFAULT NULL");
```

Also add to schema.sql's video_gen_tasks table:
```sql
    negative_prompt TEXT NULL DEFAULT NULL,
```

And add `customSubtitles` parameter passthrough fix — the frontend `submitVideoGen` function currently doesn't pass `customSubtitles`:

In `frontend/src/api/index.js`, update `submitVideoGen` destructuring:
```javascript
export async function submitVideoGen({ prompt, duration, aspectRatio, seed, firstFrameBase64, conversationId, subtitleEnabled, generateAudio, narrateSubtitles, customSubtitles, negativePrompt }) {
  ...
  body: JSON.stringify({ prompt, duration, aspectRatio, seed, firstFrameBase64, conversationId, subtitleEnabled, generateAudio, narrateSubtitles, customSubtitles, negativePrompt })
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/myagent/model/VideoGenRequest.java \
        backend/src/main/java/com/myagent/model/VideoGenTask.java \
        backend/src/main/java/com/myagent/service/VideoGenService.java \
        backend/src/main/java/com/myagent/config/SchemaMigration.java \
        backend/src/main/resources/schema.sql \
        frontend/src/api/index.js
git commit -m "feat: add negativePrompt support in video generation"
```

---

### Task 6: Add prompt endpoints to VideoGenController

**Files:**
- Modify: `backend/src/main/java/com/myagent/controller/VideoGenController.java` — add 4 endpoints

- [ ] **Step 1: Add VideoPromptService dependency and response DTOs**

Add import after existing imports:
```java
import com.myagent.service.VideoPromptService;
import com.myagent.model.VideoPromptTemplate;
```

Update constructor to accept VideoPromptService:
```java
    private final VideoGenService videoGenService;
    private final VideoPromptService promptService;

    public VideoGenController(VideoGenService videoGenService, VideoPromptService promptService) {
        this.videoGenService = videoGenService;
        this.promptService = promptService;
    }
```

Add response DTOs as nested records at the bottom of the controller class:
```java
    // DTOs for prompt endpoints
    public record EnhanceResponse(String enhanced, String suggestedNegative) {}
    public record TranslateRequest(String prompt, String target) {}
    public record TranslateResponse(String translated) {}
```

- [ ] **Step 2: Add the 4 endpoints**

Add before the closing `}` of the class:

```java
    /**
     * Enhance a user's brief prompt into a detailed video-generation prompt.
     */
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

    /**
     * Translate/polish a prompt between Chinese and English.
     */
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

    /**
     * Get all video prompt templates (preset + user-defined).
     */
    @GetMapping("/prompt/templates")
    public ResponseEntity<List<VideoPromptTemplate>> getPromptTemplates(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(promptService.getTemplates());
    }

    /**
     * Create a user-defined template.
     */
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

    /**
     * Delete a video prompt template (non-preset only).
     */
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
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/myagent/controller/VideoGenController.java
git commit -m "feat: add prompt enhance/translate/template endpoints"
```

---

### Task 7: Add frontend API functions

**Files:**
- Modify: `frontend/src/api/index.js` — add 5 new API functions

- [ ] **Step 1: Add API functions**

Add after the existing video-generation API section (after `getVideoUrl` function):

```javascript
// ── Video Prompt Engineering ──

export async function enhanceVideoPrompt(prompt) {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/enhance`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt })
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || '增强失败');
  }
  return res.json();
}

export async function translateVideoPrompt(prompt, target) {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/translate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt, target })
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || '翻译失败');
  }
  return res.json();
}

export async function getVideoPromptTemplates() {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/templates`);
  if (!res.ok) throw new Error('Failed to fetch video prompt templates');
  return res.json();
}

export async function createVideoPromptTemplate(data) {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/templates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error('Failed to create video prompt template');
  return res.json();
}

export async function deleteVideoPromptTemplate(id) {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/templates/${id}`, {
    method: 'DELETE'
  });
  if (!res.ok) throw new Error('Failed to delete video prompt template');
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/index.js
git commit -m "feat: add video prompt engineering API functions"
```

---
### Task 8: Update VideoGenPanel.vue

**Files:**
- Modify: frontend/src/components/VideoGenPanel.vue

This task adds four new UI areas:
1. Prompt toolbar (template/enhance/translate buttons below textarea)
2. Template selector (horizontal chip selector, expandable)
3. Translate modal (side-by-side original vs translated with replace/cancel)
4. Negative prompt (tag selector + custom input in advanced settings)

All detailed code for this task is in the design spec:
docs/superpowers/specs/2026-06-12-video-prompt-engineering-design.md section 3.

#### Steps:
1. Update imports - add computed
2. Add prompt toolbar HTML after textarea
3. Add template selector expandable area
4. Add translate confirmation modal
5. Add negative prompt section in advanced settings with toggle tags
6. Add new reactive state variables
7. Add new methods: loadTemplates, toggleTemplates, selectTemplate,
   saveCurrentAsTemplate, enhancePrompt, toggleTranslateMenu,
   doTranslate, applyTranslation, toggleNegativeTag
8. Update submitTask to include negativePrompt
9. Add CSS styles for all new elements
10. Commit

```bash
git add frontend/src/components/VideoGenPanel.vue
git commit -m "feat: add prompt toolbar, template selector, translate modal, negative prompt UI"
```
