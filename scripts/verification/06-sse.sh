wait_for_file_pattern() {
  local file="$1"
  local pattern="$2"
  local timeout_seconds="$3"
  local elapsed=0

  while [[ "${elapsed}" -lt "${timeout_seconds}" ]]; do
    if [[ -f "${file}" ]] && grep -Eq "${pattern}" "${file}" 2>/dev/null; then
      return 0
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  return 1
}

wait_for_process_exit() {
  local pid="$1"
  local timeout_seconds="$2"
  local elapsed=0

  while [[ "${elapsed}" -lt "${timeout_seconds}" ]]; do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      return 0
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  return 1
}

wait_for_sse_headers() {
  local headers_file="$1"
  local timeout_seconds="$2"
  local elapsed=0

  while [[ "${elapsed}" -lt "${timeout_seconds}" ]]; do
    if [[ -s "${headers_file}" ]] && assert_sse_headers "${headers_file}"; then
      return 0
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  return 1
}

print_sse_diagnostics() {
  local primary_headers="$1"
  local primary_log="$2"
  local unrelated_headers="$3"
  local unrelated_log="$4"
  local analyze_headers="$5"
  local analyze_body="$6"

  echo ""
  echo "========== SSE Analyze Headers =========="
  cat "${analyze_headers}" 2>/dev/null || true

  echo ""
  echo "========== SSE Analyze Response =========="
  jq . "${analyze_body}" 2>/dev/null || cat "${analyze_body}" 2>/dev/null || true

  echo ""
  echo "========== SSE Primary Headers =========="
  cat "${primary_headers}" 2>/dev/null || true

  echo ""
  echo "========== SSE Primary Stream =========="
  cat "${primary_log}" 2>/dev/null || true

  echo ""
  echo "========== SSE Unrelated Headers =========="
  cat "${unrelated_headers}" 2>/dev/null || true

  echo ""
  echo "========== SSE Unrelated Stream =========="
  cat "${unrelated_log}" 2>/dev/null || true
}

