verify_ollama_models() {
  local attempt=""
  local models=""

  for attempt in {1..180}; do
    models="$(docker exec "${OLLAMA_CONTAINER}" ollama list 2>/dev/null || true)"
    echo "${models}"

    if echo "${models}" | awk '{print $1}' | grep -q "^${QWEN_MODEL}" \
      && echo "${models}" | awk '{print $1}' | grep -q "^${EMBEDDING_MODEL}"; then
      return 0
    fi

    info "Waiting for Ollama models: ${QWEN_MODEL}, ${EMBEDDING_MODEL}"
    sleep 5
  done

  docker logs --tail 240 "${OLLAMA_CONTAINER}" 2>/dev/null || true
  docker logs --tail 240 "${OLLAMA_CONTAINER}-init" 2>/dev/null || true
  return 1
}

seed_opensearch() {
  local body="/tmp/qwenbridge-opensearch-count.json"
  local count=""

  echo "Seeding OpenSearch index with real BGE-M3 embeddings: ${OPENSEARCH_INDEX}"

  OPENSEARCH_URL="${OPENSEARCH_URL}" \
  OPENSEARCH_INDEX="${OPENSEARCH_INDEX}" \
  OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}" \
  QWENBRIDGE_EMBEDDING_MODEL="${EMBEDDING_MODEL}" \
  ./scripts/opensearch-seed.sh || return 1

  curl -fsS "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_count" -o "${body}" || return 1
  jq . "${body}"

  count="$(jq -r '.count // 0' "${body}")"
  echo "OpenSearch document count: ${count}"

  [[ "${count}" -gt 0 ]]
}

opensearch_vector_mapping_validation() {
  local body="/tmp/qwenbridge-opensearch-mapping.json"

  curl -fsS "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_mapping" -o "${body}" || return 1
  jq . "${body}"

  jq -e \
    --arg index "${OPENSEARCH_INDEX}" \
    '.[ $index ].mappings.properties.embedding.type == "knn_vector"
     and .[ $index ].mappings.properties.embedding.dimension == 1024
     and .[ $index ].mappings.properties.category.type == "keyword"' \
    "${body}" >/dev/null
}

ollama_embedding_generation_validation() {
  local body="/tmp/qwenbridge-embedding.json"

  curl -fsS "${OLLAMA_URL:-http://localhost:11434}/api/embed" \
    -H "Content-Type: application/json" \
    -d "{\"model\":\"${EMBEDDING_MODEL}\",\"input\":\"gaming mouse razer esports\"}" \
    -o "${body}" || return 1

  jq '{model, embeddingCount: (.embeddings | length), embeddingLength: (.embeddings[0] | length)}' "${body}"

  jq -e \
    '.embeddings
     and (.embeddings[0] | type == "array")
     and (.embeddings[0] | length == 1024)' \
    "${body}" >/dev/null
}

opensearch_vector_retrieval_validation() {
  local embedding=""
  local body="/tmp/qwenbridge-vector-search.json"
  local first_title=""

  embedding="$(
    curl -fsS "${OLLAMA_URL:-http://localhost:11434}/api/embed" \
      -H "Content-Type: application/json" \
      -d "{\"model\":\"${EMBEDDING_MODEL}\",\"input\":\"gaming mouse for esports\"}" \
      | jq -c '.embeddings[0]'
  )"

  [[ -n "${embedding}" && "${embedding}" != "null" ]] || return 1

  curl -fsS "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_search?pretty" \
    -H "Content-Type: application/json" \
    -d "{
      \"size\": 3,
      \"query\": {
        \"knn\": {
          \"embedding\": {
            \"vector\": ${embedding},
            \"k\": 3
          }
        }
      }
    }" \
    -o "${body}" || return 1

  jq '.hits.hits[] | {id: ._id, score: ._score, title: ._source.title}' "${body}"

  first_title="$(jq -r '.hits.hits[0]._source.title // ""' "${body}")"
  [[ "${first_title}" == "Razer DeathAdder V3" ]]
}

opensearch_hybrid_retrieval_validation() {
  local embedding=""
  local body="/tmp/qwenbridge-hybrid-search.json"
  local first_title=""
  local duplicate_count=""

  embedding="$(
    curl -fsS "${OLLAMA_URL:-http://localhost:11434}/api/embed" \
      -H "Content-Type: application/json" \
      -d "{\"model\":\"${EMBEDDING_MODEL}\",\"input\":\"gaming mouse razer esports\"}" \
      | jq -c '.embeddings[0]'
  )"

  [[ -n "${embedding}" && "${embedding}" != "null" ]] || return 1

  curl -fsS "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_search?pretty" \
    -H "Content-Type: application/json" \
    -d "{
      \"size\": 3,
      \"query\": {
        \"bool\": {
          \"should\": [
            {
              \"multi_match\": {
                \"query\": \"razer gaming mouse\",
                \"fields\": [\"title^3\", \"brand^2\", \"category\", \"description\"]
              }
            },
            {
              \"knn\": {
                \"embedding\": {
                  \"vector\": ${embedding},
                  \"k\": 3
                }
              }
            }
          ],
          \"minimum_should_match\": 1
        }
      }
    }" \
    -o "${body}" || return 1

  jq '.hits.hits[] | {id: ._id, score: ._score, title: ._source.title}' "${body}"

  first_title="$(jq -r '.hits.hits[0]._source.title // ""' "${body}")"
  duplicate_count="$(jq -r '[.hits.hits[]._id] as $ids | ($ids | length) - ($ids | unique | length)' "${body}")"

  [[ "${first_title}" == "Razer DeathAdder V3" ]] \
    && [[ "${duplicate_count}" == "0" ]]
}

ollama_chat_readiness_validation() {
  local body="/tmp/qwenbridge-ollama-chat-readiness.json"
  local status=""

  status="$(
    curl -sS \
      --connect-timeout 10 \
      --max-time 180 \
      -H "Content-Type: application/json" \
      -d "{\"model\":\"${QWEN_MODEL}\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply with exactly: ready\"}],\"stream\":false,\"options\":{\"num_predict\":4}}" \
      -o "${body}" \
      -w "%{http_code}" \
      "${OLLAMA_URL:-http://localhost:11434}/api/chat"
  )"

  echo "Ollama chat readiness HTTP status: ${status}"
  jq . "${body}" || true

  [[ "${status}" == "200" ]] \
    && jq -e '.message.content' "${body}" >/dev/null
}
