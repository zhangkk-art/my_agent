<template>
  <div class="chat-input-container">
    <transition name="tip-fade">
      <div v-if="tipMsg" class="input-tip" :class="tipType">{{ tipMsg }}</div>
    </transition>
    <div v-if="images.length > 0" class="image-preview-bar">
      <div v-for="(img, i) in images" :key="i" class="preview-thumb">
        <img :src="img" alt="Preview" />
        <button class="btn-remove-img" @click="removeImage(i)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
    <!-- Prompt history dropdown -->
    <div v-if="historyOpen && promptHistory.length > 0" class="prompt-history-dropdown">
      <div class="history-header">最近使用</div>
      <div
        v-for="(h, i) in promptHistory"
        :key="i"
        class="history-item"
        @click="useHistory(h)"
      >{{ h.length > 80 ? h.substring(0, 80) + '…' : h }}</div>
    </div>
    <div class="chat-input-wrapper">
      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        class="file-input-hidden"
        @change="onFileChange"
      />
      <button class="btn-upload" @click="triggerUpload" title="Upload image">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <circle cx="8.5" cy="8.5" r="1.5"/>
          <polyline points="21 15 16 10 5 21"/>
        </svg>
      </button>
      <textarea
        ref="inputRef"
        v-model="text"
        class="chat-input"
        placeholder="Send a message..."
        :disabled="disabled"
        rows="1"
        @keydown="handleKeydown"
        @input="autoResize"
        @paste="onPaste"
      ></textarea>
      <!-- Prompt history toggle -->
      <button
        v-if="promptHistory.length > 0"
        class="btn-history"
        :class="{ active: historyOpen }"
        title="历史记录"
        @click="historyOpen = !historyOpen"
        type="button"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 3h6l1 9H2L3 3z"/><path d="M13 3h6l2 9H12L13 3z"/>
          <path d="M2 12h20v2a2 2 0 01-2 2H4a2 2 0 01-2-2v-2z"/>
          <path d="M8 20h8M12 16v4"/>
        </svg>
      </button>
      <!-- Voice input -->
      <button
        class="btn-voice"
        :class="{ recording: isRecording }"
        title="语音输入"
        @click="toggleVoice"
        type="button"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z"/>
          <path d="M19 10v2a7 7 0 01-14 0v-2M12 19v4M8 23h8"/>
        </svg>
      </button>
      <!-- Web search toggle -->
      <button
        class="btn-web-search"
        :class="{ active: webSearch }"
        title="联网搜索"
        @click="webSearch = !webSearch"
        type="button"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="2" y1="12" x2="22" y2="12"/>
          <path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/>
        </svg>
      </button>
      <button
        v-if="loading"
        class="btn-stop"
        @click="$emit('stop')"
        title="Stop generating"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <rect x="4" y="4" width="16" height="16" rx="2" />
        </svg>
      </button>
      <button
        v-else
        class="btn-send"
        :disabled="(!text.trim() && images.length === 0) || disabled"
        @click="send"
        title="Send"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="22" y1="2" x2="11" y2="13"/>
          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const props = defineProps({
  disabled: Boolean,
  loading: Boolean,
  enterToSend: { type: Boolean, default: true },
  voiceLang: { type: String, default: 'zh-CN' }
})

const emit = defineEmits(['send', 'stop'])

const text = ref('')
const inputRef = ref(null)
const fileInput = ref(null)
const images = ref([])
const isRecording = ref(false)
const webSearch = ref(false)
const tipMsg = ref('')
const tipType = ref('error')
const historyOpen = ref(false)
const promptHistory = ref(JSON.parse(localStorage.getItem('prompt-history') || '[]'))
const historyIndex = ref(-1)  // -1 = not navigating
let recognition = null
let tipTimer = null

function saveToHistory(msg) {
  if (!msg || msg.length < 3) return
  const list = [msg, ...promptHistory.value.filter(h => h !== msg)].slice(0, 10)
  promptHistory.value = list
  localStorage.setItem('prompt-history', JSON.stringify(list))
}

