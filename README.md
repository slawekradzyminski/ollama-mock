# Ollama Mock

Lightweight Spring Boot service that emulates the Ollama HTTP API for local development. It exposes `/api/generate`, `/api/chat`, `/api/chat/tools`, `/api/chat/tools/definitions`, `/api/version`, etc., and uses deterministic JSON scenarios so frontend and backend teams can run the stack without pulling a real Ollama/Qwen container. The default model is `qwen3.5:2b` unless `OLLAMA_MOCK_MODEL` overrides it.

## Endpoints & Behavior

| Endpoint | Scenario File | Thinking? | Tools? | Notes |
| --- | --- | --- | --- | --- |
| `POST /api/generate` | `src/main/resources/scenarios/generate-scenarios.json` | Controlled by `think` flag | ❌ | Stream NDJSON or single JSON chunk mirroring Ollama `/generate`. |
| `POST /api/chat` | `src/main/resources/scenarios/chat-dialog-scenarios.json` | Controlled by `think` flag | ❌ | Plain conversation scenarios (“status update”, “limitations”, etc.). |
| `POST /api/chat/tools` (or `/chat/tools`) | `src/main/resources/scenarios/chat-scenarios.json` | ❌ | ✅ | Tool-calling loops: `list_products` → `get_product_snapshot` etc. Tool schemas exposed via `GET /api/chat/tools/definitions`. |
| `POST /api/chat` with non-empty `tools` array | auto-delegates to `ChatToolsService` so legacy callers work without changing URLs. |

The latest user message in `messages` selects the scenario, so a multi-turn history answers its newest question; earlier user messages are consulted only when the newer ones match no scenario. Tool chats follow the same rule, so a follow-up question starts its own tool loop.

Each scenario file contains deterministic steps. Add or modify prompts by editing the JSON and restarting the app.

### Thinking Flag & Streaming Delays

- `/api/generate` and `/api/chat` include “thinking” chunks **only** when the request payload sets `"think": true`.
- `/api/chat/tools` never emits `thinking` to match how tool handlers expect payloads.
- All responses stream token-by-token with a configurable delay (`ollama.mock.token-delay`, default `150ms`). Tool calls pause for `ollama.mock.tool-call-delay` (default `1s`) before emitting the tool payload to mimic function execution.
- With `"stream": false`, `/api/generate`, `/api/chat` and `/api/chat/tools` return one JSON object with `"done": true`, like Ollama.
- Requests that omit `model` resolve to `qwen3.5:2b`, keeping the mock aligned with the migration target.

### Models

The mock serves the default model (`OLLAMA_MOCK_MODEL`, `qwen3.5:2b`) plus the comma-separated
`OLLAMA_MOCK_MODELS` (`ollama.mock.models`), which defaults to the frontend's default model
`hf.co/prism-ml/Bonsai-27B-gguf:Q1_0` and the Docker Model Runner name
`hf.co/unsloth/Qwen3.5-2B-GGUF:Q4_K_M`. Like real Ollama, a name without a tag means `:latest`,
and any other model is rejected on `/api/generate`, `/api/chat` and `/api/chat/tools` before a
stream starts:

```
HTTP/1.1 404 Not Found
{"error":"model 'no-such-model:1b' not found"}
```

### Grounded tool answers

The final answer of a tool scenario is composed from the tool results the client sent back in the
current turn (the messages after the latest user message), never from a canned product list:

- `list_products` results are listed by name (the backend's tool returns ids and names; price and
  stock are added when a result carries them); an empty result gives the scenario's `emptyAnswer`,
  and a result with `error` (or unreadable content) is reported as a failed lookup.
- A `get_product_snapshot` result adds `Snapshot for <name>: priced at $price with N unit(s) in stock.`
- A snapshot call asks for the first product the catalog returned (`{{firstListedProductId}}`); when
  the catalog returned none, the mock skips the snapshot call and answers from the empty result.
- "List the laptops under $1500." asks for the seeded `computers` category. The backend tool has no
  price filter and returns no prices, so the answer lists the computers it returned and says the
  $1500 limit is unconfirmed (`maxPrice` drops priced results above the limit). "What gaming
  accessories can you recommend?" asks for `gaming`.

