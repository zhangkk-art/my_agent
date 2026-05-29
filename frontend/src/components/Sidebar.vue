<template>
  <aside class="sidebar" :class="{ open: sidebarOpen }">
    <div class="sidebar-header">
      <div class="logo">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" stroke="none">
          <path d="M10 2 L11.2 8.8 L18 10 L11.2 11.2 L10 18 L8.8 11.2 L2 10 L8.8 8.8 Z"/>
          <path d="M18.5 13 L19.2 16.3 L22.5 17 L19.2 17.7 L18.5 21 L17.8 17.7 L14.5 17 L17.8 16.3 Z"/>
        </svg>
        <span>Ayer</span>
      </div>
      <div class="header-actions">
        <button class="btn-icon" :title="themeTitle" @click="toggleTheme">
          <svg v-if="theme === 'light'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="5"/>
            <line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/>
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
            <line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/>
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
          </svg>
        </button>
        <button class="btn-new-chat" @click="$emit('new')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          <span>New Chat</span>
        </button>
      </div>
    </div>
    <div class="search-box" v-if="conversations.length > 0">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
      </svg>
      <input
        v-model="searchQuery"
        class="search-input"
        placeholder="Search conversations..."
        @click.stop
      />
    </div>
    <div class="conversation-list">
      <template v-if="filteredConversations.length > 0">
        <template v-for="group in groupedConversations" :key="group.label">
          <div class="list-group-label">{{ group.label }}</div>
          <div
            v-for="conv in group.items"
            :key="conv.id"
            class="conversation-item"
            :class="{ active: conv.id === activeId }"
            @click="selectConv(conv.id)"
          >
            <svg class="conv-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>
            </svg>
            <span
              class="conv-title"
              :title="conv.title"
              @dblclick.stop="startRename(conv)"
            >
              <template v-if="renamingId === conv.id">
                <input
                  ref="renameInput"
                  v-model="renameText"
                  class="rename-input"
                  @blur="finishRename(conv)"
                  @keydown.enter="finishRename(conv)"
                  @keydown.escape="cancelRename"
                  @click.stop
                />
              </template>
              <template v-else>
                {{ conv.title }}
              </template>
            </span>
            <button
              class="btn-delete"
              title="Delete"
              @click.stop="confirmDelete(conv)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
              </svg>
            </button>
          </div>
        </template>
      </template>
      <div v-else-if="searchQuery && conversations.length > 0" class="empty-list">
        No matching conversations
      </div>
      <div v-else-if="conversations.length === 0" class="empty-list">
        No conversations yet
      </div>

      <!-- Message content search results -->
      <template v-if="searchQuery.trim().length >= 2">
        <div class="list-group-label msg-search-label">
          消息内容
          <span v-if="msgSearching" class="searching-dot">...</span>
        </div>
        <template v-if="msgResults.length > 0">
          <div
            v-for="r in msgResults"
            :key="r.messageId"
            class="msg-result-item"
            @click="selectConv(r.conversationId)"
          >
            <div class="msg-result-conv">{{ r.conversationTitle }}</div>
            <div class="msg-result-content">{{ r.content.substring(0, 80) }}{{ r.content.length > 80 ? '…' : '' }}</div>
          </div>
        </template>
        <div v-else-if="!msgSearching" class="empty-list">No messages found</div>
      </template>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { dateGroup } from '../utils/time.js'
import * as api from '../api/index.js'

const props = defineProps({
  conversations: Array,
  activeId: String
})

const emit = defineEmits(['select', 'new', 'delete', 'rename'])

const renamingId = ref(null)
const renameText = ref('')
const renameInput = ref(null)
const searchQuery = ref('')
const theme = ref('light')
const sidebarOpen = ref(false)
const msgResults = ref([])
const msgSearching = ref(false)
let searchTimer = null

watch(searchQuery, (q) => {
  clearTimeout(searchTimer)
  msgResults.value = []
  if (q.trim().length < 2) return
  msgSearching.value = true
  searchTimer = setTimeout(async () => {
    try {
      msgResults.value = await api.searchMessages(q.trim())
    } catch {}
    msgSearching.value = false
  }, 500)
})