function useHistory(h) {
  text.value = h
  historyOpen.value = false
  nextTick(() => inputRef.value?.focus())
}

function showTip(msg, type = 'error', duration = 3000) {
  clearTimeout(tipTimer)
  tipMsg.value = msg
  tipType.value = type
  tipTimer = setTimeout(() => { tipMsg.value = '' }, duration)
}

function autoResize() {
  nextTick(() => {
    const el = inputRef.value
    if (el) {
      el.style.height = 'auto'
      el.style.height = Math.min(el.scrollHeight, 200) + 'px'
    }
  })
}

function handleKeydown(e) {
  if (e.key === 'Enter') {
    const shouldSend = props.enterToSend ? !e.shiftKey : (e.ctrlKey || e.metaKey)
    if (shouldSend) {
      e.preventDefault()
      send()
    }
    return
  }
  // ↑ when input is empty: cycle backward through prompt history
  if (e.key === 'ArrowUp' && !text.value.trim() && promptHistory.value.length > 0) {
    e.preventDefault()
    historyIndex.value = Math.min(historyIndex.value + 1, promptHistory.value.length - 1)
    text.value = promptHistory.value[historyIndex.value]
    nextTick(adjustHeight)
    return
  }
  // ↓ to cycle forward (or clear) when navigating history
  if (e.key === 'ArrowDown' && historyIndex.value >= 0) {
    e.preventDefault()
    historyIndex.value--
    text.value = historyIndex.value >= 0 ? promptHistory.value[historyIndex.value] : ''
    nextTick(adjustHeight)
    return
  }
  // Any other key resets history navigation index
  if (!e.ctrlKey && !e.metaKey && !e.altKey) {
    historyIndex.value = -1
  }
}

function triggerUpload() {
  fileInput.value?.click()
}

const MAX_IMAGE_SIZE = 5 * 1024 * 1024 // 5MB

function addImageFile(file) {
  if (!file.type.startsWith('image/')) {
    showTip('请选择图片文件')
    return
  }
  if (file.size > MAX_IMAGE_SIZE) {
    showTip(`图片大小不能超过 5MB（当前: ${(file.size / 1024 / 1024).toFixed(1)}MB）`)
    return
  }
  if (images.value.length >= 4) {
    showTip('最多只能上传 4 张图片')
    return
  }
  const reader = new FileReader()
  reader.onload = () => { images.value.push(reader.result) }
  reader.onerror = () => { showTip('读取文件失败，请重试') }
  reader.readAsDataURL(file)
}

function onFileChange(e) {
  const file = e.target.files[0]
  if (file) addImageFile(file)
  fileInput.value.value = ''
}

function onPaste(e) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      e.preventDefault()
      addImageFile(item.getAsFile())
      break
    }
  }
}

function removeImage(i) {
  images.value.splice(i, 1)
}

function toggleVoice() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) {
    showTip('当前浏览器不支持语音输入（推荐使用 Chrome）')
    return
  }
  if (isRecording.value) {
    recognition?.stop()
    return
  }
  recognition = new SR()
  recognition.lang = props.voiceLang
  recognition.continuous = false
  recognition.interimResults = true
  recognition.onresult = (e) => {
    const interim = Array.from(e.results)
      .map(r => r[0].transcript)
      .join('')
    if (e.results[e.results.length - 1].isFinal) {
      text.value += interim
      autoResize()
    }
  }
  recognition.onend = () => {
    isRecording.value = false
    if (tipType.value === 'info') tipMsg.value = ''
  }
  recognition.onerror = (e) => {
    isRecording.value = false
    const msgs = {
      'not-allowed': '麦克风权限被拒绝，请在浏览器设置中允许麦克风访问',
      'no-speech': '未检测到语音，请重试',
      'network': '语音识别网络错误，请检查网络连接',
    }
    showTip(msgs[e.error] || `语音识别失败：${e.error}`)
  }
  try {
    recognition.start()
    isRecording.value = true
    showTip('正在录音，点击麦克风停止...', 'info', 30000)
  } catch (e) {
    showTip('无法启动语音识别，请检查麦克风权限')
  }
}

