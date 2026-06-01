# Always Attach Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove keyword-based `needsTools()` pre-filter; always attach function-calling tools so the AI model autonomously decides when to call them.

**Architecture:** Delete the `TOOL_KEYWORDS` set and `needsTools()` method from `ChatService.java`. In all 4 chat methods (`chat`, `chatStream`, `chatImageStream`, `regenerateStream`), remove the `if (needsTools(...))` guard and always attach `toolFunctions` + `getFileSystemTools()`. Keep `webSearch` toggle for web search tools. Update `system-prompt.txt` with clearer tool usage rules.

**Tech Stack:** Java 17, Spring AI 1.0.0-M6

---

### Task 1: Modify ChatService.java — Remove keyword filter and always attach tools

**Files:**
- Modify: `backend/src/main/java/com/myagent/service/ChatService.java`

- [ ] **Step 1: Remove TOOL_KEYWORDS constant**

Delete lines 39-49:
```java
    private static final Set<String> TOOL_KEYWORDS = Set.of(
        // 时间相关
        "时间", "几点", "几号", "日期", "今天", "明天", "昨天", "现在",
        // 天气相关
        "天气", "温度", "气温", "下雨", "下雪",
        // 文件系统相关
        "文件", "目录", "文件夹", "读取", "写入", "搜索", "查找", "列出",
        // English
        "time", "date", "weather", "temperature",
        "file", "directory", "folder", "search", "read", "write"
    );
```

- [ ] **Step 2: Remove needsTools() method**

Delete lines 51-55:
```java
    private boolean needsTools(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase();
        return TOOL_KEYWORDS.stream().anyMatch(lower::contains);
    }
```

- [ ] **Step 3: Modify chat() method — always attach tools**

Change (lines 169-173):
```java
        var spec = selectClient(model).prompt().messages(history);
        if (needsTools(userMessage)) {
            spec.tools(toolFunctions);
            spec.toolCallbacks(getFileSystemTools());
        }
```
To:
```java
        var spec = selectClient(model).prompt().messages(history);
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
```

- [ ] **Step 4: Modify chatStream() method — always attach tools**

Change (lines 199-203):
```java
        if (needsTools(userMessage)) {
            spec.tools(toolFunctions);
            spec.toolCallbacks(getFileSystemTools());
        }
```
To:
```java
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
```

- [ ] **Step 5: Modify chatImageStream() method — always attach tools**

Change (lines 271-274):
```java
        if (needsTools(userMessage)) {
            spec.tools(toolFunctions);
            spec.toolCallbacks(getFileSystemTools());
        }
```
To:
```java
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
```

- [ ] **Step 6: Modify regenerateStream() method — always attach tools**

Change (lines 292-295):
```java
        if (needsTools(userMessage)) {
            spec.tools(toolFunctions);
            spec.toolCallbacks(getFileSystemTools());
        }
```
To:
```java
        spec.tools(toolFunctions);
        spec.toolCallbacks(getFileSystemTools());
```

- [ ] **Step 7: Commit ChatService.java changes**

```bash
git add backend/src/main/java/com/myagent/service/ChatService.java
git commit -m "refactor: remove keyword-based tool filter, always attach tools for AI to decide"
```

---

### Task 2: Update system-prompt.txt — Enhanced tool usage instructions

**Files:**
- Modify: `backend/src/main/resources/system-prompt.txt`

- [ ] **Step 1: Replace the single-line tool instruction with a dedicated section**

Change line 16:
```
- 查询实时信息时必须调用工具，不能依赖历史记录
```
To:
```
## 工具使用规则（重要）
- 查询实时信息（时间、日期、天气等）时必须调用对应的工具函数获取真实数据，绝对不能依赖训练数据或编造
- 涉及当前时间的问题 → 调用 getCurrentTime
- 涉及天气的问题 → 调用 getWeather
- 涉及文件读写操作 → 调用文件系统工具
- 你不知道的实时信息（股价、新闻、赛事等） → 坦诚说明需要联网搜索，引导用户开启联网搜索功能
```

- [ ] **Step 2: Verify the file content**

Read the file to confirm the change is correct.

- [ ] **Step 3: Commit system-prompt.txt changes**

```bash
git add backend/src/main/resources/system-prompt.txt
git commit -m "docs: strengthen tool usage rules in system prompt"
```

---

### Task 3: Verify the build compiles

**Files:**
- Verify: `backend/` project compiles

- [ ] **Step 1: Build the backend**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: If build fails, fix any compilation errors**

Check for unused imports (e.g., `java.util.Set` may now be unused if no other Set fields exist). Remove any unused imports.
```
