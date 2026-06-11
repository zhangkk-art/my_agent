const BASE_URL = '/api';

const SSE_BUFFER_LIMIT = 1024 * 1024; // 1MB

function getToken() {
  return localStorage.getItem('token');
}

function authHeaders() {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function apiFetch(url, options = {}) {
  const res = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      ...authHeaders(),
    },
  });
  if (res.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    window.dispatchEvent(new CustomEvent('auth:logout'));
    throw new Error('Unauthorized');
  }
  return res;
}

function streamSse(url, body, onReasoning, onChunk, onDone, onError, signal) {
  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
    signal
  }).then(response => {
    if (response.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      window.dispatchEvent(new CustomEvent('auth:logout'));
      onError(new Error('Unauthorized'));
      return;
    }
    if (!response.ok) throw new Error('Stream request failed');
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let finished = false;

    function finish(messageId) {
      if (finished) return;
      finished = true;
      try { reader.cancel(); } catch {}
      onDone(messageId);
    }

    async function read() {
      try {
        const { done, value } = await reader.read();
        if (done) { finish(); return; }
        buffer += decoder.decode(value, { stream: true });
        if (buffer.length > SSE_BUFFER_LIMIT) {
          reader.cancel();
          onError(new Error('SSE response exceeded maximum buffer size'));
          return;
        }
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.substring(6);
            if (data === '[DONE]') { finish(); return; }
            try {
              const parsed = JSON.parse(data);
              if (parsed.error) { onError(new Error(parsed.error)); return; }
              if (parsed.done) { finish(parsed.messageId); return; }
              if (parsed.reasoning) onReasoning(parsed.reasoning);
              if (parsed.content) {
                onChunk(parsed.content);
                await new Promise(r => requestAnimationFrame(r));
              }
            } catch (e) {
              console.warn('SSE parse error:', e.message, 'data:', data.substring(0, 100));
            }
          }
        }
        read();
      } catch (err) {
        if (err.name === 'AbortError') finish();
        else onError(err);
      }
    }
    read();
  }).catch(err => {
    if (err.name !== 'AbortError') onError(err);
  });
}

// ── Auth ──

export async function login(username, password) {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || '用户名或密码错误');
  }
  return res.json();
}

export async function register(username, password) {
  const res = await fetch(`${BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || '注册失败');
  }
  return res.json();
}

// ── Chat ──

export async function sendMessage(conversationId, message, model) {
  const res = await apiFetch(`${BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message, model })
  });
  if (!res.ok) throw new Error('Chat request failed');
  return res.json();
}

export function sendMessageStream(conversationId, message, model, webSearch, temperature, maxTokens, onReasoning, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/stream`, { conversationId, message, model, webSearch, temperature, maxTokens }, onReasoning, onChunk, onDone, onError, signal);
}

export function sendImageStream(conversationId, message, model, images, webSearch, temperature, maxTokens, onReasoning, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/image`, { conversationId, message, model, images, webSearch, temperature, maxTokens }, onReasoning, onChunk, onDone, onError, signal);
}

export function regenerateStream(conversationId, message, model, webSearch, temperature, maxTokens, onReasoning, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/regenerate`, { conversationId, message, model, webSearch, temperature, maxTokens }, onReasoning, onChunk, onDone, onError, signal);
}

export async function savePartialMessage(conversationId, content, reasoning) {
  const res = await apiFetch(`${BASE_URL}/messages/save-partial`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, content, reasoning })
  });
  if (!res.ok) throw new Error('Failed to save partial message');
  return res.json();
}

export async function saveInterruptedUpdate(messageId, content) {
  const res = await apiFetch(`${BASE_URL}/messages/${messageId}/interrupted`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  });
  if (!res.ok) throw new Error('Failed to update interrupted message');
  return res.json();
}

export function continueStream(conversationId, messageId, model, temperature, maxTokens, onReasoning, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/continue`, { conversationId, messageId, model, temperature, maxTokens }, onReasoning, onChunk, onDone, onError, signal);
}

export async function getConversations() {
  const res = await apiFetch(`${BASE_URL}/conversations`);
  if (!res.ok) throw new Error('Failed to fetch conversations');
  return res.json();
}

export async function createConversation(title) {
  const res = await apiFetch(`${BASE_URL}/conversations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: title || 'New Chat' })
  });
  if (!res.ok) throw new Error('Failed to create conversation');
  return res.json();
}

export async function getConversation(id, signal) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}`, { signal });
  if (!res.ok) throw new Error('Failed to fetch conversation');
  return res.json();
}

export async function renameConversation(id, title) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title })
  });
  if (!res.ok) throw new Error('Failed to rename conversation');
  return res.json();
}

export async function touchConversation(id) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}/touch`, { method: 'PATCH' });
  if (!res.ok) throw new Error('Failed to touch conversation');
  return res.json();
}

export async function deleteConversation(id) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete conversation');
}

export async function updateMessage(id, content) {
  const res = await apiFetch(`${BASE_URL}/messages/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  });
  if (!res.ok) throw new Error('Failed to update message');
  return res.json();
}

export async function deleteMessage(id) {
  const res = await apiFetch(`${BASE_URL}/messages/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete message');
}

export async function updateSystemPrompt(id, systemPrompt) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}/system-prompt`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ systemPrompt: systemPrompt || '' })
  });
  if (!res.ok) throw new Error('Failed to update system prompt');
  return res.json();
}

export async function searchMessages(q, signal) {
  const res = await apiFetch(`${BASE_URL}/messages/search?q=${encodeURIComponent(q)}`, { signal });
  if (!res.ok) throw new Error('Search failed');
  return res.json();
}

