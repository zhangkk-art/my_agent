# Design: Always Attach Tools for Real-time Information Queries

**Date:** 2026-05-29
**Status:** Approved

## Problem

The current `ChatService.needsTools()` method uses a hardcoded keyword list to decide whether to attach function-calling tools to AI requests. This approach has fundamental flaws:

1. **Incomplete keyword coverage** — queries like "股市怎么样", "比特币什么价", "今天有什么新闻" don't match any keywords, so tools are never attached
2. **Wrong decision maker** — the AI model should decide when to call tools, not a static keyword filter
3. **System prompt contradiction** — `system-prompt.txt` instructs the model to use tools for real-time info, but the code doesn't always provide them

## Solution

Remove the keyword-based pre-filter. Always attach available tools and let the AI model autonomously decide whether to call them based on the user's actual intent.

### Changes

#### 1. ChatService.java

**Remove:**
- `TOOL_KEYWORDS` static set (lines 39-49)
- `needsTools()` method (lines 51-55)

**Modify tool attachment in 4 methods:** `chat()`, `chatStream()`, `chatImageStream()`, `regenerateStream()`

```java
// Before (keyword-guarded):
if (needsTools(userMessage)) {
    spec.tools(toolFunctions);
    spec.toolCallbacks(getFileSystemTools());
}
if (webSearch) {
    spec.toolCallbacks(getWebSearchToolCallbacks());
}

// After (always available):
spec.tools(toolFunctions);                    // Always: time, weather
spec.toolCallbacks(getFileSystemTools());     // Always: filesystem (when MCP enabled)
if (webSearch) {
    spec.toolCallbacks(getWebSearchToolCallbacks()); // User-toggleable: web search
}
```

**Tool attachment policy:**

| Tool Group | Strategy | Rationale |
|---|---|---|
| `toolFunctions` (time, weather) | Always attached | Low cost, essential for real-time queries |
| `getFileSystemTools()` (MCP filesystem) | Always attached | File operations should be always available |
| `getWebSearchToolCallbacks()` (Brave search) | User toggle (`webSearch` flag) | Has external API cost; controlled by frontend button |

#### 2. system-prompt.txt

Strengthen tool usage instructions from a single line to a dedicated section:

```markdown
## 工具使用规则（重要）
- 查询实时信息（时间、日期、天气等）时必须调用对应的工具函数获取真实数据，绝对不能依赖训练数据或编造
- 涉及当前时间的问题 → 调用 getCurrentTime
- 涉及天气的问题 → 调用 getWeather
- 涉及文件读写的问题 → 调用文件系统工具
- 你不知道、不确定的实时信息 → 坦诚说明需要联网搜索，引导用户开启联网搜索功能
```

### Impact Analysis

| Scenario | Before | After |
|---|---|---|
| "现在几点了" | ✅ Keyword match → tools attached | ✅ Tools always attached → model calls |
| "比特币什么价格" | ❌ No keyword → no tools → hallucinates | ✅ Tools available → model honestly says needs web search |
| "解释量子计算" | ❌ No match → no tools (correct) | ✅ Tools attached but not called (correct) |
| Token overhead | ~0 extra (keyword match) or hallucination risk | ~500-800 tokens/request for tool definitions |

### Files Modified

| File | Change |
|---|---|
| `backend/src/main/java/com/myagent/service/ChatService.java` | Remove keyword filter, always attach tools |
| `backend/src/main/resources/system-prompt.txt` | Enhanced tool usage instructions |
