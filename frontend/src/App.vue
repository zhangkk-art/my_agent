<template>
  <div class="app-layout">
    <div v-if="sidebarRef?.sidebarOpen" class="sidebar-overlay" @click="sidebarRef.sidebarOpen = false"></div>
    <Sidebar
      ref="sidebarRef"
      :conversations="conversations"
      :activeId="activeConversationId"
      @select="selectConversation"
      @new="newConversation"
      @delete="handleDeleteConversation"
      @rename="handleRenameConversation"
    />
    <ChatArea
      :conversation="currentConversation"
      :loading="loading"
      :conversationLoading="conversationLoading"
      :model="selectedModel"
      @send="handleSendMessage"
      @stop="stopStream"
      @regenerate="regenerateMessage"
      @editMessage="handleEditMessage"
      @deleteMessage="handleDeleteMessage"
      @updateSystemPrompt="handleUpdateSystemPrompt"
      @update:model="onModelChange"
    >
      <template #hamburger>
        <button class="btn-hamburger" @click="sidebarRef.sidebarOpen = !sidebarRef.sidebarOpen">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
      </template>
    </ChatArea>
    <Toast ref="toastRef" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import ChatArea from './components/ChatArea.vue'
import Toast from './components/Toast.vue'
import * as api from './api/index.js'

const conversations = ref([])
const activeConversationId = ref(null)
const loading = ref(false)
const conversationLoading = ref(false)
const abortController = ref(null)
const sidebarRef = ref(null)
const toastRef = ref(null)
const selectedModel = ref(localStorage.getItem('model') || 'deepseek')

const currentConversation = computed(() => {
  if (!activeConversationId.value) return null
  return conversations.value.find(c => c.id === activeConversationId.value) || null
})

// Dynamic page title
watch(currentConversation, (conv) => {
  document.title = conv ? conv.title + ' - Ayer' : 'Ayer'
})

onMounted(async () => {
  try {
    conversations.value = await api.getConversations()
  } catch (e) {
    toastRef.value?.show('加载会话列表失败', 'error')
  }
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
  }
}

async function newConversation() {
  try {
    const conv = await api.createConversation()
    conversations.value.unshift(conv)
    activeConversationId.value = conv.id
  } catch (e) {
    toastRef.value?.show('创建会话失败', 'error')
  }
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
      createdAt: new Date().toISOString()
    })
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
    const c = conversations.value.find(c => c.id === conversationId)
    if (messageId && c) {
      const lastMsg = c.messages[c.messages.length - 1]
      if (lastMsg && lastMsg.id.startsWith('streaming-')) {
        lastMsg.id = messageId
      }
    }
    api.getConversations().then(list => {
      if (!loading.value) conversations.value = list
    })
  }
  const onError = (error) => {
    loading.value = false
    abortController.value = null
    toastRef.value?.show('流式响应出错: ' + error.message, 'error')
    const c = conversations.value.find(c => c.id === conversationId)
    if (c) {
      const lastMsg = c.messages[c.messages.length - 1]
      if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
        lastMsg.content = 'Error: ' + error.message
      }
    }
  }

  if (images.length > 0) {
    api.sendImageStream(conversationId, message, selectedModel.value, images, webSearch,
      onChunk, onDone, onError, controller.signal)
  } else {
    api.sendMessageStream(conversationId, message, selectedModel.value, webSearch,
      onChunk, onDone, onError, controller.signal)
  }
}

function stopStream() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
    loading.value = false
  }
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
    createdAt: new Date().toISOString()
  })

  api.regenerateStream(
    activeConversationId.value,
    lastUserContent,
    selectedModel.value,
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
      }
      api.getConversations().then(list => {
        if (!loading.value) conversations.value = list
      })
    },
    // onError
    (error) => {
      loading.value = false
      abortController.value = null
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
      createdAt: new Date().toISOString()
    })

    api.regenerateStream(
      activeConversationId.value,
      updated.content,
      selectedModel.value,
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
        }
        api.getConversations().then(list => {
          if (!loading.value) conversations.value = list
        })
      },
      (error) => {
        loading.value = false
        abortController.value = null
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
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100%;
  position: relative;
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
}
</style>
