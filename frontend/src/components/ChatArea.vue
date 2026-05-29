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
          <div v-if="exportOpen" class="export-dropdown">
            <div class="export-option" @click="doExport('md')">导出为 Markdown</div>
            <div class="export-option" @click="doExport('txt')">导出为 TXT</div>
          </div>
        </div>
        <!-- Model picker -->
        <div class="model-picker">
          <button class="model-btn" @click="open = !open">
            <span class="model-label">{{ model === 'qwen' ? 'Qwen' : 'DeepSeek' }}</span>
            <svg class="model-chevron" :class="{ open }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
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
        </div>
      </div>
      <div v-if="open || exportOpen" class="top-bar-overlay" @click="open = false; exportOpen = false"></div>
    </div>

    <!-- System prompt modal -->
    <div v-if="promptModalOpen" class="modal-overlay" @click.self="promptModalOpen = false">
      <div class="modal-box">
        <div class="modal-title">自定义系统提示词</div>
        <p class="modal-hint">留空则使用默认提示词（小凯角色）</p>
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
        :disabled="loading"
        :loading="loading"
        @send="(msg, imgs, ws) => $emit('send', msg, imgs, ws)"
        @stop="$emit('stop')"
      />
    </template>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import WelcomeScreen from './WelcomeScreen.vue'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'

const props = defineProps({
  conversation: Object,
  loading: Boolean,
  conversationLoading: Boolean,
  model: String
})

const emit = defineEmits(['send', 'stop', 'regenerate', 'editMessage', 'deleteMessage', 'update:model', 'updateSystemPrompt'])

const open = ref(false)
const exportOpen = ref(false)
const promptModalOpen = ref(false)
const promptDraft = ref('')

function select(m) {
  emit('update:model', m)
  open.value = false
}

function openPromptModal() {
  promptDraft.value = props.conversation?.systemPrompt || ''
  promptModalOpen.value = true
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
</style>
