# Spring AI Ollama Mock — Implementation Plan

## 1. Context and Goals
- `test-secure-backend` talks directly to a real Ollama server through `/api/generate`, `/api/chat`, and `/api/chat/tools`, streaming Server-Sent Events and relaying tool-calling payloads to downstream handlers (`src/main/java/com/awesome/testing/service/ollama/OllamaService.java`, `OllamaFunctionCallingService.java`). This creates a heavy dependency on dockerized Ollama/Qwen models for every local run.
- `vite-react-frontend` consumes the backend’s SSE stream via custom React hooks (`src/hooks/useOllamaGenerate.ts`, `useOllamaChat.ts`, `useOllamaToolChat.ts`) and mirrors Ollama’s JSON schema when presenting chat/generation output.
- `awesome-localstack` composes the entire stack, including an Ollama container that slows profile start-up and requires GPU/CPU resources many developers lack.
- The new `ollama-mock` project must offer a lightweight, Spring Boot 3.x application that emulates Ollama’s API semantics while delegating completions to Spring AI’s provider-agnostic `ChatClient`. This lets the backend/frontend behave unchanged while swapping the base URL to the mock service for the `local` profile.

## 2. High-Level Requirements
1. **Protocol parity:** Expose `/api/generate`, `/api/chat`, `/api/chat/tools`, `/api/tags`, `/api/version`, etc., with request/response JSON structures indistinguishable from the official spec (`ollama-api.md`) so existing DTOs continue to deserialize without changes.
2. **Streaming behavior:** Support SSE chunking (`text/event-stream`) for both `generate` and `chat`, including `thinking` payloads, `done` flags, and timing metadata to satisfy backend logging/tests.
3. **Tool-calling loop:** Accept Ollama-style `tools` definitions, execute mock “tool” functions (backed by Spring services or scripted data), and stream tool call chunks to match `OllamaFunctionCallingService` expectations.
4. **Spring AI driven responses:** Use the Spring AI `ChatClient` builder (auto-configured `ChatModel` bean per `/spring-projects/spring-ai` docs) so the mock can target OpenAI, Azure, Bedrock, local LMStudio endpoints, or simple template-based responders by editing `application.yml`.
5. **Local profile ergonomics:** Provide Dockerfile/docker-compose snippets and helper scripts so `awesome-localstack` can swap the heavy Ollama image with this mock for developer machines.
6. **Testability:** Ship contract tests to ensure schema parity, and lightweight simulations to guarantee `think`, `tool_calls`, and streaming chunks are emitted predictably for frontend Playwright/Vitest suites.

## 3. Proposed Architecture
- Spring Boot 3.5.8 (current latest GA) + Java 25 for alignment with the backend project and to match the user requirement.
- Dependencies: Spring WebFlux (for SSE), Spring AI (core + chosen provider, e.g., OpenAI or local `SentenceTransformer`), Jackson, and optional Actuator.
- Core modules:
  - **API layer:** Controllers replicating Ollama endpoints with DTOs cloned from `ollama-api.md`.
  - **Mock orchestration:** Services that translate Ollama payloads into Spring AI `Prompt`s / `ChatClient` calls, generate deterministic timing metadata, and orchestrate tool loops.
  - **Tool registry:** Spring-managed beans implementing interfaces similar to backend handlers so we can reuse product snapshots or stub catalogs.
  - **Streaming infrastructure:** Reactor `Flux` pipelines that emit chunk DTOs, optionally persisting transcripts for debugging.
- Configuration toggles:
  - Provider credentials (`spring.ai.openai.api-key`, etc.).
  - Response modes (deterministic canned answers vs. live LLM calls).
  - Tool registry options (mock data vs. HTTP proxy to backend functions).

## 4. Phased Delivery Plan

### Phase 1 — Foundations & Skeleton
- Bootstrap Spring Boot 3.5.8 project (Maven build) with Spring WebFlux + Spring AI starter.
- Add core DTOs mirroring Ollama spec (GenerateRequest/Response, ChatRequest/Response, Tool definitions, Tags, etc.) and ensure JSON naming matches `ollama-api.md`.
- Implement configuration properties for provider selection, mock toggles, and default models. Prove Spring AI integration by wiring a `ChatClient` bean using the documented builder (`ChatClient.builder(chatModel)…build()`).
- Deliver a thin `/actuator/health` plus `/api/version` endpoint returning static values to validate wiring.

### Phase 2 — `/api/generate` Streaming Parity
- Implement the generate controller + service:
  - Convert incoming request to Spring AI `Prompt` (system + user content).
  - Stream responses via `Flux<GenerateChunk>`; simulate Ollama’s timing metadata and token counts (initially heuristic, then optionally using provider telemetry if available).
  - Support `think`, `stream`, `raw`, and `options` flags that the backend already supplies. For unsupported options, return graceful warnings while continuing.
- Add contract tests verifying SSE framing, JSON fields, and `done` semantics expected by `test-secure-backend`.

### Phase 3 — `/api/chat` and Tool Invocation
- Extend to chat endpoint with conversation state:
  - Map incoming message arrays to Spring AI conversation history and stream replies chunk-by-chunk with `ChatClient.prompt().user(..).call().chatResponse()`.
  - Emit placeholder `thinking` text when the provider lacks that concept, ensuring backend logs remain consistent.
- Implement tool-calling workflow:
  - Parse `tools` array, register Spring beans implementing a `FunctionCallHandler` contract, and expose deterministic mock data (e.g., sample product catalog) so requests like `get_product_snapshot` behave identically to the backend’s expectations.
  - Support looped request/response (assistant tool_call → tool result → assistant follow-up) to unblock `/api/ollama/chat/tools`.
- Add unit tests for registry routing, error cases, and streaming sequences.

### Phase 4 — Auxiliary Endpoints
- Implement remaining Ollama endpoints needed by dev tooling: `/api/tags`, `/api/ps`, `/api/delete`, `/api/pull` (can return static/mock data), plus `/api/embed` returning deterministic vectors for frontend smoke tests.
- Document configuration profiles (real LLM vs. canned), sample `.env`, and curl examples mirroring `ollama-api.md`.

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
