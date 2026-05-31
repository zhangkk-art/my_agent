# Prompt Template System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add save/reuse system prompt templates across conversations with localStorage caching.

**Architecture:** New `prompt_templates` table + MyBatis-Plus CRUD layer + REST API. Frontend adds template selector in ChatArea's prompt modal and a management section in SettingsModal. localStorage caches templates for instant reads.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Vue 3, Vite

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `backend/.../model/PromptTemplate.java` | **Create** | Entity class |
| `backend/.../mapper/PromptTemplateMapper.java` | **Create** | MyBatis-Plus mapper |
| `backend/.../service/PromptTemplateService.java` | **Create** | CRUD service |
| `backend/.../controller/PromptTemplateController.java` | **Create** | REST controller at `/api/prompt-templates` |
| `backend/.../resources/schema.sql` | Modify | Add `prompt_templates` DDL |
| `frontend/src/api/index.js` | Modify | Add 4 template API functions |
| `frontend/src/components/ChatArea.vue` | Modify | Add template selector in prompt modal |
| `frontend/src/components/SettingsModal.vue` | Modify | Add template management section |

---

### Task 1: Database Table

**Files:** Modify: `backend/src/main/resources/schema.sql`

- [ ] **Step 1: Add prompt_templates DDL**

Insert before the existing `conversations` table definition:

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

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/schema.sql
git commit -m "feat: add prompt_templates table DDL"
```

---

### Task 2: Backend — PromptTemplate Model

**Files:** Create: `backend/src/main/java/com/myagent/model/PromptTemplate.java`

- [ ] **Step 1: Create PromptTemplate entity**

```java
package com.myagent.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("prompt_templates")
public class PromptTemplate {

    @TableId
    private String id;
    private String name;
    private String content;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PromptTemplate() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/myagent/model/PromptTemplate.java
git commit -m "feat: add PromptTemplate entity"
```

---

### Task 3: Backend — PromptTemplateMapper

**Files:** Create: `backend/src/main/java/com/myagent/mapper/PromptTemplateMapper.java`

- [ ] **Step 1: Create mapper interface**

```java
package com.myagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.model.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/myagent/mapper/PromptTemplateMapper.java
git commit -m "feat: add PromptTemplateMapper"
```

---

### Task 4: Backend — PromptTemplateService

**Files:** Create: `backend/src/main/java/com/myagent/service/PromptTemplateService.java`

- [ ] **Step 1: Create service with CRUD operations**

```java
package com.myagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.PromptTemplateMapper;
import com.myagent.model.PromptTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PromptTemplateService {

    private final PromptTemplateMapper mapper;

    public PromptTemplateService(PromptTemplateMapper mapper) {
        this.mapper = mapper;
    }

    public List<PromptTemplate> getAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .orderByAsc(PromptTemplate::getSortOrder)
                        .orderByDesc(PromptTemplate::getUpdatedAt));
    }

    @Transactional
    public PromptTemplate create(String name, String content) {
        PromptTemplate t = new PromptTemplate();
        t.setId(UUID.randomUUID().toString());
        t.setName(name);
        t.setContent(content);
        t.setSortOrder(0);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        mapper.insert(t);
        return t;
    }

    @Transactional
    public PromptTemplate update(String id, String name, String content) {
        PromptTemplate t = mapper.selectById(id);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found: " + id);
        }
        t.setName(name);
        t.setContent(content);
        t.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(t);
        return t;
    }

    @Transactional
    public void delete(String id) {
        if (mapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found: " + id);
        }
        mapper.deleteById(id);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/myagent/service/PromptTemplateService.java
git commit -m "feat: add PromptTemplateService with CRUD"
```

---

### Task 5: Backend — PromptTemplateController

**Files:** Create: `backend/src/main/java/com/myagent/controller/PromptTemplateController.java`

- [ ] **Step 1: Create REST controller**

```java
package com.myagent.controller;

import com.myagent.model.PromptTemplate;
import com.myagent.service.PromptTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateService service;

    public PromptTemplateController(PromptTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<PromptTemplate> getAll() {
        return service.getAll();
    }

    @PostMapping
    public PromptTemplate create(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "Untitled");
        String content = body.getOrDefault("content", "");
        return service.create(name, content);
    }

    @PutMapping("/{id}")
    public PromptTemplate update(@PathVariable String id, @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "Untitled");
        String content = body.getOrDefault("content", "");
        return service.update(id, name, content);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/myagent/controller/PromptTemplateController.java
git commit -m "feat: add PromptTemplateController REST API"
```

---

### Task 6: Frontend — API Client Functions

**Files:** Modify: `frontend/src/api/index.js`

- [ ] **Step 1: Add 4 template API functions**

Append to the end of `frontend/src/api/index.js`:

```js
// ── Prompt templates ──

export async function getPromptTemplates() {
  const res = await fetch(`${BASE_URL}/prompt-templates`);
  if (!res.ok) throw new Error('Failed to fetch templates');
  return res.json();
}

export async function createPromptTemplate(name, content) {
  const res = await fetch(`${BASE_URL}/prompt-templates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, content })
  });
  if (!res.ok) throw new Error('Failed to create template');
  return res.json();
}

export async function updatePromptTemplate(id, name, content) {
  const res = await fetch(`${BASE_URL}/prompt-templates/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, content })
  });
  if (!res.ok) throw new Error('Failed to update template');
  return res.json();
}

