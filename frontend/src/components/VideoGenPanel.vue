<template>
  <div class="video-gen-panel">
    <div class="vgp-header">
      <div class="vgp-title">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="23 7 16 12 23 17 23 7"/>
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
        </svg>
        <span>视频生成 (即梦3.0 Pro)</span>
      </div>
      <button class="btn-icon-sm" title="返回聊天" @click="$emit('close')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>

    <div class="vgp-body">
      <!-- Task list -->
      <div v-if="tasks.length > 0" class="vgp-task-list">
        <div class="vgp-section-title task-list-header" @click="tasksCollapsed = !tasksCollapsed">
          <span>我的任务 ({{ tasks.length }})</span>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            class="collapse-chevron" :class="{ collapsed: tasksCollapsed }">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </div>
        <template v-if="!tasksCollapsed">
          <div
            v-for="task in tasks"
            :key="task.id"
            class="vgp-task-item"
          >
          <div class="task-status-icon">
            <template v-if="isPending(task.status)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
              </svg>
            </template>
            <template v-else-if="task.status === 'SUCCEEDED'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--success)" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </template>
            <template v-else-if="task.status === 'FAILED'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--danger)" stroke-width="2">
                <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
            </template>
          </div>
          <div class="task-info">
            <div class="task-prompt">{{ task.prompt.substring(0, 50) }}{{ task.prompt.length > 50 ? '...' : '' }}</div>
            <div class="task-meta">
              <span :class="'task-status-text status-' + task.status.toLowerCase()">{{ statusLabel(task.status) }}</span>
              <span v-if="task.errorMessage" class="task-error">{{ task.errorMessage }}</span>
            </div>
          </div>
          <div class="task-actions">
            <button
              v-if="task.status === 'SUCCEEDED'"
              class="btn-play"
              @click="$emit('play', task)"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                <polygon points="8 5 19 12 8 19 8 5"/>
              </svg>
              播放
            </button>
            <button
              class="btn-delete-task"
              title="删除"
              @click="$emit('deleteTask', task.id)"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
              </svg>
            </button>
          </div>
        </div>
        </template>
      </div>

      <!-- Submit form -->
      <div class="vgp-form">
        <div class="vgp-section-title">新建视频</div>

        <div class="form-group">
          <label class="form-label">提示词</label>
          <textarea
            v-model="prompt"
            class="form-textarea"
            rows="4"
            placeholder="描述你想要的视频内容，支持中英文..."
          ></textarea>
        </div>

        <!-- Prompt toolbar -->
        <div class="prompt-toolbar">
          <div class="toolbar-left">
            <button class="btn-tool" :class="{ active: showTemplates }" title="模板" @click="toggleTemplates">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/>
              </svg>
              模板
            </button>
            <button class="btn-tool" :disabled="enhancing || !prompt.trim()" title="AI增强" @click="enhancePrompt">
              <svg v-if="!enhancing" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
              </svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
              </svg>
              {{ enhancing ? '增强中...' : '增强' }}
            </button>
            <div class="translate-wrapper">
              <button class="btn-tool" :disabled="translating || !prompt.trim()" title="翻译" @click="toggleTranslateMenu">
                <svg v-if="!translating" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 8l6 6"/><path d="M4 14l6-6 2-3"/><path d="M2 5h12"/><path d="M7 2h1"/><path d="M22 22l-5-10-5 10"/><path d="M14 18h6"/>
                </svg>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                  <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
                </svg>
                翻译
              </button>
              <div v-if="showTranslateMenu" class="translate-menu">
                <button @click="doTranslate('zh')">译为中文</button>
                <button @click="doTranslate('en')">译为英文</button>
                <button @click="doTranslate('auto')">统一润色</button>
              </div>
            </div>
          </div>
          <span class="char-count">{{ prompt.length }}/500</span>
        </div>

        <!-- Template selector -->
        <div v-if="showTemplates" class="template-selector">
          <div class="template-chips">
            <button v-for="t in templates" :key="t.id" class="chip-template" :class="{ preset: t.isPreset }" @click="selectTemplate(t)">{{ t.name }}</button>
            <div class="template-add-row">
              <input v-model="newTemplateName" class="input-new-template" placeholder="新模板名称" />
              <button class="btn-save-template" :disabled="!newTemplateName.trim() || !prompt.trim()" @click="saveCurrentAsTemplate">保存当前</button>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">首帧图片 (可选)</label>
          <div v-if="firstFramePreview" class="first-frame-preview">
            <img :src="firstFramePreview" alt="First frame" />
            <button class="btn-remove-img" @click="removeFirstFrame">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <button v-else class="btn-upload-img" @click="triggerFileInput">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21 15 16 10 5 21"/>
            </svg>
            上传图片
          </button>
          <input ref="fileInput" type="file" accept="image/*" class="file-hidden" @change="onFileChange" />
        </div>

        <div class="form-group">
          <button class="btn-toggle-advanced" @click="showAdvanced = !showAdvanced">
            高级设置
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              :class="{ rotated: showAdvanced }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
        </div>

        <div v-if="showAdvanced" class="advanced-settings">
          <div class="form-row">
            <label class="form-label">时长</label>
            <div class="duration-input-row">
              <button
                :class="{ active: duration === 5 }"
                class="btn-option"
                @click="duration = 5"
              >5秒</button>
              <button
                :class="{ active: duration === 10 }"
                class="btn-option"
                @click="duration = 10"
              >10秒</button>
              <button
                :class="{ active: duration === 12 }"
                class="btn-option"
                @click="duration = 12"
              >12秒</button>
              <div class="duration-custom">
                <input
                  type="number"
                  class="duration-input"
                  :value="duration"
                  min="4"
                  max="12"
                  step="1"
                  @input="onDurationInput"
                  @focus="onDurationFocus"
                />
                <span class="duration-unit">秒</span>
              </div>
            </div>
          </div>

          <div class="form-row">
            <label class="form-label">比例</label>
            <div class="btn-group">
              <button
                v-for="r in ratios"
                :key="r"
                :class="{ active: aspectRatio === r }"
                class="btn-option"
                @click="aspectRatio = r"
              >{{ r }}</button>
            </div>
          </div>

          <div class="form-row">
            <label class="form-label">嵌入字幕</label>
            <label class="toggle-switch">
              <input type="checkbox" v-model="subtitleEnabled" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div class="form-row">
            <label class="form-label">生成音频</label>
            <label class="toggle-switch">
              <input type="checkbox" v-model="generateAudio" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div class="form-row">
            <label class="form-label">字幕配音</label>
            <label class="toggle-switch">
              <input type="checkbox" v-model="narrateSubtitles" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div class="form-row">
            <label class="form-label">自定义字幕</label>
            <label class="toggle-switch">
              <input type="checkbox" v-model="customSubtitlesEnabled" />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div v-if="customSubtitlesEnabled" class="custom-subtitles-editor">
            <div class="subtitle-entries">
              <div v-for="(entry, i) in customSubtitleEntries" :key="i" class="subtitle-entry">
                <div class="subtitle-entry-header">
                  <span class="entry-index">#{{ i + 1 }}</span>
                  <button class="btn-remove-entry" @click="removeSubtitleEntry(i)" title="删除">✕</button>
                </div>
                <div class="entry-time-row">
                  <input type="number" v-model.number="entry.startSec" class="entry-time" min="0" :max="duration" step="0.5" placeholder="0.0" />
                  <span class="time-sep">→</span>
                  <input type="number" v-model.number="entry.endSec" class="entry-time" min="0" :max="duration" step="0.5" placeholder="2.0" />
                  <span class="time-unit">秒</span>
                </div>
                <input type="text" v-model="entry.text" class="entry-text" placeholder="输入字幕文字..." />
              </div>
            </div>
            <button class="btn-add-subtitle" @click="addSubtitleEntry">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              添加字幕
            </button>
          </div>

          <div class="negative-prompt-section">
            <label class="form-label">排除内容（反向提示词）</label>
            <div class="negative-tags">
              <button v-for="tag in negativeTagOptions" :key="tag" class="tag-negative" :class="{ selected: selectedNegativeTags.includes(tag) }" @click="toggleNegativeTag(tag)">{{ tag }}</button>
            </div>
            <input type="text" v-model="customNegative" class="input-custom-negative" placeholder="输入想排除的内容，逗号分隔..." />
          </div>
        </div>

        <button
          class="btn-submit"
          :disabled="!prompt.trim() || submitting"
          @click="submitTask"
        >
          <svg v-if="submitting" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
            <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="23 7 16 12 23 17 23 7"/>
            <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
          </svg>
          {{ submitting ? '提交中...' : '开始生成' }}
        </button>
      </div>
    </div>
    <!-- Translate confirmation modal -->
    <div v-if="showTranslateModal" class="modal-overlay" @click.self="showTranslateModal = false">
      <div class="translate-modal">
        <div class="translate-modal-header">
          <span>翻译结果</span>
          <button class="btn-icon-sm" @click="showTranslateModal = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="translate-columns">
          <div class="translate-col">
            <div class="translate-col-label">原文</div>
            <div class="translate-col-text">{{ translateOriginal }}</div>
          </div>
          <div class="translate-col">
            <div class="translate-col-label">译文</div>
            <div class="translate-col-text">{{ translateResult }}</div>
          </div>
        </div>
        <div class="translate-actions">
          <button class="btn-cancel" @click="showTranslateModal = false">取消</button>
          <button class="btn-replace" @click="applyTranslation">替换</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import * as api from '../api/index.js'

