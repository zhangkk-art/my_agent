# 即梦AI-视频生成3.0 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Ayer 聊天应用集成即梦AI-视频生成3.0 Pro 功能，支持文生视频和图生视频，异步任务轮询

**Architecture:** 后端新增 VideoGenService 调用火山引擎即梦视觉API（IAM v4签名），VideoGenController 暴露REST端点，任务持久化到MySQL。前端新增 VideoGenPanel 替换ChatArea内容区，ChatInput新增🎬切换按钮

**Tech Stack:** Java 17, Spring Boot 3.5.x, MyBatis-Plus, java.net.http.HttpClient, Vue 3, Vite

**Spec:** docs/superpowers/specs/2026-06-11-jimeng-video-gen-design.md

---

## File Structure

**Created (backend):**
- `backend/src/main/java/com/myagent/model/VideoGenTask.java` — 任务实体
- `backend/src/main/java/com/myagent/model/VideoGenRequest.java` — 请求DTO
- `backend/src/main/java/com/myagent/mapper/VideoGenTaskMapper.java` — MyBatis-Plus Mapper
- `backend/src/main/java/com/myagent/util/IamV4Signer.java` — IAM v4签名工具
- `backend/src/main/java/com/myagent/service/VideoGenService.java` — 业务逻辑
- `backend/src/main/java/com/myagent/controller/VideoGenController.java` — REST端点

**Created (frontend):**
- `frontend/src/components/VideoGenPanel.vue` — 视频生成面板
- `frontend/src/components/VideoPlayerModal.vue` — 视频播放弹窗

**Modified:**
- `backend/src/main/resources/application.yml` — 新增jimeng配置
- `backend/src/main/java/com/myagent/config/SchemaMigration.java` — 新增video_gen_tasks表
- `frontend/src/components/ChatInput.vue` — 新增🎬按钮 + videoGen事件
- `frontend/src/components/ChatArea.vue` — 支持视频模式
- `frontend/src/api/index.js` — 新增5个视频生成API函数
- `frontend/src/App.vue` — 视频模式状态管理

---
test append
