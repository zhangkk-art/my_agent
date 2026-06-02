<template>
  <div class="settings-overlay" @click.self="$emit('close')">
    <div class="settings-modal">
      <div class="settings-header">
        <span class="settings-title">设置</span>
        <button class="btn-close" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <div class="settings-body">

        <!-- 外观 -->
        <div class="settings-section">
          <div class="section-label">外观</div>

          <div class="setting-row">
            <div class="setting-info">
              <div class="setting-name">字体大小</div>
            </div>
            <div class="seg-control">
              <button
                v-for="opt in fontSizeOptions" :key="opt.value"
                class="seg-btn"
                :class="{ active: local.fontSize === opt.value }"
                @click="set('fontSize', opt.value)"
              >{{ opt.label }}</button>
            </div>
          </div>
        </div>

        <!-- 生成参数 -->
        <div class="settings-section">
          <div class="section-label">生成参数</div>

          <div class="setting-row setting-row-col">
            <div class="setting-info">
              <div class="setting-name">Temperature <span class="param-val">{{ local.temperature?.toFixed(1) ?? '0.7' }}</span></div>
              <div class="setting-desc">越高越有创意，越低越保守</div>
            </div>
            <input type="range" min="0" max="2" step="0.1"
              :value="local.temperature ?? 0.7"
              @input="set('temperature', parseFloat($event.target.value))"
              class="param-slider" />
          </div>

          <div class="setting-row setting-row-col">
            <div class="setting-info">
              <div class="setting-name">最大 Token <span class="param-val">{{ local.maxTokens ?? 2048 }}</span></div>
              <div class="setting-desc">单次回答的最大输出长度</div>
            </div>
            <input type="range" min="256" max="8192" step="256"
              :value="local.maxTokens ?? 2048"
              @input="set('maxTokens', parseInt($event.target.value))"
              class="param-slider" />
          </div>

          <div class="setting-row" style="padding-top:4px;padding-bottom:12px;">
            <div></div>
            <button class="btn-param-reset"
              @click="set('temperature', 0.7); set('maxTokens', 2048)">
              恢复默认
            </button>
          </div>
        </div>

        <!-- 对话 -->
        <div class="settings-section">
          <div class="section-label">对话</div>

          <div class="setting-row">
            <div class="setting-info">
              <div class="setting-name">默认模型</div>
              <div class="setting-desc">新会话使用的模型</div>
            </div>
            <div class="seg-control">
              <button
                class="seg-btn" :class="{ active: local.defaultModel === 'deepseek' }"
                @click="set('defaultModel', 'deepseek')"
              >DeepSeek</button>
              <button
                class="seg-btn" :class="{ active: local.defaultModel === 'qwen' }"
                @click="set('defaultModel', 'qwen')"
              >Qwen</button>
            </div>
          </div>

          <div class="setting-row">
            <div class="setting-info">
              <div class="setting-name">Enter 键直接发送</div>
              <div class="setting-desc">关闭后需 Shift+Enter 换行，Ctrl+Enter 发送</div>
            </div>
            <button class="toggle" :class="{ on: local.enterToSend }" @click="set('enterToSend', !local.enterToSend)">
              <span class="toggle-knob"></span>
            </button>
          </div>
        </div>

        <!-- 语音 -->
        <div class="settings-section">
          <div class="section-label">语音输入</div>

          <div class="setting-row">
            <div class="setting-info">
              <div class="setting-name">识别语言</div>
            </div>
            <select class="setting-select" v-model="local.voiceLang" @change="set('voiceLang', local.voiceLang)">
              <option value="zh-CN">中文（普通话）</option>
              <option value="zh-TW">中文（繁体）</option>
              <option value="en-US">English (US)</option>
              <option value="en-GB">English (UK)</option>
              <option value="ja-JP">日本語</option>
              <option value="ko-KR">한국어</option>
            </select>
          </div>
        </div>

        <!-- 提示词模板 -->
        <div class="settings-section">
          <div class="section-label">提示词模板</div>

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

          <div v-for="t in templates" :key="t.id" class="template-row">
            <div class="template-info">
              <div class="template-name">{{ t.name }}</div>
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

        <!-- 对话模板 -->
        <div class="settings-section">
          <div class="section-label">对话模板</div>

          <div v-if="editingWorkflowId" class="template-editor">
            <input v-model="wfName" class="template-edit-name" placeholder="模板名称" maxlength="50" />
            <input v-model="wfDesc" class="template-edit-name" placeholder="简介（可选）" maxlength="100" style="margin-top:0" />
            <textarea v-model="wfSystemPrompt" class="template-edit-content" rows="3" placeholder="系统提示词（可选）"></textarea>
            <textarea v-model="wfInitialMessage" class="template-edit-content" rows="2" placeholder="初始消息，创建后自动发送（可选）"></textarea>
            <div class="template-editor-actions">
              <button class="btn-template-save" @click="saveWorkflow">保存</button>
              <button class="btn-template-cancel" @click="cancelWorkflowEdit">取消</button>
            </div>
          </div>

          <div v-for="wf in workflowTemplates" :key="wf.id" class="template-row">
            <div class="template-info">
              <div class="template-name">{{ wf.name }}</div>
              <div class="template-preview">{{ wf.description || wf.initialMessage || wf.systemPrompt || '无描述' }}</div>
            </div>
            <div class="template-actions">
              <button class="btn-template-edit" @click="startEditWorkflow(wf)">编辑</button>
              <button class="btn-template-delete" @click="deleteWorkflow(wf.id)">删除</button>
            </div>
          </div>

          <div v-if="!editingWorkflowId" class="template-add-row">
            <button class="btn-template-add" @click="startNewWorkflow">+ 新建模板</button>
          </div>
        </div>

        <!-- 数据 -->
        <div class="settings-section">
          <div class="section-label">数据</div>

          <div class="setting-row">
            <div class="setting-info">
              <div class="setting-name">清除所有对话</div>
              <div class="setting-desc">删除全部历史记录，此操作不可撤销</div>
            </div>
            <div class="danger-ctrl">
              <template v-if="confirmingClear">
                <span class="confirm-hint">确认删除全部？</span>
                <button class="btn-danger-sm" @click="doClear">删除</button>
                <button class="btn-cancel-sm" @click="confirmingClear = false">取消</button>
              </template>
              <button v-else class="btn-danger-outline" @click="confirmingClear = true">清除</button>
            </div>
          </div>
        </div>

      </div>

      <div class="settings-footer">
        <button class="btn-footer-cancel" @click="$emit('close')">取消</button>
        <button class="btn-footer-save" @click="save">确定</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import * as api from '../api/index.js'

