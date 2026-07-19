#!/usr/bin/env bash

set -euo pipefail

base_url="${1:-${OLLAMA_MOCK_BASE_URL:-http://127.0.0.1:11434}}"
expected_model="${OLLAMA_MOCK_EXPECTED_MODEL:-qwen3.5:2b}"
expected_version="${OLLAMA_MOCK_EXPECTED_VERSION:-}"
temporary_directory="$(mktemp -d)"
trap 'rm -rf "${temporary_directory}"' EXIT

for attempt in $(seq 1 60); do
  if curl --fail --silent --show-error "${base_url}/api/version" \
    --output "${temporary_directory}/version.json" 2>/dev/null; then
    break
  fi
  if [[ "${attempt}" -eq 60 ]]; then
    echo "Ollama Mock did not become ready at ${base_url}" >&2
    exit 1
  fi
  sleep 1
done

python3 - "${temporary_directory}/version.json" "${expected_model}" "${expected_version}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response_file:
    payload = json.load(response_file)
assert payload["mockModel"] == sys.argv[2], payload
assert payload["version"], payload
assert payload["timestamp"], payload
if sys.argv[3]:
    assert payload["version"] == sys.argv[3], payload
PY

curl --fail --silent --show-error \
  --header 'Content-Type: application/json' \
  --data '{"model":"qwen3.5:2b","prompt":"Provide a motivational quote","think":false,"stream":false}' \
  "${base_url}/api/generate" \
  --output "${temporary_directory}/generate.json"

python3 - "${temporary_directory}/generate.json" "${expected_model}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response_file:
    payload = json.load(response_file)
assert payload["model"] == sys.argv[2], payload
assert payload["done"] is True, payload
assert "momentum beats perfection" in payload["response"], payload
assert not payload.get("thinking"), payload
PY

curl --fail --silent --show-error \
  --dump-header "${temporary_directory}/generate-stream.headers" \
  --header 'Content-Type: application/json' \
  --data '{"model":"qwen3.5:2b","prompt":"Provide a motivational quote","think":false,"stream":true}' \
  "${base_url}/api/generate" \
  --output "${temporary_directory}/generate-stream.ndjson"

python3 - "${temporary_directory}/generate-stream.headers" "${temporary_directory}/generate-stream.ndjson" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as headers_file:
    headers = headers_file.read().lower()
assert "content-type: application/x-ndjson" in headers, headers
with open(sys.argv[2], encoding="utf-8") as response_file:
    chunks = [json.loads(line) for line in response_file if line.strip()]
assert len(chunks) > 1, chunks
assert chunks[-1]["done"] is True, chunks[-1]
assert "".join(chunk.get("response") or "" for chunk in chunks).strip(), chunks
PY

curl --fail --silent --show-error \
  --header 'Content-Type: application/json' \
  --data '{"model":"qwen3.5:2b","messages":[{"role":"user","content":"Give me a quick status update on the Ollama mock"}],"think":false,"stream":false}' \
  "${base_url}/api/chat" \
  --output "${temporary_directory}/chat.json"

python3 - "${temporary_directory}/chat.json" "${expected_model}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response_file:
    payload = json.load(response_file)
assert payload["model"] == sys.argv[2], payload
assert isinstance(payload["done"], bool), payload
assert payload["message"]["role"] == "assistant", payload
assert "port 11434" in payload["message"]["content"], payload
PY

curl --fail --silent --show-error \
  "${base_url}/api/chat/tools/definitions" \
  --output "${temporary_directory}/tool-definitions.json"

curl --fail --silent --show-error \
  --header 'Content-Type: application/json' \
  --data '{"model":"qwen3.5:2b","messages":[{"role":"user","content":"What iphones do we have available? Tell me the details about them"}],"tools":[{"type":"function","function":{"name":"list_products"}}],"stream":false}' \
  "${base_url}/api/chat" \
  --output "${temporary_directory}/tool-chat.json"

python3 - "${temporary_directory}/tool-definitions.json" "${temporary_directory}/tool-chat.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as definitions_file:
    definitions = json.load(definitions_file)
names = {definition["function"]["name"] for definition in definitions}
assert {"list_products", "get_product_snapshot"}.issubset(names), names
with open(sys.argv[2], encoding="utf-8") as response_file:
    payload = json.load(response_file)
assert isinstance(payload["done"], bool), payload
tool_calls = payload["message"]["tool_calls"]
assert tool_calls[0]["function"]["name"] == "list_products", payload
PY

echo "Ollama Mock container contract passed at ${base_url}"
