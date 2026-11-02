#!/usr/bin/env bash
# Sourced by scripts/verify-release.sh (not executed directly).
APP_PORT="${APP_PORT:-8080}"
BASE_URL="${BASE_URL:-http://localhost:${APP_PORT}}"

ANALYZE_ENDPOINT="${ANALYZE_ENDPOINT:-/api/v1/search/analyze}"
AI_CHAT_ENDPOINT="${AI_CHAT_ENDPOINT:-/api/v1/ai/chat}"
PUBLIC_HEALTH_ENDPOINT="${PUBLIC_HEALTH_ENDPOINT:-/api/v1/health}"
VERSION_ENDPOINT="${VERSION_ENDPOINT:-/api/v1/version}"
SSE_ENDPOINT_PREFIX="${SSE_ENDPOINT_PREFIX:-/api/v1/search/stream}"

EXPECTED_VERSION="${EXPECTED_VERSION:-$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -1)}"
EXPECTED_BRANCH="${EXPECTED_BRANCH:-}"
EXPECTED_TAG="${EXPECTED_TAG:-v${EXPECTED_VERSION}}"
EXPECTED_TAG_REQUIRED="${EXPECTED_TAG_REQUIRED:-false}"

TEST_QUERY="${TEST_QUERY:-best gaming laptop under 1500 euro}"
TEST_PROMPT="${TEST_PROMPT:-hello qwenbridge}"
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-10}"

SSE_CONNECT_TIMEOUT_SECONDS="${SSE_CONNECT_TIMEOUT_SECONDS:-10}"
SSE_EVENT_WAIT_SECONDS="${SSE_EVENT_WAIT_SECONDS:-60}"
SSE_STARTUP_WAIT_SECONDS="${SSE_STARTUP_WAIT_SECONDS:-1}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
# Comma-separated Compose profiles (QB-293). The application is the "production" profile; OpenSearch
# is an optional connector backend in its own profile, enabled here because the verification
# exercises the OpenSearch connector path (and selects it as the Commerce backend).
COMPOSE_PROFILE="${COMPOSE_PROFILE:-production,opensearch}"
export QWENBRIDGE_COMMERCE_BACKEND="${QWENBRIDGE_COMMERCE_BACKEND:-opensearch}"
export QWENBRIDGE_SEARCH_BACKEND="${QWENBRIDGE_SEARCH_BACKEND:-opensearch}"

APP_CONTAINER="${APP_CONTAINER:-qwenbridge-app}"
REDIS_CONTAINER="${REDIS_CONTAINER:-qwenbridge-redis}"
OLLAMA_CONTAINER="${OLLAMA_CONTAINER:-qwenbridge-ollama}"
OPENSEARCH_CONTAINER="${OPENSEARCH_CONTAINER:-qwenbridge-opensearch}"

OPENSEARCH_URL="${OPENSEARCH_URL:-http://127.0.0.1:9200}"
OLLAMA_URL="${OLLAMA_URL:-http://127.0.0.1:11434}"
VERIFY_RUN_ID="${VERIFY_RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
export OPENSEARCH_INDEX="${OPENSEARCH_INDEX:-qwenbridge-verify-${VERIFY_RUN_ID}}"

QWEN_MODEL="${QWEN_MODEL:-qwen2.5}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-bge-m3}"

APP_LOG="${APP_LOG:-/tmp/qwenbridge-verify-release.log}"

FORCE_FRESH="${FORCE_FRESH:-false}"
RESTART_DOCKER="${RESTART_DOCKER:-false}"
PULL_DOCKER_IMAGES="${PULL_DOCKER_IMAGES:-true}"
NO_CACHE_BUILD="${NO_CACHE_BUILD:-false}"
# CI builds the application image beforehand (from its layer cache) and sets this to true; Compose
# then starts that image instead of building it again.
APP_IMAGE_PREBUILT="${APP_IMAGE_PREBUILT:-false}"

PASSED=0
FAILED=0
WARNINGS=0
SKIPPED=0
DOCKER_AVAILABLE=false

SSE_PRIMARY_PID=""
SSE_UNRELATED_PID=""

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
BLUE="\033[0;34m"
NC="\033[0m"

VERIFY_LOG_DIR="${VERIFY_LOG_DIR:-logs/verification}"
VERIFY_RUN_ID="${VERIFY_RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
VERIFY_LOG_FILE="${VERIFY_LOG_FILE:-${VERIFY_LOG_DIR}/verify-release-${VERIFY_RUN_ID}.log}"

SERVER_DIR="${SERVER_DIR:-qwenbridge-server}"
JAVA_SDK_DIR="${JAVA_SDK_DIR:-qwenbridge-java-sdk}"
TS_SDK_DIR="${TS_SDK_DIR:-qwenbridge-typescript-sdk}"


RUN_V9_RUNTIME_SDK_TESTS="${RUN_V9_RUNTIME_SDK_TESTS:-true}"
RUN_V9_PERFORMANCE="${RUN_V9_PERFORMANCE:-false}"

PERF_TOTAL_REQUESTS="${PERF_TOTAL_REQUESTS:-1000000}"
PERF_VUS="${PERF_VUS:-250}"
PERF_MAX_DURATION="${PERF_MAX_DURATION:-30m}"
PERF_PROMETHEUS_REMOTE_WRITE="${PERF_PROMETHEUS_REMOTE_WRITE:-false}"
PERF_RUNNER="${PERF_RUNNER:-docker}"

RELEASE_LIGHTWEIGHT="${RELEASE_LIGHTWEIGHT:-false}"
RUN_DOCKER_RUNTIME="${RUN_DOCKER_RUNTIME:-true}"
RUN_V8_RUNTIME_VALIDATIONS="${RUN_V8_RUNTIME_VALIDATIONS:-true}"
