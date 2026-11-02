#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENSEARCH_URL:-http://localhost:9200}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
INDEX="${OPENSEARCH_INDEX:-qwenbridge-products}"
EMBEDDING_MODEL="${QWENBRIDGE_EMBEDDING_MODEL:-bge-m3}"
EMBEDDING_DIMENSIONS="${QWENBRIDGE_EMBEDDING_DIMENSIONS:-1024}"

opensearch_curl() {
  local args=(--connect-timeout 5 --max-time 30)
  if [[ -n "${OPENSEARCH_USERNAME:-}" ]]; then args+=(-u "${OPENSEARCH_USERNAME}:${OPENSEARCH_PASSWORD:-}"); fi
  if [[ -n "${OPENSEARCH_CA_CERT:-}" ]]; then args+=(--cacert "${OPENSEARCH_CA_CERT}"); fi
  curl "${args[@]}" "$@"
}

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
  local attributes
  if [[ "$#" -ge 6 ]]; then attributes="$6"; else attributes='{"price":49,"currency":"EUR","tenantId":"public","inStock":true,"condition":"new"}'; fi
  local content="$title. $brand. $category. $description"
  local embedding

  embedding="$(EMBEDDING_MODEL="$EMBEDDING_MODEL" embed "$content")"

  EMBEDDING="$embedding" python3 -c '
import json, os, sys
payload = {
    "documentId": sys.argv[6],
    "domain": "commerce",
    "catalogVersion": 1,
    "title": sys.argv[1],
    "brand": sys.argv[2],
    "category": sys.argv[3],
    "description": sys.argv[4],
    "embedding": json.loads(os.environ["EMBEDDING"]),
}
payload.update(json.loads(sys.argv[5]))
print(json.dumps(payload))
' "$title" "$brand" "$category" "$description" "$attributes" "$id" \
  | opensearch_curl -fsS -X POST "$BASE_URL/$INDEX/_doc/$id?refresh=true" \
      -H "Content-Type: application/json" \
      --data-binary @- >/dev/null
}

require_command curl
require_command python3

echo "Creating OpenSearch vector index: $INDEX"

index_status="$(opensearch_curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/$INDEX")"
if [[ "${index_status}" == "200" ]]; then
  if [[ "${QWENBRIDGE_SEED_RESET_INDEX:-false}" != "true" ]]; then
    echo "Index already exists: $INDEX. Choose a fresh OPENSEARCH_INDEX; explicit reset requires QWENBRIDGE_SEED_RESET_INDEX=true." >&2
    exit 1
  fi
  opensearch_curl -fsS -X DELETE "$BASE_URL/$INDEX" >/dev/null
elif [[ "${index_status}" != "404" ]]; then
  echo "Cannot check index $INDEX: HTTP $index_status" >&2
  exit 1
fi

opensearch_curl -fsS -X PUT "$BASE_URL/$INDEX" \
  -H "Content-Type: application/json" \
  -d "$(cat <<JSON
{
  "settings": {
    "index": {
      "knn": true
    },
    "analysis": { "normalizer": { "casefold": { "type": "custom", "filter": ["lowercase"] } } }
  },
  "mappings": {
    "properties": {
      "title": { "type": "text" },
      "brand": { "type": "text", "fields": { "keyword": { "type": "keyword", "normalizer": "casefold" } } },
      "tenantId": { "type": "keyword" },
      "documentId": { "type": "keyword" },
      "domain": { "type": "keyword" },
      "price": { "type": "double" },
      "currency": { "type": "keyword" },
      "inStock": { "type": "boolean" },
      "color": { "type": "text", "fields": { "keyword": { "type": "keyword", "normalizer": "casefold" } } },
      "size": { "type": "text", "fields": { "keyword": { "type": "keyword", "normalizer": "casefold" } } },
      "condition": { "type": "text", "fields": { "keyword": { "type": "keyword", "normalizer": "casefold" } } },
      "weightKg": { "type": "double" },
      "availableAt": { "type": "date" },
      "location": { "type": "text", "fields": { "keyword": { "type": "keyword", "normalizer": "casefold" } } },
      "category": { "type": "keyword", "normalizer": "casefold" },
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

for variant in good price currency color size condition stock tenant brand category; do
  attributes="$(python3 -c '
import json,sys
doc={"tenantId":"public","price":99,"currency":"EUR","color":"black","size":"42","condition":"new","inStock":True,"weightKg":0.5,"availableAt":"2026-10-01","location":"Stockholm"}
changes={"price":100,"currency":"USD","color":"red","size":"41","condition":"refurbished","stock":False,"tenant":"other","brand":"Adidas","category":"smartphone"}
k=sys.argv[1]
if k in changes: doc[{"stock":"inStock","tenant":"tenantId"}.get(k,k)]=changes[k]
print(json.dumps(doc))
' "$variant")"
  index_document "shoe-$variant" "Nike black running shoes size 42" "Nike" "shoes" "Running shoes black footwear" "$attributes"
done
index_document "laptop-good" "Lenovo Legion gaming laptop" "Lenovo" "laptop" "Lightweight gaming laptop computer" '{"tenantId":"public","price":1299,"currency":"EUR","inStock":true,"condition":"new","weightKg":1.7}'

opensearch_curl -fsS -X POST "$BASE_URL/$INDEX/_refresh" >/dev/null

echo "Seed complete: $INDEX"
