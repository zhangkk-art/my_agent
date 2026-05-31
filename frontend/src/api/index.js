const BASE_URL = '/api';

const SSE_BUFFER_LIMIT = 1024 * 1024; // 1MB

function streamSse(url, body, onReasoning, onChunk, onDone, onError, signal) {
  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal
  }).then(response => {
    if (!response.ok) throw new Error('Stream request failed');
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let finished = false;  // guard against double onDone

    function finish(messageId) {
      if (finished) return;
      finished = true;
      try { reader.cancel(); } catch {}
      onDone(messageId);
    }

    async function read() {
      try {
        const { done, value } = await reader.read();
        if (done) {
          finish();
          return;
        }
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
            if (data === '[DONE]') {
              finish();
              return;
            }
            try {
              const parsed = JSON.parse(data);
              if (parsed.error) {
                onError(new Error(parsed.error));
                return;
              }
              if (parsed.done) {
                finish(parsed.messageId);
                return;
              }
              if (parsed.reasoning) {
                onReasoning(parsed.reasoning);
              }
              if (parsed.content) {
                onChunk(parsed.content);
                // Yield to browser between chunks — allows Vue DOM flush + paint
                await new Promise(r => requestAnimationFrame(r));
              }
            } catch (e) {
              console.warn('SSE parse error:', e.message, 'data:', data.substring(0, 100))
            }
          }
        }
        read();
      } catch (err) {
        if (err.name === 'AbortError') {
          finish();
        } else {
          onError(err);
        }
      }
    }
    read();
  }).catch(err => {
    if (err.name !== 'AbortError') {
      onError(err);
    }
  });
}

export async function sendMessage(conversationId, message, model) {
  const res = await fetch(`${BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message, model })
  });
  if (!res.ok) throw new Error('Chat request failed');
  return res.json();
}

export function sendMessageStream(conversationId, message, model, webSearch, onReasoning, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/stream`, { conversationId, message, model, webSearch }, onReasoning, onChunk, onDone, onError, signal);
}

export function sendImageStream(conversationId, message, model, images, webSearch, onReasoning, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/image`, { conversationId, message, model, images, webSearch }, onReasoning, onChunk, onDone, onError, signal);
}

export function regenerateStream(conversationId, message, model, onReasoning, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/regenerate`, { conversationId, message, model }, onReasoning, onChunk, onDone, onError, signal);
}

export async function getConversations() {
  const res = await fetch(`${BASE_URL}/conversations`);
  if (!res.ok) throw new Error('Failed to fetch conversations');
  return res.json();
}

export async function createConversation(title) {
  const res = await fetch(`${BASE_URL}/conversations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: title || 'New Chat' })
  });
  if (!res.ok) throw new Error('Failed to create conversation');
  return res.json();
}

export async function getConversation(id) {
  const res = await fetch(`${BASE_URL}/conversations/${id}`);
  if (!res.ok) throw new Error('Failed to fetch conversation');
  return res.json();
}

export async function renameConversation(id, title) {
  const res = await fetch(`${BASE_URL}/conversations/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title })
  });
  if (!res.ok) throw new Error('Failed to rename conversation');
  return res.json();
}

export async function touchConversation(id) {
  const res = await fetch(`${BASE_URL}/conversations/${id}/touch`, { method: 'PATCH' });
  if (!res.ok) throw new Error('Failed to touch conversation');
  return res.json();
}

export async function deleteConversation(id) {
  const res = await fetch(`${BASE_URL}/conversations/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete conversation');
}

export async function updateMessage(id, content) {
  const res = await fetch(`${BASE_URL}/messages/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  });
  if (!res.ok) throw new Error('Failed to update message');
  return res.json();
}

export async function deleteMessage(id) {
  const res = await fetch(`${BASE_URL}/messages/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete message');
}

export async function updateSystemPrompt(id, systemPrompt) {
  const res = await fetch(`${BASE_URL}/conversations/${id}/system-prompt`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ systemPrompt: systemPrompt || '' })
  });
  if (!res.ok) throw new Error('Failed to update system prompt');
  return res.json();
}

export async function searchMessages(q) {
  const res = await fetch(`${BASE_URL}/messages/search?q=${encodeURIComponent(q)}`);
  if (!res.ok) throw new Error('Search failed');
  return res.json();
}

export async function shareConversation(id) {
  const res = await fetch(`${BASE_URL}/conversations/${id}/share`, { method: 'POST' });
  if (!res.ok) throw new Error('Failed to share conversation');
  return res.json();
}

export async function revokeShare(id) {
  const res = await fetch(`${BASE_URL}/conversations/${id}/share`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to revoke share');
}

export async function getSharedConversation(token) {
  const res = await fetch(`${BASE_URL}/shared/${token}`);
  if (!res.ok) throw new Error('Shared conversation not found');
  return res.json();
}

// ── Prompt templates ──

export async function getPromptTemplates() {
  const res = await fetch(`${BASE_URL}/prompt-templates`);
  if (!res.ok) throw new Error('Failed to fetch templates');
  return res.json();
}

export async function createPromptTemplate(name, content) {
  const res = await fetch(`${BASE_URL}/prompt-templates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, content })
  });
  if (!res.ok) throw new Error('Failed to create template');
  return res.json();
}

export async function updatePromptTemplate(id, name, content) {
  const res = await fetch(`${BASE_URL}/prompt-templates/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, content })
  });
  if (!res.ok) throw new Error('Failed to update template');
  return res.json();
}

export async function deletePromptTemplate(id) {
  const res = await fetch(`${BASE_URL}/prompt-templates/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete template');
}