export async function shareConversation(id) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}/share`, { method: 'POST' });
  if (!res.ok) throw new Error('Failed to share conversation');
  return res.json();
}

export async function revokeShare(id) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}/share`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to revoke share');
}

export async function pinConversation(id) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}/pin`, { method: 'PATCH' });
  if (!res.ok) throw new Error('Failed to toggle pin');
  return res.json();
}

export async function setConversationFolder(id, folderName) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}/folder`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ folderName: folderName || null })
  });
  if (!res.ok) throw new Error('Failed to set folder');
  return res.json();
}

export async function importConversation(title, messages) {
  const res = await apiFetch(`${BASE_URL}/conversations/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, messages })
  });
  if (!res.ok) throw new Error('Failed to import conversation');
  return res.json();
}

export async function starMessage(id) {
  const res = await apiFetch(`${BASE_URL}/messages/${id}/star`, { method: 'PATCH' });
  if (!res.ok) throw new Error('Failed to star message');
  return res.json();
}

export async function rateMessage(id, rating) {
  const res = await apiFetch(`${BASE_URL}/messages/${id}/rating`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rating })
  });
  if (!res.ok) throw new Error('Failed to rate message');
  return res.json();
}

export async function getStarredMessages() {
  const res = await apiFetch(`${BASE_URL}/messages/starred`);
  if (!res.ok) throw new Error('Failed to fetch starred messages');
  return res.json();
}

export async function forkConversation(id, messageId) {
  const res = await apiFetch(`${BASE_URL}/conversations/${id}/fork`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messageId })
  });
  if (!res.ok) throw new Error('Failed to fork conversation');
  return res.json();
}

// ── Workflow Templates ──

export async function getWorkflowTemplates() {
  const res = await apiFetch(`${BASE_URL}/workflow-templates`);
  if (!res.ok) throw new Error('Failed to fetch workflow templates');
  return res.json();
}

export async function createWorkflowTemplate(data) {
  const res = await apiFetch(`${BASE_URL}/workflow-templates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error('Failed to create workflow template');
  return res.json();
}

export async function updateWorkflowTemplate(id, data) {
  const res = await apiFetch(`${BASE_URL}/workflow-templates/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error('Failed to update workflow template');
  return res.json();
}

export async function deleteWorkflowTemplate(id) {
  const res = await apiFetch(`${BASE_URL}/workflow-templates/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete workflow template');
}

export async function getDailyQuestions() {
  const res = await apiFetch(`${BASE_URL}/questions/daily`);
  if (!res.ok) throw new Error('Failed to fetch daily questions');
  return res.json();
}

export async function getSharedConversation(token) {
  // Public endpoint — no auth header needed
  const res = await fetch(`${BASE_URL}/shared/${token}`);
  if (!res.ok) throw new Error('Shared conversation not found');
  return res.json();
}

// ── Prompt templates ──

export async function getPromptTemplates() {
  const res = await apiFetch(`${BASE_URL}/prompt-templates`);
  if (!res.ok) throw new Error('Failed to fetch templates');
  return res.json();
}

export async function createPromptTemplate(name, content) {
  const res = await apiFetch(`${BASE_URL}/prompt-templates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, content })
  });
  if (!res.ok) throw new Error('Failed to create template');
  return res.json();
}

export async function updatePromptTemplate(id, name, content) {
  const res = await apiFetch(`${BASE_URL}/prompt-templates/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, content })
  });
  if (!res.ok) throw new Error('Failed to update template');
  return res.json();
}

export async function deletePromptTemplate(id) {
  const res = await apiFetch(`${BASE_URL}/prompt-templates/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete template');
}

// ── Knowledge Base ──

export async function uploadKnowledgeDocument(file) {
  const form = new FormData();
  form.append('file', file);
  const res = await apiFetch(`${BASE_URL}/knowledge/documents`, {
    method: 'POST',
    body: form
  });
  if (!res.ok) throw new Error('Failed to upload document');
  return res.json();
}

export async function getKnowledgeDocuments() {
  const res = await apiFetch(`${BASE_URL}/knowledge/documents`);
  if (!res.ok) throw new Error('Failed to fetch knowledge documents');
  return res.json();
}

export async function deleteKnowledgeDocument(id) {
  const res = await apiFetch(`${BASE_URL}/knowledge/documents/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete document');
}

export function ragChatStream(conversationId, message, model, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/knowledge/chat`, { conversationId, message, model },
    () => {}, onChunk, onDone, onError, signal);
}

// ── Video Generation ──

export async function submitVideoGen({ prompt, duration, aspectRatio, seed, firstFrameBase64, conversationId }) {
  const res = await apiFetch(`${BASE_URL}/video-gen/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt, duration, aspectRatio, seed, firstFrameBase64, conversationId })
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || 'Failed to submit video generation task');
  }
  return res.json();
}

export async function getConversationVideoTasks(conversationId) {
  const res = await apiFetch(`${BASE_URL}/video-gen/conversations/${conversationId}/tasks`);
  if (!res.ok) throw new Error('Failed to fetch video tasks');
  return res.json();
}

export async function getVideoGenTasks() {
  const res = await apiFetch(`${BASE_URL}/video-gen/tasks`);
  if (!res.ok) throw new Error('Failed to fetch video tasks');
  return res.json();
}

export async function getVideoGenTask(id) {
  const res = await apiFetch(`${BASE_URL}/video-gen/tasks/${id}`);
  if (!res.ok) throw new Error('Failed to fetch video task');
  return res.json();
}

export async function deleteVideoGenTask(id) {
  const res = await apiFetch(`${BASE_URL}/video-gen/tasks/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete video task');
}

export function getVideoUrl(taskId) {
  const token = getToken();
  return `${BASE_URL}/video-gen/tasks/${taskId}/video${token ? '?token=' + token : ''}`;
}
