# Storyboarding Feature Design Spec

> 内容策划与分镜编排（Storyboarding）—— 帮助用户将创意想法扩展为结构化分镜脚本，逐个或批量生成视频片段

**Date:** 2026-06-12
**Status:** Approved

---

## 1. Overview

### 1.1 Purpose

在现有视频生成功能上增加分镜编排能力。用户输入创意主题，LLM 自动拆分为包含多个镜头的分镜脚本（含画面描述、运镜方式、时长等），用户可编辑调整后，批量或逐个提交生成视频。

### 1.2 Key Design Decisions

| Decision | Choice |
|----------|--------|
| 与现有单镜头模式的关系 | 可选切换（Tab：「单镜头生成」/「分镜编排」） |
| 分镜数据持久化 | 数据库存储（storyboards + storyboard_shots 表） |
| 分镜生成方式 | AI 生成初版 + 用户手动编辑 |
| 镜头与视频任务关系 | 每个镜头 = 独立的 video_gen_task，后端独立生成 |
| 前端展示 | 独立视频 / 合并播放 两种模式 |

---

## 2. Architecture

### 2.1 System Flow

```
用户选择「分镜编排」Tab
       ↓
Phase 1: 输入创意主题 + 设定镜头数量
       ↓
Phase 2: LLM 生成分镜脚本 JSON → 渲染可编辑表格
       ↓
Phase 3: 用户编辑调整（增删改镜头、调参数）
       ↓
Phase 4: 提交生成（批量或逐个）
       ↓
Phase 5: 轮询任务状态，展示结果（独立 / 合并播放）
```

### 2.2 Component Diagram

```
┌──────────────────────────────────────────────────┐
│                VideoGenPanel.vue                  │
│  ┌─────────────────┐ ┌─────────────────────────┐ │
│  │  单镜头模式 (现有) │  分镜编排模式 (新)         │ │
│  │  提示词 → 提交    │  创意 → 分镜表 → 提交      │ │
│  └─────────────────┘ └─────────────────────────┘ │
└──────────────────────────────────────────────────┘
         │                        │
         ▼                        ▼
  VideoGenController      StoryboardController (NEW)
         │                        │
         ▼                        ▼
   VideoGenService         StoryboardService (NEW)
         │                        │
         ▼                        ▼
     Ark API                DeepSeek LLM
                            (via ChatClientRegistry)
```

---

## 3. Backend Design

### 3.1 New Files

| File | Role |
|------|------|
| `model/Storyboard.java` | 分镜实体，映射 storyboards 表 |
| `model/StoryboardShot.java` | 镜头实体，映射 storyboard_shots 表 |
| `mapper/StoryboardMapper.java` | MyBatis-Plus BaseMapper for Storyboard |
| `mapper/StoryboardShotMapper.java` | MyBatis-Plus BaseMapper for StoryboardShot |
| `service/StoryboardService.java` | 分镜生成（LLM调用）+ CRUD + 提交逻辑 |
| `controller/StoryboardController.java` | REST API endpoints |

### 3.2 Modified Files

| File | Change |
|------|--------|
| `config/SchemaMigration.java` | 创建 storyboards + storyboard_shots 表 |
| `resources/schema.sql` | 新表 DDL |

### 3.3 Database Schema