const props = defineProps({
  conversationId: { type: String, default: null }
})

const emit = defineEmits(['close', 'play', 'toast', 'videoSubmitted'])

const prompt = ref('')
const duration = ref(5)
const aspectRatio = ref('16:9')
const subtitleEnabled = ref(false)
const generateAudio = ref(true)
const narrateSubtitles = ref(false)
const tasksCollapsed = ref(true)
const customSubtitlesEnabled = ref(false)
const customSubtitleEntries = ref([])  // [{startSec, endSec, text}]
const showAdvanced = ref(true)
const submitting = ref(false)
const tasks = ref([])
const firstFramePreview = ref(null)
const firstFrameBase64 = ref(null)
const fileInput = ref(null)

const ratios = ['16:9', '9:16', '1:1']

// ── Prompt engineering state ──
const enhancing = ref(false)
const translating = ref(false)
const showTemplates = ref(false)
const showTranslateMenu = ref(false)
const showTranslateModal = ref(false)
const translateOriginal = ref('')
const translateResult = ref('')
const targetLanguage = ref('auto')
const templates = ref([])
const newTemplateName = ref('')

// Negative prompt state
const negativeTagOptions = ['模糊', '畸变', '多余手指', '文字水印', '低画质', '画面撕裂', '闪烁']
const selectedNegativeTags = ref([])
const customNegative = ref('')