export async function deletePromptTemplate(id) {
  const res = await fetch(`${BASE_URL}/prompt-templates/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete template');
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/index.js
git commit -m "feat: add prompt template API client functions"
```

---

### Task 7: Frontend — ChatArea Template Selector

**Files:** Modify: `frontend/src/components/ChatArea.vue`

- [ ] **Step 1: Add template list ref and fetch logic**

Find the line `const promptModalOpen = ref(false)` and replace with:

```js
const promptModalOpen = ref(false)
const promptDraft = ref('')
const templateList = ref([])

function loadTemplates() {
  // Read from localStorage cache first for fast open
  const cached = localStorage.getItem('prompt-templates-cache')
  if (cached) {
    try { templateList.value = JSON.parse(cached) } catch {}
  }
  // Refresh from API in background
  api.getPromptTemplates().then(list => {
    templateList.value = list
    localStorage.setItem('prompt-templates-cache', JSON.stringify(list))
  }).catch(() => {})
}
```

- [ ] **Step 2: Load templates when prompt modal opens**

Replace `function openPromptModal()`:

```js
function openPromptModal() {
  promptDraft.value = props.conversation?.systemPrompt || ''
  loadTemplates()
  promptModalOpen.value = true
}
```

- [ ] **Step 3: Add template selector in prompt modal template**

Find `<textarea v-model="promptDraft" class="prompt-textarea"` and insert this above it:

```html
<div v-if="templateList.length > 0" class="template-select-row">
  <select
    class="template-select"
    @change="onTemplateSelect"
  >
    <option value="">— 选择模板 —</option>
    <option v-for="t in templateList" :key="t.id" :value="t.content">
      {{ t.name }}
    </option>
  </select>
</div>
```

- [ ] **Step 4: Add onTemplateSelect function**

Below `openPromptModal`, add:

```js
function onTemplateSelect(e) {
  if (e.target.value) {
    promptDraft.value = e.target.value
    // Reset select back to placeholder so same template can be re-selected
    e.target.value = ''
  }
}
```

- [ ] **Step 5: Add CSS for template selector**

Add these styles inside the `<style scoped>` block:

```css
.template-select-row {
  margin-bottom: 10px;
}
.template-select {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 6px 10px;
  color: var(--text-primary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  outline: none;
}
.template-select:focus {
  border-color: var(--accent);
}
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/ChatArea.vue
git commit -m "feat: add template selector in system prompt modal"
```

---

### Task 8: Frontend — SettingsModal Template Management

**Files:** Modify: `frontend/src/components/SettingsModal.vue`

- [ ] **Step 1: Add template management state and logic in script**

Find `const confirmingClear = ref(false)` and add after it:

```js
const templates = ref([])
const editingId = ref(null)
const editName = ref('')
const editContent = ref('')
let templatesLoaded = false

function loadTemplatesFromApi() {
  api.getPromptTemplates().then(list => {
    templates.value = list
    localStorage.setItem('prompt-templates-cache', JSON.stringify(list))
  }).catch(() => {})
}

// Load when settings opens: API first, fallback to cache
watch(() => props.modelValue, (v) => {
  if (v && !templatesLoaded) {
    templatesLoaded = true
    api.getPromptTemplates().then(list => {
      templates.value = list
      localStorage.setItem('prompt-templates-cache', JSON.stringify(list))
    }).catch(() => {
      const cached = localStorage.getItem('prompt-templates-cache')
      if (cached) {
        try { templates.value = JSON.parse(cached) } catch {}
      }
    })
  }
})

function startNewTemplate() {
  editingId.value = '__new__'
  editName.value = ''
  editContent.value = ''
}

function startEditTemplate(t) {
  editingId.value = t.id
  editName.value = t.name
  editContent.value = t.content
}

function cancelEdit() {
  editingId.value = null
  editName.value = ''
  editContent.value = ''
}

async function saveTemplate() {
  const name = editName.value.trim()
  if (!name) return
  try {
    if (editingId.value === '__new__') {
      await api.createPromptTemplate(name, editContent.value)
    } else {
      await api.updatePromptTemplate(editingId.value, name, editContent.value)
    }
    cancelEdit()
    loadTemplatesFromApi()
  } catch (e) {
    console.error('Save template failed:', e)
  }
}

async function deleteTemplate(id) {
  try {
    await api.deletePromptTemplate(id)
    if (editingId.value === id) cancelEdit()
    loadTemplatesFromApi()
  } catch (e) {
    console.error('Delete template failed:', e)
  }
}
```

- [ ] **Step 2: Add import for watch and api**

Update the import line:

```js
import { reactive, ref, watch } from 'vue'
import * as api from '../api/index.js'
```

- [ ] **Step 3: Add template management section in template**

Insert between the "语音输入" section and "数据" section (after the `</div>` closing the voice section's settings-section div, before the data section):

```html
<!-- 提示词模板 -->
<div class="settings-section">
  <div class="section-label">提示词模板</div>

  <!-- New / inline editor -->
  <div v-if="editingId" class="template-editor">
    <input
      v-model="editName"
      class="template-edit-name"
      placeholder="模板名称"
      maxlength="50"
    />
    <textarea
      v-model="editContent"
      class="template-edit-content"
      rows="4"
      placeholder="模板内容，例如：你是一个专业的程序员..."
    ></textarea>
    <div class="template-editor-actions">
      <button class="btn-template-save" @click="saveTemplate">保存</button>
      <button class="btn-template-cancel" @click="cancelEdit">取消</button>
    </div>
  </div>

  <!-- Template list -->
  <div v-for="t in templates" :key="t.id" class="template-row">
    <div class="template-info">
      <div class="template-name">📝 {{ t.name }}</div>
      <div class="template-preview">{{ t.content.substring(0, 80) }}{{ t.content.length > 80 ? '…' : '' }}</div>
    </div>
    <div class="template-actions">
      <button class="btn-template-edit" @click="startEditTemplate(t)">编辑</button>
      <button class="btn-template-delete" @click="deleteTemplate(t.id)">删除</button>
    </div>
  </div>

  <div v-if="!editingId" class="template-add-row">
    <button class="btn-template-add" @click="startNewTemplate">+ 新建模板</button>
  </div>
</div>
```

- [ ] **Step 4: Add CSS for template management**

Add at the end of the `<style scoped>` block, before the closing `</style>`:

```css
/* ── Template management ── */
.template-editor {
  padding: 4px 20px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.template-edit-name {
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 6px 10px;
  color: var(--text-primary);
  font-size: 13px;
  font-family: inherit;
  outline: none;
}
.template-edit-name:focus {
  border-color: var(--accent);
}
.template-edit-content {
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 8px 10px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
  font-family: inherit;
  outline: none;
}
.template-edit-content:focus {
  border-color: var(--accent);
}
.template-editor-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.btn-template-save {
  padding: 5px 14px;
  background: var(--accent);
  color: white;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}
.btn-template-save:hover {
  background: var(--accent-hover);
}
.btn-template-cancel {
  padding: 5px 14px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  border-radius: 6px;
  font-size: 12px;
}

.template-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 20px;
  border-bottom: 1px solid var(--border-color);
}
.template-row:last-of-type {
  border-bottom: none;
}
.template-info {
  flex: 1;
  min-width: 0;
}
.template-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}
.template-preview {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.template-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.btn-template-edit {
  padding: 4px 10px;
  background: none;
  color: var(--text-muted);
  border-radius: 5px;
  font-size: 11px;
  transition: all 0.15s;
}
.btn-template-edit:hover {
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}
.btn-template-delete {
  padding: 4px 10px;
  background: none;
  color: var(--text-muted);
  border-radius: 5px;
  font-size: 11px;
  transition: all 0.15s;
}
.btn-template-delete:hover {
  color: var(--danger);
  background: rgba(224, 85, 106, 0.08);
}

.template-add-row {
  padding: 8px 20px;
}
.btn-template-add {
  padding: 6px 14px;
  background: none;
  color: var(--accent);
  border: 1px dashed var(--border-color);
  border-radius: 6px;
  font-size: 12px;
  transition: all 0.15s;
}
.btn-template-add:hover {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 5%, transparent);
}
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/SettingsModal.vue
git commit -m "feat: add prompt template management in settings"
```

---

### Task 9: Verify End-to-End

- [ ] **Step 1: Restart backend to apply schema changes**

```bash
cd backend && mvn spring-boot:run
```

- [ ] **Step 2: Verify frontend dev server is running**

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: Manual smoke test**

1. Open the app, click settings
2. In "提示词模板" section, click "+ 新建模板"
3. Enter name "Code Helper" and content "You are a helpful coding assistant."
4. Click "保存" — template should appear in the list
5. Open a conversation, click the system prompt button
6. A "选择模板" dropdown should appear — select "Code Helper"
7. Content should fill the textarea
8. Edit and save the system prompt for this conversation (does not affect the template)
9. Back to settings, delete the template — confirm it's removed
