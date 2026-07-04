import { ThemeProvider } from './context/ThemeContext';
import { useChat } from './hooks/useChat';
import Header from './components/Header';
import ChatWindow from './components/ChatWindow';
import ChatInput from './components/ChatInput';
import './App.css';

function ChatApp() {
  const { messages, sendMessage, isStreaming, error, stopStreaming } = useChat();

  return (
    <div className="app-shell">
      <Header />
      <main className="app-main">
        <ChatWindow messages={messages} />
        {error && <div className="error-banner">{error}</div>}
      </main>
      <footer className="app-footer">
        <ChatInput onSend={sendMessage} isStreaming={isStreaming} onStop={stopStreaming} />
        <p className="disclaimer">Lekhpotli can make mistakes. Verify important information.</p>
      </footer>
    </div>
  );
}

function App() {
  return (
    <ThemeProvider>
      <ChatApp />
    </ThemeProvider>
  );
}

export default App;