const negativePrompt = computed(() => {
  const parts = [...selectedNegativeTags.value]
  if (customNegative.value.trim()) {
    parts.push(...customNegative.value.split(/[,，]/).map(s => s.trim()).filter(Boolean))
  }
  return parts.join(', ')
})

let pollTimer = null

function onDurationInput(e) {
  const val = parseFloat(e.target.value)
  if (!isNaN(val) && val > 0) {
    duration.value = Math.min(val, 12)
  }
}

function onDurationFocus(e) {
  // Select all text on focus for easy editing
  e.target.select()
}

onMounted(async () => {
  await loadTasks()
  loadTemplates()
  startPolling()
})

async function loadTasks() {
  try {
    if (props.conversationId) {
      tasks.value = await api.getConversationVideoTasks(props.conversationId)
    } else {
      tasks.value = await api.getVideoGenTasks()
    }
  } catch (e) {
    console.warn('Failed to load video tasks', e)
  }
}

onUnmounted(() => {
  stopPolling()
})

function isPending(status) {
  return status === 'PENDING' || status === 'SUBMITTED' || status === 'PROCESSING'
}

function statusLabel(status) {
  const map = {
    PENDING: '等待中',
    SUBMITTED: '已提交',
    PROCESSING: '生成中',
    SUCCEEDED: '完成',
    FAILED: '失败'
  }
  return map[status] || status
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    const pendingTasks = tasks.value.filter(t => isPending(t.status))
    if (pendingTasks.length === 0) return

    for (const task of pendingTasks) {
      try {
        const updated = await api.getVideoGenTask(task.id)
        const idx = tasks.value.findIndex(t => t.id === task.id)
        if (idx >= 0) {
          tasks.value.splice(idx, 1, updated)
        }
      } catch (e) {
        console.warn('Poll failed for task', task.id, e)
      }
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function submitTask() {
  if (!prompt.value.trim() || submitting.value) return
  submitting.value = true
  try {
    const task = await api.submitVideoGen({
      prompt: prompt.value,
      duration: duration.value,
      aspectRatio: aspectRatio.value,
      seed: -1,
      firstFrameBase64: firstFrameBase64.value,
      conversationId: props.conversationId,
      subtitleEnabled: subtitleEnabled.value,
      generateAudio: generateAudio.value,
      narrateSubtitles: narrateSubtitles.value,
      customSubtitles: customSubtitlesEnabled.value ? customSubtitleEntries.value : null,
      negativePrompt: negativePrompt.value || null
    })
    const fullTask = {
      ...task,
      prompt: prompt.value,
      duration: duration.value,
      aspectRatio: aspectRatio.value,
      status: task.status || 'SUBMITTED',
      createdAt: task.createdAt || new Date().toISOString()
    }
    tasks.value.unshift(fullTask)
    prompt.value = ''
    firstFramePreview.value = null
    firstFrameBase64.value = null
    selectedNegativeTags.value = []
    customNegative.value = ''
    emit('videoSubmitted', fullTask)
    emit('toast', { message: '视频任务已提交', type: 'success' })
  } catch (e) {
    emit('toast', { message: '提交失败: ' + e.message, type: 'error' })
  } finally {
    submitting.value = false
  }
}

function triggerFileInput() {
  fileInput.value?.click()
}

function onFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    emit('toast', { message: '图片大小不能超过5MB', type: 'error' })
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    firstFramePreview.value = reader.result
    // Extract base64 without data:image/... prefix
    firstFrameBase64.value = reader.result.split(',')[1]
  }
  reader.readAsDataURL(file)
  fileInput.value.value = ''
}

