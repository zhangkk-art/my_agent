<template>
  <!-- Login view -->
  <LoginView v-if="!isLoggedIn && !sharedMode" @logged-in="handleLoggedIn" />
  <!-- Shared conversation view -->
  <SharedView v-else-if="sharedMode" :conversation="sharedConversation" :loading="sharedLoading" />
  <!-- Normal app layout -->
  <div v-else-if="isLoggedIn" class="app-layout">
    <div v-if="sidebarRef?.sidebarOpen" class="sidebar-overlay" @click="sidebarRef.sidebarOpen = false"></div>
    <Sidebar
      ref="sidebarRef"
      :conversations="conversations"
      :activeId="activeConversationId"
      :width="sidebarWidth"
      :username="currentUsername"
      @select="selectConversation"
      @selectMessage="handleSelectMessage"
      @new="newConversation"
      @delete="handleDeleteConversation"
      @rename="handleRenameConversation"
      @pin="handlePinConversation"
      @setFolder="handleSetFolder"
      @import="handleImportConversation"
      @bulkDelete="handleBulkDelete"
      @openSettings="showSettings = true"
      @logout="handleLogout"
    />
    <div
      class="resize-handle"
      @mousedown="startResize"
    ></div>
    <ChatArea
      ref="chatAreaRef"
      :conversation="currentConversation"
      :loading="loading"
      :conversationLoading="conversationLoading"
      :model="selectedModel"
      :enterToSend="settings.enterToSend"
      :voiceLang="settings.voiceLang"
      @send="handleSendMessage"
      @stop="stopStream"
      @regenerate="regenerateMessage"
      @editMessage="handleEditMessage"
      @deleteMessage="handleDeleteMessage"
      @forkMessage="handleForkMessage"
      @starMessage="handleStarMessage"
      @rateMessage="handleRateMessage"
      @continueMessage="handleContinueMessage"
      @createFromTemplate="handleCreateFromTemplate"
      @updateSystemPrompt="handleUpdateSystemPrompt"
      @update:model="onModelChange"
      @toast="(e) => toastRef?.show(e.message, e.type)"
    >
      <template #hamburger>
        <button class="btn-hamburger" @click="sidebarRef.sidebarOpen = !sidebarRef.sidebarOpen">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
      </template>
    </ChatArea>
    <Transition name="modal">
      <SettingsModal
        v-if="showSettings"
        :modelValue="settings"
        @close="showSettings = false"
        @update="handleSettingsUpdate"
        @clearAll="handleClearAll"
        @themeChange="applyTheme"
      />
    </Transition>
    <Toast ref="toastRef" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import Sidebar from './components/Sidebar.vue'
import ChatArea from './components/ChatArea.vue'
import Toast from './components/Toast.vue'
import SettingsModal from './components/SettingsModal.vue'
import SharedView from './components/SharedView.vue'
import LoginView from './components/LoginView.vue'
import * as api from './api/index.js'

// ── Auth ──
const token = ref(localStorage.getItem('token'))
const isLoggedIn = computed(() => !!token.value)
const currentUsername = ref(localStorage.getItem('username') || '')

function handleLoggedIn(username) {
  token.value = localStorage.getItem('token')
  currentUsername.value = username
  // Load conversations after login
  api.getConversations().then(list => { conversations.value = list }).catch(() => {})
}

function handleLogout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  token.value = null
  currentUsername.value = ''
  conversations.value = []
  activeConversationId.value = null
}

const DEFAULT_SETTINGS = {
  fontSize: 'medium',
  defaultModel: 'deepseek',
  enterToSend: true,
  voiceLang: 'zh-CN',
  temperature: 0.7,
  maxTokens: 2048,
  theme: 'system',
}
const FONT_SIZE_MAP = { small: '13px', medium: '14px', large: '16px' }

function loadSettings() {
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem('app-settings') || '{}') }
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

