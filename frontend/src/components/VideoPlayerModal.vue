<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="video-modal-overlay" @click.self="close">
        <div class="video-modal">
          <div class="video-modal-header">
            <span class="video-modal-title">{{ task?.prompt?.substring(0, 40) }}{{ task?.prompt?.length > 40 ? '...' : '' }}</span>
            <button class="btn-close" @click="close">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="video-modal-body">
            <video
              ref="videoRef"
              :src="videoUrl"
              controls
              autoplay
              class="video-player"
              @error="onError"
            >
              您的浏览器不支持视频播放
            </video>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getVideoUrl } from '../api/index.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  task: { type: Object, default: null }
})

const emit = defineEmits(['close'])

const videoRef = ref(null)
const error = ref(false)

const videoUrl = computed(() => {
  if (!props.task) return ''
  return getVideoUrl(props.task.id)
})

function close() {
  error.value = false
  if (videoRef.value) {
    videoRef.value.pause()
    videoRef.value.currentTime = 0
  }
  emit('close')
}

function onError() {
  error.value = true
}
</script>

<style scoped>
.video-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 24px;
}

.video-modal {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  width: 100%;
  max-width: 900px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.3);
}

.video-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.video-modal-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.btn-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border-radius: 6px;
  color: var(--text-muted);
  padding: 0;
}
.btn-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.video-modal-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  min-height: 300px;
}

.video-player {
  width: 100%;
  max-height: 80vh;
  outline: none;
}

/* Transition */
.modal-enter-active { transition: opacity 0.2s ease; }
.modal-leave-active { transition: opacity 0.15s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
