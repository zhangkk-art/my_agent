<template>
  <aside class="sidebar" :class="{ open: sidebarOpen }" :style="{ width: width + 'px', minWidth: width + 'px' }">
    <div class="sidebar-header">
      <div class="logo">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" stroke="none">
          <path d="M10 2 L11.2 8.8 L18 10 L11.2 11.2 L10 18 L8.8 11.2 L2 10 L8.8 8.8 Z"/>
          <path d="M18.5 13 L19.2 16.3 L22.5 17 L19.2 17.7 L18.5 21 L17.8 17.7 L14.5 17 L17.8 16.3 Z"/>
        </svg>
        <span>Ayer</span>
      </div>
      <div class="header-actions">
        <button class="btn-icon" title="设置" @click="$emit('openSettings')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/>
          </svg>
        </button>
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
        <input ref="importInput" type="file" accept=".json,.md,.txt" class="file-hidden" @change="onImportFile" />
        <button class="btn-icon" title="导入对话" @click="importInput.click()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </button>
        <button class="btn-icon" :class="{ active: bulkMode }" title="多选" @click="toggleBulkMode">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 11 12 14 22 4"/>
            <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
          </svg>
        </button>
        <button class="btn-new-chat" @click="$emit('new')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          <span>New Chat</span>
        </button>
      </div>
    </div>

    <div v-if="bulkMode" class="bulk-bar">
      <span class="bulk-count">已选 {{ selected.size }}</span>
      <button class="bulk-btn" @click="selectAll">全选</button>
      <button class="bulk-btn" @click="selected.clear()">清除</button>
      <button class="bulk-btn bulk-delete" :disabled="selected.size === 0" @click="bulkDelete">删除</button>
    </div>

    <div class="search-box" v-if="conversations.length > 0">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
      </svg>
      <input v-model="searchQuery" class="search-input" placeholder="Search conversations..." @click.stop />
      <button class="btn-star-filter" :class="{ active: showStarred }" title="收藏的消息" @click="toggleStarred">
        <svg width="13" height="13" viewBox="0 0 24 24" :fill="showStarred ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
        </svg>
      </button>
    </div>

    <div class="conversation-list">
      <!-- Starred messages -->
      <template v-if="showStarred">
        <div class="list-group-label">收藏的消息</div>
        <div v-if="starredLoading" class="empty-list">加载中…</div>
        <template v-else-if="starredMessages.length > 0">
          <div v-for="r in starredMessages" :key="r.messageId" class="msg-result-item" @click="jumpToMessage(r.conversationId, r.messageId)">
            <div class="msg-result-conv">{{ r.conversationTitle }}</div>
            <div class="msg-result-content">{{ r.content.substring(0, 100) }}{{ r.content.length > 100 ? '…' : '' }}</div>
          </div>
        </template>
        <div v-else class="empty-list">暂无收藏消息</div>
      </template>

      <!-- Search results -->
      <template v-else-if="searchQuery.trim()">
        <template v-if="filteredConversations.length > 0">
          <div class="list-group-label">会话</div>
          <ConvItem
            v-for="conv in filteredConversations" :key="conv.id"
            :conv="conv" :activeId="activeId" :bulkMode="bulkMode" :selected="selected"
            :renamingId="renamingId" :renameText="renameText" :folders="allFolders"
            @select="selectConv" @rename="startRename" @finishRename="finishRename"
            @cancelRename="cancelRename" @pin="id => $emit('pin', id)"
            @setFolder="(id, f) => $emit('setFolder', id, f)"
            @delete="confirmDelete" @toggleSelect="toggleSelect"
            @update:renameText="v => renameText.value = v"
          />
        </template>
        <div v-else class="empty-list">No matching conversations</div>
        <template v-if="searchQuery.trim().length >= 2">
          <div class="list-group-label msg-search-label">
            消息内容 <span v-if="msgSearching" class="searching-dot">...</span>
          </div>
          <template v-if="msgResults.length > 0">
            <div v-for="r in msgResults" :key="r.messageId" class="msg-result-item" @click="jumpToMessage(r.conversationId, r.messageId)">
              <div class="msg-result-conv" v-html="highlightText(r.conversationTitle)"></div>
              <div class="msg-result-content" v-html="highlightText(r.content.substring(0, 80) + (r.content.length > 80 ? '…' : ''))"></div>
            </div>
          </template>
          <div v-else-if="!msgSearching" class="empty-list">No messages found</div>
        </template>
      </template>

      <!-- Normal grouped list -->
      <template v-else>
        <!-- Pinned -->
        <template v-if="pinnedConvs.length > 0">
          <div class="list-group-label">📌 置顶</div>
          <ConvItem
            v-for="conv in pinnedConvs" :key="conv.id"
            :conv="conv" :activeId="activeId" :bulkMode="bulkMode" :selected="selected"
            :renamingId="renamingId" :renameText="renameText" :folders="allFolders"
            @select="selectConv" @rename="startRename" @finishRename="finishRename"
            @cancelRename="cancelRename" @pin="id => $emit('pin', id)"
            @setFolder="(id, f) => $emit('setFolder', id, f)"
            @delete="confirmDelete" @toggleSelect="toggleSelect"
            @update:renameText="v => renameText.value = v"
          />
        </template>
        <!-- Folder groups -->
        <template v-for="folder in folderGroups" :key="'folder-' + folder.name">
          <div class="list-group-label folder-label" @click="toggleFolderCollapse(folder.name)">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              :style="{ transform: collapsedFolders.has(folder.name) ? '' : 'rotate(90deg)', transition: 'transform 0.15s' }">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
            📁 {{ folder.name }}
            <span class="folder-count">{{ folder.items.length }}</span>
          </div>
          <template v-if="!collapsedFolders.has(folder.name)">
            <ConvItem
              v-for="conv in folder.items" :key="conv.id"
              :conv="conv" :activeId="activeId" :bulkMode="bulkMode" :selected="selected"
              :renamingId="renamingId" :renameText="renameText" :folders="allFolders"
              @select="selectConv" @rename="startRename" @finishRename="finishRename"
              @cancelRename="cancelRename" @pin="id => $emit('pin', id)"
              @setFolder="(id, f) => $emit('setFolder', id, f)"
              @delete="confirmDelete" @toggleSelect="toggleSelect"
              @update:renameText="v => renameText.value = v"
            />
          </template>
        </template>
        <!-- Date groups (unpinned, unfoldered) -->
        <template v-for="group in dateGroups" :key="group.label">
          <div class="list-group-label">{{ group.label }}</div>
          <ConvItem
            v-for="conv in group.items" :key="conv.id"
            :conv="conv" :activeId="activeId" :bulkMode="bulkMode" :selected="selected"
            :renamingId="renamingId" :renameText="renameText" :folders="allFolders"
            @select="selectConv" @rename="startRename" @finishRename="finishRename"
            @cancelRename="cancelRename" @pin="id => $emit('pin', id)"
            @setFolder="(id, f) => $emit('setFolder', id, f)"
            @delete="confirmDelete" @toggleSelect="toggleSelect"
            @update:renameText="v => renameText.value = v"
          />
        </template>
        <div v-if="conversations.length === 0" class="empty-list">No conversations yet</div>
      </template>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch, reactive } from 'vue'
