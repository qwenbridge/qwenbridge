#!/usr/bin/env bash

set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

APP_PORT="${APP_PORT:-8080}"
REDIS_PORT="${REDIS_PORT:-6379}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
BASE_URL="${BASE_URL:-http://localhost:${APP_PORT}}"
ANALYZE_ENDPOINT="${ANALYZE_ENDPOINT:-/api/v1/search/analyze}"
APP_LOG="${APP_LOG:-/tmp/qwenbridge-verify-release.log}"
OLLAMA_LOG="${OLLAMA_LOG:-/tmp/qwenbridge-ollama.log}"
REDIS_CONTAINER="${REDIS_CONTAINER:-qwenbridge-redis}"
TEST_QUERY="${TEST_QUERY:-best gaming laptop under 1500 euro}"
EXPECTED_BRANCH="${EXPECTED_BRANCH:-feat/v4-ai-core-refactor}"
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-10}"

PASSED=0
FAILED=0
WARNINGS=0
APP_PID=""
OLLAMA_PID=""

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
BLUE="\033[0;34m"
NC="\033[0m"

pass() {
  echo -e "${GREEN}PASS${NC} - $1"
  PASSED=$((PASSED + 1))
}

fail() {
  echo -e "${RED}FAIL${NC} - $1"
  FAILED=$((FAILED + 1))
}

warn() {
  echo -e "${YELLOW}WARN${NC} - $1"
  WARNINGS=$((WARNINGS + 1))
}

info() {
  echo -e "${BLUE}INFO${NC} - $1"
}

section() {
  echo ""
  echo "======================================================"
  echo "$1"
  echo "======================================================"
}

require_command() {
  command -v "$1" >/dev/null 2>&1
}

