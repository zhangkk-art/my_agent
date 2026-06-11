<template>
  <!-- Video message: inline video generation task -->
  <div v-if="message.role === 'video'" class="message-row video-msg" :id="'msg-' + message.id">
    <div class="message-bubble video-bubble">
      <div class="message-header">
        <span class="message-role">🎬 视频生成</span>
        <span class="message-time" v-if="message.createdAt" :title="message.createdAt">{{ timeAgo(message.createdAt) }}</span>
        <div v-if="hovered" class="message-actions">
          <button class="btn-action" title="删除" @click="confirmingDelete = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
            </svg>
          </button>
          <template v-if="confirmingDelete">
            <span class="delete-confirm-label">删除?</span>
            <button class="btn-action btn-confirm-yes" title="确认删除" @click="doDelete">✓</button>
            <button class="btn-action" title="取消" @click="confirmingDelete = false">✕</button>
          </template>
        </div>
      </div>
      <div class="video-msg-content">
        <!-- Prompt text always visible -->
        <div class="video-msg-prompt">{{ message.content }}</div>

        <!-- Video placeholder: dark rounded container -->
        <div class="video-placeholder" :class="{ loaded: inlineVideoUrl }" @click="onPlaceholderClick">
          <!-- Actual video player (replaces placeholder when loaded) -->
          <video
            v-if="inlineVideoUrl"
            :src="inlineVideoUrl"
            controls
            class="video-player-el"
            @error="videoError = true"
            @click.stop
          >
            您的浏览器不支持视频播放
          </video>

          <!-- Overlay on top of placeholder -->
          <div v-else class="video-placeholder-overlay">
            <!-- Pending / Processing -->
            <template v-if="isVideoPending">
              <div class="vp-spinner">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.8)" stroke-width="1.5" class="spin">
                  <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
                </svg>
              </div>
              <div class="vp-status-text">{{ videoStatusLabel }}</div>
            </template>

            <!-- Succeeded: play button -->
            <template v-else-if="message.videoTask.status === 'SUCCEEDED'">
              <div v-if="videoLoading" class="vp-loading">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.8)" stroke-width="1.5" class="spin">
                  <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
                </svg>
                <span>加载中...</span>
              </div>
              <template v-else-if="!videoError">
                <div class="vp-play-btn">
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="white" stroke="none">
                    <polygon points="8 5 19 12 8 19 8 5"/>
                  </svg>
                </div>
                <div class="vp-status-text">点击播放</div>
              </template>
              <template v-else>
                <div class="vp-error">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5">
                    <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
                  </svg>
                  <span>加载失败</span>
                  <button class="vp-retry-btn" @click.stop="loadInlineVideo">重试</button>
                </div>
              </template>
            </template>

            <!-- Failed -->
            <template v-else-if="message.videoTask.status === 'FAILED'">
              <div class="vp-error-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="1.5">
                  <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
                </svg>
              </div>
              <div class="vp-status-text">生成失败{{ message.videoTask.errorMessage ? ': ' + message.videoTask.errorMessage : '' }}</div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Normal chat messages -->
  <div v-else class="message-row" :class="message.role" :id="'msg-' + message.id">
    <div class="message-bubble" @mouseenter="hovered = true" @mouseleave="hovered = false">
      <div class="message-header">
        <span class="message-role">
          {{ message.role === 'user' ? 'You' : 'AI' }}
        </span>
        <span class="message-time" v-if="message.createdAt" :title="message.createdAt">{{ timeAgo(message.createdAt) }}</span>
        <div v-if="(hovered || confirmingDelete) && !isEditing" class="message-actions">
          <button
            class="btn-action"
            title="Copy"
            @click="copyContent"
          >
            <svg v-if="!copied" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
            </svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          </button>
          <button
            v-if="message.role === 'user'"
            class="btn-action"
            title="Edit"
            @click="startEdit"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          </button>
          <template v-if="confirmingDelete">
            <span class="delete-confirm-label">删除?</span>
            <button class="btn-action btn-confirm-yes" title="Confirm delete" @click="doDelete">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            </button>
            <button class="btn-action" title="Cancel" @click="confirmingDelete = false">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </template>
          <button
            v-else
            class="btn-action"
            title="Delete"
            @click="confirmingDelete = true"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
            </svg>
          </button>
          <!-- TTS -->
          <button class="btn-action" :title="isSpeaking ? '停止朗读' : '朗读'" @click="toggleTts">
            <svg v-if="!isSpeaking" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
              <path d="M19.07 4.93a10 10 0 010 14.14M15.54 8.46a5 5 0 010 7.07"/>
            </svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>
            </svg>
          </button>
          <!-- Star -->
          <button class="btn-action" :class="{ 'btn-starred': message.starred }" title="收藏" @click="toggleStar">
            <svg width="14" height="14" viewBox="0 0 24 24" :fill="message.starred ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
          </button>
          <!-- Rating (assistant only) -->
          <template v-if="message.role === 'assistant'">
            <button class="btn-action" :class="{ 'btn-rated-up': message.rating === 1 }" title="好评" @click="rate(1)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 9V5a3 3 0 00-3-3l-4 9v11h11.28a2 2 0 002-1.7l1.38-9a2 2 0 00-2-2.3H14z"/>
                <path d="M7 22H4a2 2 0 01-2-2v-7a2 2 0 012-2h3"/>
              </svg>
            </button>
            <button class="btn-action" :class="{ 'btn-rated-down': message.rating === -1 }" title="差评" @click="rate(-1)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10 15v4a3 3 0 003 3l4-9V2H5.72a2 2 0 00-2 1.7l-1.38 9a2 2 0 002 2.3H10z"/>
                <path d="M17 2h2.67A2.31 2.31 0 0122 4v7a2.31 2.31 0 01-2.33 2H17"/>
              </svg>
            </button>
          </template>
          <button
            class="btn-action"
            title="从此处分叉新对话"
            @click="$emit('forkMessage', message.id)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="6" y1="3" x2="6" y2="15"/>
              <circle cx="18" cy="6" r="3"/>
              <circle cx="6" cy="18" r="3"/>
              <path d="M18 9a9 9 0 01-9 9"/>
            </svg>
          </button>
          <button
            v-if="isLastAi"
            class="btn-action"
            title="Regenerate"
            @click="$emit('regenerate')"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/>
            </svg>
          </button>
        </div>
      </div>
      <div v-if="isEditing" class="edit-area">
        <textarea
          ref="editInput"
          v-model="editText"
          class="edit-input"
          rows="3"
          @keydown.escape="cancelEdit"
          @keydown.enter.exact.prevent="finishEdit"
        ></textarea>
        <div class="edit-actions">
          <button class="btn-edit-save" @click="finishEdit">Save</button>
          <button class="btn-edit-cancel" @click="cancelEdit">Cancel</button>
        </div>
      </div>
      <template v-else>
        <!-- Reasoning / thinking section (collapsible) -->
        <div v-if="hasReasoning" class="thinking-section" :class="{ collapsed: !thinkingExpanded }">
          <div class="thinking-header" @click="thinkingExpanded = !thinkingExpanded">
            <svg class="thinking-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
            <span class="thinking-label">思考过程</span>
            <span v-if="isStreamingMsg && !message.content" class="thinking-dot">●</span>
            <svg class="thinking-chevron" :class="{ expanded: thinkingExpanded }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </div>
          <div v-show="thinkingExpanded" class="thinking-content">
            <div class="thinking-rendered markdown-body" v-html="renderedReasoning"></div>
            <div v-if="isStreamingMsg && !message.content" class="thinking-loading">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
        <!-- Assistant: rendered markdown — during streaming renders escaped plain text to avoid flash -->
        <template v-if="message.role === 'assistant'">
          <div
               class="markdown-body"
               :class="{ 'is-streaming': isStreamingMsg && message.content }"
               v-html="renderedContent"
               @click="handleContentClick">
          </div>
          <!-- Token usage for assistant messages -->
          <div v-if="!isStreamingMsg && message.totalTokens" class="token-footer">
            🧮 {{ message.totalTokens >= 1000 ? (message.totalTokens / 1000).toFixed(1) + 'k' : message.totalTokens }} tokens
            <span v-if="message.promptTokens"> · 输入 {{ message.promptTokens }}</span>
            <span v-if="message.completionTokens"> · 输出 {{ message.completionTokens }}</span>
          </div>
          <!-- Continue generation button for interrupted messages -->
          <div v-if="message.interrupted && !isStreamingMsg" class="interrupted-footer">
            <span class="interrupted-label">⚠ 回答被中断</span>
            <button
              class="btn-continue"
              :disabled="loading"
              @click="$emit('continueMessage', message.id)"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="5 12 19 12"/><polyline points="13 6 19 12 13 18"/>
              </svg>
              继续生成
            </button>
          </div>
        </template>
        <!-- User: images + raw text -->
        <template v-else>
          <div v-if="message.images && message.images.length > 0" class="message-images">
            <img
              v-for="(img, i) in message.images"
              :key="i"
              :src="img"
              class="message-image"
              @click="previewImage = img"
            />
          </div>
          <div class="message-text">{{ message.content }}</div>
        </template>
      </template>
      <div v-if="isStreamingMsg && !message.content" class="typing-indicator">
        <span></span><span></span><span></span>
      </div>
    </div>
    <div v-if="previewImage" class="image-preview-overlay" @click="previewImage = null">
      <img :src="previewImage" class="image-preview-full" @click.stop />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import katex from 'katex'
