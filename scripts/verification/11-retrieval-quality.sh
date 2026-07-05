#!/usr/bin/env bash

retrieval_quality_corpus_validation() {
  local failed=0
  local case_file="/tmp/qwenbridge-retrieval-quality-corpus.jsonl"
  local headers="/tmp/qwenbridge-retrieval-quality.headers"
  local body="/tmp/qwenbridge-retrieval-quality.json"
  local status=""
  local index=0

  cat > "${case_file}" <<'JSONL'
{"name":"iphone","query":"best iphone for photography","expected":"iPhone 16 Pro"}
{"name":"samsung","query":"android flagship smartphone with bright display","expected":"Samsung Galaxy S25"}
{"name":"headphones","query":"wireless noise cancelling headphones for travel","expected":"Sony WH-1000XM5"}
{"name":"productivity_mouse","query":"ergonomic wireless mouse for office work","expected":"Logitech MX Master 3S"}
{"name":"gaming_mouse","query":"lightweight gaming mouse for esports","expected":"Razer DeathAdder V3"}
JSONL

  while IFS= read -r line; do
    index=$((index + 1))

    local name=""
    local query=""
    local expected=""
    local request_id=""
    local payload=""
    local titles=""

    name="$(echo "${line}" | jq -r '.name')"
    query="$(echo "${line}" | jq -r '.query')"
    expected="$(echo "${line}" | jq -r '.expected')"
    request_id="verify-retrieval-${name}-${index}-$(date +%s)"

    payload="$(jq -n --arg requestId "${request_id}" --arg query "${query}" '{requestId: $requestId, query: $query}')"

    status="$(
      curl -sS \
        -D "${headers}" \
        -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -H "X-Request-ID: ${request_id}" \
        --data "${payload}" \
        -o "${body}" \
        -w "%{http_code}"
    )"

    echo ""
    echo "Retrieval case #${index}: ${name}"
    echo "Expected title: ${expected}"
    echo "HTTP status: ${status}"
    jq . "${body}" || cat "${body}" || true

    [[ "${status}" == "200" ]] || {
      echo "Retrieval quality case did not return 200: ${name}"
      failed=1
      continue
    }

    assert_common_headers "${headers}" || failed=1

    jq -e '.search.available == true and (.search.hits | type == "array")' "${body}" >/dev/null || {
      echo "Search result is not available for retrieval case: ${name}"
      failed=1
      continue
    }

    titles="$(jq -r '.search.hits[].document.title? // empty' "${body}")"
    echo "Returned titles:"
    echo "${titles}"

    echo "${titles}" | grep -Fx "${expected}" >/dev/null || {
      echo "Expected title was not found for retrieval case: ${name}"
      failed=1
    }
  done < "${case_file}"

  [[ "${failed}" -eq 0 ]]
}