const props = defineProps({ modelValue: Object })
const emit = defineEmits(['close', 'update', 'clearAll'])

const local = reactive({ ...props.modelValue })
const confirmingClear = ref(false)

// ── Prompt template management ──
const templates = ref([])
const editingId = ref(null)
const editName = ref('')
const editContent = ref('')
let templatesLoaded = false

// ── Workflow template management ──
const workflowTemplates = ref([])
const editingWorkflowId = ref(null)
const wfName = ref('')
const wfDesc = ref('')
const wfSystemPrompt = ref('')
const wfInitialMessage = ref('')
let workflowsLoaded = false

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
  if (v && !workflowsLoaded) {
    workflowsLoaded = true
    api.getWorkflowTemplates().then(list => { workflowTemplates.value = list }).catch(() => {})
  }
})

function loadTemplatesFromApi() {
  api.getPromptTemplates().then(list => {
    templates.value = list
    localStorage.setItem('prompt-templates-cache', JSON.stringify(list))
  }).catch(() => {})
}

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

function startNewWorkflow() {
  editingWorkflowId.value = '__new__'
  wfName.value = ''
  wfDesc.value = ''
  wfSystemPrompt.value = ''
  wfInitialMessage.value = ''
}

function startEditWorkflow(wf) {
  editingWorkflowId.value = wf.id
  wfName.value = wf.name
  wfDesc.value = wf.description || ''
  wfSystemPrompt.value = wf.systemPrompt || ''
  wfInitialMessage.value = wf.initialMessage || ''
}

function cancelWorkflowEdit() {
  editingWorkflowId.value = null
}

async function saveWorkflow() {
  const name = wfName.value.trim()
  if (!name) return
  const data = {
    name,
    description: wfDesc.value.trim() || null,
    systemPrompt: wfSystemPrompt.value.trim() || null,
    initialMessage: wfInitialMessage.value.trim() || null,
  }
  try {
    if (editingWorkflowId.value === '__new__') {
      await api.createWorkflowTemplate(data)
    } else {
      await api.updateWorkflowTemplate(editingWorkflowId.value, data)
    }
    cancelWorkflowEdit()
    api.getWorkflowTemplates().then(list => { workflowTemplates.value = list }).catch(() => {})
  } catch (e) {
    console.error('Save workflow template failed:', e)
  }
}