const themeTitle = computed(() => theme.value === 'light' ? 'Switch to dark mode' : 'Switch to light mode')

const filteredConversations = computed(() => {
  if (!searchQuery.value.trim()) return props.conversations
  const q = searchQuery.value.toLowerCase()
  return props.conversations.filter(c => c.title.toLowerCase().includes(q))
})

const groupedConversations = computed(() => {
  const list = filteredConversations.value
  if (searchQuery.value.trim()) return [{ label: '搜索结果', items: list }]
  const groups = { '今天': [], '昨天': [], '本周': [], '更早': [] }
  for (const conv of list) {
    const label = dateGroup(conv.updatedAt || conv.createdAt)
    groups[label].push(conv)
  }
  const order = ['今天', '昨天', '本周', '更早']
  return order.filter(k => groups[k].length > 0).map(k => ({ label: k, items: groups[k] }))
})

function selectConv(id) {
  sidebarOpen.value = false
  emit('select', id)
}

onMounted(() => {
  const saved = localStorage.getItem('theme')
  if (saved) {
    theme.value = saved
  } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
    theme.value = 'dark'
  }
  applyTheme()
})

function applyTheme() {
  document.documentElement.setAttribute('data-theme', theme.value)
}

function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  localStorage.setItem('theme', theme.value)
  applyTheme()
}

function startRename(conv) {
  renamingId.value = conv.id
  renameText.value = conv.title
  nextTick(() => {
    if (renameInput.value) {
      renameInput.value.focus()
      renameInput.value.select()
    }
  })
}

function finishRename(conv) {
  if (renameText.value.trim() && renameText.value.trim() !== conv.title) {
    emit('rename', conv.id, renameText.value.trim())
  }
  renamingId.value = null
}

function cancelRename() {
  renamingId.value = null
}

function confirmDelete(conv) {
  if (window.confirm('Delete conversation "' + conv.title + '"?')) {
    emit('delete', conv.id)
  }
}

defineExpose({ sidebarOpen })
</script>

<style scoped>
.sidebar {
  width: 280px;
  min-width: 280px;
  height: 100%;
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  transition: transform 0.25s ease;
  z-index: 100;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border-radius: 8px;
  color: var(--text-secondary);
  flex-shrink: 0;
  transition: background 0.15s;
}
.btn-icon:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.btn-new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 14px;
  transition: background 0.15s;
}
.btn-new-chat:hover {
  background: var(--bg-hover);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px;
  padding: 8px 12px;
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  color: var(--text-muted);
}
.search-box:focus-within {
  border-color: var(--accent);
}

.search-input {
  flex: 1;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.5;
}
.search-input::placeholder {
  color: var(--text-muted);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
}
.conversation-item:hover {
  background: var(--bg-hover);
}
.conversation-item.active {
  background: var(--bg-card);
}

.list-group-label {
  padding: 16px 12px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.conv-icon {
  flex-shrink: 0;
  color: var(--text-muted);
}

.conv-title {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--text-secondary);
}

.conversation-item:hover .btn-delete {
  opacity: 1;
}

.btn-delete {
  opacity: 0;
  background: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s;
  flex-shrink: 0;
}
.btn-delete:hover {
  color: var(--danger);
  background: rgba(224, 85, 106, 0.1);
}

.rename-input {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--accent);
  border-radius: 4px;
  padding: 2px 6px;
  color: var(--text-primary);
  font-size: 13px;
}

.empty-list {
  padding: 24px 12px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

.msg-search-label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.searching-dot {
  color: var(--accent);
  font-size: 11px;
}
.msg-result-item {
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.msg-result-item:hover {
  background: var(--bg-hover);
}
.msg-result-conv {
  font-size: 11px;
  color: var(--accent);
  font-weight: 500;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.msg-result-content {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.4;
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    transform: translateX(-100%);
  }
  .sidebar.open {
    transform: translateX(0);
  }
}
</style>