sse_streaming_lifecycle_validation() {
  local suffix=""
  local request_id=""
  local unrelated_request_id=""

  local primary_headers="/tmp/qwenbridge-sse-primary.headers"
  local primary_log="/tmp/qwenbridge-sse-primary.log"

  local unrelated_headers="/tmp/qwenbridge-sse-unrelated.headers"
  local unrelated_log="/tmp/qwenbridge-sse-unrelated.log"

  local analyze_headers="/tmp/qwenbridge-sse-analyze.headers"
  local analyze_body="/tmp/qwenbridge-sse-analyze.json"

  local analyze_status=""
  local primary_started_line=""
  local primary_terminal_line=""
  local primary_pid=""
  local unrelated_pid=""
  local terminal_pattern='^event:pipeline\.(completed|failed|stopped)$'

  cleanup_background_processes

  suffix="$(date +%s)"
  request_id="verify-release-sse-primary-${suffix}"
  unrelated_request_id="verify-release-sse-unrelated-${suffix}"

  rm -f \
    "${primary_headers}" \
    "${primary_log}" \
    "${unrelated_headers}" \
    "${unrelated_log}" \
    "${analyze_headers}" \
    "${analyze_body}"

  info "Opening primary SSE stream: ${request_id}"

  curl --silent --show-error --no-buffer \
    --connect-timeout "${SSE_CONNECT_TIMEOUT_SECONDS}" \
    --max-time "$((SSE_EVENT_WAIT_SECONDS + 30))" \
    -D "${primary_headers}" \
    -H "Accept: text/event-stream" \
    "${BASE_URL}${SSE_ENDPOINT_PREFIX}/${request_id}" \
    > "${primary_log}" 2>&1 &

  primary_pid=$!
  SSE_PRIMARY_PID="${primary_pid}"

  info "Opening unrelated SSE stream: ${unrelated_request_id}"

  curl --silent --show-error --no-buffer \
    --connect-timeout "${SSE_CONNECT_TIMEOUT_SECONDS}" \
    --max-time "$((SSE_EVENT_WAIT_SECONDS + 30))" \
    -D "${unrelated_headers}" \
    -H "Accept: text/event-stream" \
    "${BASE_URL}${SSE_ENDPOINT_PREFIX}/${unrelated_request_id}" \
    > "${unrelated_log}" 2>&1 &

  unrelated_pid=$!
  SSE_UNRELATED_PID="${unrelated_pid}"

  if ! wait_for_sse_headers "${primary_headers}" "${SSE_CONNECT_TIMEOUT_SECONDS}"; then
    echo "Primary SSE stream did not return valid headers."
    print_sse_diagnostics \
      "${primary_headers}" \
      "${primary_log}" \
      "${unrelated_headers}" \
      "${unrelated_log}" \
      "${analyze_headers}" \
      "${analyze_body}"
    return 1
  fi

  if ! wait_for_sse_headers "${unrelated_headers}" "${SSE_CONNECT_TIMEOUT_SECONDS}"; then
    echo "Unrelated SSE stream did not return valid headers."
    print_sse_diagnostics \
      "${primary_headers}" \
      "${primary_log}" \
      "${unrelated_headers}" \
      "${unrelated_log}" \
      "${analyze_headers}" \
      "${analyze_body}"
    return 1
  fi

  info "Calling analyze endpoint with matching request ID: ${request_id}"

  analyze_status="$(
    curl --silent --show-error \
      -D "${analyze_headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -H "X-Request-ID: ${request_id}" \
      --data "$(json_payload "${request_id}" "${TEST_QUERY}")" \
      -o "${analyze_body}" \
      -w "%{http_code}"
  )"

  echo "Analyze HTTP status: ${analyze_status}"
  jq . "${analyze_body}" || true

  if [[ "${analyze_status}" != "200" ]]; then
    echo "Analyze endpoint did not return HTTP 200."
    print_sse_diagnostics \
      "${primary_headers}" \
      "${primary_log}" \
      "${unrelated_headers}" \
      "${unrelated_log}" \
      "${analyze_headers}" \
      "${analyze_body}"
    return 1
  fi

  assert_common_headers "${analyze_headers}" || return 1

  jq -e \
    --arg request_id "${request_id}" \
    '.requestId == $request_id' \
    "${analyze_body}" >/dev/null || return 1

  if ! wait_for_file_pattern \
    "${primary_log}" \
    "${terminal_pattern}" \
    "${SSE_EVENT_WAIT_SECONDS}"; then

    echo "Timed out waiting for terminal SSE event."
    print_sse_diagnostics \
      "${primary_headers}" \
      "${primary_log}" \
      "${unrelated_headers}" \
      "${unrelated_log}" \
      "${analyze_headers}" \
      "${analyze_body}"
    return 1
  fi

  sleep 1

  if kill -0 "${primary_pid}" >/dev/null 2>&1; then
    info "Primary SSE curl is still open after terminal event; closing verifier client."
    terminate_background_process "${primary_pid}"
  else
    wait "${primary_pid}" >/dev/null 2>&1 || true
  fi

  SSE_PRIMARY_PID=""

  primary_started_line="$(
    grep -n '^event:pipeline.started$' "${primary_log}" \
      | head -n 1 \
      | cut -d: -f1
  )"

  primary_terminal_line="$(
    grep -nE "${terminal_pattern}" "${primary_log}" \
      | tail -n 1 \
      | cut -d: -f1
  )"

  print_sse_diagnostics \
    "${primary_headers}" \
    "${primary_log}" \
    "${unrelated_headers}" \
    "${unrelated_log}" \
    "${analyze_headers}" \
    "${analyze_body}"

  [[ -n "${primary_started_line}" ]] || {
    echo "Primary SSE stream never received pipeline.started."
    return 1
  }

  [[ -n "${primary_terminal_line}" ]] || {
    echo "Primary SSE stream never received a terminal pipeline event."
    return 1
  }

  [[ "${primary_started_line}" -lt "${primary_terminal_line}" ]] || {
    echo "Terminal SSE event appeared before pipeline.started."
    return 1
  }

  grep -Eq '^id:[[:space:]]*[^[:space:]]+' "${primary_log}" || {
    echo "Primary SSE stream has no valid event id."
    return 1
  }

  grep -Eq '^data:.*' "${primary_log}" || {
    echo "Primary SSE stream has no event payload."
    return 1
  }

  grep -Eq "\"requestId\"[[:space:]]*:[[:space:]]*\"${request_id}\"" "${primary_log}" || {
    echo "Primary SSE stream does not contain the expected request ID."
    return 1
  }

  grep -q '^event:pipeline.started$' "${primary_log}" || {
    echo "Primary SSE stream is missing pipeline.started."
    return 1
  }

  grep -Eq "${terminal_pattern}" "${primary_log}" || {
    echo "Primary SSE stream is missing terminal event."
    return 1
  }

  if grep -Eq "\"requestId\"[[:space:]]*:[[:space:]]*\"${unrelated_request_id}\"" "${primary_log}"; then
    echo "Primary SSE stream received an unrelated request event."
    return 1
  fi

  if grep -Eq '^event:pipeline\.|^event:(language|normalization|threat|ai_analysis|intent|rewrite|semantic|policy|decision)\.' "${unrelated_log}"; then
    echo "Unrelated SSE stream received pipeline events unexpectedly."
    return 1
  fi

  terminate_background_process "${unrelated_pid}"
  SSE_UNRELATED_PID=""

  return 0
}