import { dateGroup } from '../utils/time.js'
import * as api from '../api/index.js'
import ConvItem from './ConvItem.vue'

const props = defineProps({
  conversations: Array,
  activeId: String,
  width: { type: Number, default: 280 }
})

const emit = defineEmits(['select', 'selectMessage', 'new', 'delete', 'rename', 'openSettings', 'pin', 'setFolder', 'import', 'bulkDelete'])

const renamingId = ref(null)
const renameText = ref('')
const searchQuery = ref('')
const theme = ref('light')
const sidebarOpen = ref(false)
const msgResults = ref([])
const msgSearching = ref(false)
const importInput = ref(null)

const bulkMode = ref(false)
const selected = reactive(new Set())

const showStarred = ref(false)
const starredMessages = ref([])
const starredLoading = ref(false)

const collapsedFolders = reactive(new Set())

let searchTimer = null

watch(searchQuery, (q) => {
  clearTimeout(searchTimer)
  msgResults.value = []
  if (q.trim().length < 2) return
  msgSearching.value = true
  searchTimer = setTimeout(async () => {
    try { msgResults.value = await api.searchMessages(q.trim()) } catch {}
    msgSearching.value = false
  }, 500)
})

const themeTitle = computed(() => theme.value === 'light' ? 'Switch to dark mode' : 'Switch to light mode')

