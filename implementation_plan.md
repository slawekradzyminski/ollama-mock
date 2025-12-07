# Spring AI Ollama Mock — Implementation Plan

## 1. Context and Goals
- `test-secure-backend` talks directly to a real Ollama server through `/api/generate`, `/api/chat`, and `/api/chat/tools`, streaming Server-Sent Events and relaying tool-calling payloads to downstream handlers (`src/main/java/com/awesome/testing/service/ollama/OllamaService.java`, `OllamaFunctionCallingService.java`). This creates a heavy dependency on dockerized Ollama/Qwen models for every local run.
- `vite-react-frontend` consumes the backend’s SSE stream via custom React hooks (`src/hooks/useOllamaGenerate.ts`, `useOllamaChat.ts`, `useOllamaToolChat.ts`) and mirrors Ollama’s JSON schema when presenting chat/generation output.
- `awesome-localstack` composes the entire stack, including an Ollama container that slows profile start-up and requires GPU/CPU resources many developers lack.
- The new `ollama-mock` project must offer a lightweight, Spring Boot 3.5.8 (Java 25, Maven) application that emulates Ollama’s API semantics while delegating completions to Spring AI’s provider-agnostic `ChatClient`. Spring Boot + Spring AI are a good fit because Spring Boot gives us fast HTTP/SSE plumbing, while Spring AI’s `ChatClient` (see `/spring-projects/spring-ai` docs on `ChatClient.builder(chatModel)` and prompt APIs) lets us swap between real LLMs and deterministic mock logic without changing controllers.

## 2. High-Level Requirements
1. **Protocol parity:** Expose `/api/generate`, `/api/chat`, `/api/chat/tools`, `/api/tags`, `/api/version`, etc., with request/response JSON structures indistinguishable from the official spec (`ollama-api.md`) so existing DTOs continue to deserialize without changes.
2. **Streaming behavior:** Support SSE/NDJSON chunking for both `generate` and `chat`, including `thinking` payloads for assistant-text chunks (but never inside tool call emissions because `/tools` currently ignores the field), `done` flags, realistic small delays (~150 ms) to mimic real LLM streaming, and timing metadata so backend logs/tests stay stable. `/api/chat` auto-detects embedded `tools` definitions and transparently delegates to the chat-tools scenario engine so legacy clients don’t need to change URLs.
3. **Scenario-driven mocks:** Maintain three dedicated scenario files for each endpoint type so behavior is deterministic:
   - `scenarios/generate-scenarios.json` for `/api/generate` (thinking optional, no tools).
   - `scenarios/chat-dialog-scenarios.json` for `/api/chat` (thinking optional, no tools).
   - `scenarios/chat-scenarios.json` for `/api/chat/tools` (tool calls only, never `thinking`). The controller also accepts `/chat/tools` to match older clients.
   Unsupported prompts immediately respond with “Sorry, only these prompts are supported …” plus the relevant list, helping devs discover valid cases quickly.
4. **Tool-calling loop:** Accept Ollama-style `tools` definitions, execute mock “tool” functions (backed by Spring services or scripted data), and stream tool call chunks to match `OllamaFunctionCallingService` expectations. Canonical validation prompt must emit the predetermined tool sequence from the chat-tools scenario file, ensuring deterministic tool chaining.
4. **Spring AI driven responses:** Use the Spring AI `ChatClient` builder (auto-configured `ChatModel` bean per `/spring-projects/spring-ai` docs like `ChatClient.builder(chatModel)…build()`) so the mock can target OpenAI, Azure, Bedrock, local LMStudio endpoints, or simple template-based responders by editing `application.yml`.
5. **Local profile ergonomics:** Provide Dockerfile/docker-compose snippets and helper scripts so `awesome-localstack` can swap the heavy Ollama image with this mock for developer machines. The service must default to `server.port=11434`, while tests explicitly rely on random ports to avoid clashes.
6. **Just-enough ops:** No dedicated observability or tracing stack is required—keep the mock lightweight. Only default Spring Boot metrics/health are acceptable.
7. **Testability:** Ship contract tests to ensure schema parity, and lightweight simulations to guarantee `think`, `tool_calls`, and streaming chunks are emitted predictably for frontend Playwright/Vitest suites.

## 3. Proposed Architecture
- Spring Boot 3.5.8 (Java 25) + Maven + Spring WebFlux for non-blocking streaming.
- Spring AI 1.1.1 starter for OpenAI-compatible models (others can be swapped with config) using the documented `ChatClient` builder to set default system prompts and tool configuration.
- No extra observability dependencies—logging + optional health endpoint from Boot are enough.
- Core modules:
  - **API layer:** Controllers replicating Ollama endpoints with DTOs cloned from `ollama-api.md`.
  - **Mock orchestration:** Services that translate Ollama payloads into Spring AI `Prompt`s / `ChatClient` calls when needed and otherwise return deterministic canned responses.
  - **Tool registry:** Spring-managed beans implementing interfaces similar to backend handlers so we can reuse product snapshots or stub catalogs.
  - **Streaming infrastructure:** Reactor `Flux` pipelines that emit chunk DTOs with intentional delays (~150 ms) to mimic LLM tokenization.
