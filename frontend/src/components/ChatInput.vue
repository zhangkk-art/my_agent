<template>
  <div class="chat-input-container">
    <div class="chat-input-wrapper">
      <textarea
        ref="inputRef"
        v-model="text"
        class="chat-input"
        placeholder="Send a message..."
        :disabled="disabled"
        rows="1"
        @keydown="handleKeydown"
        @input="autoResize"
      ></textarea>
      <button
        v-if="loading"
        class="btn-stop"
        @click="$emit('stop')"
        title="Stop generating"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <rect x="4" y="4" width="16" height="16" rx="2" />
        </svg>
      </button>
      <button
        v-else
        class="btn-send"
        :disabled="!text.trim() || disabled"
        @click="send"
        title="Send"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="22" y1="2" x2="11" y2="13"/>
          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const props = defineProps({
  disabled: Boolean,
  loading: Boolean
})

const emit = defineEmits(['send', 'stop'])

const text = ref('')
const inputRef = ref(null)

function autoResize() {
  nextTick(() => {
    const el = inputRef.value
    if (el) {
      el.style.height = 'auto'
      el.style.height = Math.min(el.scrollHeight, 200) + 'px'
    }
  })
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function send() {
  const msg = text.value.trim()
  if (!msg || props.disabled) return
  emit('send', msg)
  text.value = ''
  nextTick(() => {
    const el = inputRef.value
    if (el) {
      el.style.height = 'auto'
    }
  })
}

defineExpose({ focus: () => inputRef.value?.focus() })
</script>

<style scoped>
.chat-input-container {
  padding: 16px 24px 24px;
}

.chat-input-wrapper {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  gap: 8px;
  align-items: flex-end;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 8px 12px;
  transition: border-color 0.15s;
}
.chat-input-wrapper:focus-within {
  border-color: var(--accent);
}

.chat-input {
  flex: 1;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  max-height: 200px;
  padding: 4px 0;
}
.chat-input::placeholder {
  color: var(--text-muted);
}
.chat-input:disabled {
  opacity: 0.5;
}

.btn-send {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent);
  border-radius: 8px;
  color: white;
  transition: background 0.15s;
}
.btn-send:hover:not(:disabled) {
  background: var(--accent-hover);
}
.btn-send:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  background: var(--text-muted);
}

.btn-stop {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--danger);
  border-radius: 8px;
  color: white;
  transition: background 0.15s;
}
.btn-stop:hover {
  background: #d32f2f;
}
</style>
