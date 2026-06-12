# 视频生成智能提示词工程 — 设计文档

> **日期:** 2026-06-12
> **关联:** [[2026-06-11-jimeng-video-gen-plan]]

## 概述

为视频生成面板增加智能提示词工程能力：LLM 驱动的 prompt 增强/润色、快捷标签式反向提示词、可复用的 prompt 模板库、双向中英翻译。

**核心原则**：所有增强/翻译操作由用户手动触发，结果回填输入框让用户最终确认和编辑，不做隐式自动处理。

---

## 一、架构

```
frontend (Vue 3)                    backend (Spring Boot)
─────────────                       ─────────────────────
VideoGenPanel.vue
  ├─ [✨增强] ──── POST /api/video-gen/prompt/enhance ───┐
  ├─ [🌐翻译] ──── POST /api/video-gen/prompt/translate ─┤
  ├─ [📋模板] ──── GET  /api/video-gen/prompt/templates ─┤
  └─ [🚫反向词] ────────────────────────────────────────┘
                                                          │
                                              VideoPromptService
                                                ├─ enhance()  → DeepSeek
                                                ├─ translate()→ DeepSeek
                                                └─ getTemplates() → MySQL
                                                          │
                                              VideoGenService (existing)
                                                └─ submitTask()
```

- 新增 `VideoPromptService` — 专注 prompt 智能处理
- 在 `VideoGenController` 中新增 4 个端点
- 不修改 `VideoGenService` 已有逻辑

### 新增文件

| 文件 | 职责 |
|------|------|
| `backend/.../service/VideoPromptService.java` | 增强、翻译、模板 CRUD |
| `backend/.../model/VideoPromptTemplate.java` | 模板实体 |
| `backend/.../mapper/VideoPromptTemplateMapper.java` | MyBatis-Plus Mapper |

### 修改文件

| 文件 | 改动 |
|------|------|
| `backend/.../controller/VideoGenController.java` | 新增 4 个端点 |
| `backend/.../config/SchemaMigration.java` | 新增 `video_prompt_templates` 表 + 预设数据 |
| `frontend/src/components/VideoGenPanel.vue` | 输入框下方增加工具栏，高级设置增加反向词 |
| `frontend/src/api/index.js` | 新增 3 个 API 函数 |

---

## 二、后端设计

### 2.1 VideoPromptService

```java
@Service
public class VideoPromptService {

    private final ChatClientRegistry clientRegistry;
    private final VideoPromptTemplateMapper templateMapper;

    /**
     * 调用 DeepSeek 将简短 prompt 扩展为详细视频生成提示词。
     * @return 增强结果（含增强文本 + 建议的反向提示词）
     */
    public EnhanceResult enhance(String userPrompt) {
        // 系统提示词：视频导演 prompt 工程师
        // 调用 clientRegistry.getDefault().prompt()...call().content()
        // 返回结构化的 EnhanceResult
    }

    /**
     * 双向翻译/润色。
     * @param prompt    原始文本
     * @param target    目标语言: "en" | "zh" | "auto"
     *                  auto = 将中英混杂统一润色为地道英文
     */
    public String translate(String prompt, String target) {
        // 根据 target 选择不同的系统提示词
        // 调用 LLM 返回翻译结果
    }

    /**
     * 获取所有模板（预设 + 用户自定义），按 category + sortOrder 排序。
     */
    public List<VideoPromptTemplate> getTemplates() { ... }

    /**
     * 用户创建自定义模板。
     */
    public VideoPromptTemplate createTemplate(VideoPromptTemplate template) { ... }

    /**
     * 删除模板。预设模板（isPreset=true）不可删除。
     */
    public void deleteTemplate(String id) { ... }
}
```

### 2.2 增强的系统提示词设计

```
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

用户输入：{userPrompt}

输出格式：
{增强后的提示词}
[NEGATIVE] {反向提示词1}, {反向提示词2}, ...
```

### 2.3 翻译的系统提示词

