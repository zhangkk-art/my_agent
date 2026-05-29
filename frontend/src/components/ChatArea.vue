<template>
  <main class="chat-area">
    <div class="chat-top-bar">
      <slot name="hamburger"></slot>
    </div>
    <WelcomeScreen v-if="!conversation" @send="$emit('send', $event)" />
    <template v-else>
      <MessageList
        :messages="conversation.messages || []"
        :loading="loading"
        @regenerate="$emit('regenerate')"
        @editMessage="(id, content) => $emit('editMessage', id, content)"
        @deleteMessage="id => $emit('deleteMessage', id)"
      />
      <ChatInput
        :disabled="loading"
        :loading="loading"
        @send="$emit('send', $event)"
        @stop="$emit('stop')"
      />
    </template>
  </main>
</template>

<script setup>
import WelcomeScreen from './WelcomeScreen.vue'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'

defineProps({
  conversation: Object,
  loading: Boolean
})

defineEmits(['send', 'stop', 'regenerate', 'editMessage', 'deleteMessage'])
</script>

<style scoped>
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.chat-top-bar {
  display: none;
  padding: 8px 16px;
}

@media (max-width: 768px) {
  .chat-top-bar {
    display: flex;
  }
}
</style>