```sql
CREATE TABLE IF NOT EXISTS storyboards (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36),
    title VARCHAR(200) NOT NULL,
    idea TEXT NOT NULL,
    shot_count INT DEFAULT 5,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sb_user (user_id),
    INDEX idx_sb_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS storyboard_shots (
    id VARCHAR(36) PRIMARY KEY,
    storyboard_id VARCHAR(36) NOT NULL,
    scene_number INT NOT NULL,
    scene_note VARCHAR(200),
    shot_description TEXT NOT NULL,
    camera_movement VARCHAR(50),
    duration INT DEFAULT 5,
    audio_hint VARCHAR(200),
    sort_order INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'PENDING',
    task_id VARCHAR(36),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ss_storyboard (storyboard_id),
    FOREIGN KEY (storyboard_id) REFERENCES storyboards(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.4 Entity Fields

**Storyboard:**
- id (String, UUID)
- userId (String)
- conversationId (String, nullable)
- title (String, max 200)
- idea (String, TEXT — 用户原始创意)
- shotCount (Integer, default 5)
- createdAt, updatedAt (LocalDateTime)

**StoryboardShot:**
- id (String, UUID)
- storyboardId (String, FK → storyboards)
- sceneNumber (Integer)
- sceneNote (String, max 200, nullable)
- shotDescription (String, TEXT — 实际作为 prompt 提交)
- cameraMovement (String, max 50 — 推镜/拉镜/摇镜/跟镜/固定/升降)
- duration (Integer, default 5, range 4-12)
- audioHint (String, max 200, nullable)
- sortOrder (Integer, default 0)
- status (String: PENDING / SUBMITTED / PROCESSING / SUCCEEDED / FAILED)
- taskId (String, nullable — 关联 video_gen_tasks.id)
- createdAt, updatedAt (LocalDateTime)

### 3.5 API Endpoints

All endpoints are authenticated via `@AuthenticationPrincipal UserPrincipal`.

**`POST /api/video-gen/storyboard/generate`**
- Body: `{ "idea": "创意主题", "shotCount": 5 }`
- Calls DeepSeek LLM to produce a storyboard JSON array
- Returns: `{ "shots": [{ "sceneNote": "...", "shotDescription": "...", "cameraMovement": "...", "duration": 5, "audioHint": "..." }] }`
- Errors: 400 (idea empty), 500 (LLM failure / JSON parse failure)

**`POST /api/video-gen/storyboard`**
- Body: `{ "conversationId": "...", "title": "...", "idea": "...", "shotCount": 5, "shots": [...] }`
- Saves storyboard + all shots to DB
- Returns: `{ "id": "...", "shots": [...] }`

**`GET /api/video-gen/storyboard/{id}`**
- Returns full storyboard with all shots
- 404 if not found, 403 if not owner

**`PUT /api/video-gen/storyboard/{id}`**
- Body: `{ "title": "...", "shots": [...] }`
- Replaces all shots (delete old, insert new)
- 404 if not found, 403 if not owner

**`DELETE /api/video-gen/storyboard/{id}`**
- Deletes storyboard + cascade shots
- 404 if not found, 403 if not owner

**`GET /api/video-gen/storyboards?conversationId=xxx`**
- Returns list of storyboards for a conversation (summary only, no shots)
- Filtered by current user

**`POST /api/video-gen/storyboard/{id}/submit`**
- Body (optional, for shared params): `{ "aspectRatio": "16:9", "negativePrompt": "...", "subtitleEnabled": false, ... }`
- Iterates all PENDING shots, calls VideoGenService.submitTask() for each
- Returns: `{ "tasks": [{ "shotId": "...", "taskId": "...", "status": "SUBMITTED" }] }`
- 404 if not found, 403 if not owner

**`POST /api/video-gen/storyboard/{id}/shots/{shotId}/submit`**
- Same as above but for a single shot
- Body: shared generation params
- Returns the created task info

### 3.6 StoryboardService Key Logic

**generate(idea, shotCount):**
```
1. Build system prompt with shotCount injected
2. Call clientRegistry.getDefault().prompt().system(...).user(idea).call().content()
3. Parse JSON array from response (strip markdown code fences if present)
4. Validate each shot has at least shotDescription
5. Return list of StoryboardShot (not persisted yet)
```

**submitAll(storyboardId, commonParams, userId):**
```
1. Load storyboard, verify ownership
2. Load all shots with status PENDING
3. For each shot:
   a. Build VideoGenRequest(shot.shotDescription, commonParams...)
   b. Call videoGenService.submitTask(request, userId)
   c. Update shot.taskId = task.id, shot.status = "SUBMITTED"