| target | 系统提示词 |
|--------|-----------|
| `en` | "将以下中文精准翻译为英文视频生成提示词，保持原意的同时让表达更符合AI视频模型的习惯用语。只输出英文结果，不要解释。" |
| `zh` | "将以下英文视频生成提示词翻译为中文，保持专业术语的准确性。只输出中文结果，不要解释。" |
| `auto` | "将以下中英混杂的视频生成提示词统一润色为地道的中文表达，保持所有要素（镜头、光线、动作、风格）不丢失。只输出润色结果，不要解释。" |

### 2.4 模板实体 (VideoPromptTemplate)

```java
@TableName("video_prompt_templates")
public class VideoPromptTemplate {
    @TableId private String id;          // UUID
    private String name;                 // 模板名称
    private String content;              // 模板的 prompt 文本
    private String category;             // cinematic / anime / commercial / realistic / cyberpunk
    private Integer sortOrder;           // 排序
    private Boolean isPreset;            // 是否系统预设
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 2.5 数据库表 (SchemaMigration 中创建)

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

### 2.6 预设模板数据

应用启动时通过 SchemaMigration 插入（仅当表为空时）：

| name | category | content |
|------|----------|---------|
| 电影大片 | cinematic | Cinematic masterpiece, shallow depth of field, golden hour lighting, 8K, anamorphic lens, film grain, dramatic color grading — |
| 日系动漫 | anime | Studio Ghibli inspired animation style, vibrant cel shading, soft pastel color palette, gentle breeze animating hair and clothing, dreamlike atmosphere... |
| 商业广告 | commercial | Professional product commercial shot, studio lighting, clean minimalist background, 60fps slow motion, macro lens detail, elegant presentation... |
| 写实纪录片 | realistic | Photorealistic documentary style, natural ambient lighting, handheld camera subtle movement, shallow depth of field on subject, detailed textures... |
| 国风美学 | chinese | 中国古典风格，水墨意境，留白构图，丝绸飘动，烟雾缭绕，金色与朱红色调，意境深远... |
| 赛博朋克 | cyberpunk | Cyberpunk cityscape, neon lights reflecting on wet streets, holographic advertisements, volumetric fog, blue and magenta color palette, high contrast... |

### 2.7 API 端点详情

#### POST /api/video-gen/prompt/enhance

```
Request:  { "prompt": "一只猫在花园里" }
Response: {
  "enhanced": "温暖的午后阳光下，一只橘色虎斑猫在开满玫瑰的英式花园中漫步，微距镜头跟随猫的步伐，浅景深虚化背景花瓣，柔和的金色逆光勾勒出猫的轮廓毛发，缓慢平稳的运镜节奏，治愈系清新色调",
  "suggestedNegative": "模糊, 畸变, 文字水印, 画面闪烁, 低画质"
}
```

#### POST /api/video-gen/prompt/translate

```
Request:  { "prompt": "武侠打斗场景在水面上", "target": "en" }
Response: { "translated": "Martial arts duel on the surface of a calm lake..." }
```

#### GET /api/video-gen/prompt/templates

```
Response: [
  { "id": "...", "name": "电影大片", "content": "...", "category": "cinematic", ... },
  ...
]
```

#### POST /api/video-gen/prompt/templates

```
Request:  { "name": "我的模板", "content": "...", "category": "custom" }
Response: { "id": "...", "name": "...", ... }
```

#### DELETE /api/video-gen/prompt/templates/{id}

```
Response: 204 No Content (预设模板返回 400)
```

---

## 三、前端设计

### 3.1 VideoGenPanel 改动点

在 prompt 输入框下方增加工具栏：

```
┌──────────────────────────────────────────────┐
│ 提示词                                       │
│ ┌────────────────────────────────────────────┐│
│ │ 一只猫在花园里                              ││
│ │                                            ││
│ └────────────────────────────────────────────┘│
│ ┌────────────────────────────────────────────┐│
│ │ 输入框下方工具栏                            ││
│ └────────────────────────────────────────────┘│  ← 正文解释在下面
│                                              │
│ 📋模板  ✨增强  🌐翻译         字符计数/限制   │
│                                              │
│ [选中的模板内容回填到这里]                     │
└──────────────────────────────────────────────┘
```

实际布局（在 textarea 下方，同一行）：

```
[📋 模板 ▾]    [✨ 增强]    [🌐 翻译]          120/500
```

### 3.2 各功能交互细节

#### 增强按钮

1. 用户点击 → 按钮显示 loading 动画
2. 调用 `POST /api/video-gen/prompt/enhance`
3. 成功 → 增强结果填入输入框，Toast 提示"已增强"
4. 失败 → Toast 显示错误信息，输入框不变
5. 增强完成后，如果返回了 `suggestedNegative`，自动填充到反向提示词区域

#### 翻译按钮

1. 用户点击 → 弹出小型语言选择下拉菜单（「译成中文」「译成英文」「统一润色」）
2. 选择目标语言 → 按钮显示 loading
3. 调用 `POST /api/video-gen/prompt/translate`
4. 返回结果 → 弹出确认弹窗，并排显示原文 vs 译文
5. 用户点击「替换」→ 填入输入框；点击「取消」→ 关闭弹窗

#### 模板按钮

1. 用户点击 → 在输入框下方展开模板选择区（一行水平滚动的标签）
2. 再次点击或选择模板后自动收起
3. 选中某模板 → 内容填入输入框（替换现有内容）
4. 模板区分预设（不可删除）和用户自定义
5. 用户可长按自定义模板标签 → 出现删除按钮

#### 反向提示词（高级设置区域内）

```
┌ 排除内容（反向提示词）──────────────────────────┐
│                                                  │
│ 快捷标签：                                       │
│ ┌──────┐ ┌──────┐ ┌────────┐ ┌────────┐        │
│ │ 模糊 │ │ 畸变 │ │多余手指│ │文字水印│        │
│ └──────┘ └──────┘ └────────┘ └────────┘        │
│ ┌──────┐ ┌────────┐ ┌──────┐                   │
│ │低画质│ │画面撕裂│ │ 闪烁 │                   │
│ └──────┘ └────────┘ └──────┘                   │
│                                                  │
│ 自定义：                                         │
│ ┌──────────────────────────────────────────────┐ │
│ │ 输入想排除的内容，逗号分隔...                  │ │
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

