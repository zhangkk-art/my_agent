<template>
  <div
    class="conversation-item"
    :class="{ active: conv.id === activeId, 'is-selected': selected?.has(conv.id) }"
    @click="bulkMode ? $emit('toggleSelect', conv.id) : $emit('select', conv.id)"
  >
    <input
      v-if="bulkMode"
      type="checkbox"
      class="conv-checkbox"
      :checked="selected?.has(conv.id)"
      @click.stop
      @change="$emit('toggleSelect', conv.id)"
    />
    <svg v-else class="conv-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>
    </svg>

    <span class="conv-title" :title="conv.title" @dblclick.stop="$emit('rename', conv)">
      <template v-if="renamingId === conv.id">
        <input
          ref="renameInputRef"
          v-model="localRenameText"
          class="rename-input"
          @blur="$emit('finishRename', conv)"
          @keydown.enter="$emit('finishRename', conv)"
          @keydown.escape="$emit('cancelRename')"
          @click.stop
        />
      </template>
      <template v-else>{{ conv.title }}</template>
    </span>

    <div v-if="!bulkMode" class="conv-actions">
      <button
        class="btn-conv-action"
        :class="{ pinned: conv.pinned }"
        :title="conv.pinned ? '取消置顶' : '置顶'"
        @click.stop="$emit('pin', conv.id)"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="17" x2="12" y2="22"/>
          <path d="M5 17h14v-1.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V6h1a2 2 0 000-4H8a2 2 0 000 4h1v4.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005 15.24V17z"/>
        </svg>
      </button>

      <div class="folder-picker" @click.stop>
        <button class="btn-conv-action" :class="{ 'has-folder': conv.folderName }" title="文件夹">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/>
          </svg>
        </button>
        <div class="folder-dropdown">
          <div class="folder-opt" @click="$emit('setFolder', conv.id, null)">无文件夹</div>
          <div
            v-for="f in folders" :key="f"
            class="folder-opt"
            :class="{ active: conv.folderName === f }"
            @click="$emit('setFolder', conv.id, f)"
          >{{ f }}</div>
          <div class="folder-new-row">
            <input
              class="folder-new-input"
              placeholder="新建文件夹…"
              @keydown.enter.stop="onNewFolder"
              @click.stop
            />
          </div>
        </div>
      </div>

      <button class="btn-conv-action btn-conv-delete" title="删除" @click.stop="$emit('delete', conv)">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="3 6 5 6 21 6"/>
          <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  conv: Object,
  activeId: String,
  bulkMode: Boolean,
  selected: Object,
  renamingId: String,
  renameText: String,
  folders: Array,
})

const emit = defineEmits(['select', 'rename', 'finishRename', 'cancelRename', 'pin', 'setFolder', 'delete', 'toggleSelect'])

const renameInputRef = ref(null)
const localRenameText = ref(props.renameText || '')

watch(() => props.renameText, v => { localRenameText.value = v || '' })
watch(() => props.renamingId, id => {
  if (id === props.conv.id) {
    nextTick(() => { renameInputRef.value?.focus(); renameInputRef.value?.select() })
  }
})

// Sync local text back up when finishing rename
watch(localRenameText, v => {
  if (props.renamingId === props.conv.id) emit('update:renameText', v)
})

function onNewFolder(e) {
  const val = e.target.value.trim()
  if (val) {
    emit('setFolder', props.conv.id, val)
    e.target.value = ''
  }
}
</script>

<style scoped>
.conversation-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  border-radius: 8px; cursor: pointer; transition: background 0.15s; position: relative;
}
.conversation-item:hover { background: var(--bg-hover); }
.conversation-item.active { background: var(--bg-card); }
.conversation-item.is-selected { background: color-mix(in srgb, var(--accent) 12%, transparent); }

.conv-checkbox { flex-shrink: 0; width: 14px; height: 14px; cursor: pointer; }
.conv-icon { flex-shrink: 0; color: var(--text-muted); }
.conv-title {
  flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  font-size: 13px; color: var(--text-secondary);
}
.rename-input {
  width: 100%; background: var(--bg-input); border: 1px solid var(--accent);
  border-radius: 4px; padding: 2px 6px; color: var(--text-primary); font-size: 13px;
}

.conv-actions { display: none; align-items: center; gap: 2px; flex-shrink: 0; }
.conversation-item:hover .conv-actions { display: flex; }

.btn-conv-action {
  width: 22px; height: 22px; display: flex; align-items: center; justify-content: center;
  background: none; color: var(--text-muted); border-radius: 4px; transition: all 0.15s;
}
.btn-conv-action:hover { color: var(--text-primary); background: var(--bg-card); }
.btn-conv-action.pinned { color: var(--accent); }
.btn-conv-action.has-folder { color: var(--accent); }
.btn-conv-delete:hover { color: var(--danger) !important; }

.folder-picker { position: relative; }
.folder-dropdown {
  display: none; position: absolute; right: 0; top: 100%; z-index: 50;
  background: var(--bg-primary); border: 1px solid var(--border-color);
  border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.12); min-width: 140px; overflow: hidden;
}
.folder-picker:hover .folder-dropdown { display: block; }
.folder-opt {
  padding: 7px 12px; font-size: 12px; color: var(--text-secondary);
  cursor: pointer; transition: background 0.1s; white-space: nowrap;
}
.folder-opt:hover { background: var(--bg-hover); color: var(--text-primary); }
.folder-opt.active { color: var(--accent); font-weight: 500; }
.folder-new-row { padding: 4px 8px 8px; border-top: 1px solid var(--border-color); }
.folder-new-input {
  width: 100%; background: var(--bg-input); border: 1px solid var(--border-color);
  border-radius: 5px; padding: 4px 8px; font-size: 12px; color: var(--text-primary);
  outline: none; box-sizing: border-box;
}
.folder-new-input:focus { border-color: var(--accent); }
</style>