const filteredConversations = computed(() => {
  if (!searchQuery.value.trim()) return props.conversations
  const q = searchQuery.value.toLowerCase()
  return props.conversations.filter(c => c.title.toLowerCase().includes(q))
})

const pinnedConvs = computed(() =>
  props.conversations.filter(c => c.pinned && !c.folderName))

const allFolders = computed(() => {
  const names = new Set(props.conversations.map(c => c.folderName).filter(Boolean))
  return [...names]
})

const folderGroups = computed(() =>
  allFolders.value.map(name => ({
    name,
    items: props.conversations.filter(c => c.folderName === name)
  }))
)

const dateGroups = computed(() => {
  const list = props.conversations.filter(c => !c.pinned && !c.folderName)
  const groups = { '今天': [], '昨天': [], '本周': [], '更早': [] }
  for (const conv of list) {
    const label = dateGroup(conv.updatedAt || conv.createdAt)
    groups[label].push(conv)
  }
  return ['今天', '昨天', '本周', '更早']
    .filter(k => groups[k].length > 0)
    .map(k => ({ label: k, items: groups[k] }))
})

function selectConv(id) { sidebarOpen.value = false; emit('select', id) }
function jumpToMessage(conversationId, messageId) { sidebarOpen.value = false; emit('selectMessage', conversationId, messageId) }
function toggleFolderCollapse(name) { collapsedFolders.has(name) ? collapsedFolders.delete(name) : collapsedFolders.add(name) }
function toggleBulkMode() { bulkMode.value = !bulkMode.value; if (!bulkMode.value) selected.clear() }
function toggleSelect(id) { selected.has(id) ? selected.delete(id) : selected.add(id) }
function selectAll() { props.conversations.forEach(c => selected.add(c.id)) }

function bulkDelete() {
  if (selected.size === 0) return
  if (window.confirm(`确认删除选中的 ${selected.size} 个对话？`)) {
    emit('bulkDelete', [...selected]); selected.clear(); bulkMode.value = false
  }
}

async function toggleStarred() {
  showStarred.value = !showStarred.value
  if (showStarred.value && starredMessages.value.length === 0) {
    starredLoading.value = true
    try { starredMessages.value = await api.getStarredMessages() } catch {}
    starredLoading.value = false
  }
}

onMounted(() => {
  const saved = localStorage.getItem('theme')
  if (saved) theme.value = saved
  else if (window.matchMedia('(prefers-color-scheme: dark)').matches) theme.value = 'dark'
  applyTheme()
})

function applyTheme() { document.documentElement.setAttribute('data-theme', theme.value) }
function toggleTheme() { theme.value = theme.value === 'light' ? 'dark' : 'light'; localStorage.setItem('theme', theme.value); applyTheme() }

function startRename(conv) {
  renamingId.value = conv.id
  renameText.value = conv.title
}
function finishRename(conv) {
  if (renameText.value.trim() && renameText.value.trim() !== conv.title) emit('rename', conv.id, renameText.value.trim())
  renamingId.value = null
}
function cancelRename() { renamingId.value = null }

function highlightText(text) {
  if (!searchQuery.value.trim()) return text
  const escaped = searchQuery.value.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return text.replace(new RegExp('(' + escaped + ')', 'gi'), '<mark>$1</mark>')
}

function confirmDelete(conv) {
  if (window.confirm('Delete conversation "' + conv.title + '"?')) emit('delete', conv.id)
}