4. Return result map
```

### 3.7 Error Handling

| Scenario | HTTP | Response |
|----------|------|----------|
| idea / prompt is blank | 400 | `{"error": "创意主题不能为空"}` |
| shotCount < 1 or > 12 | 400 | `{"error": "镜头数量需在1-12之间"}` |
| LLM returns unparseable content | 500 | `{"error": "分镜生成失败，请重试"}` |
| Storyboard not found | 404 | `{"error": "分镜不存在"}` |
| Not owner | 403 | `{"error": "无权访问此分镜"}` |
| Shots list is empty on save | 400 | `{"error": "分镜脚本至少需要一个镜头"}` |
| No PENDING shots on submit | 400 | `{"error": "没有可提交的镜头"}` |

---

## 4. Frontend Design

### 4.1 Mode Switch

VideoGenPanel.vue 顶部增加 Tab 切换：

```
┌─────────────────────────────────────────────────┐
│  [○ 单镜头生成]   [● 分镜编排]                    │  ← mode tabs
├─────────────────────────────────────────────────┤
│  ... (当前模式的表单)                              │
└─────────────────────────────────────────────────┘
```

- 默认选中「单镜头生成」（保持向后兼容）。
- 切换到「分镜编排」时，隐藏现有单镜头表单，显示分镜流程。

### 4.2 Storyboard Mode — Phase 1: Input

```
┌─────────────────────────────────────────────────┐
│  视频创意                                         │
│  ┌─────────────────────────────────────────────┐│
│  │ 描述你的视频创意，AI 将自动拆分为分镜脚本     ││
│  │                                              ││
│  └─────────────────────────────────────────────┘│
│  镜头数量: [5] 个 (2-12)                          │
│  [✨ AI 生成分镜]                                 │
└─────────────────────────────────────────────────┘
```

### 4.3 Storyboard Mode — Phase 2: Edit

生成后显示可编辑分镜表：

```
┌─────────────────────────────────────────────────┐
│  分镜标题: [雨夜猫影_______________]  [保存分镜]   │
│                                                  │
│  ┌────┬────────────────┬─────────┬────┬──────┐  │
│  │镜头│ 画面描述        │ 运镜    │时长│ 操作  │  │
│  ├────┼────────────────┼─────────┼────┼──────┤  │
│  │ 1  │ [特写：猫眼中  │ [推镜 ▼]│[5]s│ ↑↓ ✕ │  │
│  │    │  霓虹倒影...]  │         │    │      │  │
│  ├────┼────────────────┼─────────┼────┼──────┤  │
│  │ 2  │ [中景：雨夜街  │ [跟镜 ▼]│[8]s│ ↑↓ ✕ │  │
│  │    │  道，猫穿行]   │         │    │      │  │
│  ├────┼────────────────┼─────────┼────┼──────┤  │
│  │ 3  │ [远景：猫跃上  │ [摇镜 ▼]│[6]s│ ↑↓ ✕ │  │
│  │    │  天际线...]    │         │    │      │  │
│  └────┴────────────────┴─────────┴────┴──────┘  │
│                                                  │
│  [+ 添加镜头]                                     │
│                                                  │
│  场景备注: [随意输入...]  音效提示: [雨声渐强...]  │  ← 展开编辑时显示
└─────────────────────────────────────────────────┘
```

- 画面描述使用 textarea（2行，点击展开更多）
- 运镜方式使用 select 下拉（推镜/拉镜/摇镜/跟镜/固定/升降）
- 时长使用 input[type=number]（min=4, max=12, step=1）
- ↑ ↓ 按钮调整镜头顺序
- ✕ 按钮删除镜头
- 点击镜头行可展开场景备注和音效提示编辑
- [+ 添加镜头] 在末尾插入空白行

### 4.4 Storyboard Mode — Phase 3: Submit

分镜表下方显示提交区域：

```
┌─────────────────────────────────────────────────┐
│  生成设置（复用现有高级设置面板）                  │
│  比例: [16:9] [9:16] [1:1]                       │
│  嵌入字幕: [○]  生成音频: [●]  反向提示词: [...]  │
│                                                  │
│  [🚀 全部生成 (3个镜头)]                          │
│                                                  │
│  或逐个生成：                                     │
│  镜头1 [生成此镜]  ○ PENDING                      │
│  镜头2 [生成此镜]  ◷ PROCESSING                   │
│  镜头3 [生成此镜]  ● SUCCEEDED [播放]             │
└─────────────────────────────────────────────────┘
```

### 4.5 Storyboard Mode — Phase 4: Result Display

- **独立视频模式**（默认）：镜头列表每行显示状态，完成的镜头可点击播放（复用现有视频播放器，作为独立 MP4 播放）
- **合并展示模式**：所有完成的镜头按顺序排列成视频序列，支持逐个自动连播

### 4.6 New State Variables

```javascript
// Mode
const mode = ref('single')  // 'single' | 'storyboard'

