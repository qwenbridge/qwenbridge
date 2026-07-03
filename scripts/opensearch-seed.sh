#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENSEARCH_URL:-http://localhost:9200}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
INDEX="${OPENSEARCH_INDEX:-qwenbridge-products}"
EMBEDDING_MODEL="${QWENBRIDGE_EMBEDDING_MODEL:-bge-m3}"
EMBEDDING_DIMENSIONS="${QWENBRIDGE_EMBEDDING_DIMENSIONS:-1024}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

embed() {
  local text="$1"

  curl -fsS "$OLLAMA_URL/api/embed" \
    -H "Content-Type: application/json" \
    -d "$(python3 -c 'import json, os, sys; print(json.dumps({"model": os.environ["EMBEDDING_MODEL"], "input": sys.argv[1]}))' "$text")" \
    | python3 -c '
import json, sys
payload = json.load(sys.stdin)
if "embedding" in payload:
    print(json.dumps(payload["embedding"]))
elif "embeddings" in payload and payload["embeddings"]:
    print(json.dumps(payload["embeddings"][0]))
else:
    raise SystemExit("Ollama embedding response did not contain embedding/embeddings")
'
}

index_document() {
  local id="$1"
  local title="$2"
  local brand="$3"
  local category="$4"
  local description="$5"
  local content="$title. $brand. $category. $description"
  local embedding

  embedding="$(EMBEDDING_MODEL="$EMBEDDING_MODEL" embed "$content")"

  python3 -c '
import json, os, sys
payload = {
    "title": sys.argv[1],
    "brand": sys.argv[2],
    "category": sys.argv[3],
    "description": sys.argv[4],
    "embedding": json.loads(os.environ["EMBEDDING"]),
}
print(json.dumps(payload))
' "$title" "$brand" "$category" "$description" \
  | curl -fsS -X POST "$BASE_URL/$INDEX/_doc/$id?refresh=true" \
      -H "Content-Type: application/json" \
      --data-binary @- >/dev/null
}

require_command curl
require_command python3

echo "Creating OpenSearch vector index: $INDEX"

curl -fsS -X DELETE "$BASE_URL/$INDEX" >/dev/null 2>&1 || true

curl -fsS -X PUT "$BASE_URL/$INDEX" \
  -H "Content-Type: application/json" \
  -d "$(cat <<JSON
{
  "settings": {
    "index": {
      "knn": true
    }
  },
  "mappings": {
    "properties": {
      "title": { "type": "text" },
      "brand": { "type": "text" },
      "category": { "type": "keyword" },
      "description": { "type": "text" },
      "embedding": {
        "type": "knn_vector",
        "dimension": $EMBEDDING_DIMENSIONS,
        "method": {
          "name": "hnsw",
          "space_type": "cosinesimil",
          "engine": "lucene"
        }
      }
    }
  }
}
JSON
)" >/dev/null

echo "Generating BGE-M3 embeddings through Ollama and indexing deterministic corpus"

index_document "product-1" "iPhone 16 Pro" "Apple" "smartphone" "Flagship smartphone with pro camera system and premium mobile photography features"
index_document "product-2" "Samsung Galaxy S25" "Samsung" "smartphone" "Android flagship smartphone with bright display and advanced camera features"
index_document "product-3" "Sony WH-1000XM5" "Sony" "headphones" "Wireless noise cancelling headphones for travel music and focus"
index_document "product-4" "Logitech MX Master 3S" "Logitech" "mouse" "Ergonomic wireless productivity mouse for office work and precision scrolling"
index_document "product-5" "Razer DeathAdder V3" "Razer" "mouse" "Lightweight wired gaming mouse with high precision sensor for competitive esports"

curl -fsS -X POST "$BASE_URL/$INDEX/_refresh" >/dev/null

echo "Seed complete: $INDEX"