sse_ai_token_streaming_validation() {
  local suffix=""
  local request_id=""
  local headers="/tmp/qwenbridge-v8-ai-sse.headers"
  local stream_log="/tmp/qwenbridge-v8-ai-sse.log"
  local analyze_headers="/tmp/qwenbridge-v8-ai-sse-analyze.headers"
  local analyze_body="/tmp/qwenbridge-v8-ai-sse-analyze.json"
  local analyze_status=""
  local pid=""

  cleanup_background_processes

  suffix="$(date +%s)"
  request_id="verify-release-v8-ai-sse-${suffix}"

  rm -f "${headers}" "${stream_log}" "${analyze_headers}" "${analyze_body}"
  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null || true

  curl --silent --show-error --no-buffer \
    --connect-timeout "${SSE_CONNECT_TIMEOUT_SECONDS}" \
    --max-time "$((SSE_EVENT_WAIT_SECONDS + 30))" \
    -D "${headers}" \
    -H "Accept: text/event-stream" \
    "${BASE_URL}${SSE_ENDPOINT_PREFIX}/${request_id}" \
    > "${stream_log}" 2>&1 &

  pid=$!
  SSE_PRIMARY_PID="${pid}"

  wait_for_sse_headers "${headers}" "${SSE_CONNECT_TIMEOUT_SECONDS}" || {
    echo "AI SSE stream did not return valid headers."
    cat "${headers}" 2>/dev/null || true
    cat "${stream_log}" 2>/dev/null || true
    return 1
  }

  analyze_status="$(
    curl --silent --show-error \
      -D "${analyze_headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -H "X-Request-ID: ${request_id}" \
      --data "$(json_payload "${request_id}" "v8 unique ai streaming gaming laptop ${suffix}")" \
      -o "${analyze_body}" \
      -w "%{http_code}"
  )"

  echo "Analyze HTTP status: ${analyze_status}"
  jq . "${analyze_body}" || true

  [[ "${analyze_status}" == "200" ]] || return 1
  assert_common_headers "${analyze_headers}" || return 1

  wait_for_file_pattern "${stream_log}" '^event:ai\.(token|failed)$' "${SSE_EVENT_WAIT_SECONDS}" || {
    echo "Timed out waiting for ai.token or ai.failed."
    cat "${stream_log}" 2>/dev/null || true
    return 1
  }

  wait_for_file_pattern "${stream_log}" '^event:ai\.(completed|failed)$' "${SSE_EVENT_WAIT_SECONDS}" || {
    echo "Timed out waiting for ai.completed or ai.failed."
    cat "${stream_log}" 2>/dev/null || true
    return 1
  }

  wait_for_file_pattern "${stream_log}" '^event:pipeline\.(completed|failed|stopped)$' "${SSE_EVENT_WAIT_SECONDS}" || {
    echo "Timed out waiting for pipeline terminal event."
    cat "${stream_log}" 2>/dev/null || true
    return 1
  }

  terminate_background_process "${pid}"
  SSE_PRIMARY_PID=""

  echo ""
  echo "========== V8 AI SSE Stream =========="
  cat "${stream_log}" 2>/dev/null || true

  grep -Eq '^event:ai\.(token|failed)$' "${stream_log}" \
    && grep -Eq '^event:ai\.(completed|failed)$' "${stream_log}" \
    && grep -Eq "\"requestId\"[[:space:]]*:[[:space:]]*\"${request_id}\"" "${stream_log}"
}
