<template>
  <div class="message-row" :class="message.role">
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
        <div v-if="message.role === 'assistant'"
             class="markdown-body"
             :class="{ 'is-streaming': isStreamingMsg && message.content }"
             v-html="renderedContent"
             @click="handleContentClick">
        </div>
        <!-- Token usage for assistant messages -->
        <div v-if="message.role === 'assistant' && !isStreamingMsg && message.totalTokens" class="token-footer">
          🧮 {{ message.totalTokens >= 1000 ? (message.totalTokens / 1000).toFixed(1) + 'k' : message.totalTokens }} tokens
          <span v-if="message.promptTokens"> · 输入 {{ message.promptTokens }}</span>
          <span v-if="message.completionTokens"> · 输出 {{ message.completionTokens }}</span>
        </div>
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
import { timeAgo } from '../utils/time.js'

const SAFE_URL_PROTOCOLS = /^(https?:|mailto:)/i

function sanitizeUrl(url) {
  if (!url) return '#'
  const trimmed = url.trim()
  return SAFE_URL_PROTOCOLS.test(trimmed) ? trimmed : '#'
}

function sanitizeHtml(html) {
  return html
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
    .replace(/<\/?script[^>]*>/gi, '')
    .replace(/\bon\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\bon\w+\s*=\s*'[^']*'/gi, '')
    .replace(/\bon\w+\s*=\s*[^\s>]+/gi, '')
    .replace(/(href|src)\s*=\s*["']?\s*(javascript|vbscript|data)[^"'\s>]*/gi, '$1="#"')
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
  isLastAi: Boolean
})

const emit = defineEmits(['regenerate', 'editMessage', 'deleteMessage'])

const hovered = ref(false)
const copied = ref(false)
const isEditing = ref(false)
const editText = ref('')
const editInput = ref(null)
const previewImage = ref(null)
const confirmingDelete = ref(false)
const thinkingExpanded = ref(false)

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
  emit('deleteMessage', props.message.id)
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
</style>