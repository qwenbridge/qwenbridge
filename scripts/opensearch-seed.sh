#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENSEARCH_URL:-http://localhost:9200}"
INDEX="${OPENSEARCH_INDEX:-qwenbridge-products}"

curl -X PUT "$BASE_URL/$INDEX" \
  -H "Content-Type: application/json" \
  -d '{
    "mappings": {
      "properties": {
        "title": { "type": "text" },
        "brand": { "type": "text" },
        "category": { "type": "keyword" },
        "description": { "type": "text" }
      }
    }
  }'

curl -X POST "$BASE_URL/$INDEX/_doc/product-1?refresh=true" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "iPhone 16 Pro",
    "brand": "Apple",
    "category": "smartphone",
    "description": "Apple flagship smartphone with pro camera system"
  }'

curl -X POST "$BASE_URL/$INDEX/_doc/product-2?refresh=true" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Samsung Galaxy S25",
    "brand": "Samsung",
    "category": "smartphone",
    "description": "Android flagship smartphone"
  }'