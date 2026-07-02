# Ayer — AI Chat Application

全栈 AI 对话应用，Spring Boot + Vue 3 + 多模型（DeepSeek / Qwen），支持流式对话、联网搜索、图片分析、AI 视频生成、分镜编排、知识库检索等功能。

## 技术栈

| 层 | 技术 | 版本 |
|---|------|------|
| 后端框架 | Spring Boot | 3.5.14 |
| AI 框架 | Spring AI | 1.1.7 |
| AI 模型 | DeepSeek Chat / Qwen Plus | — |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | 8.0+ |
| 缓存 | Redis (Lettuce) | 7.x |
| 认证 | Spring Security + JWT | — |
| 前端 | Vue 3 + Vite | ^3.4 / ^5.4 |
| Markdown | marked + highlight.js | ^12 / ^11.9 |
| 数学公式 | KaTeX | ^0.17 |
| HTML 净化 | DOMPurify | ^3 |
| 知识库 | Elasticsearch | 8.x |
| 文档解析 | Apache Tika | 2.9 |
| AI 视频生成 | 即梦 AI (Seedance) via 火山方舟 Ark API | — |
| 视频处理 | FFmpeg (字幕烧录、音频合成、多段拼接) | — |

## 功能一览

### 对话核心
- **多模型切换** — DeepSeek / Qwen，顶栏一键切换
- **SSE 流式响应** — AsyncContext + Reactor Flux，逐字实时输出，可中途停止
- **中断续传** — 停止后保存断点内容，消息底部显示「继续生成」按钮，追加而非重发
- **思维链展示** — DeepSeek-R1 推理过程可展开/折叠
- **Markdown 渲染** — 代码语法高亮、表格、KaTeX 数学公式（行内 + 块级）
- **Token 用量** — 每条回复底部显示输入/输出/总 Token 数
- **参数调节** — 顶栏面板调节 Temperature / Max Tokens
- **键盘快捷键** — Ctrl+K 搜索、Ctrl+Enter 发送、Ctrl+/ 帮助面板、↑↓ 历史记录、Esc 停止

### 消息操作
- 编辑 / 删除 / 重新生成
- 👍 / 👎 回复评分
- ⭐ 收藏重要回复，侧边栏专属过滤入口
- 🔊 TTS 语音朗读（浏览器原生 Web Speech API，支持停止）
- 🔀 从任意消息处**分叉**创建新对话分支

### 输入增强
- 图片上传（最多 4 张，支持粘贴）
- 🎤 语音输入（Web Speech API，多语言）
- 🌐 联网搜索开关（BochaAI）
- 📋 Prompt 历史记录（最近 10 条，一键回填）
- Temperature / Max Tokens 滑块面板

### 会话管理
- 多会话侧边栏，按时间分组（今天 / 昨天 / 本周 / 更早）
- 📌 置顶 · 📁 文件夹分组 · 批量多选删除
- 全文搜索（标题 + 消息内容），点击结果**跳转并高亮**原消息
- 导出对话（Markdown / TXT）
- 导入对话（JSON / Markdown 两种格式）
- 公开分享链接（可撤销）
- 每会话自定义系统提示词

### 模板
- **提示词模板** — 保存常用系统提示词，会话时快速应用
- **对话模板（Workflow）** — 预设系统提示词 + 初始消息，欢迎页一键启动

### AI 视频生成 🆕
- **文生视频** — 通过即梦 AI (Seedance) 生成 MP4 视频，支持自定义时长、比例、随机种子
- **首帧图引导** — 上传参考图片作为视频首帧
- **提示词增强** — AI 自动优化视频提示词，支持中英互译
- **视频提示词模板** — 保存常用视频生成参数模板
- **字幕烧录** — 生成后可选烧录 SRT 字幕到视频
- **TTS 旁白** — 可选 AI 语音合成并合成到视频音轨
- **负向提示词** — 排除不希望出现的画面元素
- **异步任务** — 提交后轮询状态，完成后在线播放/下载

### 分镜编排（Storyboard）🆕
- **LLM 自动生成分镜脚本** — 输入创意主题，AI 生成多镜头分镜（含场景描述、运镜、时长、音效提示）
- **逐镜生成视频** — 每个镜头单独提交视频生成，可单独查看/重试
- **批量提交** — 一键提交所有待处理镜头
- **FFmpeg 合并** — 将多个已生成镜头的视频片段用 xfade 转场拼接为完整视频
- **分镜编辑** — 手动调整镜头顺序、描述、运镜指令
- **合并模式预览** — 只读摘要视图快速浏览全部分镜

