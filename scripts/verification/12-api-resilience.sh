#!/usr/bin/env bash

api_resilience_corpus_validation() {
  malformed_json_resilience_validation \
    && blank_query_resilience_validation \
    && oversized_query_resilience_validation \
    && invalid_content_type_resilience_validation
}

assert_no_internal_details() {
  local body="$1"

  ! grep -Eiq 'StackTrace|NullPointerException|org\.springframework|io\.qwenbridge' "${body}"
}

assert_json_error_response() {
  local headers="$1"
  local body="$2"

  assert_common_headers "${headers}" \
    && jq -e '
      .requestId
      and .timestamp
      and (
        (.status == 400 and (.code == "VALIDATION_ERROR" or .code == "BAD_REQUEST"))
        or (.status == 413)
      )
    ' "${body}" >/dev/null \
    && assert_no_internal_details "${body}"
}

assert_rate_limited_error_response() {
  local body="$1"

  jq -e '
    .status == 429
    and .code == "RATE_LIMITED"
    and .requestId
    and .timestamp
  ' "${body}" >/dev/null \
    && assert_no_internal_details "${body}"
}

malformed_json_resilience_validation() {
  local headers="/tmp/qwenbridge-resilience-malformed.headers"
  local body="/tmp/qwenbridge-resilience-malformed.json"
  local status=""

  status="$(
    curl -sS -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      --data '{"query":' \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "Malformed JSON status: ${status}"
  jq . "${body}" || cat "${body}" || true

  [[ "${status}" == "400" ]] \
    && assert_json_error_response "${headers}" "${body}"
}

blank_query_resilience_validation() {
  local headers="/tmp/qwenbridge-resilience-blank.headers"
  local body="/tmp/qwenbridge-resilience-blank.json"
  local status=""

  status="$(
    curl -sS -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      --data '{"query":""}' \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "Blank query status: ${status}"
  jq . "${body}" || cat "${body}" || true

  [[ "${status}" == "400" ]] \
    && assert_json_error_response "${headers}" "${body}"
}

oversized_query_resilience_validation() {
  local headers="/tmp/qwenbridge-resilience-oversized.headers"
  local body="/tmp/qwenbridge-resilience-oversized.json"
  local status=""
  local request_id="verify-oversized-$(date +%s)"
  local payload=""

  payload="$(
    python3 - <<PY
import json
print(json.dumps({
  "requestId": "${request_id}",
  "query": "gaming laptop " * 5000
}))
PY
  )"

  status="$(
    curl -sS -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -H "X-Request-ID: ${request_id}" \
      --data "${payload}" \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "Oversized query status: ${status}"
  jq . "${body}" || cat "${body}" || true

  if [[ "${status}" == "429" ]]; then
    assert_rate_limited_error_response "${body}"
  else
    [[ "${status}" == "400" || "${status}" == "413" ]] \
      && assert_json_error_response "${headers}" "${body}"
  fi
}

invalid_content_type_resilience_validation() {
  local headers="/tmp/qwenbridge-resilience-content-type.headers"
  local body="/tmp/qwenbridge-resilience-content-type.json"
  local status=""

  status="$(
    curl -sS -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: text/plain" \
      --data 'query=table' \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "Invalid content type status: ${status}"
  jq . "${body}" || cat "${body}" || true

  if [[ "${status}" == "429" ]]; then
    assert_rate_limited_error_response "${body}"
  else
    [[ "${status}" == "400" || "${status}" == "415" ]] \
      && assert_common_headers "${headers}" \
      && assert_no_internal_details "${body}"
  fi
}