import DOMPurify from 'dompurify'
import { timeAgo } from '../utils/time.js'

const SAFE_URL_PROTOCOLS = /^(https?:|mailto:)/i

function sanitizeUrl(url) {
  if (!url) return '#'
  const trimmed = url.trim()
  return SAFE_URL_PROTOCOLS.test(trimmed) ? trimmed : '#'
}

function sanitizeHtml(html) {
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
  })
}

marked.setOptions({ breaks: true })

const renderer = {
  link(href, title, text) {
    const safeHref = sanitizeUrl(href)
    const titleAttr = title ? ` title="${title}"` : ''
    const rel = safeHref !== '#' ? ' rel="noopener noreferrer"' : ''
    const target = safeHref !== '#' ? ' target="_blank"' : ''
    return `<a href="${safeHref}"${titleAttr}${target}${rel}>${text}</a>`
  },
  code(code, infostring) {
    const lang = (infostring || '').trim() || 'code'
    let highlighted
    if (lang !== 'code' && hljs.getLanguage(lang)) {
      highlighted = hljs.highlight(code, { language: lang }).value
    } else {
      highlighted = hljs.highlightAuto(code).value
    }
    return '<div class="code-block-wrapper">'
      + '<div class="code-block-header"><span class="code-lang">' + lang + '</span>'
      + '<button class="btn-code-copy" data-copy>'
      + '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>'
      + '</button></div>'
      + '<pre><code class="hljs' + (lang !== 'code' ? ' language-' + lang : '') + '">' + highlighted + '</code></pre>'
      + '</div>'
  },
  table(header, body) {
    return '<div class="table-wrapper"><table><thead>' + header + '</thead><tbody>' + body + '</tbody></table></div>'
  }
}