### 知识库（RAG）
- 上传 PDF / Word / TXT / Markdown 等文档
- 自动解析、分块、向量化存入 Elasticsearch
- 回答时自动检索相关段落，拼接到上下文

### 其他
- 今日推荐问题（AI 每天生成 20 条，5 分钟随机轮换，localStorage 缓存）
- 深色 / 浅色主题，跟随系统偏好自动切换
- 可拖拽调宽侧边栏（宽度持久化）
- 响应式布局（移动端适配）
- MCP 工具集成（文件系统等可选）

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis 7.x（可选，无 Redis 时自动降级到直查数据库）
- FFmpeg（视频处理功能需要，路径在 `application.yml` 中配置）
- Elasticsearch 8.x（知识库功能需要）

### 1. 克隆仓库

```bash
git clone https://github.com/zhangkk-art/my_agent.git
cd my_agent
```

### 2. 配置 API Keys

修改 `backend/src/main/resources/application.yml`，或设置以下环境变量：

```bash
# DeepSeek（必填）
export DEEPSEEK_API_KEY=sk-your-key

# 通义千问（使用 Qwen 时必填）
export DASHSCOPE_API_KEY=sk-your-key

# MySQL 密码
export MYSQL_PASSWORD=your_password

# 联网搜索 - BochaAI（可选，https://open.bochaai.com）
export WEB_SEARCH_API_KEY=sk-your-key

# 视频生成 - 火山方舟 Ark API（可选，https://www.volcengine.com）
export ARK_API_KEY=your-ark-api-key
export ARK_VIDEO_MODEL=doubao-seedance-1-0-pro-fast-251015

# JWT 密钥（生产环境务必修改）
export JWT_SECRET=your-secret-key
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
# → http://localhost:8080
```

首次启动会自动创建 `ai_chat` 数据库及全部表，无需手动执行任何 SQL。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

Vite 自动将 `/api/*` 代理到后端 8080 端口。

### 生产构建

```bash
cd frontend
npm run build   # 产物输出到 frontend/dist/
```

将 `dist/` 部署到静态服务器，或让 Nginx 同时托管前端静态文件与反代后端接口。

## 项目结构

