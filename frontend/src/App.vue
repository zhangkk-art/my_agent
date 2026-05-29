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
      @send="handleSendMessage"
      @stop="stopStream"
      @regenerate="regenerateMessage"
      @editMessage="handleEditMessage"
      @deleteMessage="handleDeleteMessage"
    >
      <template #hamburger>
        <button class="btn-hamburger" @click="sidebarRef.sidebarOpen = !sidebarRef.sidebarOpen">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
      </template>
    </ChatArea>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import ChatArea from './components/ChatArea.vue'
import * as api from './api/index.js'

const conversations = ref([])
const activeConversationId = ref(null)
const loading = ref(false)
const abortController = ref(null)
const sidebarRef = ref(null)

const currentConversation = computed(() => {
  if (!activeConversationId.value) return null
  return conversations.value.find(c => c.id === activeConversationId.value) || null
})

onMounted(async () => {
  try {
    conversations.value = await api.getConversations()
  } catch (e) {
    console.error('Failed to load conversations:', e)
  }
})

async function selectConversation(id) {
  activeConversationId.value = id
  // Move to top immediately for instant feedback
  const idx = conversations.value.findIndex(c => c.id === id)
  if (idx > 0) {
    const [conv] = conversations.value.splice(idx, 1)
    conversations.value.unshift(conv)
  }
  // Update backend timestamp + load full data
  try {
    api.touchConversation(id)
    const updated = await api.getConversation(id)
    const newIdx = conversations.value.findIndex(c => c.id === id)
    if (newIdx >= 0) {
      conversations.value[newIdx] = updated
    }
  } catch (e) {
    console.error('Failed to load conversation:', e)
  }
}

async function newConversation() {
  try {
    const conv = await api.createConversation()
    conversations.value.unshift(conv)
    activeConversationId.value = conv.id
  } catch (e) {
    console.error('Failed to create conversation:', e)
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
    console.error('Failed to delete conversation:', e)
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
    console.error('Failed to rename conversation:', e)
  }
}

function handleSendMessage(message) {
  if (loading.value) return

  if (!activeConversationId.value) {
    const title = message.length > 10 ? message.substring(0, 10) : message
    api.createConversation(title).then(conv => {
      conversations.value.unshift(conv)
      activeConversationId.value = conv.id
      sendStreamMessage(conv.id, message)
    }).catch(e => console.error('Failed to create conversation:', e))
    return
  }
  sendStreamMessage(activeConversationId.value, message)
}

function sendStreamMessage(conversationId, message) {
  loading.value = true

  const controller = new AbortController()
  abortController.value = controller

  // Add user message locally
  const conv = conversations.value.find(c => c.id === conversationId)
  if (conv) {
    conv.messages.push({
      id: Date.now().toString(),
      conversationId,
      role: 'user',
      content: message,
      createdAt: new Date().toISOString()
    })
    // Add empty assistant message for streaming
    conv.messages.push({
      id: 'streaming-' + Date.now(),
      conversationId,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString()
    })
  }

  api.sendMessageStream(
    conversationId,
    message,
    // onChunk
    (content) => {
      const conv = conversations.value.find(c => c.id === conversationId)
      if (conv && conv.messages.length > 0) {
        const lastMsg = conv.messages[conv.messages.length - 1]
        if (lastMsg.role === 'assistant') {
          lastMsg.content += content
        }
      }
    },
    // onDone
    (messageId) => {
      loading.value = false
      abortController.value = null
      if (messageId && conv) {
        const lastMsg = conv.messages[conv.messages.length - 1]
        if (lastMsg && lastMsg.id.startsWith('streaming-')) {
          lastMsg.id = messageId
        }
      }
      // Refresh conversation list
      api.getConversations().then(list => {
        conversations.value = list
      })
    },
    // onError
    (error) => {
      loading.value = false
      abortController.value = null
      console.error('Stream error:', error)
      if (conv) {
        const lastMsg = conv.messages[conv.messages.length - 1]
        if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
          lastMsg.content = 'Error: ' + error.message
        }
      }
    },
    controller.signal
  )
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
    id: 'streaming-' + Date.now(),
    conversationId: activeConversationId.value,
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString()
  })

  api.regenerateStream(
    activeConversationId.value,
    lastUserContent,
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
        conversations.value = list
      })
    },
    // onError
    (error) => {
      loading.value = false
      abortController.value = null
      console.error('Regenerate error:', error)
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

    // Update the message content locally
    const msg = conv.messages.find(m => m.id === messageId)
    if (msg) {
      msg.content = updated.content
    }

    // Remove all messages after the edited one, then regenerate
    const idx = conv.messages.findIndex(m => m.id === messageId)
    if (idx >= 0) {
      conv.messages = conv.messages.slice(0, idx + 1)
    }

    // Trigger regeneration
    if (loading.value) {
      abortController.value?.abort()
      loading.value = false
    }
    regenerateMessage()
  } catch (e) {
    console.error('Failed to edit message:', e)
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
    console.error('Failed to delete message:', e)
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