**Exception: the beauty scenario keeps its canned answer.** "What Beauty products do we have
available?" still ends with "The beauty shelf currently offers: Hydrating Face Serum and Velvet
Matte Lipstick. Let me know if you want pricing for either product." The AI testers course
(`ai-testers-api` lessons l12 to l20, branches `master` and `graphql`) asserts this exact text in
`tests/api/ollama/chat-tools.post.spec.ts`, and the seeded catalog has no beauty products, so a
grounded answer would break every published lesson. It stays verbatim until the course changes;
treat it as a known teaching artefact, not as catalog data. Do not use it as a template for new scenarios.

## Running Locally

```bash
./mvnw spring-boot:run
# service listens on http://localhost:11434
```

Example requests:

```bash
# /api/generate with thinking
curl -sN -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.5:2b","prompt":"Summarize the release plan","think":true}' \
  http://localhost:11434/api/generate

# /api/chat without thinking
curl -sN -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.5:2b","messages":[{"role":"user","content":"Give me a quick status update on the Ollama mock"}],"think":false}' \
  http://localhost:11434/api/chat

# /api/chat/tools
curl -sN -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.5:2b","messages":[{"role":"user","content":"What iphones do we have available? Tell me the details about them"}],"tools":[{"function":{"name":"list_products"}}]}' \
  http://localhost:11434/api/chat

# Streaming showcase prompts (observe logs for token-by-token output)
curl -sN -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.5:2b","prompt":"Walk me through the streaming demo for /api/generate","think":true}' \
  http://localhost:11434/api/generate

curl -sN -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.5:2b","messages":[{"role":"user","content":"Narrate the full streaming timeline for this mock"}],"think":true}' \
  http://localhost:11434/api/chat

curl -sN -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.5:2b","messages":[{"role":"user","content":"Show me the streaming timeline when a tool call is involved"}],"tools":[{"function":{"name":"list_products"}}]}' \
  http://localhost:11434/api/chat
```

Stop the app with `Ctrl+C` or `kill <PID>`.

## Tests

```bash
./mvnw test
```

Unit tests cover scenario parsing, controller routing, and the thinking flag behavior for both chat and generate flows.

The CI workflow also builds the final image and runs the container contract covering metadata, non-streaming and streaming generation, chat, tool definitions, and tool-call routing:

```bash
docker build --tag ollama-mock:local .
docker run --detach --name ollama-mock-local \
  --publish 127.0.0.1:11434:11434 \
  --env OLLAMA_MOCK_TOKEN_DELAY=0ms \
  --env OLLAMA_MOCK_TOOL_DELAY=0ms \
  ollama-mock:local
./scripts/container-smoke.sh
docker rm --force ollama-mock-local
```

## Container releases

The project version uses Maven's `revision` property. Stable release commits use an exact version such as `1.0.8`; development and manual candidates may use the matching snapshot release line. A `v1.0.8` tag or a candidate such as `1.0.8-rc.1` must match that line.

Tags and manual publishing runs inject the validated release version, verify the Maven build and container contract, and then produce `linux/amd64` and `linux/arm64` images. The same build is published with SBOM and provenance to:

- `ghcr.io/slawekradzyminski/ollama-mock`
- `slawekradzyminski/ollama-mock` on Docker Hub

GitHub publishing uses `GITHUB_TOKEN`. Docker Hub publishing requires repository secrets named `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`. Stable Git tags also update `latest`; manually dispatched candidates never do.

## Inspecting the Token Stream

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
   Give the final stage a `grounded` object (`listIntro`, `emptyAnswer`, optional `maxPrice` and `closing`)
   instead of a fixed `response`, so the answer only names what the tools returned.

## Status & Next Steps

- Core endpoints (`/generate`, `/chat`, `/chat/tools`, `/chat/tools/definitions`, `/version`) stream deterministic tokens with realistic latency controls via `ollama.mock.token-delay` and `ollama.mock.tool-call-delay`.
- For additional Ollama endpoints (`/api/tags`, `/api/ps`, `/api/delete`, `/api/pull`, `/api/embed`, etc.) extend the controllers/services following the existing pattern when needed.
- Publish a versioned image through GitHub Actions and update Awesome LocalStack to pin the resulting immutable digest.
