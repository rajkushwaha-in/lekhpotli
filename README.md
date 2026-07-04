# Lekhpotli

A chatbot with a React frontend and a Java (Spring Boot) backend, powered by the Claude API. Supports streaming responses and a light/dark theme toggle.

## Project layout

```
backend/    Spring Boot API (Java 21+) — calls Claude and streams the reply back over SSE
frontend/   React + Vite app — chat UI, theme toggle
```

## 1. Add your Claude API key

Edit `backend/.env` (already created, gitignored) and set your real key:

```
ANTHROPIC_API_KEY=sk-ant-...your real key...
```

`backend/.env.example` shows the expected format.

## 2. Run the backend

Requires Java 21+ (Maven is not required — the project includes the Maven Wrapper).

```bash
cd backend
./mvnw spring-boot:run
```

Starts on **http://localhost:8080**. Health check: `GET /api/health`.

## 3. Run the frontend

Requires Node.js 18+.

```bash
cd frontend
npm install   # first time only
npm run dev
```

Starts on **http://localhost:5173**.

`frontend/.env` points the UI at the backend (`VITE_API_BASE_URL`, defaults to `http://localhost:8080`) — change it if you deploy the backend elsewhere.

## How it works

- The frontend posts the conversation history to `POST /api/chat/stream`.
- The backend calls Claude (`claude-opus-4-8` by default, configurable in `backend/src/main/resources/application.properties`) via the official Anthropic Java SDK and streams the response back to the browser as Server-Sent Events, so text appears incrementally like a typical chat UI.
- If the API key is missing/invalid, the backend replies with a clear error event instead of crashing, and the UI surfaces it as a banner.

## Customizing

- **Model / system prompt / max tokens**: `backend/src/main/resources/application.properties`
- **Theme colors**: CSS variables in `frontend/src/index.css`
- **App name/branding**: `frontend/index.html` (title), `frontend/public/favicon.svg`, `frontend/src/components/Header.jsx`
