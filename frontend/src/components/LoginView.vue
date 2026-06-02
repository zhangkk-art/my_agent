<template>
  <div class="login-bg">
    <div class="login-card">
      <h1 class="brand">Ayer AI</h1>
      <p class="brand-sub">智能对话助手</p>

      <div class="tab-bar">
        <button :class="['tab-btn', { active: mode === 'login' }]" @click="mode = 'login'">登录</button>
        <button :class="['tab-btn', { active: mode === 'register' }]" @click="mode = 'register'">注册</button>
      </div>

      <form @submit.prevent="submit" class="login-form">
        <div class="field">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="请输入用户名" autocomplete="username" required />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="password" type="password"
                 :placeholder="mode === 'register' ? '至少 6 个字符' : '请输入密码'"
                 autocomplete="current-password" required />
        </div>
        <div v-if="mode === 'register'" class="field">
          <label>确认密码</label>
          <input v-model="confirm" type="password" placeholder="再次输入密码" required />
        </div>

        <p v-if="error" class="error-msg">{{ error }}</p>

        <button type="submit" class="submit-btn" :disabled="loading">
          <span v-if="loading" class="spinner"></span>
          {{ loading ? '请稍候…' : (mode === 'login' ? '登录' : '注册') }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { login, register } from '../api/index.js'

const emit = defineEmits(['logged-in'])

const mode     = ref('login')
const username = ref('')
const password = ref('')
const confirm  = ref('')
const error    = ref('')
const loading  = ref(false)

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '用户名和密码不能为空'
    return
  }
  if (mode.value === 'register') {
    if (password.value.length < 6) {
      error.value = '密码至少 6 个字符'
      return
    }
    if (password.value !== confirm.value) {
      error.value = '两次输入的密码不一致'
      return
    }
  }

  loading.value = true
  try {
    const fn   = mode.value === 'login' ? login : register
    const data = await fn(username.value.trim(), password.value)
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    emit('logged-in', data.username)
  } catch (e) {
    error.value = e.message || '操作失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary, #f5f5f5);
}

.login-card {
  width: 360px;
  background: var(--bg-secondary, #fff);
  border-radius: 16px;
  padding: 40px 36px 32px;
  box-shadow: 0 4px 24px rgba(0,0,0,.10);
}

.brand {
  font-size: 28px;
  font-weight: 700;
  color: var(--accent, #2563eb);
  text-align: center;
  margin: 0 0 4px;
}

.brand-sub {
  text-align: center;
  color: var(--text-secondary, #888);
  font-size: 13px;
  margin: 0 0 28px;
}

.tab-bar {
  display: flex;
  border-radius: 8px;
  background: var(--bg-tertiary, #f0f0f0);
  padding: 3px;
  margin-bottom: 24px;
  gap: 4px;
}

.tab-btn {
  flex: 1;
  padding: 8px 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary, #888);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all .18s;
}

.tab-btn.active {
  background: var(--bg-secondary, #fff);
  color: var(--text-primary, #222);
  box-shadow: 0 1px 4px rgba(0,0,0,.12);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-size: 13px;
  color: var(--text-secondary, #666);
  font-weight: 500;
}

.field input {
  padding: 10px 12px;
  border: 1px solid var(--border, #ddd);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-primary, #f9f9f9);
  color: var(--text-primary, #222);
  outline: none;
  transition: border-color .15s;
}

.field input:focus {
  border-color: var(--accent, #2563eb);
}

.error-msg {
  font-size: 13px;
  color: #e53e3e;
  text-align: center;
  margin: 0;
}

.submit-btn {
  margin-top: 4px;
  padding: 11px;
  border: none;
  border-radius: 8px;
  background: var(--accent, #2563eb);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity .15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.submit-btn:disabled {
  opacity: .65;
  cursor: not-allowed;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin .7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