marked.use({ renderer })

// Extract math blocks before marked processes them, to avoid Markdown mangling LaTeX.
// Returns the processed text and a map of placeholder → KaTeX HTML.
function extractAndRenderMath(text) {
  const rendered = new Map()
  let idx = 0

  // Display math: $$...$$
  text = text.replace(/\$\$([\s\S]+?)\$\$/g, (_, math) => {
    const key = `\x02MATH${idx++}\x03`
    try {
      rendered.set(key, katex.renderToString(math.trim(), { displayMode: true, throwOnError: false }))
    } catch {
      rendered.set(key, `<span class="math-error">$$${math}$$</span>`)
    }
    return key
  })

  // Inline math: $...$  (single line only, avoids false positives on currency)
  text = text.replace(/\$([^\$\n]+?)\$/g, (_, math) => {
    const key = `\x02MATH${idx++}\x03`
    try {
      rendered.set(key, katex.renderToString(math.trim(), { displayMode: false, throwOnError: false }))
    } catch {
      rendered.set(key, `<span class="math-error">$${math}$</span>`)
    }
    return key
  })

  return { text, rendered }
}

function restoreMath(html, rendered) {
  rendered.forEach((katexHtml, key) => {
    html = html.split(key).join(katexHtml)
  })
  return html
}