- Configuration toggles:
  - Provider credentials (`spring.ai.openai.api-key`, etc.).
  - Response modes (deterministic canned answers vs. live LLM calls).
  - Tool registry options (mock data vs. HTTP proxy to backend functions).

## 4. Phased Delivery Plan

### Phase 1 — Foundations & Skeleton ✅
- Bootstrap Spring Boot 3.5.8 project (Maven, Java 25) with Spring WebFlux + Spring AI starter.
- Add core DTOs mirroring Ollama spec (GenerateRequest/Response, ChatRequest/Response, Tool definitions, Tags, etc.) and ensure JSON naming matches `ollama-api.md`.
- Implement configuration properties for provider selection, mock toggles, and default models. Prove Spring AI integration by wiring a `ChatClient` bean using the documented builder (`ChatClient.builder(chatModel)…build()`).
- Expose `/api/version` for quick verification. (No extra observability dependencies per latest requirement.)

### Phase 2 — `/api/generate` Streaming Parity ✅
- Implement the generate controller + service:
  - Convert incoming request to Spring AI `Prompt` (system + user content).
  - Stream responses via `Flux<GenerateChunk>`; simulate Ollama’s timing metadata and token counts (initially heuristic, then optionally using provider telemetry if available).
  - Support `think`, `stream`, `raw`, and `options` flags that the backend already supplies. For unsupported options, return graceful warnings while continuing.
- Add contract tests verifying SSE/NDJSON framing, JSON fields, and `done` semantics expected by `test-secure-backend`.

### Phase 3 — `/api/chat` and Tool Invocation ✅ (now scenario-driven)
- Extend to chat endpoint with conversation state and deterministic streaming delays (~150 ms between chunks).
- Implement a scenario registry (JSON + Spring component) that maps prompts to stage-by-stage sequences (thinking chunk → tool call → summary). Canonical prompts like “What iphones…” and “What Beauty products…” are preloaded. Unsupported prompts return a friendly list of supported ones so developers instantly know what the mock can do.
- Tool-calling workflow respects the `/tools` limitation: tool call chunks never include `thinking`, only `tool_calls` arrays and metadata, while assistant summaries still include `thinking` when appropriate.
- **Readiness note:** After finishing this phase, the `test-secure-backend` local profile can point to `http://localhost:11434` and should observe the deterministic scenario pipeline described above.

### Phase 4 — Auxiliary Endpoints & Mock Data (In Progress)
- Implement remaining Ollama endpoints needed by dev tooling: `/api/tags`, `/api/ps`, `/api/delete`, `/api/pull`, `/api/embed`, `/api/chat/tools/definitions`. These can return static/mock data but must remain shape-compatible with the official spec and `test-secure-backend`.
- Flesh out the mock catalog to keep backend/frontend demos engaging (e.g., include two phones, ensure the first tool returns both iPhone and a second phone, then the snapshot call returns price/stock info). Provide deterministic JSON payloads for other sample tools.
- Document the local “thinking delay” knobs and how to switch between canned responses and Spring AI-driven completions.

### Phase 5 — Integration & Deployment
- Provide Dockerfile and optional docker-compose override to drop into `awesome-localstack`, ensuring ports and health checks align with the original Ollama service.
- Update `test-secure-backend` local profile instructions to point at the mock when desired, possibly via `application-local.yml` property overrides.
- Run end-to-end validation:
  - Backend unit/integration tests targeting the mock API.
  - Frontend Vitest + Playwright suites against the mock to confirm SSE/tool-calling flows succeed.
  - Localstack docker-compose smoke test verifying the entire stack boots faster and remains resource-light.
- Publish developer guide describing how to toggle between real Ollama and the mock, plus troubleshooting tips.

## 5. Risks & Mitigations
- **SSE fidelity:** Browser EventSource clients are sensitive to formatting; include integration tests that pipe actual SSE responses into a minimal EventSource implementation.
- **Provider variability:** Spring AI abstracts multiple vendors, but latency/stream chunking varies. Provide a “canned responder” mode for deterministic CI runs where live LLM calls would be flaky.
- **Tool registry drift:** Backend already defines concrete business tools (product snapshots). Decide whether the mock mirrors data locally or proxies calls back to the backend to stay in sync.
- **Authentication/quotas:** If developers point the mock at OpenAI, they need API keys. Offer a fully offline template mode (e.g., rule-based responders) as a fallback.

## 6. Deliverables
- Spring Boot project containing the mock service, Maven wrapper, Docker artifacts, and documentation.
- Automated tests (unit + contract) proving protocol parity.
- Integration instructions for backend/frontend/localstack repos and CI examples to keep the mock in sync.

Following this plan keeps the change manageable: we first guarantee schema/streaming shape, then iterate toward advanced tool behavior, and finally harden the deployment experience so the mock becomes a drop-in replacement for local development.
