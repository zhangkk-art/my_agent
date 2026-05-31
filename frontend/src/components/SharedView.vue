<template>
  <div class="shared-page">
    <!-- Loading -->
    <div v-if="loading" class="shared-loading">
      <div class="shared-loading-spinner"></div>
      <span>Loading shared conversation...</span>
    </div>

    <!-- Not found -->
    <div v-else-if="!conversation" class="shared-not-found">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <h2>Not Found</h2>
      <p>This shared conversation doesn't exist or has been revoked.</p>
    </div>

    <!-- Shared conversation -->
    <div v-else class="shared-content">
      <header class="shared-header">
        <div class="shared-header-inner">
          <div class="shared-logo">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" stroke="none">
              <path d="M10 2 L11.2 8.8 L18 10 L11.2 11.2 L10 18 L8.8 11.2 L2 10 L8.8 8.8 Z"/>
              <path d="M18.5 13 L19.2 16.3 L22.5 17 L19.2 17.7 L18.5 21 L17.8 17.7 L14.5 17 L17.8 16.3 Z"/>
            </svg>
            <span>Ayer</span>
          </div>
          <h1 class="shared-title">{{ conversation.title }}</h1>
        </div>
      </header>

      <div class="shared-messages">
        <div v-for="msg in conversation.messages" :key="msg.id" class="shared-msg-row" :class="msg.role">
          <div class="shared-msg-bubble">
            <div class="shared-msg-role">{{ msg.role === 'user' ? 'You' : 'AI' }}</div>
            <div
              v-if="msg.role === 'assistant'"
              class="markdown-body"
              v-html="renderMsg(msg)"
            ></div>
            <div v-else class="shared-msg-text">{{ msg.content }}</div>
          </div>
        </div>
      </div>

      <footer class="shared-footer">
        <span>Shared via Ayer</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { marked } from 'marked'
import hljs from 'highlight.js'
import katex from 'katex'

const props = defineProps({
  conversation: Object,
  loading: Boolean
})

// Simplified markdown renderer for shared view
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
      + '<div class="code-block-header"><span class="code-lang">' + lang + '</span></div>'
      + '<pre><code class="hljs' + (lang !== 'code' ? ' language-' + lang : '') + '">' + highlighted + '</code></pre>'
      + '</div>'
  },
  table(header, body) {
    return '<div class="table-wrapper"><table><thead>' + header + '</thead><tbody>' + body + '</tbody></table></div>'
  }
}

marked.use({ renderer })

function extractAndRenderMath(text) {
  const rendered = new Map()
  let idx = 0
  text = text.replace(/\$\$([\s\S]+?)\$\$/g, (_, math) => {
    const key = `\x02MATH${idx++}\x03`
    try {
      rendered.set(key, katex.renderToString(math.trim(), { displayMode: true, throwOnError: false }))
    } catch {
      rendered.set(key, `<span class="math-error">$$${math}$$</span>`)
    }
    return key
  })
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

function renderMsg(msg) {
  if (!msg.content) return ''
  const { text, rendered } = extractAndRenderMath(msg.content)
  let html = marked.parse(text)
  html = sanitizeHtml(html)
  rendered.forEach((katexHtml, key) => {
    html = html.split(key).join(katexHtml)
  })
  return html
}
</script>

<style scoped>
.shared-page {
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.shared-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 16px;
  color: var(--text-muted);
  font-size: 14px;
}

.shared-loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.shared-not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 12px;
  color: var(--text-muted);
  text-align: center;
}
.shared-not-found h2 {
  font-size: 20px;
  color: var(--text-primary);
  margin: 0;
}
.shared-not-found p {
  font-size: 14px;
  margin: 0;
}

.shared-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 24px;
}

.shared-header {
  position: sticky;
  top: 0;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  padding: 14px 0;
  z-index: 10;
}

.shared-header-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.shared-logo {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: var(--accent);
  flex-shrink: 0;
}

.shared-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-secondary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shared-messages {
  padding: 24px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.shared-msg-row {
  display: flex;
}
.shared-msg-row.user {
  justify-content: flex-end;
}

.shared-msg-bubble {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
}

.shared-msg-row.user .shared-msg-bubble {
  background: var(--bg-user-msg);
  border-bottom-right-radius: 4px;
}

.shared-msg-row.assistant .shared-msg-bubble {
  background: var(--bg-ai-msg);
  border-bottom-left-radius: 4px;
}

.shared-msg-role {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.shared-msg-text {
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
  color: var(--text-primary);
}

.shared-footer {
  text-align: center;
  padding: 32px 0;
  border-top: 1px solid var(--border-color);
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 768px) {
  .shared-content {
    padding: 0 12px;
  }
  .shared-msg-bubble {
    max-width: 88%;
  }
}
</style>