const props = defineProps({
  message: Object,
  isStreaming: Boolean,
  isLastAi: Boolean,
  loading: Boolean
})

const emit = defineEmits(['regenerate', 'editMessage', 'deleteMessage', 'forkMessage', 'starMessage', 'rateMessage', 'continueMessage', 'deleteVideoTask'])

// ── Video message support ──
const inlineVideoUrl = ref('')
const videoLoading = ref(false)
const videoError = ref(false)

const isVideoPending = computed(() => {
  if (!props.message.videoTask) return false
  const s = props.message.videoTask.status
  return s === 'PENDING' || s === 'SUBMITTED' || s === 'PROCESSING'
})

const videoStatusLabel = computed(() => {
  const map = {
    PENDING: '等待中...',
    SUBMITTED: '已提交，排队中...',
    PROCESSING: 'AI生成中...',
    SUCCEEDED: '已完成',
    FAILED: '失败'
  }
  return map[props.message.videoTask?.status] || props.message.videoTask?.status || '等待中'
})

function onPlaceholderClick() {
  if (isVideoPending.value) return
  if (inlineVideoUrl.value) return
  if (props.message.videoTask?.status === 'SUCCEEDED' && !videoLoading.value) {
    loadInlineVideo()
  }
}

async function loadInlineVideo() {
  if (!props.message.videoTask) return
  const taskId = props.message.videoTask.id
  if (!taskId) return
  videoLoading.value = true
  videoError.value = false
  try {
    const token = localStorage.getItem('token')
    const res = await fetch(`/api/video-gen/tasks/${taskId}/video`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!res.ok) throw new Error('Failed to load video')
    const blob = await res.blob()
    inlineVideoUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    videoError.value = true
    console.error('Failed to load inline video:', e)
  } finally {
    videoLoading.value = false
  }
}

const hovered = ref(false)
const copied = ref(false)
const isEditing = ref(false)
const editText = ref('')
const editInput = ref(null)
const previewImage = ref(null)
const confirmingDelete = ref(false)
const thinkingExpanded = ref(false)
const isSpeaking = ref(false)

function getVoiceLang() {
  try { return JSON.parse(localStorage.getItem('app-settings') || '{}').voiceLang || 'zh-CN' } catch { return 'zh-CN' }
}

function toggleTts() {
  const synth = window.speechSynthesis
  if (!synth) return
  if (isSpeaking.value) {
    synth.cancel()
    isSpeaking.value = false
    return
  }
  const plain = (props.message.content || '').replace(/```[\s\S]*?```/g, '代码块').replace(/[#*`_~>]/g, '').trim()
  const utt = new SpeechSynthesisUtterance(plain)
  utt.lang = getVoiceLang()
  utt.onend = () => { isSpeaking.value = false }
  utt.onerror = () => { isSpeaking.value = false }
  synth.cancel()
  synth.speak(utt)
  isSpeaking.value = true
}

function toggleStar() { emit('starMessage', props.message.id) }
function rate(val) { emit('rateMessage', props.message.id, props.message.rating === val ? null : val) }

// Start expanded during streaming so user sees the thinking process unfold
if (props.message.id && props.message.id.startsWith('streaming-')) {
  thinkingExpanded.value = true
}

const hasReasoning = computed(() => {
  return !!(props.message.reasoning && props.message.reasoning.length > 0)
})

const renderedReasoning = computed(() => {
  if (!props.message.reasoning) return ''
  const { text, rendered } = extractAndRenderMath(props.message.reasoning)
  const html = marked.parse(text)
  return sanitizeHtml(restoreMath(html, rendered))
})

const isStreamingMsg = computed(() => {
  return props.message.id && props.message.id.startsWith('streaming-')
})

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  // During streaming, show escaped plain text to avoid raw markdown flashing.
  // Uses message ID prefix as reliable streaming indicator.
  if (isStreamingMsg.value) {
    return props.message.content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>')
  }
  const { text, rendered } = extractAndRenderMath(props.message.content)
  const html = marked.parse(text)
  return sanitizeHtml(restoreMath(html, rendered))
})