```
my_agent/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/myagent/
│       │   ├── config/
│       │   │   ├── WebConfig.java              # CORS（允许 GET/POST/PUT/DELETE/PATCH）
│       │   │   ├── ChatClientRegistry.java     # DeepSeek / Qwen 客户端注册
│       │   │   ├── RedisConfig.java            # Redis 配置及降级处理
│       │   │   └── SchemaMigration.java        # 启动时自动补齐增量字段和索引
│       │   ├── controller/
│       │   │   ├── ChatController.java         # 流式对话（stream / image / regenerate）
│       │   │   ├── ConversationController.java # 会话 CRUD + 分享 + Pin + 文件夹 + 导入
│       │   │   ├── MessageController.java      # 消息编辑 / 删除 / 搜索 / 收藏 / 评分
│       │   │   ├── DailyQuestionController.java# 今日推荐问题（AI 生成 + 内存缓存）
│       │   │   ├── PromptTemplateController.java
│       │   │   ├── WorkflowTemplateController.java
│       │   │   ├── AuthController.java         # 登录 / 注册
│       │   │   ├── SharePageController.java
│       │   │   ├── VideoGenController.java     # 视频生成（提交、轮询、播放、后期处理）
│       │   │   └── StoryboardController.java   # 分镜编排（生成、保存、提交、合并）
│       │   ├── service/
│       │   │   ├── ChatService.java            # AI 对话核心（多模型、工具调用、参数）
│       │   │   ├── ConversationService.java    # 会话生命周期管理
│       │   │   ├── UserService.java            # 用户认证
│       │   │   ├── PromptTemplateService.java
│       │   │   ├── WorkflowTemplateService.java
│       │   │   ├── VideoGenService.java        # 视频生成（Ark API 调用、下载、FFmpeg 处理）
│       │   │   ├── VideoPromptService.java     # 视频提示词增强和翻译
│       │   │   └── StoryboardService.java      # 分镜生成、视频合并
│       │   ├── model/                          # 数据实体（Conversation, Message, User, VideoGenTask, Storyboard, StoryboardShot 等）
│       │   ├── mapper/                         # MyBatis-Plus Mapper
│       │   ├── security/                       # JWT 认证（JwtUtil, JwtAuthFilter, SecurityConfig）
│       │   ├── tool/
│       │   │   ├── ToolFunctions.java          # 时间 / 天气预注入
│       │   │   └── WebSearchTools.java         # BochaAI 联网搜索
│       │   ├── rag/                            # 知识库模块（ES + Tika）
│       │   └── util/                           # 工具类（IamV4Signer 等）
│       └── resources/
│           ├── application.yml
│           ├── schema.sql
│           └── system-prompt.txt
│
└── frontend/
    └── src/
        ├── App.vue                             # 根组件，全局状态管理
        ├── api/index.js                        # 所有后端接口封装
        └── components/
            ├── Sidebar.vue                     # 侧边栏（会话列表 + 搜索 + 收藏 + 多选）
            ├── ConvItem.vue                    # 单条会话项（Pin / 文件夹 / 删除）
            ├── ChatArea.vue                    # 聊天主区域（顶栏 + 参数面板）
            ├── ChatInput.vue                   # 输入框（图片 / 语音 / 搜索 / 历史）
            ├── MessageList.vue                 # 消息列表（自动滚动 + 跳转高亮）
            ├── MessageBubble.vue               # 消息气泡（Markdown + TTS + 收藏 + 评分）
            ├── WelcomeScreen.vue               # 欢迎页（今日推荐 + Workflow 模板）
            ├── SettingsModal.vue               # 设置面板（外观 + 模板管理）
            ├── LoginView.vue                   # 登录/注册页
            ├── SharedView.vue                  # 分享对话只读视图
            ├── ShortcutsModal.vue              # 键盘快捷键帮助弹窗
            ├── VideoGenPanel.vue               # 视频生成面板（提示词编辑、参数、轮询预览）
            ├── VideoPlayerModal.vue            # 视频播放器弹窗
            └── Toast.vue                       # 全局通知
```

## 数据库表结构

| 表 | 主要字段 |
|---|---------|
| `conversations` | id · title · system_prompt · share_token · pinned · folder_name · user_id · created_at · updated_at |
| `messages` | id · conversation_id · role · content · reasoning · prompt_tokens · completion_tokens · total_tokens · starred · rating · interrupted · created_at（索引：`idx_msg_conv (conversation_id, created_at)`）|
| `users` | id · username · password_hash · created_at |
| `prompt_templates` | id · name · content · sort_order |
| `workflow_templates` | id · name · description · system_prompt · initial_message |
| `knowledge_documents` | id · name · content_type · chunk_count |
| `video_gen_tasks` | id · user_id · conversation_id · storyboard_id · title · prompt · req_key · duration · aspect_ratio · seed · first_frame_url · task_id · status · video_path · subtitle_enabled · subtitle_path · generate_audio · narrate_subtitles · custom_subtitles · negative_prompt · error_message · created_at · updated_at |
| `storyboards` | id · user_id · conversation_id · title · idea · shot_count · created_at · updated_at |
| `storyboard_shots` | id · storyboard_id · scene_number · scene_note · shot_description · camera_movement · duration · audio_hint · sort_order · status · task_id · created_at · updated_at |
| `video_prompt_templates` | id · name · content · category · created_at · updated_at |

所有表由 `schema.sql` 初始创建，增量字段（如 `user_id`、`pinned`、`starred`、`interrupted`、`storyboard_id`、`negative_prompt` 等）由 `SchemaMigration` 在每次启动时自动补齐，**无需手动执行任何迁移脚本**。