function send() {
  const msg = text.value.trim()
  if ((!msg && images.value.length === 0) || props.disabled) return
  saveToHistory(msg)
  emit('send', msg, [...images.value], webSearch.value)
  historyOpen.value = false
  historyIndex.value = -1
  text.value = ''
  images.value = []
  nextTick(() => {
    const el = inputRef.value
    if (el) el.style.height = 'auto'
  })
}

defineExpose({ focus: () => inputRef.value?.focus() })
</script>

<style scoped>
.chat-input-container {
  padding: 16px 24px 24px;
}

.input-tip {
  max-width: 800px;
  margin: 0 auto 8px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.4;
}
.input-tip.error {
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  color: var(--danger);
  border: 1px solid color-mix(in srgb, var(--danger) 30%, transparent);
}
.input-tip.info {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  border: 1px solid color-mix(in srgb, var(--accent) 30%, transparent);
}

.tip-fade-enter-active, .tip-fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.tip-fade-enter-from, .tip-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* Image preview bar */
.image-preview-bar {
  max-width: 800px;
  margin: 0 auto 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.preview-thumb {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.preview-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.btn-remove-img {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  color: white;
  padding: 0;
}
.btn-remove-img:hover {
  background: rgba(0, 0, 0, 0.7);
}

/* File input hidden */
.file-input-hidden {
  display: none;
}

/* Upload button */
.btn-upload {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 8px;
  transition: all 0.15s;
}
.btn-upload:hover {
  background: var(--bg-hover);
  color: var(--accent);
}

@media (max-width: 768px) {
  .chat-input-container {
    padding: 12px 12px 16px;
  }
}

.chat-input-wrapper {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  gap: 8px;
  align-items: flex-end;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 8px 12px;
  transition: border-color 0.15s;
}
.chat-input-wrapper:focus-within {
  border-color: var(--accent);
}

.chat-input {
  flex: 1;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: var(--base-font-size, 14px);
  line-height: 1.5;
  resize: none;
  max-height: 200px;
  padding: 4px 0;
}
.chat-input::placeholder {
  color: var(--text-muted);
}
.chat-input:disabled {
  opacity: 0.5;
}

.btn-send {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent);
  border-radius: 8px;
  color: white;
  transition: background 0.15s;
}
.btn-send:hover:not(:disabled) {
  background: var(--accent-hover);
}
.btn-send:disabled {
  opacity: 1;
  cursor: not-allowed;
  background: var(--bg-hover);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.btn-voice {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 7px;
  transition: all 0.15s;
}
.btn-voice:hover { background: var(--bg-hover); color: var(--text-primary); }
.btn-voice.recording { color: var(--danger); animation: pulse 1s infinite; }

.btn-web-search {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--text-muted);
  border-radius: 7px;
  transition: all 0.15s;
}
.btn-web-search:hover { background: var(--bg-hover); color: var(--text-primary); }
.btn-web-search.active { color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.btn-history {
  flex-shrink: 0; width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  background: none; color: var(--text-muted); border-radius: 7px; transition: all 0.15s;
}
.btn-history:hover { background: var(--bg-hover); color: var(--text-primary); }
.btn-history.active { color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }

.prompt-history-dropdown {
  max-width: 800px; margin: 0 auto 6px;
  background: var(--bg-primary); border: 1px solid var(--border-color);
  border-radius: 10px; box-shadow: 0 4px 16px rgba(0,0,0,0.1); overflow: hidden;
}
.history-header {
  padding: 8px 14px 4px; font-size: 11px; font-weight: 600;
  color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px;
}
.history-item {
  padding: 8px 14px; font-size: 13px; color: var(--text-secondary);
  cursor: pointer; transition: background 0.1s; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis;
}
.history-item:hover { background: var(--bg-hover); color: var(--text-primary); }

.btn-stop {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--danger);
  border-radius: 8px;
  color: white;
  transition: background 0.15s;
}
.btn-stop:hover {
  background: #d32f2f;
}
</style>