function applyFontSize(size) {
  document.documentElement.style.setProperty('--base-font-size', FONT_SIZE_MAP[size] || '14px')
}

function applyTheme(theme) {
  if (theme === 'system') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light')
  } else {
    document.documentElement.setAttribute('data-theme', theme || 'light')
  }
}

const conversations = ref([])
const activeConversationId = ref(null)
const loading = ref(false)
const conversationLoading = ref(false)
const abortController = ref(null)
const sidebarRef = ref(null)
const chatAreaRef = ref(null)
const toastRef = ref(null)
const selectedModel = ref(localStorage.getItem('model') || 'deepseek')
const showSettings = ref(false)
const settings = ref(loadSettings())
// Track the last webSearch state so regenerate can reuse it
const lastWebSearch = ref(false)
const continuingMessageId = ref(null)  // messageId being continued, null when not in continuation mode

// ── Resizable sidebar ──
const SIDEBAR_MIN = 240
const SIDEBAR_MAX = 500
const sidebarWidth = ref(Number(localStorage.getItem('sidebarWidth')) || 280)
let resizing = false

function startResize(e) {
  resizing = true
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
}

function onResize(e) {
  if (!resizing) return
  const w = Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, e.clientX))
  sidebarWidth.value = w
}

function stopResize() {
  resizing = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  localStorage.setItem('sidebarWidth', sidebarWidth.value)
}

// ── Shared conversation mode ──
const sharedMode = ref(false)
const sharedConversation = ref(null)
const sharedLoading = ref(true)

// Detect share token from URL — supports both:
//   /share/<token>   (path-based, generated by ChatArea)
//   ?share=<token>   (query param, for backward compatibility)
let shareToken = null
const urlParams = new URLSearchParams(window.location.search)
shareToken = urlParams.get('share')
if (!shareToken) {
  const match = window.location.pathname.match(/^\/share\/([a-zA-Z0-9]+)/)
  if (match) shareToken = match[1]
}
if (shareToken) {
  sharedMode.value = true
  sharedLoading.value = true
  api.getSharedConversation(shareToken).then(conv => {
    sharedConversation.value = conv
    document.title = conv.title + ' - Shared from Ayer'
  }).catch(() => {
    sharedConversation.value = null
  }).finally(() => {
    sharedLoading.value = false
  })
}

const currentConversation = computed(() => {
  if (!activeConversationId.value) return null
  return conversations.value.find(c => c.id === activeConversationId.value) || null
})

// Dynamic page title
watch(currentConversation, (conv) => {
  document.title = conv ? conv.title + ' - Ayer' : 'Ayer'
})

function handleGlobalKeydown(e) {
  const mod = e.ctrlKey || e.metaKey

  // Esc: close modals in priority order, then stop streaming
  if (e.key === 'Escape') {
    if (showSettings.value) { showSettings.value = false; return }
    if (loading.value) { stopStream(); return }
    return
  }

  // Ctrl/Cmd + / — open settings (shortcuts are now inside settings)
  if (mod && e.key === '/') {
    e.preventDefault()
    showSettings.value = true
    return
  }

  // Ctrl/Cmd + K — focus sidebar search (fires even in inputs)
  if (mod && e.key === 'k') {
    e.preventDefault()
    sidebarRef.value?.focusSearch()
    return
  }

  // Don't fire remaining shortcuts when user is typing in an input or textarea
  const tag = e.target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable) return

  // Ctrl/Cmd + , — open settings
  if (mod && e.key === ',') {
    e.preventDefault()
    showSettings.value = true
    return
  }
}

