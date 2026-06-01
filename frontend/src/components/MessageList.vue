<template>
  <div class="message-list" ref="listRef" @scroll="onScroll">
    <template v-if="conversationLoading && messages.length === 0">
      <div v-for="i in 4" :key="'s'+i" class="skeleton-row" :class="i % 2 === 0 ? 'right' : 'left'">
        <div class="skeleton-bubble">
          <div class="skeleton-line skeleton-role"></div>
          <div class="skeleton-line skeleton-text" :style="{ width: (40 + Math.random() * 40) + '%' }"></div>
          <div class="skeleton-line skeleton-text" :style="{ width: (20 + Math.random() * 50) + '%' }"></div>
          <div class="skeleton-line skeleton-text-short" :style="{ width: (30 + Math.random() * 30) + '%' }"></div>
        </div>
      </div>
    </template>
    <template v-else>
      <MessageBubble
        v-for="(msg, index) in messages"
        :key="msg.id"
        :message="msg"
        :isStreaming="loading && msg === messages[messages.length - 1] && msg.role === 'assistant'"
        :isLastAi="msg.role === 'assistant' && index === messages.length - 1 && !loading"
        @regenerate="$emit('regenerate')"
        @editMessage="(id, content) => $emit('editMessage', id, content)"
        @deleteMessage="id => $emit('deleteMessage', id)"
        @forkMessage="id => $emit('forkMessage', id)"
        @starMessage="id => $emit('starMessage', id)"
        @rateMessage="(id, v) => $emit('rateMessage', id, v)"
      />
      <div v-if="messages.length === 0" class="empty-messages">
        Send a message to start the conversation.
      </div>
    </template>
    <button v-if="showScrollBtn" class="btn-scroll-bottom" title="回到底部" @click="scrollToBottom">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="6 9 12 15 18 9"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'

function scrollToMessage(messageId) {
  nextTick(() => {
    const el = document.getElementById('msg-' + messageId)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('msg-highlight')
    setTimeout(() => el.classList.remove('msg-highlight'), 2000)
  })
}

defineExpose({ scrollToMessage })

const props = defineProps({
  messages: Array,
  loading: Boolean,
  conversationLoading: Boolean
})

defineEmits(['regenerate', 'editMessage', 'deleteMessage', 'forkMessage', 'starMessage', 'rateMessage'])

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

// When a new message is sent (loading starts), always jump to the new exchange
watch(() => props.loading, (isLoading) => {
  if (isLoading) {
    userScrolledUp.value = false
    nextTick(() => scrollToBottom())
  }
})

// During streaming, keep following the bottom unless user scrolled up.
// Track length + last message content size — avoids deep traversal of the whole array.
watch(
  () => {
    const msgs = props.messages
    const last = msgs[msgs.length - 1]
    return msgs.length * 1e9 + (last?.content?.length ?? 0) + (last?.reasoning?.length ?? 0)
  },
  () => {
    nextTick(() => {
      if (!userScrolledUp.value) scrollToBottom()
    })
  }
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

/* Skeleton */
.skeleton-row {
  padding: 12px 24px;
  display: flex;
}
.skeleton-row.left { justify-content: flex-start; }
.skeleton-row.right { justify-content: flex-end; }

.skeleton-bubble {
  max-width: 70%;
  padding: 16px;
  border-radius: 12px;
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 180px;
}

.skeleton-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--border-color) 25%, var(--bg-hover) 50%, var(--border-color) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-role {
  width: 30px;
  height: 10px;
  margin-bottom: 4px;
}

.skeleton-text-short {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--border-color) 25%, var(--bg-hover) 50%, var(--border-color) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

:global(.msg-highlight) {
  animation: msg-flash 2s ease;
}
@keyframes msg-flash {
  0%, 100% { background: transparent; }
  20% { background: color-mix(in srgb, var(--accent) 15%, transparent); }
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
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
  transition: background 0.15s, transform 0.2s;
  z-index: 10;
  margin-top: -48px;
  animation: scroll-btn-in 0.3s ease;
}
.btn-scroll-bottom:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  transform: translateX(-50%) translateY(-2px);
}

@keyframes scroll-btn-in {
  from { opacity: 0; transform: translateX(-50%) translateY(8px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}
</style>