function handleContentClick(e) {
  const btn = e.target.closest('[data-copy]')
  if (!btn) return
  const wrapper = btn.closest('.code-block-wrapper')
  const code = wrapper?.querySelector('code')
  if (code) {
    navigator.clipboard.writeText(code.textContent).then(() => {
      btn.classList.add('copied')
      btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>'
      setTimeout(() => {
        btn.classList.remove('copied')
        btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>'
      }, 2000)
    }).catch(() => {})
  }
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(props.message.content)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch (e) {
    console.error('Copy failed:', e)
  }
}

function startEdit() {
  isEditing.value = true
  editText.value = props.message.content
  nextTick(() => {
    if (editInput.value) {
      editInput.value.focus()
    }
  })
}

function finishEdit() {
  if (editText.value.trim() && editText.value.trim() !== props.message.content) {
    emit('editMessage', props.message.id, editText.value.trim())
  }
  isEditing.value = false
}

function cancelEdit() {
  isEditing.value = false
}

function doDelete() {
  confirmingDelete.value = false
  if (props.message.role === 'video') {
    emit('deleteVideoTask', props.message.videoTask?.id)
  } else {
    emit('deleteMessage', props.message.id)
  }
}
</script>

<style scoped>
.message-row {
  padding: 4px 24px;
  display: flex;
  content-visibility: auto;
  contain-intrinsic-size: auto 80px;
}

@media (max-width: 768px) {
  .message-row {
    padding: 4px 12px;
  }
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: var(--base-font-size, 14px);
  position: relative;
}

@media (max-width: 768px) {
  .message-bubble {
    max-width: 88%;
  }
}

.message-row.user .message-bubble {
  background: var(--bg-user-msg);
  border-bottom-right-radius: 4px;
}

.message-row.assistant .message-bubble {
  background: var(--bg-ai-msg);
  border-bottom-left-radius: 4px;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.message-role {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.message-time {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 4px;
}

.message-actions {
  display: flex;
  gap: 2px;
  margin-left: auto;
}

.btn-action {
  background: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-action:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.message-text {
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}
.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: typing 1.4s infinite ease-in-out;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-4px); }
}

.edit-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.edit-input {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--accent);
  border-radius: 6px;
  padding: 8px;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.5;
  resize: vertical;
  font-family: inherit;
}

.edit-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.btn-edit-save {
  padding: 4px 12px;
  background: var(--accent);
  color: white;
  border-radius: 4px;
  font-size: 12px;
}
.btn-edit-save:hover {
  background: var(--accent-hover);
}

.btn-edit-cancel {
  padding: 4px 12px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  border-radius: 4px;
  font-size: 12px;
}
.btn-edit-cancel:hover {
  background: var(--border-color);
}

.btn-starred { color: #f59e0b !important; }
.btn-rated-up { color: #22c55e !important; }
.btn-rated-down { color: var(--danger) !important; }

.markdown-body.is-streaming::after {
  content: '▌';
  animation: blink 0.8s step-end infinite;
  color: var(--accent);
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.token-footer {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.7;
  user-select: none;
}

.interrupted-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--border-color);
}
.interrupted-label {
  font-size: 11px;
  color: var(--text-muted);
}
.btn-continue {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  font-size: 12px;
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
  border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-continue:hover:not(:disabled) {
  background: color-mix(in srgb, var(--accent) 20%, transparent);
}
.btn-continue:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.message-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid var(--border-color);
  transition: transform 0.15s;
}
.message-image:hover {
  transform: scale(1.05);
}

.image-preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  cursor: pointer;
}

.image-preview-full {
  max-width: 90vw;
  max-height: 90vh;
  border-radius: 8px;
  object-fit: contain;
  cursor: default;
}

/* ── Thinking / reasoning section ── */
.thinking-section {
  margin-bottom: 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-card);
  transition: background 0.2s;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.thinking-header:hover {
  background: var(--bg-hover);
}