async function deleteWorkflow(id) {
  try {
    await api.deleteWorkflowTemplate(id)
    if (editingWorkflowId.value === id) cancelWorkflowEdit()
    api.getWorkflowTemplates().then(list => { workflowTemplates.value = list }).catch(() => {})
  } catch (e) {
    console.error('Delete workflow template failed:', e)
  }
}

const fontSizeOptions = [
  { label: '小', value: 'small' },
  { label: '中', value: 'medium' },
  { label: '大', value: 'large' },
]

function set(key, value) {
  local[key] = value
}

function save() {
  emit('update', { ...local })
}

function doClear() {
  confirmingClear.value = false
  emit('clearAll')
}
</script>

<style scoped>
.settings-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}

.settings-modal {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  width: 480px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  background: var(--bg-primary);
  z-index: 1;
}

.settings-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.btn-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 6px;
  transition: all 0.15s;
}
.btn-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.settings-body {
  padding: 8px 0 12px;
}

.settings-section {
  padding: 4px 0;
}
.settings-section + .settings-section {
  border-top: 1px solid var(--border-color);
  margin-top: 4px;
}

.section-label {
  padding: 12px 20px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 20px;
  min-height: 52px;
}

.setting-info {
  flex: 1;
  min-width: 0;
}
.setting-name {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}
.setting-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
  line-height: 1.4;
}

/* Segmented control */
.seg-control {
  display: flex;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 2px;
  flex-shrink: 0;
}
.seg-btn {
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  background: none;
  transition: all 0.15s;
  white-space: nowrap;
}
.seg-btn:hover {
  color: var(--text-primary);
}
.seg-btn.active {
  background: var(--bg-primary);
  color: var(--text-primary);
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
}

/* Toggle switch */
.toggle {
  width: 42px;
  height: 24px;
  border-radius: 12px;
  background: var(--border-color);
  position: relative;
  flex-shrink: 0;
  transition: background 0.2s;
  cursor: pointer;
}
.toggle.on {
  background: var(--accent);
}
.toggle-knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: white;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}
.toggle.on .toggle-knob {
  transform: translateX(18px);
}

/* Select */
.setting-select {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 6px 10px;
  color: var(--text-primary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  flex-shrink: 0;
  outline: none;
}
.setting-select:focus {
  border-color: var(--accent);
}

/* Danger zone */
.danger-ctrl {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.confirm-hint {
  font-size: 12px;
  color: var(--danger);
  white-space: nowrap;
}
.btn-danger-outline {
  padding: 6px 14px;
  border: 1px solid var(--danger);
  color: var(--danger);
  border-radius: 7px;
  font-size: 13px;
  background: none;
  transition: all 0.15s;
}
.btn-danger-outline:hover {
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}
.btn-danger-sm {
  padding: 5px 12px;
  background: var(--danger);
  color: white;
  border-radius: 6px;
  font-size: 12px;
  transition: opacity 0.15s;
}
.btn-danger-sm:hover {
  opacity: 0.85;
}
.btn-cancel-sm {
  padding: 5px 12px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  border-radius: 6px;
  font-size: 12px;
}
.btn-cancel-sm:hover {
  background: var(--border-color);
}

.settings-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--border-color);
}

.btn-footer-cancel {
  padding: 7px 16px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  border-radius: 7px;
  font-size: 13px;
}
.btn-footer-cancel:hover {
  background: var(--border-color);
}

.btn-footer-save {
  padding: 7px 20px;
  background: var(--accent);
  color: white;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 500;
}
.btn-footer-save:hover {
  background: var(--accent-hover);
}

/* ── Generate params ── */
.setting-row-col {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  min-height: unset;
  padding-bottom: 8px;
}
.setting-row-col .setting-info {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.param-val {
  color: var(--accent);
  font-weight: 600;
  font-size: 13px;
}
.param-slider {
  width: 100%;
  accent-color: var(--accent);
}
.btn-param-reset {
  font-size: 11px;
  color: var(--text-muted);
  background: none;
  padding: 4px 10px;
  border-radius: 5px;
  border: 1px solid var(--border-color);
  transition: all 0.15s;
}
.btn-param-reset:hover {
  color: var(--text-primary);
  border-color: var(--text-secondary);
}

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
.btn-template-cancel:hover {
  background: var(--border-color);
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
</style>