cleanup() {
  if [[ -n "${APP_PID}" ]] && ps -p "${APP_PID}" >/dev/null 2>&1; then
    info "Stopping Spring Boot application PID ${APP_PID}"
    kill "${APP_PID}" >/dev/null 2>&1 || true
  fi

  if [[ -n "${OLLAMA_PID}" ]] && ps -p "${OLLAMA_PID}" >/dev/null 2>&1; then
    info "Stopping Ollama process started by this script PID ${OLLAMA_PID}"
    kill "${OLLAMA_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

run_step() {
  local name="$1"
  shift

  section "$name"

  if "$@"; then
    pass "$name"
  else
    fail "$name"
  fi
}

check_project_root() {
  echo "Project root: ${PROJECT_ROOT}"

  if [[ ! -f "pom.xml" ]]; then
    echo "pom.xml not found in ${PROJECT_ROOT}"
    return 1
  fi

  return 0
}

check_required_tools() {
  local ok=0

  for cmd in git mvn curl docker jq; do
    if require_command "$cmd"; then
      pass "Command available: ${cmd}"
    else
      fail "Command missing: ${cmd}"
      ok=1
    fi
  done

  if require_command redis-cli; then
    pass "Command available: redis-cli"
  else
    warn "redis-cli missing on host. Redis will be checked through Docker."
  fi

  if require_command ollama; then
    pass "Command available: ollama"
  else
    warn "ollama command missing. Ollama API must already be reachable."
  fi

  return "$ok"
}

check_git_state() {
  local branch
  branch="$(git branch --show-current)"

  echo "Current branch: ${branch}"
  git status --short

  if [[ "${branch}" != "${EXPECTED_BRANCH}" ]]; then
    warn "Expected branch ${EXPECTED_BRANCH}, current branch is ${branch}"
  fi

  if git diff --quiet && git diff --cached --quiet; then
    return 0
  fi

  return 1
}

start_redis() {
  if ! require_command docker; then
    return 1
  fi

  if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
    info "Redis container already running: ${REDIS_CONTAINER}"
  elif docker ps -a --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
    docker start "${REDIS_CONTAINER}" >/dev/null
    info "Redis container started: ${REDIS_CONTAINER}"
  else
    docker run \
      --name "${REDIS_CONTAINER}" \
      -p "${REDIS_PORT}:6379" \
      -d redis:7 >/dev/null
    info "Redis container created and started: ${REDIS_CONTAINER}"
  fi

  for i in {1..30}; do
    if docker exec "${REDIS_CONTAINER}" redis-cli ping 2>/dev/null | grep -q "PONG"; then
      return 0
    fi
    sleep 1
  done

  return 1
}

check_redis_health() {
  docker exec "${REDIS_CONTAINER}" redis-cli ping | grep -q "PONG"
}

check_ollama_api() {
  curl -sf "${OLLAMA_URL}/api/tags" | jq . >/dev/null
}

start_ollama_if_needed() {
  if check_ollama_api; then
    info "Ollama API is reachable: ${OLLAMA_URL}"
    return 0
  fi

  if ! require_command ollama; then
    echo "Ollama API is not reachable and ollama command is missing."
    return 1
  fi

  info "Starting Ollama..."
  ollama serve > "${OLLAMA_LOG}" 2>&1 &
  OLLAMA_PID=$!

  for i in {1..40}; do
    if check_ollama_api; then
      return 0
    fi
    sleep 1
  done

  tail -80 "${OLLAMA_LOG}" || true
  return 1
}

show_ollama_models() {
  local model_count
  curl -sf "${OLLAMA_URL}/api/tags" | tee /tmp/qwenbridge-ollama-models.json | jq .

  model_count="$(jq '.models | length' /tmp/qwenbridge-ollama-models.json 2>/dev/null || echo 0)"

  if [[ "${model_count}" -eq 0 ]]; then
    warn "Ollama is running but no models are installed. Run: ollama pull qwen2.5"
  fi

  return 0
}

maven_clean_test() {
  mvn clean test
}

maven_verify_quality_gates() {
  mvn verify
}

port_is_busy() {
  lsof -iTCP:"${APP_PORT}" -sTCP:LISTEN >/dev/null 2>&1
}

start_spring_boot() {
  rm -f "${APP_LOG}"

  if curl -sf "${BASE_URL}/actuator/health" | jq -e '.status == "UP"' >/dev/null 2>&1; then
    warn "Application already seems UP on ${BASE_URL}. Script will use existing app and will not stop it."
    APP_PID=""
    return 0
  fi

  if port_is_busy; then
    echo "Port ${APP_PORT} is already busy, but health endpoint is not UP."
    lsof -iTCP:"${APP_PORT}" -sTCP:LISTEN || true
    return 1
  fi

  mvn spring-boot:run > "${APP_LOG}" 2>&1 &
  APP_PID=$!

  info "Spring Boot PID: ${APP_PID}"
  info "Log file: ${APP_LOG}"

  for i in {1..90}; do
    if curl -sf "${BASE_URL}/actuator/health" | jq -e '.status == "UP"' >/dev/null 2>&1; then
      return 0
    fi

    if [[ -n "${APP_PID}" ]] && ! ps -p "${APP_PID}" >/dev/null 2>&1; then
      tail -120 "${APP_LOG}" || true
      return 1
    fi

    sleep 2
  done

  tail -120 "${APP_LOG}" || true
  return 1
}

health_endpoint() {
  curl -sf "${BASE_URL}/actuator/health" | jq -e '.status == "UP"' >/dev/null
}

analyze_api_endpoint() {
  curl -sf \
    -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "{\"query\":\"${TEST_QUERY}\"}" \
    | tee /tmp/qwenbridge-analyze.json \
    | jq .

  jq -e '.originalQuery and .cache and .pipelineTrace' /tmp/qwenbridge-analyze.json >/dev/null
}

cache_miss_hit_validation() {
  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null

  curl -sf \
    -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "{\"query\":\"${TEST_QUERY}\"}" \
    > /tmp/qwenbridge-cache-first.json

  curl -sf \
    -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "{\"query\":\"${TEST_QUERY}\"}" \
    > /tmp/qwenbridge-cache-second.json

  jq . /tmp/qwenbridge-cache-first.json >/dev/null
  jq . /tmp/qwenbridge-cache-second.json >/dev/null

  local first_hit
  local first_miss
  local second_hit
  local second_miss
  local redis_dbsize

  first_hit="$(jq -r '.cache.hit // false' /tmp/qwenbridge-cache-first.json)"
  first_miss="$(jq -r '.cache.miss // false' /tmp/qwenbridge-cache-first.json)"
  second_hit="$(jq -r '.cache.hit // false' /tmp/qwenbridge-cache-second.json)"
  second_miss="$(jq -r '.cache.miss // false' /tmp/qwenbridge-cache-second.json)"
  redis_dbsize="$(docker exec "${REDIS_CONTAINER}" redis-cli dbsize | tr -d '\r')"

  echo "First request cache.hit: ${first_hit}"
  echo "First request cache.miss: ${first_miss}"
  echo "Second request cache.hit: ${second_hit}"
  echo "Second request cache.miss: ${second_miss}"
  echo "Redis dbsize: ${redis_dbsize}"

  [[ "${first_miss}" == "true" ]] \
    && [[ "${second_hit}" == "true" ]] \
    && [[ "${redis_dbsize}" -gt 0 ]]
}

singleflight_validation() {
  rm -f /tmp/qwenbridge-singleflight-*.json
  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
    curl -sf \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -d "{\"query\":\"${TEST_QUERY}\"}" \
      > "/tmp/qwenbridge-singleflight-${i}.json" &
  done

  wait

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
    jq . "/tmp/qwenbridge-singleflight-${i}.json" >/dev/null
    jq -e '.originalQuery and .cache and .pipelineTrace' "/tmp/qwenbridge-singleflight-${i}.json" >/dev/null
  done

  local redis_dbsize
  redis_dbsize="$(docker exec "${REDIS_CONTAINER}" redis-cli dbsize | tr -d '\r')"

  echo "Concurrent requests: ${CONCURRENT_REQUESTS}"
  echo "Redis dbsize after concurrent test: ${redis_dbsize}"

  [[ "${redis_dbsize}" -gt 0 ]]
}

openapi_endpoint() {
  curl -sf "${BASE_URL}/v3/api-docs" | jq . >/dev/null
}

swagger_endpoint() {
  curl -sf -I "${BASE_URL}/swagger-ui/index.html" >/dev/null
}

actuator_endpoint() {
  curl -sf "${BASE_URL}/actuator/health" >/dev/null
}

print_redis_keys() {
  docker exec "${REDIS_CONTAINER}" redis-cli keys '*'
}

print_relevant_logs() {
  echo ""
  echo "========== Relevant Application Logs =========="

  if [[ -f "${APP_LOG}" ]]; then
    grep -Ei "cache|singleflight|redis|ai|provider|ollama|analysis|error|exception|warn" "${APP_LOG}" | tail -200 || true
  else
    warn "Application log file does not exist: ${APP_LOG}"
  fi
}

print_summary() {
  echo ""
  echo "======================================================"
  echo "                 VERIFY RELEASE SUMMARY"
  echo "======================================================"

  echo -e "${GREEN}Passed:${NC} ${PASSED}"
  echo -e "${YELLOW}Warnings:${NC} ${WARNINGS}"
  echo -e "${RED}Failed:${NC} ${FAILED}"

  if [[ "${FAILED}" -eq 0 ]]; then
    echo -e "${GREEN}RESULT: RELEASE VERIFICATION PASSED${NC}"
    return 0
  fi

  echo -e "${RED}RESULT: RELEASE VERIFICATION FAILED${NC}"
  return 1
}

echo ""
echo "======================================================"
echo "       QwenBridge - Release Verification"
echo "======================================================"

run_step "Project root validation" check_project_root
run_step "Required tools validation" check_required_tools
run_step "Git state validation" check_git_state
run_step "Redis startup" start_redis
run_step "Redis health" check_redis_health
run_step "Ollama startup / availability" start_ollama_if_needed
run_step "Ollama models" show_ollama_models
run_step "Maven clean test" maven_clean_test
run_step "Maven verify / quality gates" maven_verify_quality_gates
run_step "Spring Boot startup and readiness" start_spring_boot
run_step "Health endpoint" health_endpoint
run_step "Analyze API endpoint" analyze_api_endpoint
run_step "Cache miss / cache hit validation" cache_miss_hit_validation
run_step "Concurrent SingleFlight validation" singleflight_validation
run_step "OpenAPI endpoint" openapi_endpoint
run_step "Swagger UI endpoint" swagger_endpoint
run_step "Actuator endpoint" actuator_endpoint

echo ""
echo "========== Redis Keys =========="
print_redis_keys || true

print_relevant_logs

print_summary