onMounted(async () => {
  applyFontSize(settings.value.fontSize)
  // Migrate old standalone theme from localStorage if settings has default 'system'
  if (settings.value.theme === 'system') {
    const legacyTheme = localStorage.getItem('theme')
    if (legacyTheme === 'light' || legacyTheme === 'dark') {
      settings.value.theme = legacyTheme
    }
  }
  applyTheme(settings.value.theme)
  // Listen for OS dark-mode changes when theme is 'system'
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (settings.value.theme === 'system') applyTheme('system')
  })
  window.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('auth:logout', handleLogout)
  if (isLoggedIn.value) {
    try {
      conversations.value = await api.getConversations()
    } catch (e) {
      if (e.message !== 'Unauthorized') {
        toastRef.value?.show('加载会话列表失败', 'error')
      }
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('auth:logout', handleLogout)
})

async function selectConversation(id) {
  activeConversationId.value = id
  conversationLoading.value = true
  // Move to top immediately for instant feedback
  const idx = conversations.value.findIndex(c => c.id === id)
  if (idx > 0) {
    const [conv] = conversations.value.splice(idx, 1)
    conversations.value.unshift(conv)
  }
  // Update backend timestamp (non-critical, fire-and-forget)
  api.touchConversation(id).catch(() => {})
  // Load full conversation data
  try {
    const updated = await api.getConversation(id)
    const newIdx = conversations.value.findIndex(c => c.id === id)
    if (newIdx >= 0) {
      conversations.value[newIdx] = updated
    }
  } catch (e) {
    toastRef.value?.show('加载会话失败', 'error')
  } finally {
    conversationLoading.value = false
    nextTick(() => chatAreaRef.value?.focusInput())
  }
}

function newConversation() {
  activeConversationId.value = null
  nextTick(() => chatAreaRef.value?.focusInput())
}

async function handleDeleteConversation(id) {
  try {
    await api.deleteConversation(id)
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (activeConversationId.value === id) {
      activeConversationId.value = null
    }
  } catch (e) {
    toastRef.value?.show('删除会话失败', 'error')
  }
}

async function handleRenameConversation(id, title) {
  try {
    await api.renameConversation(id, title)
    const conv = conversations.value.find(c => c.id === id)
    if (conv) {
      conv.title = title
    }
  } catch (e) {
    toastRef.value?.show('重命名失败', 'error')
  }
}

function onModelChange(m) {
  selectedModel.value = m
  localStorage.setItem('model', m)
}

// Refresh conversation metadata (titles, updatedAt) while preserving local messages.
// getAllConversations() no longer returns messages to avoid N+1 queries on the backend.
function refreshConversationList() {
  api.getConversations().then(freshList => {
    if (loading.value) return
    const localMsgMap = new Map(conversations.value.map(c => [c.id, c.messages]))
    conversations.value = freshList.map(c => ({
      ...c,
      messages: localMsgMap.get(c.id) ?? []
    }))
  })
}

function handleSendMessage(message, images = [], webSearch = false) {
  if (loading.value) return

  if (!activeConversationId.value) {
    const title = (message || 'Image').length > 10 ? (message || 'Image').substring(0, 10) : (message || 'Image')
    api.createConversation(title).then(conv => {
      conversations.value.unshift(conv)
      activeConversationId.value = conv.id
      sendStreamMessage(conv.id, message, images, webSearch)
    }).catch(e => toastRef.value?.show('创建会话失败', 'error'))
    return
  }
  sendStreamMessage(activeConversationId.value, message, images, webSearch)
}