function removeFirstFrame() {
  firstFramePreview.value = null
  firstFrameBase64.value = null
}

function addSubtitleEntry() {
  const last = customSubtitleEntries.value[customSubtitleEntries.value.length - 1]
  const startSec = last ? last.endSec : 0
  const endSec = Math.min(startSec + 2, duration.value)
  customSubtitleEntries.value.push({ startSec, endSec, text: '' })
}

function removeSubtitleEntry(index) {
  customSubtitleEntries.value.splice(index, 1)
}

// ── Prompt engineering methods ──

async function loadTemplates() {
  try {
    templates.value = await api.getVideoPromptTemplates()
  } catch (e) {
    console.warn('Failed to load video prompt templates', e)
  }
}

function toggleTemplates() { showTemplates.value = !showTemplates.value }

function selectTemplate(t) {
  prompt.value = t.content
  showTemplates.value = false
  emit('toast', { message: '已选择模板: ' + t.name, type: 'info' })
}

async function saveCurrentAsTemplate() {
  const name = newTemplateName.value.trim()
  if (!name || !prompt.value.trim()) return
  try {
    const t = await api.createVideoPromptTemplate({ name, content: prompt.value, category: 'custom' })
    templates.value.push(t)
    newTemplateName.value = ''
    emit('toast', { message: '模板已保存', type: 'success' })
  } catch (e) {
    emit('toast', { message: '保存模板失败: ' + e.message, type: 'error' })
  }
}

async function enhancePrompt() {
  if (!prompt.value.trim() || enhancing.value) return
  enhancing.value = true
  try {
    const result = await api.enhanceVideoPrompt(prompt.value)
    if (result.enhanced) prompt.value = result.enhanced
    if (result.suggestedNegative) {
      const suggestions = result.suggestedNegative.split(/[,，、]/).map(s => s.trim()).filter(Boolean)
      for (const s of suggestions) {
        const matched = negativeTagOptions.find(t => t.includes(s) || s.includes(t))
        if (matched && !selectedNegativeTags.value.includes(matched)) selectedNegativeTags.value.push(matched)
      }
      const unmatched = suggestions.filter(s => !negativeTagOptions.some(t => t.includes(s) || s.includes(t)))
      if (unmatched.length > 0) {
        const existing = customNegative.value ? customNegative.value.split(/[,，]/).map(x => x.trim()) : []
        customNegative.value = [...new Set([...existing, ...unmatched])].join(', ')
      }
    }
    emit('toast', { message: '提示词已增强', type: 'success' })
  } catch (e) {
    emit('toast', { message: '增强失败: ' + e.message, type: 'error' })
  } finally { enhancing.value = false }
}

function toggleTranslateMenu() { showTranslateMenu.value = !showTranslateMenu.value }

async function doTranslate(target) {
  showTranslateMenu.value = false
  if (!prompt.value.trim() || translating.value) return
  translating.value = true
  targetLanguage.value = target
  translateOriginal.value = prompt.value
  try {
    const result = await api.translateVideoPrompt(prompt.value, target)
    translateResult.value = result.translated
    showTranslateModal.value = true
  } catch (e) {
    emit('toast', { message: '翻译失败: ' + e.message, type: 'error' })
  } finally { translating.value = false }
}

