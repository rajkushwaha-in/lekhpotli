export default function MessageBubble({ role, content, pending }) {
  const isUser = role === 'user';
  return (
    <div className={`message-row ${isUser ? 'from-user' : 'from-assistant'}`}>
      <div className="avatar">{isUser ? 'You' : 'L'}</div>
      <div className="bubble">
        {content}
        {pending && content.length === 0 && <span className="typing-dots" aria-label="Lekhpotli is typing">
          <span />
          <span />
          <span />
        </span>}
        {pending && content.length > 0 && <span className="cursor-blink" />}
      </div>
    </div>
  );
}