async function onImportFile(e) {
  const file = e.target.files[0]
  if (!file) return
  importInput.value.value = ''
  try {
    const text = await file.text()
    let title = file.name.replace(/\.(json|md|txt)$/i, '')
    let messages = []
    if (file.name.endsWith('.json')) {
      const data = JSON.parse(text)
      title = data.title || title
      messages = data.messages || []
    } else {
      const blocks = text.split(/\n---\n/)
      for (const block of blocks) {
        const lines = block.trim().split('\n')
        if (!lines.length) continue
        const header = lines[0].trim()
        const content = lines.slice(2).join('\n').trim()
        if (!content) continue
        if (header === '**You**') messages.push({ role: 'user', content })
        else if (header === '**Ayer**') messages.push({ role: 'assistant', content })
      }
      const m = text.match(/^#\s+(.+)/m)
      if (m) title = m[1].trim()
    }
    emit('import', title, messages)
  } catch (err) { alert('导入失败: ' + err.message) }
}

defineExpose({ sidebarOpen })
</script>

<style scoped>
.sidebar {
  width: 280px; min-width: 280px; height: 100%; overflow: hidden;
  background: var(--bg-sidebar); display: flex; flex-direction: column;
  border-right: 1px solid var(--border-color); transition: transform 0.25s ease; z-index: 100;
}
.sidebar-header { padding: 16px; border-bottom: 1px solid var(--border-color); overflow: hidden; }
.logo { display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 600; margin-bottom: 12px; color: var(--text-primary); }
.header-actions { display: flex; align-items: center; gap: 6px; }
.file-hidden { display: none; }

.btn-icon {
  width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;
  background: var(--bg-card); border-radius: 8px; color: var(--text-secondary); flex-shrink: 0; transition: background 0.15s;
}
.btn-icon:hover { background: var(--bg-hover); color: var(--text-primary); }
.btn-icon.active { color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }

.btn-new-chat {
  display: flex; align-items: center; justify-content: center; gap: 6px; flex: 1;
  padding: 8px; border-radius: 8px; background: var(--bg-card); color: var(--text-primary); font-size: 13px; transition: background 0.15s;
}
.btn-new-chat:hover { background: var(--bg-hover); }

.bulk-bar {
  display: flex; align-items: center; gap: 8px; padding: 6px 12px;
  background: color-mix(in srgb, var(--accent) 8%, transparent); border-bottom: 1px solid var(--border-color);
}
.bulk-count { font-size: 12px; color: var(--text-secondary); flex: 1; }
.bulk-btn {
  padding: 4px 10px; font-size: 11px; background: var(--bg-card);
  border: 1px solid var(--border-color); border-radius: 5px; color: var(--text-secondary); transition: all 0.15s;
}
.bulk-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.bulk-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.bulk-delete:hover:not(:disabled) { border-color: var(--danger) !important; color: var(--danger) !important; }

.search-box {
  display: flex; align-items: center; gap: 8px; margin: 8px;
  padding: 8px 12px; background: var(--bg-card); border-radius: 8px;
  border: 1px solid var(--border-color); color: var(--text-muted);
}
.search-box:focus-within { border-color: var(--accent); }
.search-input { flex: 1; background: none; border: none; color: var(--text-primary); font-size: 13px; line-height: 1.5; }
.search-input::placeholder { color: var(--text-muted); }

.btn-star-filter {
  background: none; border: none; color: var(--text-muted); padding: 2px;
  border-radius: 4px; cursor: pointer; transition: color 0.15s; flex-shrink: 0;
}
.btn-star-filter.active { color: #f59e0b; }
.btn-star-filter:hover { color: #f59e0b; }

.conversation-list { flex: 1; overflow-y: auto; padding: 4px 8px 8px; }

.list-group-label {
  padding: 14px 12px 5px; font-size: 11px; font-weight: 600;
  color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px;
}
.folder-label {
  cursor: pointer; display: flex; align-items: center; gap: 5px; user-select: none;
  text-transform: none; letter-spacing: 0; font-size: 12px;
}
.folder-label:hover { color: var(--text-secondary); }
.folder-count { margin-left: auto; font-size: 11px; opacity: 0.6; }

.empty-list { padding: 24px 12px; text-align: center; color: var(--text-muted); font-size: 13px; }

.msg-search-label { display: flex; align-items: center; gap: 6px; }
.searching-dot { color: var(--accent); font-size: 11px; }
.msg-result-item { padding: 8px 12px; border-radius: 8px; cursor: pointer; transition: background 0.15s; }
.msg-result-item:hover { background: var(--bg-hover); }
.msg-result-conv { font-size: 11px; color: var(--accent); font-weight: 500; margin-bottom: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.msg-result-content { font-size: 12px; color: var(--text-muted); line-height: 1.4; }

:deep(mark) {
  background: color-mix(in srgb, var(--accent) 25%, transparent);
  color: var(--accent); font-weight: 600; border-radius: 2px; padding: 0 1px;
}

@media (max-width: 768px) {
  .sidebar { position: fixed; left: 0; top: 0; transform: translateX(-100%); }
  .sidebar.open { transform: translateX(0); }
}
</style>
