<template>
  <main class="chat-area">
    <div class="chat-top-bar">
      <slot name="hamburger"></slot>
      <div class="top-bar-right">
        <!-- System prompt button -->
        <button v-if="conversation" class="btn-icon-sm" title="系统提示词" @click="openPromptModal">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.07 4.93a10 10 0 010 14.14M4.93 4.93a10 10 0 000 14.14"/>
          </svg>
        </button>
        <!-- Export dropdown -->
        <div v-if="conversation" class="export-wrapper">
          <button class="btn-icon-sm" title="导出对话" @click="exportOpen = !exportOpen">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
          </button>
          <Transition name="drop">
            <div v-if="exportOpen" class="export-dropdown">
              <div class="export-option" @click="doExport('md')">导出为 Markdown</div>
              <div class="export-option" @click="doExport('txt')">导出为 TXT</div>
            </div>
          </Transition>
        </div>
        <!-- Share button + popup -->
        <div v-if="conversation" class="share-wrapper">
          <button
            class="btn-icon-sm"
            :class="{ 'btn-shared-active': sharePopOpen }"
            title="分享对话"
            @click="doShare"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="18" cy="5" r="3"/>
              <circle cx="6" cy="12" r="3"/>
              <circle cx="18" cy="19" r="3"/>
              <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
              <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
            </svg>
          </button>
          <Transition name="drop">
            <div v-if="sharePopOpen" class="share-popup">
              <div class="share-popup-title">分享链接已生成</div>
              <div class="share-url-row">
                <input ref="shareUrlInput" class="share-url-input" :value="shareUrl" readonly @focus="$event.target.select()" />
                <button class="btn-copy-url" @click="copyShareUrl">复制</button>
              </div>
              <div v-if="lanUrl" class="share-lan-hint">
                局域网访问：<code>{{ lanUrl }}</code>
                <button class="btn-copy-lan" @click="copyLanUrl">复制</button>
              </div>
              <div class="share-popup-actions">
                <button class="btn-revoke-share" @click="doRevokeShare">取消分享</button>
              </div>
            </div>
          </Transition>
        </div>
        <!-- Model picker -->
        <div class="model-picker">
          <button class="model-btn" @click="open = !open">
            <span class="model-label">{{ model === 'qwen' ? 'Qwen' : 'DeepSeek' }}</span>
            <svg class="model-chevron" :class="{ open }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <Transition name="drop">
            <div v-if="open" class="model-dropdown">
            <div class="model-option" :class="{ active: model === 'deepseek' }" @click="select('deepseek')">
              <div class="model-option-name">DeepSeek</div>
              <div class="model-option-desc">DeepSeek Chat</div>
            </div>
            <div class="model-option" :class="{ active: model === 'qwen' }" @click="select('qwen')">
              <div class="model-option-name">Qwen</div>
              <div class="model-option-desc">通义千问 Plus</div>
            </div>
          </div>
          </Transition>
        </div>
      </div>
      <div v-if="open || exportOpen || sharePopOpen" class="top-bar-overlay" @click="open = false; exportOpen = false; closeShare()"></div>
    </div>

    <!-- System prompt modal -->
    <div v-if="promptModalOpen" class="modal-overlay" @click.self="promptModalOpen = false">
      <div class="modal-box">
        <div class="modal-title">自定义系统提示词</div>
        <p class="modal-hint">留空则使用默认提示词（小凯角色）</p>
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
        <textarea v-model="promptDraft" class="prompt-textarea" rows="8" placeholder="在此输入系统提示词..."></textarea>
        <div class="modal-actions">
          <button class="btn-modal-cancel" @click="promptModalOpen = false">取消</button>
          <button class="btn-modal-save" @click="savePrompt">保存</button>
        </div>
      </div>
    </div>

    <WelcomeScreen v-if="!conversation" @send="$emit('send', $event)" />
    <template v-else>
      <MessageList
        :messages="conversation.messages || []"
        :loading="loading"
        :conversationLoading="conversationLoading"
        @regenerate="$emit('regenerate')"
        @editMessage="(id, content) => $emit('editMessage', id, content)"
        @deleteMessage="id => $emit('deleteMessage', id)"
      />
      <ChatInput
        ref="chatInputRef"
        :disabled="loading"
        :loading="loading"
        :enterToSend="enterToSend"
        :voiceLang="voiceLang"
        @send="(msg, imgs, ws) => $emit('send', msg, imgs, ws)"
        @stop="$emit('stop')"
      />
    </template>
  </main>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import WelcomeScreen from './WelcomeScreen.vue'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import * as api from '../api/index.js'

