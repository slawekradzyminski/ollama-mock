# Ollama Mock

Lightweight Spring Boot 3.5.8 service that emulates the Ollama HTTP API for local development. It exposes `/api/generate`, `/api/chat`, `/api/chat/tools`, `/api/chat/tools/definitions`, `/api/version`, etc., and uses deterministic JSON scenarios so frontend and backend teams can run the stack without pulling a real Ollama/Qwen container.

## Endpoints & Behavior

| Endpoint | Scenario File | Thinking? | Tools? | Notes |
| --- | --- | --- | --- | --- |
| `POST /api/generate` | `src/main/resources/scenarios/generate-scenarios.json` | Controlled by `think` flag | ❌ | Stream NDJSON or single JSON chunk mirroring Ollama `/generate`. |
| `POST /api/chat` | `src/main/resources/scenarios/chat-dialog-scenarios.json` | Controlled by `think` flag | ❌ | Plain conversation scenarios (“status update”, “limitations”, etc.). |
| `POST /api/chat/tools` (or `/chat/tools`) | `src/main/resources/scenarios/chat-scenarios.json` | ❌ | ✅ | Tool-calling loops: `list_products` → `get_product_snapshot` etc. Tool schemas exposed via `GET /api/chat/tools/definitions`. |
| `POST /api/chat` with non-empty `tools` array | auto-delegates to `ChatToolsService` so legacy callers work without changing URLs. |

Each scenario file contains deterministic steps. Add or modify prompts by editing the JSON and restarting the app.

### Thinking Flag & Streaming Delays

- `/api/generate` and `/api/chat` include “thinking” chunks **only** when the request payload sets `"think": true`.
- `/api/chat/tools` never emits `thinking` to match how tool handlers expect payloads.
- All responses stream token-by-token with a configurable delay (`ollama.mock.token-delay`, default `150ms`). Tool calls pause for `ollama.mock.tool-call-delay` (default `1s`) before emitting the tool payload to mimic function execution.

## Running Locally

```bash
./mvnw spring-boot:run
# service listens on http://localhost:11434
```

Example requests:

```bash
# /api/generate with thinking
curl -sN -H 'Content-Type: application/json' \
  -d '{"prompt":"Summarize the release plan","think":true}' \
  http://localhost:11434/api/generate

# /api/chat without thinking
curl -sN -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Give me a quick status update on the Ollama mock"}],"think":false}' \
  http://localhost:11434/api/chat

# /api/chat/tools
curl -sN -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"What iphones do we have available? Tell me the details about them"}],"tools":[{"function":{"name":"list_products"}}]}' \
  http://localhost:11434/api/chat

# Streaming showcase prompts (observe logs for token-by-token output)
curl -sN -H 'Content-Type: application/json' \
  -d '{"prompt":"Walk me through the streaming demo for /api/generate","think":true}' \
  http://localhost:11434/api/generate

curl -sN -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Narrate the full streaming timeline for this mock"}],"think":true}' \
  http://localhost:11434/api/chat

curl -sN -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Show me the streaming timeline when a tool call is involved"}],"tools":[{"function":{"name":"list_products"}}]}' \
  http://localhost:11434/api/chat
```

Stop the app with `Ctrl+C` or `kill <PID>`.

## Tests

```bash
./mvnw test
```

Unit tests cover scenario parsing, controller routing, and the thinking flag behavior for both chat and generate flows.

### Inspecting the Token Stream

`src/main/resources/logback-spring.xml` sets dedicated loggers for the chat/generate/services. When you run the app (`./mvnw spring-boot:run`) and trigger any of the streaming showcase prompts above, the console prints lines such as `[chat-stream][content-token] token text` so you can follow every emitted token without extra tooling. Adjust `ollama.mock.token-delay` / `ollama.mock.tool-call-delay` to speed up or slow down the demonstration.

## Integrating With Other Projects

### `test-secure-backend`

1. Run this mock (`./mvnw spring-boot:run`) so it listens on `http://localhost:11434`.
2. In `test-secure-backend`, set the local profile Ollama URL to the mock:
   - `application-local.yml` (or `.env`):  
     ```yaml
     awesome:
       ollama:
         base-url: http://localhost:11434
     ```
3. The backend already posts tool-enabled requests to `/api/chat`; because this mock auto-detects `tools`, there is no endpoint change. Tool schemas at `/api/chat/tools/definitions` match the original service, so DTOs continue to deserialize as-is.
4. Use the canonical prompts (“What iphones…”, “What Beauty products…”, etc.) to exercise tool loops during integration tests.

### `vite-react-frontend`

1. Configure the frontend to point at the backend that proxies this mock (no direct browser access is required unless you previously connected to Ollama from the frontend).
2. If you do call this mock directly (e.g., via `VITE_OLLAMA_URL`), use the same base URL (`http://localhost:11434`) and keep the request schema identical to the production Ollama API.
3. Frontend hooks (`useOllamaChat`, `useOllamaToolChat`, `useOllamaGenerate`) should continue to work because the mock streams NDJSON with the same field names (`message`, `thinking`, `tool_calls`, `done`, etc.).

## Extending Scenarios

1. Add new prompts to the appropriate JSON file:
   - `generate-scenarios.json` for `/api/generate`.
   - `chat-dialog-scenarios.json` for `/api/chat`.
   - `chat-scenarios.json` for `/api/chat/tools`.
2. Restart the app (or re-run tests) to load the new scenario.
3. For tool scenarios, include separate stages for user-triggered tool calls, intermediate tool responses, and final assistant summaries.

## Status & Next Steps

- Core endpoints (`/generate`, `/chat`, `/chat/tools`, `/chat/tools/definitions`, `/version`) stream deterministic tokens with realistic latency controls via `ollama.mock.token-delay` and `ollama.mock.tool-call-delay`.
- For additional Ollama endpoints (`/api/tags`, `/api/ps`, `/api/delete`, `/api/pull`, `/api/embed`, etc.) extend the controllers/services following the existing pattern when needed.
- Build/push the Docker image with `./build-multiarch.sh 1.0.0` (or another tag) and update `awesome-localstack/lightweight-docker-compose.yml` to point at the published image.
