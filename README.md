# MySpringAIAgent

全栈 AI 对话应用 —— Spring Boot 3 + Vue 3 + DeepSeek API，支持多会话管理、SSE 流式响应、Function Calling。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.3.0 |
| AI 框架 | Spring AI | 1.0.0-M6 |
| AI 提供商 | DeepSeek Chat | deepseek-chat |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | — |
| 前端框架 | Vue 3 | ^3.4.0 |
| 构建工具 | Vite | ^5.4.0 |
| Markdown | marked + highlight.js | — |

## 功能

- **多会话管理** — 对话持久化到 MySQL，支持创建、重命名、删除
- **SSE 流式响应** — AsyncContext 实现实时流输出，前端逐字渲染 Markdown
- **Function Calling** — 内置时间查询（时区感知）、天气查询（wttr.in）
- **消息操作** — 编辑、删除、重新生成回复
- **AI 角色设定** — 小凯：幽默诙谐的 AI 助手，带违禁内容过滤
- **Markdown 渲染** — 代码语法高亮，流式光标动画

## 项目结构

```
MySpringAIAgent/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/myagent/
│       │   ├── MySpringAiAgentApplication.java
│       │   ├── config/WebConfig.java          # CORS 配置
│       │   ├── controller/
│       │   │   ├── ChatController.java        # /api/chat, /api/chat/stream
│       │   │   ├── ConversationController.java
│       │   │   └── MessageController.java
│       │   ├── service/
│       │   │   ├── ChatService.java           # AI 对话核心逻辑
│       │   │   └── ConversationService.java   # 会话生命周期
│       │   ├── model/                         # 数据模型
│       │   ├── mapper/                        # MyBatis-Plus Mapper
│       │   └── tool/ToolFunctions.java        # getCurrentTime, getWeather
│       └── resources/
│           ├── application.yml
│           ├── schema.sql                     # 数据库 DDL
│           └── system-prompt.txt              # AI 角色设定
├── frontend/
│   ├── vite.config.js                         # Vite + SSE 代理配置
│   └── src/
│       ├── App.vue                            # 根组件，状态中心
│       ├── api/index.js                       # API 客户端（12个接口）
│       ├── components/
│       │   ├── ChatArea.vue, ChatInput.vue
│       │   ├── MessageBubble.vue, MessageList.vue
│       │   ├── Sidebar.vue, WelcomeScreen.vue
│       └── utils/time.js
└── README.md
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

### 1. 克隆项目

```bash
git clone <repo-url>
cd MySpringAIAgent
```

### 2. 数据库

确保 MySQL 运行中，启动时自动创建数据库和表（`schema.sql`）。

修改 `backend/src/main/resources/application.yml` 中的数据库密码，或通过环境变量：

```bash
export MYSQL_PASSWORD=your_password
```

### 3. DeepSeek API Key

```bash
export DEEPSEEK_API_KEY=sk-your-api-key
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，`/api/*` 请求自动代理到后端。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 非流式对话（含 Function Calling） |
| POST | `/api/chat/stream` | SSE 流式对话 |
| POST | `/api/chat/regenerate` | 重新生成最后一条 AI 回复 |
| GET | `/api/conversations` | 获取所有会话 |
| POST | `/api/conversations` | 创建新会话 |
| GET | `/api/conversations/{id}` | 获取会话详情（含消息） |
| PUT | `/api/conversations/{id}` | 重命名会话 |
| PATCH | `/api/conversations/{id}/touch` | 刷新会话时间戳 |
| DELETE | `/api/conversations/{id}` | 删除会话（级联删除消息） |
| PUT | `/api/messages/{id}` | 编辑消息 |
| DELETE | `/api/messages/{id}` | 删除消息 |

## 配置说明

### AI 角色设定

编辑 `backend/src/main/resources/system-prompt.txt` 修改小凯的性格、风格和违禁内容，重启后端生效。

### 切换 AI 提供商

修改 `application.yml` 中的 `spring.ai.openai` 配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
      chat:
        options:
          model: gpt-4o
```

Spring AI 兼容任何 OpenAI 接口格式的服务（DeepSeek、通义千问、智谱 GLM 等）。

## 数据库

DDL 位于 `backend/src/main/resources/schema.sql`：

```sql
conversations (id VARCHAR(36) PK, title, created_at, updated_at)
messages (id VARCHAR(36) PK, conversation_id FK, role, content TEXT, created_at)
```

所有 ID 使用 UUID，消息删除使用 `ON DELETE CASCADE`。