const chatInputRef = ref(null)
function focusInput() {
  chatInputRef.value?.focus()
}
defineExpose({ focusInput })

const props = defineProps({
  conversation: Object,
  loading: Boolean,
  conversationLoading: Boolean,
  model: String,
  enterToSend: { type: Boolean, default: true },
  voiceLang: { type: String, default: 'zh-CN' }
})

const emit = defineEmits(['send', 'stop', 'regenerate', 'editMessage', 'deleteMessage', 'update:model', 'updateSystemPrompt'])

const open = ref(false)
const exportOpen = ref(false)
// shareToken is a temporary ref — set briefly when sharing to show the popup URL,
// cleared when popup closes so the button returns to gray.
const shareToken = ref(null)
const sharePopOpen = ref(false)
const shareUrlInput = ref(null)

const shareUrl = computed(() => {
  if (!shareToken.value) return ''
  return window.location.origin + '/share/' + shareToken.value
})

// Detect LAN IP for sharing hint (when on localhost)
const lanUrl = ref('')
function detectLanIp() {
  // Only show LAN hint if user is on localhost
  if (!window.location.hostname.includes('localhost') && window.location.hostname !== '127.0.0.1') return
  // Try WebRTC to get LAN IP (works in Chrome/Firefox)
  try {
    const pc = new RTCPeerConnection({ iceServers: [] })
    pc.createDataChannel('')
    pc.createOffer().then(offer => pc.setLocalDescription(offer))
    pc.onicecandidate = (e) => {
      if (!e.candidate) return
      const ip = e.candidate.candidate.match(/(\d+\.\d+\.\d+\.\d+)/)?.[1]
      if (ip && !ip.startsWith('127.') && ip.split('.')[0] !== '0') {
        lanUrl.value = window.location.protocol + '//' + ip + ':' + window.location.port + '/share/' + shareToken.value
      }
      pc.close()
    }
  } catch { /* WebRTC not supported */ }
}

function copyLanUrl() {
  navigator.clipboard.writeText(lanUrl.value)
}

// Trigger LAN detection after share
watch(shareToken, (token) => {
  if (token) {
    setTimeout(detectLanIp, 500)
  } else {
    lanUrl.value = ''
  }
})

// Close share popup when switching conversations
watch(() => props.conversation, () => {
  closeShare()
})

async function doShare() {
  if (!props.conversation) return
  try {
    const result = await api.shareConversation(props.conversation.id)
    shareToken.value = result.shareToken
    sharePopOpen.value = true
  } catch (e) {
    console.error('Share failed:', e)
  }
}

function closeShare() {
  sharePopOpen.value = false
  shareToken.value = null
}

async function doRevokeShare() {
  if (!props.conversation) return
  try {
    await api.revokeShare(props.conversation.id)
    closeShare()
  } catch (e) {
    console.error('Revoke share failed:', e)
  }
}

function copyShareUrl() {
  if (shareUrlInput.value) {
    shareUrlInput.value.select()
    navigator.clipboard.writeText(shareUrl.value)
  }
}

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

function select(m) {
  emit('update:model', m)
  open.value = false
}

function openPromptModal() {
  promptDraft.value = props.conversation?.systemPrompt || ''
  loadTemplates()
  promptModalOpen.value = true
}

function onTemplateSelect(e) {
  if (e.target.value) {
    promptDraft.value = e.target.value
    e.target.value = ''
  }
}

function savePrompt() {
  emit('updateSystemPrompt', props.conversation.id, promptDraft.value)
  promptModalOpen.value = false
}