function sendStreamMessage(conversationId, message, images = [], webSearch = false) {
  lastWebSearch.value = webSearch
  loading.value = true

  const controller = new AbortController()
  abortController.value = controller

  // Add user message locally
  const conv = conversations.value.find(c => c.id === conversationId)
  if (conv) {
    // Store images in message metadata for display
    const userMsg = {
      id: crypto.randomUUID(),
      conversationId,
      role: 'user',
      content: message || '[Image]',
      images: images.length > 0 ? images : undefined,
      createdAt: new Date().toISOString()
    }
    conv.messages.push(userMsg)
    // Add empty assistant message for streaming
    conv.messages.push({
      id: 'streaming-' + crypto.randomUUID(),
      conversationId,
      role: 'assistant',
      content: '',
      reasoning: '',
      createdAt: new Date().toISOString()
    })
  }

  const onReasoning = (reasoning) => {
    const c = conversations.value.find(c => c.id === conversationId)
    if (c && c.messages.length > 0) {
      const lastMsg = c.messages[c.messages.length - 1]
      if (lastMsg.role === 'assistant') {
        lastMsg.reasoning = (lastMsg.reasoning || '') + reasoning
      }
    }
  }
  const onChunk = (content) => {
    const c = conversations.value.find(c => c.id === conversationId)
    if (c && c.messages.length > 0) {
      const lastMsg = c.messages[c.messages.length - 1]
      if (lastMsg.role === 'assistant') {
        lastMsg.content += content
      }
    }
  }
  const onDone = (messageId) => {
    loading.value = false
    abortController.value = null
    if (messageId) {
      const c = conversations.value.find(c => c.id === conversationId)
      if (c) {
        const lastMsg = c.messages[c.messages.length - 1]
        if (lastMsg && lastMsg.id.startsWith('streaming-')) {
          lastMsg.id = messageId
        }
      }
      // Only refresh from server on normal completion; on abort, keep local state
      refreshConversationList()
    }
  }
  const onError = (error) => {
    loading.value = false
    abortController.value = null
    // AbortError is expected when user clicks stop — keep streamed content, no toast
    if (error.name === 'AbortError') return
    toastRef.value?.show('流式响应出错: ' + error.message, 'error')
    const c = conversations.value.find(c => c.id === conversationId)
    if (c) {
      const lastMsg = c.messages[c.messages.length - 1]
      if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
        lastMsg.content = 'Error: ' + error.message
      }
    }
  }

  const { temperature, maxTokens } = settings.value
  if (images.length > 0) {
    api.sendImageStream(conversationId, message, selectedModel.value, images, webSearch,
      temperature ?? null, maxTokens ?? null, onReasoning, onChunk, onDone, onError, controller.signal)
  } else {
    api.sendMessageStream(conversationId, message, selectedModel.value, webSearch,
      temperature ?? null, maxTokens ?? null, onReasoning, onChunk, onDone, onError, controller.signal)
  }
}

async function stopStream() {
  if (!abortController.value) return

  const conversationId = activeConversationId.value
  const conv = conversations.value.find(c => c.id === conversationId)
  const continueId = continuingMessageId.value  // capture before clearing

  abortController.value.abort()
  abortController.value = null
  loading.value = false
  continuingMessageId.value = null

  if (continueId) {
    // Continuation was in progress — update existing message with current content
    const msg = conv?.messages.find(m => m.id === continueId)
    if (msg) {
      msg.interrupted = true
      if (msg.content) {
        try { await api.saveInterruptedUpdate(continueId, msg.content) }
        catch (e) { toastRef.value?.show('保存中断内容失败', 'error') }
      }
    }
  } else {
    // Initial stream — last message still has streaming-xxx id
    const lastMsg = conv?.messages[conv.messages.length - 1]
    if (lastMsg?.id?.startsWith('streaming-') && lastMsg?.role === 'assistant') {
      if (lastMsg.content) {
        try {
          const saved = await api.savePartialMessage(conversationId, lastMsg.content, lastMsg.reasoning || null)
          lastMsg.id = saved.id
          lastMsg.interrupted = true
        } catch (e) {
          toastRef.value?.show('保存中断内容失败', 'error')
        }
      } else {
        // Nothing streamed yet — remove placeholder
        conv.messages.pop()
      }
    }
  }
}

