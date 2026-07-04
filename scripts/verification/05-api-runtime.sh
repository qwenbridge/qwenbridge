app_public_health_up() {
  curl -fsS "${BASE_URL}${PUBLIC_HEALTH_ENDPOINT}" \
    | jq -e '.status == "UP"' >/dev/null 2>&1
}

wait_for_app_readiness() {
  local attempt=""

  for attempt in {1..120}; do
    if app_public_health_up; then
      return 0
    fi
    sleep 2
  done

  docker logs --tail 200 "${APP_CONTAINER}" || true
  return 1
}

assert_common_headers() {
  local headers_file="$1"

  grep -qi '^x-request-id:' "${headers_file}" \
    && grep -qi "^x-qwenbridge-version: ${EXPECTED_VERSION}" "${headers_file}"
}

assert_sse_headers() {
  local headers_file="$1"

  grep -qi '^content-type: text/event-stream' "${headers_file}" \
    && assert_common_headers "${headers_file}"
}

json_payload() {
  local request_id="$1"
  local query="$2"

  jq -n \
    --arg requestId "${request_id}" \
    --arg query "${query}" \
    '{requestId: $requestId, query: $query}'
}

actuator_health_endpoint() {
  local body="/tmp/qwenbridge-actuator-health.json"

  curl -fsS "${BASE_URL}/actuator/health" -o "${body}" || {
    cat "${body}" 2>/dev/null || true
    return 1
  }

  jq . "${body}" || true
  jq -e '.status == "UP"' "${body}" >/dev/null
}

public_health_endpoint() {
  local headers="/tmp/qwenbridge-health.headers"
  local body="/tmp/qwenbridge-health.json"

  curl -fsS \
    -D "${headers}" \
    "${BASE_URL}${PUBLIC_HEALTH_ENDPOINT}" \
    -o "${body}" \
    || return 1

  assert_common_headers "${headers}" \
    && jq -e \
      '.status == "UP" and .service == "qwenbridge" and .apiVersion == "v1"' \
      "${body}" >/dev/null
}

version_endpoint() {
  local headers="/tmp/qwenbridge-version.headers"
  local body="/tmp/qwenbridge-version.json"

  curl -fsS \
    -D "${headers}" \
    "${BASE_URL}${VERSION_ENDPOINT}" \
    -o "${body}" \
    || return 1

  assert_common_headers "${headers}" \
    && jq -e \
      --arg version "${EXPECTED_VERSION}" \
      '.name == "qwenbridge" and .version == $version and .apiVersion == "v1" and .javaVersion' \
      "${body}" >/dev/null
}

analyze_api_endpoint() {
  local headers="/tmp/qwenbridge-analyze.headers"
  local body="/tmp/qwenbridge-analyze.json"
  local request_id="verify-release-analyze-$(date +%s)"
  local payload=""
  local status=""

  payload="$(json_payload "${request_id}" "${TEST_QUERY}")"

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

  echo "HTTP status: ${status}"
  jq . "${body}" || true

  [[ "${status}" == "200" ]] \
    && assert_common_headers "${headers}" \
    && jq -e \
      --arg request_id "${request_id}" \
      '.requestId == $request_id
       and .originalQuery
       and .cache
       and .pipelineTrace
       and .executionPlan
       and .executionResult
       and .search' \
      "${body}" >/dev/null
}

ai_chat_endpoint() {
  local headers="/tmp/qwenbridge-ai-chat.headers"
  local body="/tmp/qwenbridge-ai-chat.json"
  local status=""
  local payload=""

  payload="$(jq -n --arg prompt "${TEST_PROMPT}" '{prompt: $prompt}')"

  status="$(
    curl -sS \
      -D "${headers}" \
      -X POST "${BASE_URL}${AI_CHAT_ENDPOINT}" \
      -H "Content-Type: application/json" \
      --data "${payload}" \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  jq . "${body}" || true

  assert_common_headers "${headers}" || return 1

  if [[ "${status}" == "200" ]]; then
    jq -e '.content' "${body}" >/dev/null
    return $?
  fi

  if [[ "${status}" == "502" ]]; then
    jq -e '.code == "AI_PROVIDER_ERROR" and .status == 502' "${body}" >/dev/null
    return $?
  fi

  return 1
}

validation_error_contract() {
  local headers="/tmp/qwenbridge-validation.headers"
  local body="/tmp/qwenbridge-validation.json"
  local status=""

  status="$(
    curl -sS \
      -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -d '{"query":""}' \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  jq . "${body}" || true

  [[ "${status}" == "400" ]] \
    && assert_common_headers "${headers}" \
    && jq -e \
      '.status == 400
       and .error == "Bad Request"
       and .code == "VALIDATION_ERROR"
       and .path == "/api/v1/search/analyze"
       and .requestId
       and .timestamp' \
      "${body}" >/dev/null
}

custom_request_id_propagation() {
  local headers="/tmp/qwenbridge-request-id.headers"
  local body="/tmp/qwenbridge-request-id.json"
  local request_id="verify-release-custom-request-id"

  curl -fsS \
    -D "${headers}" \
    -H "X-Request-ID: ${request_id}" \
    "${BASE_URL}${PUBLIC_HEALTH_ENDPOINT}" \
    -o "${body}" \
    || return 1

  grep -qi "^x-request-id: ${request_id}" "${headers}"
}