// Storyboard
const storyboardId = ref(null)
const storyboardTitle = ref('')
const storyboardIdea = ref('')
const shotCount = ref(5)
const shots = ref([])            // [{id, sceneNumber, shotDescription, cameraMovement, duration, sceneNote, audioHint, status, taskId}]
const generating = ref(false)    // AI generation loading
const showShotDetail = ref(null) // index of expanded shot row
const savedStoryboards = ref([]) // list of saved storyboards for this conversation
const resultDisplayMode = ref('individual') // 'individual' | 'merged'
```

### 4.7 New API Functions (frontend/src/api/index.js)

```javascript
export async function generateStoryboard(idea, shotCount) { ... }
export async function saveStoryboard(data) { ... }
export async function getStoryboard(id) { ... }
export async function updateStoryboard(id, data) { ... }
export async function deleteStoryboard(id) { ... }
export async function getStoryboards(conversationId) { ... }
export async function submitStoryboard(id, params) { ... }
export async function submitStoryboardShot(shotId, params) { ... }
```

---

## 5. LLM Prompt Design

### 5.1 Storyboard Generation System Prompt

```
你是一位专业的视频导演和分镜师，擅长将创意构思转化为可执行的分镜脚本。

根据用户的创意主题，生成 {SHOT_COUNT} 个镜头的分镜脚本。每个镜头需包含：

1. shotDescription（画面描述）：详细描述画面内容、光线、色调、构图、主体动作（中文，50-150字）
2. cameraMovement（运镜方式）：推镜 / 拉镜 / 摇镜 / 跟镜 / 固定 / 升降
3. duration（时长建议）：4~12 秒之间的整数
4. sceneNote（场景备注）：简短的场景标识（5-10字）
5. audioHint（音效提示）：该镜头的音效或配乐建议

要求：
- 镜头之间要有叙事连贯性，形成完整的故事弧线
- 画面描述需包含镜头语言（景别、角度）和视觉风格（光线、色调）
- 严格输出 JSON 数组，不要任何额外文字或 markdown 标记

用户创意：{USER_IDEA}

输出格式：
[{"sceneNote": "...", "shotDescription": "...", "cameraMovement": "...", "duration": 5, "audioHint": "..."}]
```

---

## 6. Testing Strategy

### 6.1 Backend Tests

- StoryboardService.generate returns valid JSON array
- StoryboardService.generate handles LLM errors gracefully
- Storyboard CRUD operations verify ownership
- submitAll creates correct number of video_gen_tasks
- submitAll skips already-submitted shots
- SchemaMigration creates tables correctly

### 6.2 Frontend Tests

- Mode switch toggles between single and storyboard forms
- Shot editing: add/remove/reorder/update fields
- AI generation button shows loading state
- Submit validation: no shots = disabled button
- Result display modes: individual and merged

---

## 7. Non-Goals (Out of Scope)

- 复杂的镜头过渡效果/转场编辑
- 视频片段剪辑/拼接/导出
- 分镜脚本的协作/分享功能
- 基于参考图片的镜头生成
- 运镜参数精确控制（如推镜速度）
