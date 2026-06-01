# Ayer — AI Chat Application

全栈 AI 对话应用，Spring Boot + Vue 3 + 多模型（DeepSeek / Qwen），支持流式对话、联网搜索、图片分析、会话分享。

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

## 功能

- **多会话管理** — MySQL 持久化，创建、重命名、搜索（标题 + 全文消息搜索）、删除
- **SSE 流式响应** — AsyncContext + Reactor Flux，前端逐字渲染
- **双模型切换** — DeepSeek 和 Qwen 按会话切换
- **联网搜索** — 输入框左侧地球图标开启，走 BochaAI API，搜索结果注入上下文
- **图片分析** — 上传图片，base64 编码直接发给视觉模型
- **思考过程** — DeepSeek-R1 的 reasoning 可折叠展示
- **系统提示词** — 每会话自定义，支持提示词模板库（CRUD + localStorage 缓存）
- **消息操作** — 编辑、删除、重新生成、复制（整条 + 代码块单独复制）
- **Markdown 渲染** — 代码语法高亮、表格、KaTeX 数学公式（行内 + 块级）
- **会话分享** — 生成一次性链接，支持撤销分享
- **导出对话** — Markdown / TXT 格式下载
- **语音输入** — 浏览器 Web Speech API，多语言支持
- **主题切换** — 明暗双主题，跟随系统偏好
- **可拖拽侧边栏** — 鼠标拖拽调整左右宽度，宽度记忆到 localStorage
- **设置面板** — 字体大小、默认模型、Enter 发送、语音语言、清除数据

## 项目结构

```
MySpringAIAgent/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/myagent/
│       │   ├── MySpringAiAgentApplication.java
│       │   ├── config/
│       │   │   ├── WebConfig.java              # CORS
│       │   │   ├── ChatClientRegistry.java      # DeepSeek / Qwen 客户端注册
│       │   │   └── SchemaMigration.java         # 数据库自动建表
│       │   ├── controller/
│       │   │   ├── ChatController.java          # 对话 + SSE 流（chat, image, regenerate）
│       │   │   ├── ConversationController.java  # 会话 CRUD + 分享
│       │   │   ├── MessageController.java       # 消息编辑/删除/搜索
│       │   │   ├── PromptTemplateController.java # 提示词模板 CRUD
│       │   │   └── SharePageController.java     # 分享页面 HTML 渲染
│       │   ├── service/
│       │   │   ├── ChatService.java             # AI 对话核心（多模型、联网搜索）
│       │   │   ├── ConversationService.java     # 会话生命周期 + 分享管理
│       │   │   └── PromptTemplateService.java   # 提示词模板管理
│       │   ├── model/
│       │   │   ├── Conversation.java            # 会话实体（含 shareToken）
│       │   │   ├── Message.java                 # 消息实体（含 reasoning）
│       │   │   ├── ChatRequest.java             # 请求 DTO
│       │   │   └── PromptTemplate.java          # 模板实体
│       │   ├── mapper/
│       │   │   ├── ConversationMapper.java
│       │   │   ├── MessageMapper.java
│       │   │   └── PromptTemplateMapper.java
│       │   └── tool/
│       │       ├── ToolFunctions.java           # 时间/天气预注入
│       │       └── WebSearchTools.java          # BochaAI 联网搜索
│       └── resources/
│           ├── application.yml
│           └── schema.sql
├── frontend/
│   ├── vite.config.js
│   ├── package.json
│   └── src/
│       ├── main.js
│       ├── App.vue                              # 根组件，全局状态 + 拖拽侧边栏
│       ├── api/index.js                         # API 客户端（18 个接口）
│       ├── assets/style.css                     # 全局样式 + CSS 变量
│       ├── utils/time.js                        # 时间格式化 + 日期分组
│       └── components/
│           ├── Sidebar.vue                      # 侧边栏（会话列表、搜索、主题切换）
│           ├── ChatArea.vue                     # 对话区（顶部栏、提示词、分享、导出）
│           ├── ChatInput.vue                    # 输入框（图片上传、语音、联网搜索开关）
│           ├── MessageList.vue                  # 消息列表（自动滚动）
│           ├── MessageBubble.vue                # 消息气泡（Markdown、思考过程、编辑）
│           ├── WelcomeScreen.vue                # 空状态欢迎页
│           ├── SettingsModal.vue                # 设置面板 + 提示词模板管理
│           ├── SharedView.vue                   # 分享对话只读视图
│           └── Toast.vue                        # 全局通知组件
└── README.md
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

### 1. 数据库

确保 MySQL 运行中，项目启动时自动创建 `ai_chat` 库及所有表（`application.yml` 中配置了 `spring.sql.init.mode: always`）。

修改密码（如果非默认 `062017`）：

```bash
export MYSQL_PASSWORD=your_password
```

### 2. API Keys

```bash
# DeepSeek（必填）
export DEEPSEEK_API_KEY=sk-your-key

