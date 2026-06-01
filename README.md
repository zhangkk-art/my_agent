# Ayer — AI Chat Application

全栈 AI 对话应用，Spring Boot + Vue 3 + 多模型（DeepSeek / Qwen），支持流式对话、联网搜索、图片分析、知识库检索等功能。

## 技术栈

| 层 | 技术 | 版本 |
|---|------|------|
| 后端框架 | Spring Boot | 3.5.14 |
| AI 框架 | Spring AI | 1.1.7 |
| AI 模型 | DeepSeek Chat / Qwen Plus | — |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | 8.0+ |
| 前端 | Vue 3 + Vite | ^3.4 / ^5.4 |
| Markdown | marked + highlight.js | ^12 / ^11.9 |
| 数学公式 | KaTeX | ^0.17 |
| HTML 净化 | DOMPurify | ^3 |
| 知识库 | Elasticsearch | 8.x |
| 文档解析 | Apache Tika | 2.9 |

## 功能一览

### 对话核心
- **多模型切换** — DeepSeek / Qwen，顶栏一键切换
- **SSE 流式响应** — AsyncContext + Reactor Flux，逐字实时输出，可中途停止
- **思维链展示** — DeepSeek-R1 推理过程可展开/折叠
- **Markdown 渲染** — 代码语法高亮、表格、KaTeX 数学公式（行内 + 块级）
- **Token 用量** — 每条回复底部显示输入/输出/总 Token 数
- **参数调节** — 顶栏面板调节 Temperature / Max Tokens

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
│       │   │   └── SchemaMigration.java        # 启动时自动补齐增量字段和索引
│       │   ├── controller/
│       │   │   ├── ChatController.java         # 流式对话（stream / image / regenerate）
│       │   │   ├── ConversationController.java # 会话 CRUD + 分享 + Pin + 文件夹 + 导入
│       │   │   ├── MessageController.java      # 消息编辑 / 删除 / 搜索 / 收藏 / 评分
│       │   │   ├── DailyQuestionController.java# 今日推荐问题（AI 生成 + 内存缓存）
│       │   │   ├── PromptTemplateController.java
│       │   │   ├── WorkflowTemplateController.java
│       │   │   └── SharePageController.java
│       │   ├── service/
│       │   │   ├── ChatService.java            # AI 对话核心（多模型、工具调用、参数）
│       │   │   ├── ConversationService.java    # 会话生命周期管理
│       │   │   ├── PromptTemplateService.java
│       │   │   └── WorkflowTemplateService.java
│       │   ├── model/                          # 数据实体
│       │   ├── mapper/                         # MyBatis-Plus Mapper
│       │   ├── tool/
│       │   │   ├── ToolFunctions.java          # 时间 / 天气预注入
│       │   │   └── WebSearchTools.java         # BochaAI 联网搜索
│       │   └── rag/                            # 知识库模块（ES + Tika）
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
            ├── SharedView.vue                  # 分享对话只读视图
            └── Toast.vue                       # 全局通知
```

## 数据库表结构

| 表 | 主要字段 |
|---|---------|
| `conversations` | id · title · system_prompt · share_token · pinned · folder_name |
| `messages` | id · conversation_id · role · content · reasoning · token 用量 · starred · rating |
| `prompt_templates` | id · name · content · sort_order |
| `workflow_templates` | id · name · description · system_prompt · initial_message |
| `knowledge_documents` | id · name · content_type · chunk_count |

所有表由 `schema.sql` 初始创建，增量字段（如 `pinned`、`starred` 等）由 `SchemaMigration` 在每次启动时自动补齐，**无需手动执行任何迁移脚本**。

## API 接口

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/stream` | SSE 流式对话（支持 webSearch / temperature / maxTokens）|
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
| GET | `/api/messages/search?q=` | 全文搜索 |
| GET | `/api/messages/starred` | 获取所有收藏消息 |

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
    password: ${MYSQL_PASSWORD:062017}
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat.options.model: deepseek-chat

app:
  ai:
    qwen:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      model: qwen-plus
  websearch:
    api-key: ${WEB_SEARCH_API_KEY}
    endpoint: https://api.bochaai.com/v1/web-search
```

### 工作原理

**时间/天气预注入**：用户发消息前，后端识别意图后主动拉取当前时间或天气，直接写入系统提示词，无需模型侧 Function Calling，延迟更低。

**联网搜索**：前端开关开启后，后端调用 BochaAI 搜索 API，将结果摘要注入系统提示词，历史对话中的旧搜索结果自动失效，确保每次回答基于最新数据。

**流式连接断开处理**：用户点击停止或关闭页面时，前端 `AbortController` 断开连接，后端捕获 `IOException` 后取消 Reactor 订阅，已流出的内容正常保存。

**多模型架构**：`ChatClientRegistry` 启动时注册 DeepSeek 和 Qwen 两个 `ChatClient`，`ChatService` 根据请求的 `model` 字段动态选择，共享同一套流式处理逻辑。

## License

MIT
