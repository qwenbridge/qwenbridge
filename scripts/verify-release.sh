#!/usr/bin/env bash
set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

APP_PORT="${APP_PORT:-8080}"
BASE_URL="${BASE_URL:-http://localhost:${APP_PORT}}"

ANALYZE_ENDPOINT="${ANALYZE_ENDPOINT:-/api/v1/search/analyze}"
AI_CHAT_ENDPOINT="${AI_CHAT_ENDPOINT:-/api/v1/ai/chat}"
PUBLIC_HEALTH_ENDPOINT="${PUBLIC_HEALTH_ENDPOINT:-/api/v1/health}"
VERSION_ENDPOINT="${VERSION_ENDPOINT:-/api/v1/version}"

EXPECTED_BRANCH="${EXPECTED_BRANCH:-feat/v5-public-launch}"
EXPECTED_VERSION="${EXPECTED_VERSION:-0.1.0-SNAPSHOT}"

TEST_QUERY="${TEST_QUERY:-best gaming laptop under 1500 euro}"
TEST_PROMPT="${TEST_PROMPT:-hello qwenbridge}"
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-10}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
APP_CONTAINER="${APP_CONTAINER:-qwenbridge-app}"
REDIS_CONTAINER="${REDIS_CONTAINER:-qwenbridge-redis}"
OLLAMA_CONTAINER="${OLLAMA_CONTAINER:-qwenbridge-ollama}"
OPENSEARCH_CONTAINER="${OPENSEARCH_CONTAINER:-qwenbridge-opensearch}"

OPENSEARCH_URL="${OPENSEARCH_URL:-http://localhost:9200}"
OPENSEARCH_INDEX="${OPENSEARCH_INDEX:-qwenbridge-products}"

QWEN_MODEL="${QWEN_MODEL:-qwen2.5}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-bge-m3}"

APP_LOG="${APP_LOG:-/tmp/qwenbridge-verify-release.log}"

FORCE_FRESH="${FORCE_FRESH:-true}"
RESTART_DOCKER="${RESTART_DOCKER:-false}"
PULL_DOCKER_IMAGES="${PULL_DOCKER_IMAGES:-true}"
NO_CACHE_BUILD="${NO_CACHE_BUILD:-false}"

PASSED=0
FAILED=0
WARNINGS=0
DOCKER_AVAILABLE=false

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
BLUE="\033[0;34m"
NC="\033[0m"

pass() { echo -e "${GREEN}PASS${NC} - $1"; PASSED=$((PASSED + 1)); }
fail() { echo -e "${RED}FAIL${NC} - $1"; FAILED=$((FAILED + 1)); }
warn() { echo -e "${YELLOW}WARN${NC} - $1"; WARNINGS=$((WARNINGS + 1)); }
info() { echo -e "${BLUE}INFO${NC} - $1"; }

section() {
  echo ""
  echo "======================================================"
  echo "$1"
  echo "======================================================"
}

require_command() {
  command -v "$1" >/dev/null 2>&1
}

compose() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

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
  [[ -f "pom.xml" ]] && [[ -f "${COMPOSE_FILE}" ]] && [[ -f "Dockerfile" ]]
}

check_required_tools() {
  local ok=0

  for cmd in git docker curl jq lsof; do
    if require_command "$cmd"; then
      pass "Command available: ${cmd}"
    else
      fail "Command missing: ${cmd}"
      ok=1
    fi
  done

  if docker compose version >/dev/null 2>&1; then
    pass "Command available: docker compose"
  else
    fail "Command missing: docker compose"
    ok=1
  fi

  return "${ok}"
}

check_git_state() {
  local branch
  branch="$(git branch --show-current)"

  echo "Current branch: ${branch}"
  git status --short

  if [[ "${branch}" != "${EXPECTED_BRANCH}" ]]; then
    warn "Expected branch ${EXPECTED_BRANCH}, current branch is ${branch}"
  fi

  if ! git diff --quiet || ! git diff --cached --quiet; then
    warn "Working tree is not clean. This is allowed during local verification."
  fi

  return 0
}

