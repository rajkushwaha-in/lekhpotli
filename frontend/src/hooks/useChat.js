import { useCallback, useEffect, useRef, useState } from 'react';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

let nextId = 1;
function makeId() {
  return nextId++;
}

/** Parses a text/event-stream response body into { event, data } objects. */
async function* parseSse(response) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) !== -1) {
      const rawEvent = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);

      let eventName = 'message';
      const dataLines = [];
      for (const line of rawEvent.split('\n')) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).replace(/^ /, ''));
        }
      }
      yield { event: eventName, data: dataLines.join('\n') };
    }
  }
}

export function useChat() {
  const [messages, setMessages] = useState([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState(null);
  const abortRef = useRef(null);
  // Mirrors `messages` synchronously so sendMessage can read the latest
  // history immediately, without waiting for a setState updater to flush.
  const messagesRef = useRef([]);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const updateMessage = useCallback((id, updater) => {
    setMessages((prev) => prev.map((m) => (m.id === id ? updater(m) : m)));
  }, []);

  const sendMessage = useCallback(
    async (text) => {
      const trimmed = text.trim();
      if (!trimmed || isStreaming) return;

      setError(null);
      const userMessage = { id: makeId(), role: 'user', content: trimmed };
      const assistantMessage = { id: makeId(), role: 'assistant', content: '', pending: true };

      const history = [...messagesRef.current, userMessage];
      messagesRef.current = [...history, assistantMessage];
      setMessages(messagesRef.current);

      setIsStreaming(true);
      const controller = new AbortController();
      abortRef.current = controller;

      try {
        const response = await fetch(`${API_BASE}/api/chat/stream`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            messages: history.map(({ role, content }) => ({ role, content })),
          }),
          signal: controller.signal,
        });

        if (!response.ok || !response.body) {
          throw new Error(`Request failed with status ${response.status}`);
        }

        for await (const { event, data } of parseSse(response)) {
          if (event === 'delta') {
            updateMessage(assistantMessage.id, (m) => ({ ...m, content: m.content + data }));
          } else if (event === 'error') {
            setError(data || 'Something went wrong.');
          }
        }
      } catch (err) {
        if (err.name !== 'AbortError') {
          setError(err.message || 'Failed to reach Lekhpotli.');
        }
      } finally {
        // If nothing ever streamed back (e.g. the request errored before any
        // delta arrived), drop the empty placeholder bubble instead of leaving
        // a blank pill next to the error banner.
        setMessages((prev) => {
          const target = prev.find((m) => m.id === assistantMessage.id);
          if (target && target.content.length === 0) {
            return prev.filter((m) => m.id !== assistantMessage.id);
          }
          return prev.map((m) => (m.id === assistantMessage.id ? { ...m, pending: false } : m));
        });
        setIsStreaming(false);
        abortRef.current = null;
      }
    },
    [isStreaming, updateMessage],
  );

  const stopStreaming = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { messages, sendMessage, isStreaming, error, stopStreaming };
}