function doExport(format) {
  exportOpen.value = false
  const conv = props.conversation
  if (!conv) return
  const msgs = conv.messages || []

  let content = ''
  if (format === 'md') {
    content = `# ${conv.title}\n\n`
    for (const m of msgs) {
      const role = m.role === 'user' ? '**You**' : '**Ayer**'
      content += `${role}\n\n${m.content}\n\n---\n\n`
    }
  } else {
    content = `${conv.title}\n${'='.repeat(40)}\n\n`
    for (const m of msgs) {
      const role = m.role === 'user' ? 'You' : 'Ayer'
      content += `[${role}]\n${m.content}\n\n`
    }
  }

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${conv.title}.${format === 'md' ? 'md' : 'txt'}`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.chat-top-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--border-color);
  position: relative;
  z-index: 10;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.top-bar-overlay {
  position: fixed;
  inset: 0;
  z-index: 5;
}

/* ── Model Picker ── */
.model-picker {
  position: relative;
}

.model-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px 5px 12px;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-family: inherit;
  transition: all 0.15s;
}
.model-btn:hover {
  border-color: var(--accent);
  color: var(--text-primary);
}

.model-label {
  font-weight: 500;
}

.model-chevron {
  color: var(--text-muted);
  transition: transform 0.15s;
}
.model-chevron.open {
  transform: rotate(180deg);
}

.model-dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  min-width: 200px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  z-index: 20;
}

.model-option {
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.1s;
}
.model-option:hover {
  background: var(--bg-hover);
}
.model-option.active {
  background: var(--bg-card);
}

.model-option-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.model-option-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.model-overlay {
  position: fixed;
  inset: 0;
  z-index: 5;
}

@media (max-width: 768px) {
  .chat-top-bar {
    padding: 6px 12px;
  }
}

/* ── Template selector in prompt modal ── */
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

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal-box {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
  width: 480px;
  max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.modal-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
}
.prompt-textarea {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 12px;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  font-family: inherit;
  box-sizing: border-box;
}
.prompt-textarea:focus {
  outline: none;
  border-color: var(--accent);
}
.modal-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 14px;
}
.btn-modal-cancel {
  padding: 7px 16px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  border-radius: 7px;
  font-size: 13px;
}
.btn-modal-save {
  padding: 7px 16px;
  background: var(--accent);
  color: white;
  border-radius: 7px;
  font-size: 13px;
}
.btn-modal-save:hover { background: var(--accent-hover); }
.export-wrapper {
  position: relative;
}
.btn-icon-sm {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 7px;
  color: var(--text-secondary);
  transition: all 0.15s;
}
.btn-icon-sm:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.export-dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  overflow: hidden;
  min-width: 160px;
  z-index: 20;
}
.export-option {
  padding: 9px 14px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.1s;
}
.export-option:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* Dropdown transition */
.drop-enter-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.drop-leave-active { transition: opacity 0.1s ease, transform 0.1s ease; }
.drop-enter-from, .drop-leave-to { opacity: 0; transform: translateY(-4px) scale(0.97); }

/* Share */
.share-wrapper {
  position: relative;
}
.btn-shared-active {
  color: #22c55e !important;
  border-color: #22c55e !important;
}
.share-popup {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 14px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  min-width: 320px;
  z-index: 20;
}
.share-popup-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 10px;
}
.share-url-row {
  display: flex;
  gap: 6px;
}
.share-url-input {
  flex: 1;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 6px 10px;
  color: var(--text-primary);
  font-size: 12px;
  font-family: var(--font-mono);
  cursor: text;
}
.share-url-input:focus {
  border-color: var(--accent);
  outline: none;
}
.btn-copy-url {
  padding: 6px 12px;
  background: var(--accent);
  color: white;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.btn-copy-url:hover {
  background: var(--accent-hover);
}
.share-lan-hint {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.share-lan-hint code {
  font-family: var(--font-mono);
  font-size: 11px;
  background: var(--bg-card);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--text-secondary);
  word-break: break-all;
  flex: 1;
  min-width: 0;
}
.btn-copy-lan {
  padding: 3px 10px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  border-radius: 5px;
  font-size: 11px;
  white-space: nowrap;
}
.btn-copy-lan:hover {
  background: var(--border-color);
}
.share-popup-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: flex-end;
}
.btn-revoke-share {
  padding: 4px 12px;
  background: none;
  color: var(--text-muted);
  border-radius: 5px;
  font-size: 11px;
  transition: all 0.15s;
}
.btn-revoke-share:hover {
  color: var(--danger);
  background: rgba(224, 85, 106, 0.08);
}
</style>
