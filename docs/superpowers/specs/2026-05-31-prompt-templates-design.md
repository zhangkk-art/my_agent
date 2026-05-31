# Prompt Template System — Design Spec

**Date:** 2026-05-31
**Status:** approved

---

## Overview

Allow users to save, manage, and reuse system prompt templates across conversations.
Templates act as a "preset library" — selecting a template fills the current conversation's
system prompt textarea, after which the user can freely edit without affecting the template.

## Data Model

### Database Table

```sql
CREATE TABLE IF NOT EXISTS prompt_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Add this to `schema.sql` alongside existing tables.

### Backend Model

New Java class `PromptTemplate` with MyBatis-Plus annotations, mapping to `prompt_templates` table.

Fields: `id`, `name`, `content`, `sortOrder`, `createdAt`, `updatedAt`.

### Frontend Cache

localStorage key: `prompt-templates-cache`. Stored as JSON array of `{id, name, content}`.
Cache is populated on first fetch and kept in sync on every CRUD operation.

## Backend API

New controller: `PromptTemplateController` at `/api/prompt-templates`.

| Method   | Path                          | Description        |
|----------|-------------------------------|--------------------|
| GET      | `/api/prompt-templates`       | List all templates |
| POST     | `/api/prompt-templates`       | Create a template  |
| PUT      | `/api/prompt-templates/{id}`  | Update a template  |
| DELETE   | `/api/prompt-templates/{id}`  | Delete a template  |

New service: `PromptTemplateService` with MyBatis-Plus CRUD operations.
New mapper: `PromptTemplateMapper`.

No authentication required (single-user app).

## Frontend Changes

### 1. ChatArea.vue — Template Selector in Prompt Modal

Above the existing `<textarea>` in the system prompt modal, add a row:

```
[Template: ▼ Select template...        ] [Manage →]
```

- `<select>` dropdown lists all template names, with a "Select template..." default option
- Selecting a template fills `promptDraft` with the template's content
- "Manage" link opens SettingsModal with the template section scrolled into view

Implementation: fetch template list from localStorage cache on modal open. If cache is empty,
fetch from API. Use a `templateList` ref populated on modal open.

### 2. SettingsModal.vue — Template Management Section

New section "提示词模板" added between "语音输入" and "数据" sections.

Layout:
```
提示词模板
┌──────────────────────────────────────────────────┐
│ 📝 Code Assistant                                │
│    You are a professional programmer skilled...  │  [Edit] [Delete]
├──────────────────────────────────────────────────┤
│ 📝 Translator                                    │
│    You are a multilingual translator...          │  [Edit] [Delete]
├──────────────────────────────────────────────────┤
│ [+ New Template]                                  │
└──────────────────────────────────────────────────┘
```

Behaviors:
- Click a row → expand inline edit form (name input + content textarea + Save/Cancel)
- Edit button → same inline edit form
- Delete button → confirm dialog, then delete via API + update localStorage
- "+ New Template" → empty inline form appears at top
- Save → POST (create) or PUT (update) via API, update localStorage cache

### 3. API Client (frontend/src/api/index.js)

Add four new functions:
```js
export async function getPromptTemplates() { ... }
export async function createPromptTemplate(name, content) { ... }
export async function updatePromptTemplate(id, name, content) { ... }
export async function deletePromptTemplate(id) { ... }
```

### 4. Revalidation Strategy

- On app mount → SettingsModal fetches fresh templates from API, overwrites cache
- After any CRUD operation → update cache immediately (optimistic), API call in background
- ChatArea's prompt modal → reads from cache only (fast open, no loading delay)

## Files Changed

| File | Change |
|------|--------|
| `schema.sql` | Add `prompt_templates` table |
| `backend/.../model/PromptTemplate.java` | **New** — entity class |
| `backend/.../mapper/PromptTemplateMapper.java` | **New** — MyBatis-Plus mapper |
| `backend/.../service/PromptTemplateService.java` | **New** — CRUD service |
| `backend/.../controller/PromptTemplateController.java` | **New** — REST controller |
| `frontend/src/api/index.js` | Add 4 template API functions |
| `frontend/src/components/ChatArea.vue` | Add template selector in prompt modal |
| `frontend/src/components/SettingsModal.vue` | Add template management section |

## Error Handling

- API errors: show Toast notification, keep cache unchanged (no rollback needed for reads)
- Empty name/content validation on frontend before submission
- Delete: confirmation dialog before API call
- Cache miss: fallback to API fetch

## Scope Boundaries

**In scope:** Template CRUD, template selector in prompt modal, localStorage caching
**Out of scope:** Template import/export, template sharing, template categories/tags,
template variables/placeholders, per-conversation template binding