function handleContinueMessage(messageId) {
  if (!activeConversationId.value || loading.value) return
  const conv = conversations.value.find(c => c.id === activeConversationId.value)
  if (!conv) return
  const msg = conv.messages.find(m => m.id === messageId)
  if (!msg) return

  msg.interrupted = false
  loading.value = true
  continuingMessageId.value = messageId

  const controller = new AbortController()
  abortController.value = controller

  const onReasoning = (reasoning) => {
    const c = conversations.value.find(c => c.id === activeConversationId.value)
    const m = c?.messages.find(m => m.id === messageId)
    if (m) m.reasoning = (m.reasoning || '') + reasoning
  }
  const onChunk = (content) => {
    const c = conversations.value.find(c => c.id === activeConversationId.value)
    const m = c?.messages.find(m => m.id === messageId)
    if (m) m.content += content
  }
  const onDone = () => {
    loading.value = false
    abortController.value = null
    continuingMessageId.value = null
    refreshConversationList()
  }
  const onError = (error) => {
    loading.value = false
    abortController.value = null
    continuingMessageId.value = null
    if (error.name === 'AbortError') return  // stopStream handles save + interrupted flag
    const c = conversations.value.find(c => c.id === activeConversationId.value)
    const m = c?.messages.find(m => m.id === messageId)
    if (m) m.interrupted = true
    toastRef.value?.show('续传出错: ' + error.message, 'error')
  }

  const { temperature, maxTokens } = settings.value
  api.continueStream(
    activeConversationId.value, messageId, selectedModel.value,
    temperature ?? null, maxTokens ?? null,
    onReasoning, onChunk, onDone, onError, controller.signal
  )
}

function regenerateMessage() {
  if (!activeConversationId.value) return
  const conv = conversations.value.find(c => c.id === activeConversationId.value)
  if (!conv || conv.messages.length < 2) return

  const messages = conv.messages
  const lastMsg = messages[messages.length - 1]
  if (lastMsg.role !== 'assistant') return

  // Find the last user message before removing the AI reply
  let lastUserContent = ''
  for (let i = messages.length - 2; i >= 0; i--) {
    if (messages[i].role === 'user') {
      lastUserContent = messages[i].content
      break
    }
  }
  if (!lastUserContent) return

  // Remove the last AI message locally
  messages.pop()

  const controller = new AbortController()
  abortController.value = controller

  loading.value = true

  // Add empty assistant message for streaming
  conv.messages.push({
    id: 'streaming-' + crypto.randomUUID(),
    conversationId: activeConversationId.value,
    role: 'assistant',
    content: '',
    reasoning: '',
    createdAt: new Date().toISOString()
  })

  const { temperature: regenTemp, maxTokens: regenMaxTokens } = settings.value
  api.regenerateStream(
    activeConversationId.value,
    lastUserContent,
    selectedModel.value,
    lastWebSearch.value,
    regenTemp ?? null, regenMaxTokens ?? null,
    // onReasoning
    (reasoning) => {
      const lastMsg = conv.messages[conv.messages.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.reasoning = (lastMsg.reasoning || '') + reasoning
      }
    },
    // onChunk
    (content) => {
      const lastMsg = conv.messages[conv.messages.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.content += content
      }
    },
    // onDone
    (messageId) => {
      loading.value = false
      abortController.value = null
      if (messageId) {
        const lastMsg = conv.messages[conv.messages.length - 1]
        if (lastMsg && lastMsg.id.startsWith('streaming-')) {
          lastMsg.id = messageId
        }
        refreshConversationList()
      }
    },
    // onError
    (error) => {
      loading.value = false
      abortController.value = null
      if (error.name === 'AbortError') return
      toastRef.value?.show('重新生成失败: ' + error.message, 'error')
      const lastMsg = conv.messages[conv.messages.length - 1]
      if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
        lastMsg.content = 'Error: ' + error.message
      }
    },
    controller.signal
  )
}

