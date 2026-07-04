import { useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble';

export default function ChatWindow({ messages }) {
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="chat-window empty-state">
        <div className="empty-mark">L</div>
        <h1>Lekhpotli</h1>
        <p>Ask me anything — I'm powered by Claude.</p>
      </div>
    );
  }

  return (
    <div className="chat-window">
      {messages.map((m) => (
        <MessageBubble key={m.id} role={m.role} content={m.content} pending={m.pending} />
      ))}
      <div ref={bottomRef} />
    </div>
  );
}