wait_for_docker() {
  for _ in {1..90}; do
    if docker info >/dev/null 2>&1; then
      DOCKER_AVAILABLE=true
      docker version --format 'Client={{.Client.Version}} Server={{.Server.Version}}' || true
      return 0
    fi
    sleep 2
  done

  DOCKER_AVAILABLE=false
  echo "Docker daemon did not become ready."
  return 1
}

restart_docker_daemon() {
  if [[ "${RESTART_DOCKER}" != "true" ]]; then
    info "RESTART_DOCKER=false, skipping Docker restart."
    wait_for_docker
    return $?
  fi

  info "Restarting Docker..."

  if [[ "$(uname -s)" == "Darwin" ]]; then
    osascript -e 'quit app "Docker"' >/dev/null 2>&1 || true
    sleep 5
    open -a Docker >/dev/null 2>&1 || true
    wait_for_docker
    return $?
  fi

  if require_command systemctl; then
    sudo systemctl restart docker || return 1
    wait_for_docker
    return $?
  fi

  warn "Docker restart is not supported automatically on this OS. Waiting for current Docker daemon."
  wait_for_docker
}

fresh_environment_reset() {
  rm -f /tmp/qwenbridge-*.json
  rm -f /tmp/qwenbridge-*.headers
  rm -f /tmp/qwenbridge-*.body
  rm -f /tmp/qwenbridge-singleflight-*.json
  : > "${APP_LOG}"

  if [[ "${FORCE_FRESH}" != "true" ]]; then
    info "FORCE_FRESH=false, keeping current Docker environment."
    return 0
  fi

  info "Stopping and removing QwenBridge Compose stack."
  compose down -v --remove-orphans

  return 0
}

docker_pull_build_up() {
  if [[ "${PULL_DOCKER_IMAGES}" == "true" ]]; then
    compose pull --ignore-pull-failures || true
  else
    info "PULL_DOCKER_IMAGES=false, skipping docker compose pull."
  fi

  if [[ "${NO_CACHE_BUILD}" == "true" ]]; then
    compose build --no-cache
  else
    compose build
  fi

  compose up -d

  return 0
}

wait_for_container_healthy_or_running() {
  local container="$1"
  local mode="${2:-healthy}"

  for _ in {1..120}; do
    local status
    local health

    status="$(docker inspect -f '{{.State.Status}}' "${container}" 2>/dev/null || true)"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${container}" 2>/dev/null || true)"

    if [[ "${mode}" == "healthy" ]]; then
      if [[ "${health}" == "healthy" ]]; then
        return 0
      fi
    else
      if [[ "${status}" == "running" ]]; then
        return 0
      fi
    fi

    sleep 2
  done

  docker ps -a
  docker logs --tail 160 "${container}" 2>/dev/null || true
  return 1
}

wait_for_compose_services() {
  wait_for_container_healthy_or_running "${REDIS_CONTAINER}" healthy \
    && wait_for_container_healthy_or_running "${OLLAMA_CONTAINER}" healthy \
    && wait_for_container_healthy_or_running "${OPENSEARCH_CONTAINER}" healthy \
    && wait_for_container_healthy_or_running "${APP_CONTAINER}" running
}

verify_ollama_models() {
  docker exec "${OLLAMA_CONTAINER}" ollama list

  docker exec "${OLLAMA_CONTAINER}" ollama list | awk '{print $1}' | grep -q "^${QWEN_MODEL}" \
    && docker exec "${OLLAMA_CONTAINER}" ollama list | awk '{print $1}' | grep -q "^${EMBEDDING_MODEL}"
}

seed_opensearch() {
  echo "Seeding OpenSearch index: ${OPENSEARCH_INDEX}"

  for _ in {1..60}; do
    if curl -fsS "${OPENSEARCH_URL}" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  curl -sS -o /dev/null -X DELETE "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}" || true

  curl -fsS -X PUT "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}" \
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
    }' >/dev/null

  curl -fsS -X POST "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_doc/product-1?refresh=true" \
    -H "Content-Type: application/json" \
    -d '{
      "title": "iPhone 16 Pro",
      "brand": "Apple",
      "category": "smartphone",
      "description": "Apple flagship smartphone with pro camera system"
    }' >/dev/null

  curl -fsS -X POST "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_doc/product-2?refresh=true" \
    -H "Content-Type: application/json" \
    -d '{
      "title": "Samsung Galaxy S25",
      "brand": "Samsung",
      "category": "smartphone",
      "description": "Android flagship smartphone"
    }' >/dev/null

  curl -fsS "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_count" | jq .
}