async function handleEditMessage(messageId, newContent) {
  try {
    const updated = await api.updateMessage(messageId, newContent)
    const conv = conversations.value.find(c => c.id === activeConversationId.value)
    if (!conv) return

    // Update local message content
    const msg = conv.messages.find(m => m.id === messageId)
    if (msg) msg.content = updated.content

    // Remove all messages after the edited one
    const idx = conv.messages.findIndex(m => m.id === messageId)
    if (idx >= 0) {
      conv.messages = conv.messages.slice(0, idx + 1)
    }

    // Stop any ongoing stream
    if (loading.value) {
      abortController.value?.abort()
      loading.value = false
    }

    // After slicing, last message is user — regenerateMessage() would bail out.
    // Directly start the regeneration stream instead.
    const controller = new AbortController()
    abortController.value = controller
    loading.value = true

    conv.messages.push({
      id: 'streaming-' + crypto.randomUUID(),
      conversationId: activeConversationId.value,
      role: 'assistant',
      content: '',
      reasoning: '',
      createdAt: new Date().toISOString()
    })

    const { temperature: editRegenTemp, maxTokens: editRegenMaxTokens } = settings.value
    api.regenerateStream(
      activeConversationId.value,
      updated.content,
      selectedModel.value,
      lastWebSearch.value,
      editRegenTemp ?? null, editRegenMaxTokens ?? null,
      (reasoning) => {
        const lastMsg = conv.messages[conv.messages.length - 1]
        if (lastMsg?.role === 'assistant') lastMsg.reasoning = (lastMsg.reasoning || '') + reasoning
      },
      (content) => {
        const lastMsg = conv.messages[conv.messages.length - 1]
        if (lastMsg?.role === 'assistant') lastMsg.content += content
      },
      (newMsgId) => {
        loading.value = false
        abortController.value = null
        if (newMsgId) {
          const lastMsg = conv.messages[conv.messages.length - 1]
          if (lastMsg?.id.startsWith('streaming-')) lastMsg.id = newMsgId
          refreshConversationList()
        }
      },
      (error) => {
        loading.value = false
        abortController.value = null
        if (error.name === 'AbortError') return
        toastRef.value?.show('重新生成失败: ' + error.message, 'error')
        const lastMsg = conv.messages[conv.messages.length - 1]
        if (lastMsg?.role === 'assistant' && !lastMsg.content) {
          lastMsg.content = 'Error: ' + error.message
        }
      },
      controller.signal
    )
  } catch (e) {
    toastRef.value?.show('编辑消息失败', 'error')
  }
}

async function handleSelectMessage(conversationId, messageId) {
  await selectConversation(conversationId)
  nextTick(() => chatAreaRef.value?.scrollToMessage(messageId))
}

async function handlePinConversation(id) {
  try {
    const updated = await api.pinConversation(id)
    const conv = conversations.value.find(c => c.id === id)
    if (conv) { conv.pinned = updated.pinned; conversations.value.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)) }
  } catch (e) { toastRef.value?.show('操作失败', 'error') }
}

async function handleSetFolder(id, folderName) {
  try {
    await api.setConversationFolder(id, folderName)
    const conv = conversations.value.find(c => c.id === id)
    if (conv) conv.folderName = folderName || null
  } catch (e) { toastRef.value?.show('操作失败', 'error') }
}

async function handleImportConversation(title, messages) {
  try {
    const conv = await api.importConversation(title, messages)
    conversations.value.unshift(conv)
    activeConversationId.value = conv.id
    toastRef.value?.show('导入成功', 'success')
  } catch (e) { toastRef.value?.show('导入失败', 'error') }
}

async function handleBulkDelete(ids) {
  try {
    for (const id of ids) await api.deleteConversation(id)
    conversations.value = conversations.value.filter(c => !ids.includes(c.id))
    if (ids.includes(activeConversationId.value)) activeConversationId.value = null
    toastRef.value?.show(`已删除 ${ids.length} 个对话`, 'success')
  } catch (e) { toastRef.value?.show('批量删除失败', 'error') }
}