cors_preflight_validation() {
  local headers="/tmp/qwenbridge-cors.headers"
  local status=""

  status="$(
    curl -sS \
      -D "${headers}" \
      -X OPTIONS "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Origin: http://localhost:3000" \
      -H "Access-Control-Request-Method: POST" \
      -o /tmp/qwenbridge-cors.body \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  cat "${headers}"

  { [[ "${status}" == "200" ]] || [[ "${status}" == "204" ]]; } \
    && grep -qi '^access-control-allow-origin:' "${headers}" \
    && grep -qi '^access-control-allow-methods:' "${headers}"
}

cache_miss_hit_validation() {
  local first_request_id="verify-release-cache-first-$(date +%s)"
  local second_request_id="verify-release-cache-second-$(date +%s)"
  local first_status=""
  local second_status=""
  local first_miss=""
  local second_hit=""
  local redis_dbsize=""

  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null || return 1

  first_status="$(
    curl -sS \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -H "X-Request-ID: ${first_request_id}" \
      --data "$(json_payload "${first_request_id}" "${TEST_QUERY}")" \
      -o /tmp/qwenbridge-cache-first.json \
      -w "%{http_code}"
  )"

  second_status="$(
    curl -sS \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -H "X-Request-ID: ${second_request_id}" \
      --data "$(json_payload "${second_request_id}" "${TEST_QUERY}")" \
      -o /tmp/qwenbridge-cache-second.json \
      -w "%{http_code}"
  )"

  echo "First HTTP status: ${first_status}"
  jq . /tmp/qwenbridge-cache-first.json || true

  echo "Second HTTP status: ${second_status}"
  jq . /tmp/qwenbridge-cache-second.json || true

  [[ "${first_status}" == "200" ]] || return 1
  [[ "${second_status}" == "200" ]] || return 1

  first_miss="$(jq -r '.cache.miss // false' /tmp/qwenbridge-cache-first.json)"
  second_hit="$(jq -r '.cache.hit // false' /tmp/qwenbridge-cache-second.json)"
  redis_dbsize="$(docker exec "${REDIS_CONTAINER}" redis-cli dbsize | tr -d '\r')"

  echo "First request cache.miss: ${first_miss}"
  echo "Second request cache.hit: ${second_hit}"
  echo "Redis dbsize: ${redis_dbsize}"

  [[ "${first_miss}" == "true" ]] \
    && [[ "${second_hit}" == "true" ]] \
    && [[ "${redis_dbsize}" -gt 0 ]]
}

singleflight_validation() {
  local request_id_prefix="verify-release-singleflight-$(date +%s)"
  local i=""
  local redis_dbsize=""
  local failed_requests=0
  local pids=()

  rm -f /tmp/qwenbridge-singleflight-*.json
  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null || return 1

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
    (
      local request_id="${request_id_prefix}-${i}"

      curl -sS \
        --connect-timeout 10 \
        --max-time 180 \
        -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -H "X-Request-ID: ${request_id}" \
        --data "$(json_payload "${request_id}" "${TEST_QUERY}")" \
        -o "/tmp/qwenbridge-singleflight-${i}.json"
    ) &
    pids+=("$!")
  done

  for i in "${!pids[@]}"; do
    if ! wait "${pids[$i]}"; then
      echo "Concurrent request $((i + 1)) failed, timed out, or could not connect."
      failed_requests=$((failed_requests + 1))
    fi
  done

  [[ "${failed_requests}" -eq 0 ]] || return 1

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
    if ! jq -e \
      '.originalQuery and .cache and .pipelineTrace and .requestId' \
      "/tmp/qwenbridge-singleflight-${i}.json" >/dev/null; then
      echo "Invalid SingleFlight response: /tmp/qwenbridge-singleflight-${i}.json"
      cat "/tmp/qwenbridge-singleflight-${i}.json" 2>/dev/null || true
      return 1
    fi
  done

  redis_dbsize="$(docker exec "${REDIS_CONTAINER}" redis-cli dbsize | tr -d '\r')"

  echo "Concurrent requests: ${CONCURRENT_REQUESTS}"
  echo "Redis dbsize after concurrent test: ${redis_dbsize}"

  [[ "${redis_dbsize}" -gt 0 ]]
}

openapi_endpoint() {
  curl -fsS "${BASE_URL}/v3/api-docs" | jq . >/dev/null
}

openapi_contains_v8_endpoints() {
  local body="/tmp/qwenbridge-openapi.json"

  curl -fsS "${BASE_URL}/v3/api-docs" -o "${body}" || return 1

  jq -e '
    .paths["/api/v1/search/analyze"] and
    .paths["/api/v1/search/stream/{requestId}"] and
    .paths["/api/v1/ai/chat"] and
    .paths["/api/v1/health"] and
    .paths["/api/v1/version"]
  ' "${body}" >/dev/null
}

swagger_endpoint() {
  curl -fsS "${BASE_URL}/swagger-ui/index.html" >/dev/null \
    || curl -fsS "${BASE_URL}/swagger" >/dev/null
}