## API 接口

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT Token |
| POST | `/api/auth/register` | 注册新用户 |

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/stream` | SSE 流式对话（支持 webSearch / temperature / maxTokens）|
| POST | `/api/chat/continue` | 从断点续传，追加内容到已有消息 |
| POST | `/api/chat/image` | 图片分析（流式，base64 多图）|
| POST | `/api/chat/regenerate` | 重新生成最后一条 AI 回复 |

### 会话

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/conversations` | 获取所有会话列表（仅元数据，置顶排前）|
| POST | `/api/conversations` | 创建新会话 |
| GET | `/api/conversations/{id}` | 获取会话详情（含消息）|
| PUT | `/api/conversations/{id}` | 重命名 |
| PATCH | `/api/conversations/{id}/touch` | 刷新更新时间 |
| PATCH | `/api/conversations/{id}/pin` | 切换置顶 |
| PATCH | `/api/conversations/{id}/folder` | 设置文件夹 |
| PUT | `/api/conversations/{id}/system-prompt` | 更新系统提示词 |
| POST | `/api/conversations/{id}/fork` | 从指定消息分叉 |
| POST | `/api/conversations/{id}/share` | 生成分享链接 |
| DELETE | `/api/conversations/{id}/share` | 撤销分享 |
| POST | `/api/conversations/import` | 导入对话（JSON 格式）|
| DELETE | `/api/conversations/{id}` | 删除会话 |

### 消息

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/messages/{id}` | 编辑内容 |
| DELETE | `/api/messages/{id}` | 删除 |
| PATCH | `/api/messages/{id}/star` | 切换收藏 |
| PATCH | `/api/messages/{id}/rating` | 设置评分（1 / -1 / null）|
| PATCH | `/api/messages/{id}/interrupted` | 更新中断消息内容并标记 interrupted |
| POST | `/api/messages/save-partial` | 保存流式中断的部分内容 |
| GET | `/api/messages/search?q=` | 全文搜索 |
| GET | `/api/messages/starred` | 获取所有收藏消息 |

### 视频生成

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/video-gen/submit` | 提交视频生成任务 |
| GET | `/api/video-gen/tasks` | 获取当前用户所有任务 |
| GET | `/api/video-gen/tasks/{id}` | 获取单个任务（含自动轮询 Jimeng 状态）|
| DELETE | `/api/video-gen/tasks/{id}` | 删除任务及本地视频文件 |
| PATCH | `/api/video-gen/tasks/{id}/conversation` | 更新关联的会话 ID |
| GET | `/api/video-gen/tasks/{id}/video` | 流式播放生成的视频 |
| POST | `/api/video-gen/tasks/{id}/post-process` | 后期处理（烧录字幕 / TTS 旁白）|
| POST | `/api/video-gen/prompt/enhance` | AI 增强视频提示词 |
| POST | `/api/video-gen/prompt/translate` | 提示词翻译（中英互译）|
| GET | `/api/video-gen/prompt/templates` | 获取视频提示词模板 |
| POST | `/api/video-gen/prompt/templates` | 创建视频提示词模板 |
| DELETE | `/api/video-gen/prompt/templates/{id}` | 删除视频提示词模板 |

### 分镜编排

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/video-gen/storyboard/generate` | LLM 生成分镜脚本 |
| POST | `/api/video-gen/storyboard` | 保存分镜及全部镜头 |
| GET | `/api/video-gen/storyboard/{id}` | 获取分镜详情（含全部镜头）|
| PUT | `/api/video-gen/storyboard/{id}` | 更新分镜标题及镜头内容 |
| DELETE | `/api/video-gen/storyboard/{id}` | 删除分镜 |
| GET | `/api/video-gen/storyboards?conversationId=` | 获取会话的所有分镜 |
| POST | `/api/video-gen/storyboard/{id}/submit` | 批量提交所有待处理镜头 |
| POST | `/api/video-gen/storyboard/{id}/shots/{shotId}/submit` | 提交单个镜头 |
| POST | `/api/video-gen/storyboard/{id}/merge` | FFmpeg xfade 合并所有已生成镜头 |
| GET | `/api/video-gen/storyboard/{id}/merged-video` | 流式播放合并后的视频 |

### 其他

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/questions/daily` | 获取今日 AI 推荐问题（服务端按天缓存）|
| GET | `/api/shared/{token}` | 获取分享对话（公开）|
| GET/POST/PUT/DELETE | `/api/prompt-templates` | 提示词模板 CRUD |
| GET/POST/PUT/DELETE | `/api/workflow-templates` | 对话模板 CRUD |
| GET/POST/DELETE | `/api/knowledge/documents` | 知识库文档管理 |
| POST | `/api/knowledge/chat` | 知识库检索增强对话 |

## 配置说明

