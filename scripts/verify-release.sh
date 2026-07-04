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
SSE_ENDPOINT_PREFIX="${SSE_ENDPOINT_PREFIX:-/api/v1/search/stream}"

EXPECTED_BRANCH="${EXPECTED_BRANCH:-feat/v8.1-production-operability}"
EXPECTED_TAG="${EXPECTED_TAG:-v0.8.1}"
EXPECTED_TAG_REQUIRED="${EXPECTED_TAG_REQUIRED:-false}"
EXPECTED_VERSION="${EXPECTED_VERSION:-0.1.0-SNAPSHOT}"

TEST_QUERY="${TEST_QUERY:-best gaming laptop under 1500 euro}"
TEST_PROMPT="${TEST_PROMPT:-hello qwenbridge}"
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-10}"

SSE_CONNECT_TIMEOUT_SECONDS="${SSE_CONNECT_TIMEOUT_SECONDS:-10}"
SSE_EVENT_WAIT_SECONDS="${SSE_EVENT_WAIT_SECONDS:-60}"
SSE_STARTUP_WAIT_SECONDS="${SSE_STARTUP_WAIT_SECONDS:-1}"

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

SSE_PRIMARY_PID=""
SSE_UNRELATED_PID=""

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

COMPOSE_PROFILE="${COMPOSE_PROFILE:-production}"

compose() {
  if [[ -n "${COMPOSE_PROFILE}" ]]; then
    docker compose -f "${COMPOSE_FILE}" --profile "${COMPOSE_PROFILE}" "$@"
    return $?
  fi

  docker compose -f "${COMPOSE_FILE}" "$@"
}

run_step() {
  local name="$1"
  shift

  section "${name}"

  if "$@"; then
    pass "${name}"
  else
    fail "${name}"
  fi
}

cleanup_background_processes() {
  terminate_background_process "${SSE_PRIMARY_PID}"
  terminate_background_process "${SSE_UNRELATED_PID}"

  SSE_PRIMARY_PID=""
  SSE_UNRELATED_PID=""
}

trap cleanup_background_processes EXIT INT TERM

check_project_root() {
  echo "Project root: ${PROJECT_ROOT}"

  [[ -f "pom.xml" ]] \
    && [[ -f "${COMPOSE_FILE}" ]] \
    && [[ -f "Dockerfile" ]]
}