function applyTranslation() {
  prompt.value = translateResult.value
  showTranslateModal.value = false
  emit('toast', { message: '已替换为译文', type: 'success' })
}

function toggleNegativeTag(tag) {
  const idx = selectedNegativeTags.value.indexOf(tag)
  if (idx >= 0) selectedNegativeTags.value.splice(idx, 1)
  else selectedNegativeTags.value.push(tag)
}
</script>

<style scoped>
.video-gen-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.vgp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.vgp-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.vgp-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.vgp-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}

.task-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
  padding: 6px 0;
  border-radius: 4px;
  transition: color 0.15s;
}
.task-list-header:hover {
  color: var(--text-secondary);
}

.collapse-chevron {
  flex-shrink: 0;
  transition: transform 0.2s;
}
.collapse-chevron.collapsed {
  transform: rotate(-90deg);
}

/* Task list */
.vgp-task-list {
  margin-bottom: 24px;
}

.vgp-task-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 6px;
}

.task-status-icon {
  flex-shrink: 0;
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.spin {
  animation: spin 1.5s linear infinite;
}

@keyduration spin {
  to { transform: rotate(360deg); }
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-prompt {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-meta {
  font-size: 11px;
  margin-top: 2px;
}

.status-pending, .status-submitted, .status-processing {
  color: var(--accent);
}

.status-succeeded {
  color: var(--success);
}

.status-failed {
  color: var(--danger);
}

.task-error {
  color: var(--danger);
  margin-left: 6px;
}

.task-actions {
  flex-shrink: 0;
  display: flex;
  gap: 4px;
}

.btn-play {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  background: var(--success);
  color: white;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.btn-play:hover {
  opacity: 0.85;
}

.btn-delete-task {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 4px;
  padding: 0;
}
.btn-delete-task:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

/* Form */
.vgp-form {
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
}

.form-group {
  margin-bottom: 14px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.form-textarea {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
  line-height: 1.5;
}
.form-textarea::placeholder {
  color: var(--text-muted);
}
.form-textarea:focus {
  border-color: var(--accent);
  outline: none;
}

.btn-upload-img {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--bg-card);
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.btn-upload-img:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.first-frame-preview {
  position: relative;
  display: inline-block;
}
.first-frame-preview img {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}
.btn-remove-img {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--danger);
  color: white;
  border-radius: 50%;
  padding: 0;
}

.file-hidden {
  display: none;
}

.btn-toggle-advanced {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  color: var(--text-muted);
  font-size: 12px;
  padding: 0;
}
.btn-toggle-advanced:hover {
  color: var(--text-secondary);
}
.btn-toggle-advanced svg {
  transition: transform 0.15s;
}
.btn-toggle-advanced svg.rotated {
  transform: rotate(180deg);
}

.advanced-settings {
  padding: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 14px;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.form-row:last-child {
  margin-bottom: 0;
}

.btn-group {
  display: flex;
  gap: 4px;
}

.btn-option {
  padding: 5px 12px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}
.btn-option.active {
  background: var(--accent);
  border-color: var(--accent);
  color: white;
}

.duration-input-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.duration-custom {
  display: flex;
  align-items: center;
  gap: 2px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  padding: 0 6px;
  transition: border-color 0.15s;
}
.duration-custom:focus-within {
  border-color: var(--accent);
}

.duration-input {
  width: 48px;
  padding: 4px 2px;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 12px;
  font-family: inherit;
  text-align: center;
  outline: none;
  -moz-appearance: textfield;
}
.duration-input::-webkit-outer-spin-button,
.duration-input::-webkit-inner-spin-button {
  opacity: 1;
}

.duration-unit {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.btn-submit {
  width: 100%;
  padding: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  margin-top: 8px;
  transition: background 0.15s;
}
.btn-submit:hover:not(:disabled) {
  background: var(--accent-hover);
}
.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ── Custom subtitle editor ── */
.custom-subtitles-editor {
  padding: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 10px;
}

.subtitle-entries {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.subtitle-entry {
  background: var(--bg-hover);
  border-radius: 6px;
  padding: 8px 10px;
}

.subtitle-entry-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.entry-index {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
}

.btn-remove-entry {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 4px;
  font-size: 10px;
  padding: 0;
}
.btn-remove-entry:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

.entry-time-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.entry-time {
  width: 58px;
  padding: 4px 6px;
  background: var(--bg-input, var(--bg-card));
  border: 1px solid var(--border-color);
  border-radius: 5px;
  color: var(--text-primary);
  font-size: 12px;
  font-family: inherit;
  text-align: center;
  outline: none;
}
.entry-time:focus {
  border-color: var(--accent);
}

.time-sep {
  color: var(--text-muted);
  font-size: 12px;
}

.time-unit {
  font-size: 11px;
  color: var(--text-muted);
}

.entry-text {
  width: 100%;
  padding: 5px 8px;
  background: var(--bg-input, var(--bg-card));
  border: 1px solid var(--border-color);
  border-radius: 5px;
  color: var(--text-primary);
  font-size: 12px;
  font-family: inherit;
  outline: none;
  box-sizing: border-box;
}
.entry-text:focus {
  border-color: var(--accent);
}
.entry-text::placeholder {
  color: var(--text-muted);
}

.btn-add-subtitle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  background: var(--bg-hover);
  border: 1px dashed var(--border-color);
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}
.btn-add-subtitle:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.btn-icon-sm {
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 6px;
}
.btn-icon-sm:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* Toggle switch */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;
}
.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.toggle-slider {
  position: absolute;
  inset: 0;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  transition: all 0.2s;
}
.toggle-slider::before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  left: 2px;
  top: 2px;
  background: var(--text-muted);
  border-radius: 50%;
  transition: all 0.2s;
}
.toggle-switch input:checked + .toggle-slider {
  background: var(--accent);
  border-color: var(--accent);
}
.toggle-switch input:checked + .toggle-slider::before {
  background: white;
  transform: translateX(20px);
}

/* ── Prompt toolbar ── */
.prompt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  margin-bottom: 6px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 4px;
}
.btn-tool {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  transition: all 0.15s;
}
.btn-tool:hover:not(:disabled) {
  background: var(--bg-card);
  border-color: var(--accent);
  color: var(--accent);
}
.btn-tool.active {
  background: var(--accent);
  border-color: var(--accent);
  color: white;
}
.btn-tool:disabled { opacity: 0.4; cursor: not-allowed; }
.char-count {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}

/* ── Translate dropdown ── */
.translate-wrapper { position: relative; }
.translate-menu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  z-index: 20;
  min-width: 100px;
  overflow: hidden;
}
.translate-menu button {
  display: block;
  width: 100%;
  padding: 8px 14px;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.translate-menu button:hover { background: var(--bg-hover); }

/* ── Template selector ── */
.template-selector {
  margin-bottom: 12px;
  padding: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.template-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.chip-template {
  padding: 5px 12px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  color: var(--text-secondary);
  font-size: 12px;
  transition: all 0.15s;
}
.chip-template:hover { border-color: var(--accent); color: var(--accent); }
.chip-template.preset { border-style: solid; }
.template-add-row {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 8px;
  width: 100%;
}
.input-new-template {
  flex: 1;
  padding: 5px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
  min-width: 0;
}
.input-new-template:focus { border-color: var(--accent); }
.btn-save-template {
  padding: 5px 12px;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  flex-shrink: 0;
}
.btn-save-template:disabled { opacity: 0.4; cursor: not-allowed; }

/* ── Translate modal ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.translate-modal {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  width: 90%;
  max-width: 560px;
  max-height: 80vh;
  overflow-y: auto;
}
.translate-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
  font-size: 14px;
  font-weight: 600;
}
.translate-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 16px;
}
.translate-col-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 6px;
}
.translate-col-text {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
}
.translate-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
}
.btn-cancel {
  padding: 7px 16px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}
.btn-replace {
  padding: 7px 16px;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
}

/* ── Negative prompt ── */
.negative-prompt-section {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
}
.negative-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.tag-negative {
  padding: 4px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  color: var(--text-muted);
  font-size: 11px;
  transition: all 0.15s;
}
.tag-negative:hover { border-color: var(--danger); color: var(--danger); }
.tag-negative.selected {
  background: color-mix(in srgb, var(--danger) 15%, transparent);
  border-color: var(--danger);
  color: var(--danger);
}
.input-custom-negative {
  width: 100%;
  padding: 7px 10px;
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
}
.input-custom-negative:focus { border-color: var(--accent); }
.input-custom-negative::placeholder { color: var(--text-muted); }
</style>