### application.yml 关键配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_chat?createDatabaseIfNotExist=true
    username: root
    password: ${MYSQL_PASSWORD}
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat.options.model: deepseek-chat
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

app:
  ai:
    qwen:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      model: qwen-plus
  jwt:
    secret: ${JWT_SECRET:AyerAIAgentDefaultSecretKey2024ForJWT!!}
    expiration-days: 7
  websearch:
    api-key: ${WEB_SEARCH_API_KEY}
    endpoint: https://api.bochaai.com/v1/web-search
  # 视频生成 - 火山方舟 Ark API（即梦 AI / Seedance）
  ark:
    api-key: ${ARK_API_KEY}
    endpoint: https://ark.cn-beijing.volces.com/api/v3
    model: ${ARK_VIDEO_MODEL:doubao-seedance-1-0-pro-fast-251015}
  video:
    storage:
      path: ${VIDEO_STORAGE_PATH:./data/videos}
    ffmpeg:
      path: D:/install/ffmpeg/ffmpeg-8.1.1-full_build/bin/ffmpeg.exe
  elasticsearch:
    host: ${ES_HOST:localhost}
    port: ${ES_PORT:9201}
```

### 缓存说明

Redis 用于缓存每日推荐问题、提示词模板、Workflow 模板、知识库文档和分享对话。当 Redis 不可用时，`RedisConfig.errorHandler()` 自动降级——日志输出 WARN，业务逻辑直接回退到数据库查询，不影响功能正常使用。

### 用户认证

所有 `/api/**` 端点（除 `/api/auth/**` 和 `/api/shared/**`）均需携带 `Authorization: Bearer <token>` 请求头。Token 通过 `/api/auth/login` 获取，默认 7 天有效。会话列表、消息发送、对话管理均按 `user_id` 隔离。

### 工作原理

**时间/天气 Function Calling**：`ToolFunctions` 中注册 `getCurrentTime` / `getWeather` 两个 `@Tool`，Spring AI 在 `Flux<ChatResponse>` 内部透明执行——模型判断需要工具时自动调用、注入结果，再继续生成。历史对话中的旧时间/天气消息对（`shouldRedact` 检测）会在下次询问前从上下文中移除，防止模型锚定过期数据。

**联网搜索**：前端开关开启后，后端调用 BochaAI 搜索 API，将结果摘要注入系统提示词，历史对话中的旧搜索结果自动失效，确保每次回答基于最新数据。

**中断续传**：用户点击停止时，前端立即 `POST /api/messages/save-partial` 保存已流出内容（`interrupted=true`），消息气泡显示「继续生成」按钮；点击后调用 `POST /api/chat/continue`，后端以截断历史 + 合成"请继续"消息重建上下文并追加输出，完成后清除 `interrupted` 标记。

**流式连接断开处理**：用户点击停止或关闭页面时，前端 `AbortController` 断开连接，后端捕获 `IOException` 后取消 Reactor 订阅，已流出的内容正常保存。

**全文搜索取消**：侧边栏搜索框每次输入变化时，前一个尚未返回的请求会被 `AbortController` 立即取消，避免乱序结果覆盖最新查询，同时减少不必要的后端压力。

**多模型架构**：`ChatClientRegistry` 启动时注册 DeepSeek 和 Qwen 两个 `ChatClient`，`ChatService` 根据请求的 `model` 字段动态选择，共享同一套流式处理逻辑。

**视频生成**：用户提交视频生成请求后，后端调用火山方舟 Ark API（即梦 AI Seedance 模型）提交异步任务，返回 `taskId`。前端周期性轮询 `/api/video-gen/tasks/{id}`，后端自动向 Ark 查询并更新任务状态。生成完成后下载视频到本地存储，前端通过 `/api/video-gen/tasks/{id}/video` 流式播放。

**视频后期处理**：生成完成后可调用 `post-process` 接口进行字幕烧录（SRT 格式）和 TTS 旁白合成，由 FFmpeg 完成实际的视频编辑。

**分镜编排**：用户输入创意主题，LLM 生成结构化的分镜脚本（场景描述、运镜指令、时长、音效提示等 JSON）。每个镜头独立提交视频生成，最终通过 FFmpeg xfade 滤镜将所有镜头拼接为完整视频，实现从创意到成片的一站式工作流。

## License

MIT
