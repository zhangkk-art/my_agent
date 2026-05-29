<template>
  <div class="message-list" ref="listRef" @scroll="onScroll">
    <MessageBubble
      v-for="(msg, index) in messages"
      :key="msg.id"
      :message="msg"
      :isStreaming="loading && msg === messages[messages.length - 1] && msg.role === 'assistant'"
      :isLastAi="msg.role === 'assistant' && index === messages.length - 1 && !loading"
      @regenerate="$emit('regenerate')"
      @editMessage="(id, content) => $emit('editMessage', id, content)"
      @deleteMessage="id => $emit('deleteMessage', id)"
    />
    <div v-if="messages.length === 0" class="empty-messages">
      Send a message to start the conversation.
    </div>
    <button v-if="showScrollBtn" class="btn-scroll-bottom" @click="scrollToBottom">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="6 9 12 15 18 9"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'

const props = defineProps({
  messages: Array,
  loading: Boolean
})

defineEmits(['regenerate', 'editMessage', 'deleteMessage'])

const listRef = ref(null)
const showScrollBtn = ref(false)
const userScrolledUp = ref(false)

function isNearBottom() {
  const el = listRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function scrollToBottom() {
  const el = listRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
    userScrolledUp.value = false
    showScrollBtn.value = false
  }
}

function onScroll() {
  userScrolledUp.value = !isNearBottom()
  showScrollBtn.value = userScrolledUp.value
}

watch(
  () => props.messages,
  () => {
    nextTick(() => {
      if (!userScrolledUp.value) {
        scrollToBottom()
      }
    })
  },
  { deep: true }
)
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  position: relative;
}

.empty-messages {
  text-align: center;
  color: var(--text-muted);
  padding: 48px 24px;
  font-size: 14px;
}

.btn-scroll-bottom {
  position: sticky;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 50%;
  color: var(--text-secondary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: background 0.15s;
  z-index: 10;
  margin-top: -48px;
}
.btn-scroll-bottom:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
</style>