app_public_health_up() {
  curl -sf "${BASE_URL}${PUBLIC_HEALTH_ENDPOINT}" | jq -e '.status == "UP"' >/dev/null 2>&1
}

wait_for_app_readiness() {
  for _ in {1..120}; do
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

  grep -i '^x-request-id:' "${headers_file}" >/dev/null \
    && grep -i "^x-qwenbridge-version: ${EXPECTED_VERSION}" "${headers_file}" >/dev/null
}

actuator_health_endpoint() {
  curl -sf "${BASE_URL}/actuator/health" | jq -e '.status == "UP"' >/dev/null
}

public_health_endpoint() {
  local headers="/tmp/qwenbridge-health.headers"
  local body="/tmp/qwenbridge-health.json"

  curl -sf -D "${headers}" "${BASE_URL}${PUBLIC_HEALTH_ENDPOINT}" -o "${body}"

  assert_common_headers "${headers}" \
    && jq -e '.status == "UP" and .service == "qwenbridge" and .apiVersion == "v1"' "${body}" >/dev/null
}

version_endpoint() {
  local headers="/tmp/qwenbridge-version.headers"
  local body="/tmp/qwenbridge-version.json"

  curl -sf -D "${headers}" "${BASE_URL}${VERSION_ENDPOINT}" -o "${body}"

  assert_common_headers "${headers}" \
    && jq -e --arg version "${EXPECTED_VERSION}" '.name == "qwenbridge" and .version == $version and .apiVersion == "v1" and .javaVersion' "${body}" >/dev/null
}

analyze_api_endpoint() {
  local headers="/tmp/qwenbridge-analyze.headers"
  local body="/tmp/qwenbridge-analyze.json"
  local status

  status="$(
    curl -s -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -d "{\"query\":\"${TEST_QUERY}\"}" \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  jq . "${body}" || true

  [[ "${status}" == "200" ]] \
    && assert_common_headers "${headers}" \
    && jq -e '.originalQuery and .cache and .pipelineTrace and .executionPlan and .executionResult and .search' "${body}" >/dev/null
}

ai_chat_endpoint() {
  local headers="/tmp/qwenbridge-ai-chat.headers"
  local body="/tmp/qwenbridge-ai-chat.json"
  local status

  status="$(
    curl -s -D "${headers}" \
      -X POST "${BASE_URL}${AI_CHAT_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -d "{\"prompt\":\"${TEST_PROMPT}\"}" \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  jq . "${body}" || true

  [[ "${status}" == "200" ]] \
    && assert_common_headers "${headers}" \
    && jq -e '.content' "${body}" >/dev/null
}

validation_error_contract() {
  local headers="/tmp/qwenbridge-validation.headers"
  local body="/tmp/qwenbridge-validation.json"
  local status

  status="$(
    curl -s -D "${headers}" \
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
    && jq -e '.status == 400 and .error == "Bad Request" and .code == "VALIDATION_ERROR" and .path == "/api/v1/search/analyze" and .requestId and .timestamp' "${body}" >/dev/null
}

custom_request_id_propagation() {
  local headers="/tmp/qwenbridge-request-id.headers"
  local body="/tmp/qwenbridge-request-id.json"
  local request_id="verify-release-custom-request-id"

  curl -sf -D "${headers}" \
    -H "X-Request-ID: ${request_id}" \
    "${BASE_URL}${PUBLIC_HEALTH_ENDPOINT}" \
    -o "${body}"

  grep -i "^x-request-id: ${request_id}" "${headers}" >/dev/null
}

cors_preflight_validation() {
  local headers="/tmp/qwenbridge-cors.headers"
  local status

  status="$(
    curl -s -D "${headers}" \
      -X OPTIONS "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Origin: http://localhost:3000" \
      -H "Access-Control-Request-Method: POST" \
      -o /tmp/qwenbridge-cors.body \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  cat "${headers}"

  { [[ "${status}" == "200" ]] || [[ "${status}" == "204" ]]; } \
    && grep -i '^access-control-allow-origin:' "${headers}" >/dev/null \
    && grep -i '^access-control-allow-methods:' "${headers}" >/dev/null
}

cache_miss_hit_validation() {
  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null

  local first_status
  local second_status

  first_status="$(
    curl -s -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -d "{\"query\":\"${TEST_QUERY}\"}" \
      -o /tmp/qwenbridge-cache-first.json \
      -w "%{http_code}"
  )"

  second_status="$(
    curl -s -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -d "{\"query\":\"${TEST_QUERY}\"}" \
      -o /tmp/qwenbridge-cache-second.json \
      -w "%{http_code}"
  )"

  echo "First HTTP status: ${first_status}"
  jq . /tmp/qwenbridge-cache-first.json || true
  echo "Second HTTP status: ${second_status}"
  jq . /tmp/qwenbridge-cache-second.json || true

  [[ "${first_status}" == "200" ]] || return 1
  [[ "${second_status}" == "200" ]] || return 1

  local first_miss
  local second_hit
  local redis_dbsize

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
  rm -f /tmp/qwenbridge-singleflight-*.json
  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
    curl -s -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -d "{\"query\":\"${TEST_QUERY}\"}" \
      -o "/tmp/qwenbridge-singleflight-${i}.json" &
  done

  wait

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
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

