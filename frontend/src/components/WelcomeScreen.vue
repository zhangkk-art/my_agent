<template>
  <div class="welcome">
    <div class="welcome-content">
      <div class="welcome-logo">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor" stroke="none">
          <path d="M10 2 L11.2 8.8 L18 10 L11.2 11.2 L10 18 L8.8 11.2 L2 10 L8.8 8.8 Z"/>
          <path d="M18.5 13 L19.2 16.3 L22.5 17 L19.2 17.7 L18.5 21 L17.8 17.7 L14.5 17 L17.8 16.3 Z"/>
        </svg>
      </div>
      <h1>Hi, I'm Ayer.</h1>
      <p class="welcome-subtitle">Ask me anything, I'm here to help.</p>
    </div>

    <!-- Workflow Templates -->
    <div v-if="workflows.length > 0" class="workflow-section">
      <div class="workflow-label">快速开始</div>
      <div class="workflow-grid">
        <button
          v-for="wf in workflows"
          :key="wf.id"
          class="workflow-card"
          @click="$emit('createFromTemplate', wf)"
        >
          <div class="workflow-card-name">{{ wf.name }}</div>
          <div v-if="wf.description" class="workflow-card-desc">{{ wf.description }}</div>
          <div v-if="wf.initialMessage" class="workflow-card-hint">{{ wf.initialMessage.substring(0, 50) }}{{ wf.initialMessage.length > 50 ? '…' : '' }}</div>
        </button>
      </div>
    </div>

    <div class="quick-questions-row">
      <div v-if="questionsLoading" class="questions-loading">正在生成今日推荐问题…</div>
      <template v-else-if="quickQuestions.length > 0">
        <div class="quick-questions" :class="{ 'is-hidden': !visible }">
          <button
            v-for="q in quickQuestions"
            :key="q"
            class="quick-btn"
            @click="$emit('send', q)"
          >{{ q }}</button>
        </div>
        <button class="btn-refresh" title="换一批问题" @click="refresh">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"/>
            <path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/>
          </svg>
        </button>
      </template>
    </div>
    <ChatInput :disabled="false" @send="(msg, imgs) => $emit('send', msg, imgs)" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import ChatInput from './ChatInput.vue'
import * as api from '../api/index.js'

defineEmits(['send', 'createFromTemplate'])

const CACHE_KEY = () => `daily-questions-${new Date().toISOString().slice(0, 10)}`

// Pick 4 random questions from an array
function pickRandom(pool) {
  const arr = [...pool]
  const result = []
  while (result.length < 4 && arr.length > 0) {
    const i = Math.floor(Math.random() * arr.length)
    result.push(arr.splice(i, 1)[0])
  }
  return result
}

const dailyPool = ref([])
const quickQuestions = ref([])
const visible = ref(true)
const workflows = ref([])
const questionsLoading = ref(false)

async function loadDailyPool() {
  // 1. Try localStorage cache for today
  const key = CACHE_KEY()
  const cached = localStorage.getItem(key)
  if (cached) {
    try {
      const pool = JSON.parse(cached)
      if (Array.isArray(pool) && pool.length > 0) {
        dailyPool.value = pool
        quickQuestions.value = pickRandom(pool)
        return
      }
    } catch {}
  }
  // 2. Fetch from backend (AI-generated)
  questionsLoading.value = true
  try {
    const pool = await api.getDailyQuestions()
    if (pool.length > 0) {
      dailyPool.value = pool
      quickQuestions.value = pickRandom(pool)
      localStorage.setItem(key, JSON.stringify(pool))
      // Clean up yesterday's cache entry
      for (const k of Object.keys(localStorage)) {
        if (k.startsWith('daily-questions-') && k !== key) localStorage.removeItem(k)
      }
    }
  } catch {
    // 3. Silent fallback — questions stay empty, UI hides gracefully
  } finally {
    questionsLoading.value = false
  }
}

function refresh() {
  if (dailyPool.value.length === 0) return
  visible.value = false
  setTimeout(() => {
    quickQuestions.value = pickRandom(dailyPool.value)
    visible.value = true
  }, 280)
}

let timer
onMounted(() => {
  loadDailyPool()
  timer = setInterval(refresh, 5 * 60 * 1000)
  api.getWorkflowTemplates().then(list => { workflows.value = list }).catch(() => {})
})
onUnmounted(() => { clearInterval(timer) })
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
  margin-bottom: 32px;
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

/* Workflow templates */
.workflow-section {
  width: 100%;
  max-width: 560px;
  margin-bottom: 24px;
}

.workflow-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 10px;
  text-align: left;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.workflow-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s;
}
.workflow-card:hover {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 5%, var(--bg-card));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.workflow-card-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.workflow-card-desc {
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 1.4;
  margin-bottom: 4px;
}

.workflow-card-hint {
  font-size: 11px;
  color: var(--text-muted);
  font-style: italic;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-questions-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 24px;
  min-height: 36px;
}

.questions-loading {
  font-size: 12px;
  color: var(--text-muted);
  font-style: italic;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  flex: 1;
  max-width: 560px;
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.btn-refresh {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-muted);
  transition: all 0.2s;
}
.btn-refresh:hover {
  background: var(--bg-hover);
  color: var(--accent);
  border-color: var(--accent);
}
.btn-refresh:active {
  transform: rotate(90deg);
  transition: transform 0.15s;
}
.quick-questions.is-hidden {
  opacity: 0;
  transform: translateY(6px);
}

.quick-btn {
  padding: 8px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  color: var(--text-secondary);
  font-size: 13px;
  transition: all 0.15s;
}
.quick-btn:hover {
  background: var(--bg-hover);
  border-color: var(--accent);
  color: var(--accent);
}

@media (max-width: 768px) {
  .workflow-grid {
    grid-template-columns: 1fr 1fr;
  }
  .quick-questions {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
  .quick-btn {
    text-align: left;
  }
}
</style>
