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
    <div class="quick-questions" :class="{ 'is-hidden': !visible }">
      <button
        v-for="q in quickQuestions"
        :key="q"
        class="quick-btn"
        @click="$emit('send', q)"
      >{{ q }}</button>
    </div>
    <ChatInput :disabled="false" @send="(msg, imgs) => $emit('send', msg, imgs)" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import ChatInput from './ChatInput.vue'

defineEmits(['send'])

const POOL = [
  '用通俗易懂的方式解释一下什么是机器学习',
  '帮我写一段快速排序的 Java 代码',
  '给我讲个冷笑话',
  '有什么提高工作效率的建议',
  '如何学好一门新的编程语言？',
  '帮我解释一下量子计算机的原理',
  '推荐几本值得一读的科幻小说',
  '写一首关于秋天的短诗',
  '什么是 Docker，它解决了什么问题？',
  '如何克服拖延症？',
  '帮我解释一下 TCP/IP 协议',
  '写一段打印斐波那契数列的 Python 代码',
  '如何快速提高英语口语水平？',
  '深度学习和机器学习有什么区别？',
  '推荐几个提高编程效率的工具或插件',
  '用一句话解释什么是区块链',
  '帮我写一个正则表达式，匹配邮箱地址',
  '如何设计一个好的 RESTful API？',
  '有什么好用的时间管理方法？',
  '解释一下什么是设计模式，举个例子',
]

function pick() {
  const pool = [...POOL]
  const result = []
  while (result.length < 4) {
    const i = Math.floor(Math.random() * pool.length)
    result.push(pool.splice(i, 1)[0])
  }
  return result
}

const quickQuestions = ref(pick())
const visible = ref(true)

function refresh() {
  visible.value = false
  setTimeout(() => {
    quickQuestions.value = pick()
    visible.value = true
  }, 280)
}

let timer
onMounted(() => { timer = setInterval(refresh, 15000) })
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

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 560px;
  margin-bottom: 24px;
  transition: opacity 0.28s ease, transform 0.28s ease;
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