- 标签可多选（toggle 状态，选中高亮）
- 自定义输入框中的内容会与选中标签合并
- 提交时，反向提示词作为逗号分隔的字符串传给后端
- 如果增强功能返回了 `suggestedNegative`，自动勾选匹配的标签并填充自定义内容

### 3.3 新增 API 函数 (frontend/src/api/index.js)

```javascript
// 增强 prompt
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

// 翻译 prompt
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

// 获取/创建/删除模板
export async function getVideoPromptTemplates() {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/templates`);
  if (!res.ok) throw new Error('Failed to fetch templates');
  return res.json();
}

export async function createVideoPromptTemplate(data) {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/templates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error('Failed to create template');
  return res.json();
}

export async function deleteVideoPromptTemplate(id) {
  const res = await apiFetch(`${BASE_URL}/video-gen/prompt/templates/${id}`, {
    method: 'DELETE'
  });
  if (!res.ok) throw new Error('Failed to delete template');
}
```

---

## 四、VideoGenRequest 改动

在现有 `VideoGenRequest` 中新增一个字段：

```java
private String negativePrompt;  // 反向提示词，逗号分隔
```

提交任务时透传到 Ark API（如 Ark 支持的话），或附加到 prompt 末尾（如 "Avoid: ..."）。

---

## 五、错误处理

| 场景 | 前端处理 | 后端处理 |
|------|---------|---------|
| LLM 调用超时 (30s) | Toast "增强请求超时，请重试" | catch 后返回 504 |
| LLM 返回空结果 | Toast "增强失败，请尝试更详细的描述" | 检测空结果返回 422 |
| 模板名重复 | — | 不限制，允许同名 |
| 删除预设模板 | Toast "预设模板不可删除" | 返回 400 |
| API 未配置 (无 API key) | 增强/翻译按钮置灰 | 返回 503 + "AI 服务未配置" |
| prompt 为空 | 前端按钮禁用（已有） | 返回 400 |

---

## 六、自检清单

- [x] 无 TBD/TODO 占位
- [x] 内部一致：API 端点、Service 方法、前端调用一一对应
- [x] 范围可控：4 个后端端点 + 1 个新 Service + 1 个新实体 + 前端改动集中在 VideoGenPanel
- [x] 无歧义：每个交互都描述了触发方式、调用路径、成功/失败表现
