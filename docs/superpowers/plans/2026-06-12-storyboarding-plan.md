# Storyboarding Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add storyboarding (分镜编排) capability to video generation — LLM generates structured multi-shot storyboards, user edits, then batch or single-shot submission.

**Architecture:** New StoryboardService + StoryboardController (8 REST endpoints) sit alongside existing VideoGenService/Controller. New storyboards and storyboard_shots DB tables. Frontend adds a mode tab in VideoGenPanel.vue switching between single-shot (existing) and storyboard (new) forms.

**Tech Stack:** Spring Boot 3.5.x + MyBatis-Plus + DeepSeek LLM (via ChatClientRegistry) + Vue 3 Composition API

**Spec:** `docs/superpowers/specs/2026-06-12-storyboarding-design.md`

---

### Task 1: Storyboard Entity + Mapper

**Files:**
- Create: 
- Create: 

**Context:** Follows exact patterns of VideoPromptTemplate.java / VideoPromptTemplateMapper.java. Standard MyBatis-Plus entity with @TableName, @TableId, getters/setters. Mapper extends BaseMapper<Storyboard>.

- [ ] **Step 1: Create Storyboard.java entity**



- [ ] **Step 2: Create StoryboardMapper.java**



- [ ] **Step 3: Verify compilation**

Run: 
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

On branch main
Your branch is ahead of 'origin/main' by 14 commits.
  (use "git push" to publish your local commits)

Untracked files:
  (use "git add <file>..." to include in what will be committed)
	docs/superpowers/plans/2026-06-12-storyboarding-plan.md
	"é¡¹ç®äº®ç¹ä¸é¢è¯è¦ç¹.docx"

nothing added to commit but untracked files present (use "git add" to track)
