<template>
  <div class="shortcuts-overlay" @click.self="$emit('close')">
    <div class="shortcuts-panel">
      <div class="shortcuts-header">
        <span class="shortcuts-title">键盘快捷键</span>
        <button class="btn-close" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div class="shortcuts-body">
        <div v-for="group in groups" :key="group.name" class="shortcut-group">
          <div class="group-name">{{ group.name }}</div>
          <div v-for="s in group.items" :key="s.desc" class="shortcut-row">
            <span class="shortcut-desc">{{ s.desc }}</span>
            <div class="shortcut-keys">
              <kbd v-for="k in s.keys" :key="k" class="key">{{ k }}</kbd>
            </div>
          </div>
        </div>
      </div>
      <div class="shortcuts-footer">按 <kbd class="key">{{ mod }} + /</kbd> 随时打开此面板</div>
    </div>
  </div>
</template>

<script setup>
const isMac = typeof navigator !== 'undefined' && /mac/i.test(navigator.platform)
const mod = isMac ? '⌘' : 'Ctrl'

const groups = [
  {
    name: '导航',
    items: [
      { desc: '搜索对话', keys: [mod, 'K'] },
      { desc: '打开设置', keys: [mod, ','] },
      { desc: '显示快捷键帮助', keys: [mod, '/'] },
    ]
  },
  {
    name: '对话',
    items: [
      { desc: '发送消息', keys: [mod, 'Enter'] },
      { desc: '停止生成 / 关闭面板', keys: ['Esc'] },
      { desc: '填充上条消息', keys: ['↑（输入框为空时）'] },
      { desc: '清除历史填充', keys: ['↓'] },
    ]
  },
]

defineEmits(['close'])
</script>

<style scoped>
.shortcuts-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fade-in 0.15s ease;
}

@keyframes fade-in {
  from { opacity: 0 }
  to   { opacity: 1 }
}

.shortcuts-panel {
  width: 400px;
  max-width: calc(100vw - 32px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0,0,0,0.25);
  animation: slide-up 0.15s ease;
}

@keyframes slide-up {
  from { transform: translateY(12px); opacity: 0 }
  to   { transform: translateY(0);    opacity: 1 }
}

.shortcuts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--border-color);
}

.shortcuts-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.btn-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: var(--text-muted);
  transition: background 0.15s, color 0.15s;
}
.btn-close:hover { background: var(--bg-hover); color: var(--text-primary); }

.shortcuts-body {
  padding: 12px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.shortcut-group { display: flex; flex-direction: column; gap: 4px; }

.group-name {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.shortcut-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 50%, transparent);
}
.shortcut-row:last-child { border-bottom: none; }

.shortcut-desc {
  font-size: 13px;
  color: var(--text-secondary);
}

.shortcut-keys {
  display: flex;
  align-items: center;
  gap: 4px;
}

.key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  height: 22px;
  padding: 0 6px;
  font-size: 11px;
  font-family: inherit;
  font-weight: 500;
  color: var(--text-primary);
  background: var(--bg-hover);
  border: 1px solid var(--border-color);
  border-radius: 5px;
  box-shadow: 0 1px 0 var(--border-color);
  white-space: nowrap;
}

.shortcuts-footer {
  padding: 10px 20px;
  border-top: 1px solid var(--border-color);
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}
</style>