# Qwen（可选，使用通义千问时必填）
export DASHSCOPE_API_KEY=sk-your-key

# 联网搜索（可选）
export WEB_SEARCH_API_KEY=sk-your-key
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
# → http://localhost:8080
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

Vite 自动代理 `/api/*` 到后端 8080 端口。

## API 接口

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 非流式对话 |
| POST | `/api/chat/stream` | SSE 流式对话（支持 webSearch） |
| POST | `/api/chat/image` | 图片分析（流式，base64 多图） |
| POST | `/api/chat/regenerate` | 重新生成最后一条 AI 回复 |

### 会话

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/conversations` | 获取所有会话（含消息） |
| POST | `/api/conversations` | 创建新会话 |
| GET | `/api/conversations/{id}` | 获取会话详情 |
| PUT | `/api/conversations/{id}` | 重命名 |
| PATCH | `/api/conversations/{id}/touch` | 刷新时间戳 |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| POST | `/api/conversations/{id}/share` | 生成分享链接 |
| DELETE | `/api/conversations/{id}/share` | 撤销分享 |
| PUT | `/api/conversations/{id}/system-prompt` | 更新系统提示词 |

### 消息

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/messages/{id}` | 编辑消息内容 |
| DELETE | `/api/messages/{id}` | 删除单条消息 |
| GET | `/api/messages/search?q=` | 全文搜索消息 |

### 分享（公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/shared/{token}` | 根据 token 获取分享的会话 |

### 提示词模板

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/prompt-templates` | 获取所有模板 |
| POST | `/api/prompt-templates` | 创建模板 |
| PUT | `/api/prompt-templates/{id}` | 更新模板 |
| DELETE | `/api/prompt-templates/{id}` | 删除模板 |

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
      api-key: ${DEEPSEEK_API_KEY}       # DeepSeek 主模型
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat

app:
  ai:
    qwen:                                  # 通义千问（第二模型）
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      model: qwen-plus
  websearch:                               # 联网搜索
    api-key: ${WEB_SEARCH_API_KEY}
    endpoint: https://api.bochaai.com/v1/web-search
```

### 数据库表

```sql
conversations     (id, title, system_prompt, share_token, created_at, updated_at)
messages          (id, conversation_id FK, role, content, reasoning, created_at)
prompt_templates  (id, name, content, sort_order, created_at, updated_at)
```

所有 ID 使用 UUID，消息使用 `ON DELETE CASCADE`。

### 工作原理

**时间/天气预注入**：用户消息发送前，后端先拉取当前时间和天气（基于 IP 地理位置），直接拼入系统提示词，不依赖模型侧 Function Calling，更快速可靠。

**联网搜索**：前端开关开启后，后端调用 BochaAI 搜索 API，将结果摘要注入系统提示词，模型可据此回答实时信息。

**分享机制**：分享时生成 12 位随机 token 存入 `share_token` 字段，`/share/<token>` 路径渲染只读视图，撤销后 token 清空。

**多模型架构**：`ChatClientRegistry` 在启动时为 DeepSeek 和 Qwen 各注册一个 `ChatClient` bean，`ChatService` 根据请求参数动态选择。