check_required_tools() {
  local ok=0
  local cmd=""

  for cmd in git docker curl jq lsof awk grep sed; do
    if require_command "${cmd}"; then
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



check_release_tag() {
  local tag_commit=""
  local head_commit=""

  tag_commit="$(git rev-list -n 1 "${EXPECTED_TAG}" 2>/dev/null || true)"
  head_commit="$(git rev-parse HEAD)"

  echo "Expected tag: ${EXPECTED_TAG}"
  echo "Tag commit: ${tag_commit}"
  echo "HEAD commit: ${head_commit}"

  if [[ -z "${tag_commit}" ]]; then
    if [[ "${EXPECTED_TAG_REQUIRED}" == "true" ]]; then
      return 1
    fi

    warn "Expected tag ${EXPECTED_TAG} does not exist yet. Skipping strict tag validation."
    return 0
  fi

  if [[ "${tag_commit}" != "${head_commit}" ]]; then
    if [[ "${EXPECTED_TAG_REQUIRED}" == "true" ]]; then
      return 1
    fi

    warn "Expected tag ${EXPECTED_TAG} is not on HEAD yet. Skipping strict tag validation."
    return 0
  fi

  return 0
}

v8_release_docs_validation() {
  [[ -f "docs/roadmap/V8.md" ]] \
    && [[ -f "docs/release/V8-release-checklist.md" ]] \
    && [[ -f "docs/security/abuse-protection.md" ]] \
    && [[ -f "docs/security/secrets-handling-policy.md" ]] \
    && [[ -f "docs/architecture/architecture-rules.md" ]] \
    && grep -qi "production" docs/roadmap/V8.md \
    && grep -qi "security" docs/roadmap/V8.md \
    && grep -qi "abuse" docs/security/abuse-protection.md \
    && grep -qi "secret" docs/security/secrets-handling-policy.md \
    && grep -qi "archunit" docs/architecture/architecture-rules.md
}

v8_quality_test_suite_validation() {
  mvn -q \
    -Dtest='ArchitectureRulesTest,InMemoryFixedWindowRateLimiterTest,DefaultBenchmarkEvaluationRunnerTest,DefaultEvaluationThresholdPolicyTest,DefaultRankingPolicyTest,SearchResultRankerTest,DefaultRerankingServiceTest,NoOpRerankerTest' \
    test
}


check_git_state() {
  local branch=""

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
  local attempt=""

  for attempt in {1..90}; do
    if docker info >/dev/null 2>&1; then
      DOCKER_AVAILABLE=true
      docker version \
        --format 'Client={{.Client.Version}} Server={{.Server.Version}}' \
        || true
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

  warn "Docker restart is not supported automatically on this OS."
  wait_for_docker
}

fresh_environment_reset() {
  rm -f /tmp/qwenbridge-*.json
  rm -f /tmp/qwenbridge-*.headers
  rm -f /tmp/qwenbridge-*.body
  rm -f /tmp/qwenbridge-*.log
  rm -f /tmp/qwenbridge-singleflight-*.json
  rm -f /tmp/qwenbridge-sse-*

  : > "${APP_LOG}"

  if [[ "${FORCE_FRESH}" != "true" ]]; then
    info "FORCE_FRESH=false, keeping current Docker environment."
    return 0
  fi

  info "Stopping and removing QwenBridge Compose stack."
  compose down -v --remove-orphans || true

  info "Force-removing stale QwenBridge containers."
  docker rm -f \
    "${APP_CONTAINER}" \
    "${REDIS_CONTAINER}" \
    "${OLLAMA_CONTAINER}" \
    "${OLLAMA_CONTAINER}-init" \
    "${OPENSEARCH_CONTAINER}" \
    2>/dev/null || true

  docker network rm qwenbridge_default 2>/dev/null || true
}

ensure_env_file() {
  if [[ ! -f ".env" ]]; then
    info "Creating local .env for release verification."
    cat > .env <<'EOF'
QWENBRIDGE_CORS_ALLOWED_ORIGINS=*
EOF
    return 0
  fi

  grep -q '^QWENBRIDGE_CORS_ALLOWED_ORIGINS=' .env || {
    info "Adding missing QWENBRIDGE_CORS_ALLOWED_ORIGINS to .env."
    printf '\nQWENBRIDGE_CORS_ALLOWED_ORIGINS=*\n' >> .env
  }
}

docker_pull_build_up() {
  ensure_env_file

  if [[ "${PULL_DOCKER_IMAGES}" == "true" ]]; then
    compose pull --ignore-pull-failures || true
  else
    info "PULL_DOCKER_IMAGES=false, skipping docker compose pull."
  fi

  if [[ "${NO_CACHE_BUILD}" == "true" ]]; then
    compose build --no-cache || return 1
  else
    compose build || return 1
  fi

  compose up -d --build --force-recreate --remove-orphans

  echo ""
  echo "Compose services after up:"
  compose ps || true
}

wait_for_container_healthy_or_running() {
  local container="$1"
  local mode="${2:-healthy}"
  local attempt=""
  local status=""
  local health=""

  for attempt in {1..120}; do
    status="$(docker inspect -f '{{.State.Status}}' "${container}" 2>/dev/null || true)"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${container}" 2>/dev/null || true)"

    if [[ "${mode}" == "healthy" && "${health}" == "healthy" ]]; then
      return 0
    fi

    if [[ "${mode}" == "running" && "${status}" == "running" ]]; then
      return 0
    fi

    sleep 2
  done

  echo "Container did not become ${mode}: ${container}"
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

docker_runtime_user_validation() {
  local configured_user=""

  configured_user="$(docker inspect "${APP_CONTAINER}" --format '{{.Config.User}}' 2>/dev/null | tr -d '\r')"
  echo "Application container configured user: ${configured_user}"

  [[ "${configured_user}" == "nonroot" ]] || [[ "${configured_user}" == "nonroot:nonroot" ]] || [[ "${configured_user}" == "65532" ]] || [[ "${configured_user}" == "65532:65532" ]]
}

docker_app_healthcheck_validation() {
  local health=""

  health="$(docker inspect "${APP_CONTAINER}" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' 2>/dev/null || true)"
  echo "Application container Docker health: ${health:-not-configured}"

  if [[ "${health}" == "healthy" ]]; then
    return 0
  fi

  app_public_health_up
}



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

  rm -f /tmp/qwenbridge-singleflight-*.json
  docker exec "${REDIS_CONTAINER}" redis-cli flushdb >/dev/null || return 1

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
    (
      local request_id="${request_id_prefix}-${i}"

      curl -sS \
        -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -H "X-Request-ID: ${request_id}" \
        --data "$(json_payload "${request_id}" "${TEST_QUERY}")" \
        -o "/tmp/qwenbridge-singleflight-${i}.json"
    ) &
  done

  wait

  for i in $(seq 1 "${CONCURRENT_REQUESTS}"); do
    jq -e \
      '.originalQuery and .cache and .pipelineTrace and .requestId' \
      "/tmp/qwenbridge-singleflight-${i}.json" >/dev/null \
      || return 1
  done

  redis_dbsize="$(docker exec "${REDIS_CONTAINER}" redis-cli dbsize | tr -d '\r')"

  echo "Concurrent requests: ${CONCURRENT_REQUESTS}"
  echo "Redis dbsize after concurrent test: ${redis_dbsize}"

  [[ "${redis_dbsize}" -gt 0 ]]
}

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

terminate_background_process() {
  local pid="${1:-}"

  if [[ -z "${pid}" ]]; then
    return 0
  fi

  if kill -0 "${pid}" >/dev/null 2>&1; then
    kill "${pid}" >/dev/null 2>&1 || true
    wait "${pid}" >/dev/null 2>&1 || true
  fi
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

    # Terminal event is the lifecycle contract. Do not make the verifier depend
    # on when curl exits; transport shutdown timing differs across environments.
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


v8_ci_workflow_validation() {
  [[ -f ".github/workflows/ci.yml" ]] \
    && [[ -f ".github/workflows/codeql.yml" ]] \
    && [[ -f ".github/dependabot.yml" ]] \
    && grep -q "Dependency Scan" .github/workflows/ci.yml \
    && grep -q "Container Scan" .github/workflows/ci.yml \
    && grep -q "Architecture Tests" .github/workflows/ci.yml \
    && grep -q "workflow_dispatch" .github/workflows/ci.yml \
    && grep -q "CodeQL" .github/workflows/codeql.yml
}

v8_dependency_security_files_validation() {
  [[ -f "dependency-check-suppressions.xml" ]] \
    && [[ -f ".trivyignore" ]] \
    && grep -q "dependency-check" .github/workflows/ci.yml \
    && grep -q "trivy" .github/workflows/ci.yml
}

v8_abuse_source_validation() {
  [[ -f "src/main/java/io/qwenbridge/abuse/AbuseProtectionFilter.java" ]] \
    && [[ -f "src/main/java/io/qwenbridge/abuse/RateLimiter.java" ]] \
    && [[ -f "src/main/java/io/qwenbridge/abuse/InMemoryFixedWindowRateLimiter.java" ]] \
    && [[ -f "src/main/java/io/qwenbridge/abuse/RedisBackedRateLimiter.java" ]] \
    && [[ -f "src/main/java/io/qwenbridge/abuse/RateLimitDecision.java" ]] \
    && grep -R "X-RateLimit" -n src/main/java/io/qwenbridge/abuse >/dev/null
}

v8_abuse_runtime_rate_limit_validation() {
  local status=""
  local headers="/tmp/qwenbridge-v8-rate-limit.headers"
  local body="/tmp/qwenbridge-v8-rate-limit.json"
  local request_id="verify-release-v8-rate-limit-$(date +%s)"
  local payload=""

  payload="$(json_payload "${request_id}" "${TEST_QUERY}")"

  status="$(
    curl -sS \
      -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -H "X-Request-ID: ${request_id}" \
      -H "X-API-Key: verify-release-v8-abuse-test" \
      --data "${payload}" \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  cat "${headers}" || true
  jq . "${body}" || true

  [[ "${status}" == "200" || "${status}" == "429" ]] \
    && grep -qi '^x-ratelimit-limit:' "${headers}" \
    && grep -qi '^x-ratelimit-remaining:' "${headers}" \
    && grep -qi '^x-ratelimit-reset:' "${headers}"
}

v8_docker_runtime_hardening_validation() {
  grep -q "gcr.io/distroless/java21-debian12:nonroot" Dockerfile \
    && grep -q "MaxRAMPercentage" Dockerfile \
    && grep -q "ExitOnOutOfMemoryError" Dockerfile \
    && docker_runtime_user_validation \
    && app_public_health_up
}


print_redis_keys() {
  docker exec "${REDIS_CONTAINER}" redis-cli keys '*' || true
}

print_sse_logs() {
  echo ""
  echo "========== SSE Primary Headers =========="
  cat /tmp/qwenbridge-sse-primary.headers 2>/dev/null || true

  echo ""
  echo "========== SSE Primary Stream =========="
  cat /tmp/qwenbridge-sse-primary.log 2>/dev/null || true

  echo ""
  echo "========== SSE Unrelated Headers =========="
  cat /tmp/qwenbridge-sse-unrelated.headers 2>/dev/null || true

  echo ""
  echo "========== SSE Unrelated Stream =========="
  cat /tmp/qwenbridge-sse-unrelated.log 2>/dev/null || true
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

  print_sse_logs
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
echo "       QwenBridge - V8 Docker Release Verification"
echo "======================================================"

run_step "Project root validation" check_project_root
run_step "Required tools validation" check_required_tools
run_step "Git state validation" check_git_state
run_step "V8 release tag validation" check_release_tag
run_step "V8 release docs validation" v8_release_docs_validation
run_step "V8 quality test suite validation" v8_quality_test_suite_validation
run_step "V8 CI workflow validation" v8_ci_workflow_validation
run_step "V8 dependency security files validation" v8_dependency_security_files_validation
run_step "V8 abuse source validation" v8_abuse_source_validation
run_step "Docker readiness" restart_docker_daemon
run_step "Fresh Docker environment reset" fresh_environment_reset
run_step "Docker Compose pull/build/up" docker_pull_build_up
run_step "Docker Compose service readiness" wait_for_compose_services
run_step "Ollama model validation" verify_ollama_models
run_step "OpenSearch seed data" seed_opensearch
run_step "Application readiness" wait_for_app_readiness
run_step "Docker app healthcheck validation" docker_app_healthcheck_validation
run_step "Docker runtime user validation" docker_runtime_user_validation
run_step "V8 Docker runtime hardening validation" v8_docker_runtime_hardening_validation
run_step "OpenSearch vector mapping validation" opensearch_vector_mapping_validation
run_step "Ollama embedding generation validation" ollama_embedding_generation_validation
run_step "OpenSearch vector retrieval validation" opensearch_vector_retrieval_validation
run_step "OpenSearch hybrid retrieval validation" opensearch_hybrid_retrieval_validation

run_step "Actuator health endpoint" actuator_health_endpoint
run_step "Public health endpoint" public_health_endpoint
run_step "Version endpoint" version_endpoint
run_step "AI chat endpoint" ai_chat_endpoint
run_step "Analyze API endpoint" analyze_api_endpoint
run_step "SSE streaming lifecycle validation" sse_streaming_lifecycle_validation
run_step "V8 AI SSE token streaming validation" sse_ai_token_streaming_validation
run_step "Validation error contract" validation_error_contract
run_step "V8 abuse runtime rate-limit validation" v8_abuse_runtime_rate_limit_validation
run_step "Custom request ID propagation" custom_request_id_propagation
run_step "CORS preflight validation" cors_preflight_validation
run_step "Cache miss / cache hit validation" cache_miss_hit_validation
run_step "Concurrent SingleFlight validation" singleflight_validation
run_step "OpenAPI endpoint" openapi_endpoint
run_step "OpenAPI contains V8 endpoints" openapi_contains_v8_endpoints
run_step "Swagger UI endpoint" swagger_endpoint

echo ""
echo "========== Redis Keys =========="
print_redis_keys
echo "========== ========== =========="

print_relevant_logs

print_summary