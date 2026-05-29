<template>
  <div class="welcome">
    <div class="welcome-content">
      <div class="welcome-logo">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <h1>How can I help you?</h1>
      <p class="welcome-subtitle">Ask me anything, I'm here to help.</p>
    </div>
    <ChatInput :disabled="false" @send="$emit('send', $event)" />
  </div>
</template>

<script setup>
import ChatInput from './ChatInput.vue'
defineEmits(['send'])
</script>

<style scoped>
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.welcome-content {
  text-align: center;
  margin-bottom: 48px;
}

.welcome-logo {
  color: var(--accent);
  margin-bottom: 24px;
}

.welcome-content h1 {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.welcome-subtitle {
  color: var(--text-secondary);
  font-size: 14px;
}

.chat-input-wrapper {
  width: 100%;
  max-width: 700px;
}
</style>