openapi_contains_v5_endpoints() {
  local body="/tmp/qwenbridge-openapi.json"

  curl -sf "${BASE_URL}/v3/api-docs" -o "${body}"

  jq -e '
    .paths["/api/v1/search/analyze"] and
    .paths["/api/v1/ai/chat"] and
    .paths["/api/v1/health"] and
    .paths["/api/v1/version"]
  ' "${body}" >/dev/null
}

swagger_endpoint() {
  curl -sf -I "${BASE_URL}/swagger" >/dev/null \
    || curl -sf -I "${BASE_URL}/swagger-ui/index.html" >/dev/null
}

print_redis_keys() {
  docker exec "${REDIS_CONTAINER}" redis-cli keys '*' || true
}

print_relevant_logs() {
  echo ""
  echo "========== Docker Containers =========="
  docker ps -a

  echo ""
  echo "========== App Logs =========="
  docker logs --tail 240 "${APP_CONTAINER}" 2>/dev/null || true

  echo ""
  echo "========== Ollama Models =========="
  docker exec "${OLLAMA_CONTAINER}" ollama list 2>/dev/null || true

  echo ""
  echo "========== OpenSearch Count =========="
  curl -fsS "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_count" | jq . || true
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
echo "       QwenBridge - V5 Docker Release Verification"
echo "======================================================"

run_step "Project root validation" check_project_root
run_step "Required tools validation" check_required_tools
run_step "Git state validation" check_git_state
run_step "Docker readiness" restart_docker_daemon
run_step "Fresh Docker environment reset" fresh_environment_reset
run_step "Docker Compose pull/build/up" docker_pull_build_up
run_step "Docker Compose service readiness" wait_for_compose_services
run_step "Ollama model validation" verify_ollama_models
run_step "OpenSearch seed data" seed_opensearch
run_step "Application readiness" wait_for_app_readiness

run_step "Actuator health endpoint" actuator_health_endpoint
run_step "Public health endpoint" public_health_endpoint
run_step "Version endpoint" version_endpoint
run_step "Analyze API endpoint" analyze_api_endpoint
run_step "AI chat endpoint" ai_chat_endpoint
run_step "Validation error contract" validation_error_contract
run_step "Custom request id propagation" custom_request_id_propagation
run_step "CORS preflight validation" cors_preflight_validation
run_step "Cache miss / cache hit validation" cache_miss_hit_validation
run_step "Concurrent SingleFlight validation" singleflight_validation
run_step "OpenAPI endpoint" openapi_endpoint
run_step "OpenAPI contains V5 endpoints" openapi_contains_v5_endpoints
run_step "Swagger UI endpoint" swagger_endpoint

echo ""
echo "========== Redis Keys =========="
print_redis_keys

print_relevant_logs

print_summary