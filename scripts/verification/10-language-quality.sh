#!/usr/bin/env bash

language_quality_corpus_validation() {
  local failed=0
  local case_file="/tmp/qwenbridge-language-quality-corpus.jsonl"
  local headers="/tmp/qwenbridge-language-quality.headers"
  local body="/tmp/qwenbridge-language-quality.json"
  local status=""
  local index=0

  cat > "${case_file}" <<'JSONL'
{"name":"en_sentence","query":"What are the best wireless headphones for working from home?","language":"en"}
{"name":"fa_sentence","query":"بهترین هدفون بی‌سیم برای کار کردن در خانه چیست؟","language":"fa"}
{"name":"ar_sentence","query":"ما هي أفضل سماعات لاسلكية للعمل من المنزل؟","language":"ar"}
{"name":"ja_sentence","query":"自宅で仕事をするための最高のワイヤレスヘッドホンは何ですか？","language":"ja"}
{"name":"zh_sentence","query":"哪些无线耳机最适合在家办公？","language":"zh"}
{"name":"sv_sentence","query":"Vilka trådlösa hörlurar är bäst för att arbeta hemifrån?","language":"sv"}
{"name":"de_sentence","query":"Welche kabellosen Kopfhörer eignen sich am besten für die Arbeit zu Hause?","language":"de"}
{"name":"fr_sentence","query":"Quels écouteurs sans fil sont les meilleurs pour travailler à domicile ?","language":"fr"}
{"name":"es_sentence","query":"¿Qué auriculares inalámbricos son mejores para trabajar desde casa?","language":"es"}
{"name":"tr_sentence","query":"Evden çalışmak için en iyi kablosuz kulaklıklar hangileridir?","language":"tr"}
{"name":"nl_sentence","query":"Welke draadloze hoofdtelefoon is het beste om thuis te werken?","language":"nl"}
{"name":"unknown_symbols","query":"12345 !!! ???","language":"unknown"}
JSONL

  while IFS= read -r line; do
    index=$((index + 1))

    local name=""
    local query=""
    local expected_language=""
    local request_id=""
    local payload=""
    local actual_language=""

    name="$(echo "${line}" | jq -r '.name')"
    query="$(echo "${line}" | jq -r '.query')"
    expected_language="$(echo "${line}" | jq -r '.language')"
    request_id="verify-language-${name}-${index}-$(date +%s)"

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
    echo "Language case #${index}: ${name}"
    echo "Expected language: ${expected_language}"
    echo "HTTP status: ${status}"
    jq . "${body}" || cat "${body}" || true

    [[ "${status}" == "200" ]] || {
      echo "Language quality case did not return 200: ${name}"
      failed=1
      continue
    }

    assert_common_headers "${headers}" || failed=1

    actual_language="$(jq -r '.language // "missing"' "${body}")"
    echo "Actual language: ${actual_language}"

    [[ "${actual_language}" == "${expected_language}" ]] || {
      echo "Language mismatch for ${name}: expected=${expected_language}, actual=${actual_language}"
      failed=1
    }

    jq -e \
      --arg request_id "${request_id}" \
      '.requestId == $request_id
       and .pipelineTrace
       and .decision
       and .policyPassed != null' \
      "${body}" >/dev/null || failed=1
  done < "${case_file}"

  [[ "${failed}" -eq 0 ]]
}