.thinking-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.thinking-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  flex: 1;
}

.thinking-dot {
  color: var(--accent);
  font-size: 8px;
  animation: think-pulse 1.2s infinite;
}

@keyframes think-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.thinking-chevron {
  color: var(--text-muted);
  flex-shrink: 0;
  transition: transform 0.2s;
}
.thinking-chevron.expanded {
  transform: rotate(180deg);
}

.thinking-content {
  padding: 8px 14px 12px;
  border-top: 1px solid var(--border-color);
}

.thinking-rendered {
  font-size: calc(var(--base-font-size, 14px) - 1px);
  color: var(--text-secondary);
  line-height: 1.65;
}
.thinking-rendered :deep(p) {
  margin-bottom: 0.5em;
}
.thinking-rendered :deep(p:last-child) {
  margin-bottom: 0;
}
.thinking-rendered :deep(code) {
  font-size: 0.9em;
}

.thinking-loading {
  display: flex;
  gap: 4px;
  padding: 4px 0 0;
}
.thinking-loading span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: thinking-bounce 1.4s infinite ease-in-out;
}
.thinking-loading span:nth-child(2) { animation-delay: 0.2s; }
.thinking-loading span:nth-child(3) { animation-delay: 0.4s; }

@keyframes thinking-bounce {
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-3px); }
}

/* Mobile: always show action buttons since :hover doesn't work */
@media (hover: none) {
  .message-actions {
    opacity: 1;
  }
}

/* ── Video message styles ── */
.message-row.video-msg {
  justify-content: flex-start;
}

.video-bubble {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-bottom-left-radius: 4px;
  min-width: 300px;
  max-width: 75%;
}

.video-msg-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.video-msg-prompt {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ── Placeholder: dark video-like container ── */
.video-placeholder {
  position: relative;
  width: 100%;
  max-width: 480px;
  aspect-ratio: 16 / 9;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 40%, #0f3460 100%);
  border-radius: 10px;
  overflow: hidden;
  cursor: default;
  transition: box-shadow 0.2s;
}
.video-placeholder:not(.loaded) {
  cursor: pointer;
}
.video-placeholder:not(.loaded):hover {
  box-shadow: 0 0 0 2px var(--accent), 0 4px 16px rgba(0,0,0,0.3);
}

/* Grid pattern overlay to mimic video UI */
.video-placeholder::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(0deg, transparent, transparent 40px, rgba(255,255,255,0.02) 40px, rgba(255,255,255,0.02) 41px),
    repeating-linear-gradient(90deg, transparent, transparent 40px, rgba(255,255,255,0.02) 40px, rgba(255,255,255,0.02) 41px);
  pointer-events: none;
  border-radius: 10px;
}

/* Film strip decoration at top & bottom */
.video-placeholder::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(to right, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.04) 8%, transparent 8%, transparent 92%, rgba(255,255,255,0.04) 92%, rgba(255,255,255,0.04) 100%);
  pointer-events: none;
  border-radius: 10px;
}

.video-placeholder.loaded {
  aspect-ratio: auto;
  background: #000;
  cursor: default;
}
.video-placeholder.loaded::before,
.video-placeholder.loaded::after {
  display: none;
}

.video-player-el {
  width: 100%;
  max-width: 480px;
  border-radius: 10px;
  outline: none;
  display: block;
}

/* ── Overlay ── */
.video-placeholder-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  z-index: 1;
}

.vp-spinner {
  display: flex;
  align-items: center;
  justify-content: center;
}

.vp-play-btn {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s, background 0.15s;
}
.video-placeholder:not(.loaded):hover .vp-play-btn {
  transform: scale(1.1);
  background: rgba(255, 255, 255, 0.3);
}

.vp-status-text {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  text-align: center;
  max-width: 90%;
}

.vp-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
}

.vp-error, .vp-error-icon {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.vp-error {
  color: rgba(255, 255, 255, 0.7);
}

.vp-retry-btn {
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.8);
  border-radius: 5px;
  font-size: 11px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.2);
}
.vp-retry-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}
</style>