async function handleStarMessage(messageId) {
  try {
    const updated = await api.starMessage(messageId)
    const conv = currentConversation.value
    if (conv) {
      const msg = conv.messages.find(m => m.id === messageId)
      if (msg) msg.starred = updated.starred
    }
  } catch {}
}

async function handleRateMessage(messageId, rating) {
  try {
    const updated = await api.rateMessage(messageId, rating)
    const conv = currentConversation.value
    if (conv) {
      const msg = conv.messages.find(m => m.id === messageId)
      if (msg) msg.rating = updated.rating
    }
  } catch {}
}

async function handleForkMessage(messageId) {
  if (!activeConversationId.value) return
  try {
    const forked = await api.forkConversation(activeConversationId.value, messageId)
    conversations.value.unshift(forked)
    activeConversationId.value = forked.id
    toastRef.value?.show('已创建分支对话', 'success')
    nextTick(() => chatAreaRef.value?.focusInput())
  } catch (e) {
    toastRef.value?.show('创建分支失败', 'error')
  }
}

async function handleCreateFromTemplate(template) {
  try {
    const conv = await api.createConversation(template.name)
    conversations.value.unshift(conv)
    activeConversationId.value = conv.id
    if (template.systemPrompt) {
      await api.updateSystemPrompt(conv.id, template.systemPrompt)
      conv.systemPrompt = template.systemPrompt
    }
    if (template.initialMessage) {
      sendStreamMessage(conv.id, template.initialMessage, [], false)
    } else {
      nextTick(() => chatAreaRef.value?.focusInput())
    }
  } catch (e) {
    toastRef.value?.show('从模板创建失败', 'error')
  }
}

async function handleUpdateSystemPrompt(conversationId, systemPrompt) {
  try {
    await api.updateSystemPrompt(conversationId, systemPrompt)
    const conv = conversations.value.find(c => c.id === conversationId)
    if (conv) conv.systemPrompt = systemPrompt || null
    toastRef.value?.show('系统提示词已更新', 'success')
  } catch (e) {
    toastRef.value?.show('更新失败', 'error')
  }
}

async function handleDeleteMessage(messageId) {
  try {
    await api.deleteMessage(messageId)
    const conv = conversations.value.find(c => c.id === activeConversationId.value)
    if (conv) {
      conv.messages = conv.messages.filter(m => m.id !== messageId)
    }
  } catch (e) {
    toastRef.value?.show('删除消息失败', 'error')
  }
}

function handleSettingsUpdate(newSettings) {
  settings.value = { ...newSettings }
  localStorage.setItem('app-settings', JSON.stringify(newSettings))
  applyFontSize(newSettings.fontSize)
  applyTheme(newSettings.theme)
  // If default model changed, also update the current model if it hasn't been explicitly changed this session
  if (newSettings.defaultModel !== selectedModel.value) {
    selectedModel.value = newSettings.defaultModel
    localStorage.setItem('model', newSettings.defaultModel)
  }
  showSettings.value = false
}

async function handleClearAll() {
  try {
    // Delete all conversations via API
    for (const conv of conversations.value) {
      await api.deleteConversation(conv.id)
    }
    conversations.value = []
    activeConversationId.value = null
    toastRef.value?.show('所有对话已清除', 'success')
  } catch (e) {
    toastRef.value?.show('清除对话失败', 'error')
  }
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100%;
  position: relative;
}

.resize-handle {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  flex-shrink: 0;
  transition: background 0.15s;
  z-index: 10;
}
.resize-handle:hover {
  background: var(--accent);
}

.btn-hamburger {
  display: none;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border-radius: 8px;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.btn-hamburger:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.sidebar-overlay {
  display: none;
}

@media (max-width: 768px) {
  .btn-hamburger {
    display: flex;
  }
  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 99;
  }
  .resize-handle {
    display: none;
  }
}

/* Modal transition */
.modal-enter-active { transition: opacity 0.2s ease; }
.modal-leave-active { transition: opacity 0.15s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
