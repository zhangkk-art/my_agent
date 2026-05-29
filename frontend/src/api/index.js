const BASE_URL = '/api';

function streamSse(url, body, onChunk, onDone, onError, signal) {
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

    async function read() {
      try {
        const { done, value } = await reader.read();
        if (done) {
          onDone();
          return;
        }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.substring(6);
            if (data === '[DONE]') {
              onDone();
              return;
            }
            try {
              const parsed = JSON.parse(data);
              if (parsed.error) {
                onError(new Error(parsed.error));
                return;
              }
              if (parsed.done) {
                onDone(parsed.messageId);
                return;
              }
              if (parsed.content) {
                onChunk(parsed.content);
                // Yield to browser between chunks — allows Vue DOM flush + paint
                await new Promise(r => requestAnimationFrame(r));
              }
            } catch (e) {
              // skip parse errors
            }
          }
        }
        read();
      } catch (err) {
        if (err.name === 'AbortError') {
          onDone();
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

export async function sendMessage(conversationId, message) {
  const res = await fetch(`${BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message })
  });
  if (!res.ok) throw new Error('Chat request failed');
  return res.json();
}

export function sendMessageStream(conversationId, message, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/stream`, { conversationId, message }, onChunk, onDone, onError, signal);
}

export function regenerateStream(conversationId, message, onChunk, onDone, onError, signal) {
  streamSse(`${BASE_URL}/chat/regenerate`, { conversationId, message }, onChunk, onDone, onError, signal);
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
