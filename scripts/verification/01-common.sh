#!/usr/bin/env bash
# Sourced by scripts/verify-release.sh (not executed directly).
if [[ -n "${VERIFY_LOG_FILE:-}" ]]; then
  mkdir -p "$(dirname "${VERIFY_LOG_FILE}")"
  exec > >(tee -a "${VERIFY_LOG_FILE}") 2>&1
  echo "Verification log file: ${VERIFY_LOG_FILE}"
fi

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

curl() {
  command curl --connect-timeout "${HTTP_CONNECT_TIMEOUT_SECONDS:-10}" \
    --max-time "${HTTP_TIMEOUT_SECONDS:-180}" "$@"
}

compose() {
  local args=(-f "${COMPOSE_FILE}")
  local profile=""
  local profiles=()
  # COMPOSE_PROFILE is a comma-separated list (QB-293), e.g. "production,opensearch".
  IFS=',' read -r -a profiles <<<"${COMPOSE_PROFILE:-}"
  for profile in "${profiles[@]}"; do
    [[ -n "${profile}" ]] && args+=(--profile "${profile}")
  done
  docker compose "${args[@]}" "$@"
}

compose_with_profiles() {
  local args=(-f "${COMPOSE_FILE}")
  local profile=""

  for profile in "$@"; do
    if [[ "${profile}" == "--" ]]; then
      shift
      break
    fi

    args+=(--profile "${profile}")
    shift
  done

  docker compose "${args[@]}" "$@"
}

skip() {
  echo -e "${YELLOW}SKIP${NC} - $1"
  SKIPPED=$((SKIPPED + 1))
}

run_step() {
  local name="$1"
  shift

  section "${name}"

  local status=0
  "$@" || status=$?
  if [[ "${status}" -eq 0 ]]; then
    pass "${name}"
  elif [[ "${status}" -eq 77 ]]; then
    skip "${name}"
  else
    fail "${name}"
  fi
  return "${status}"
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

cleanup_background_processes() {
  terminate_background_process "${SSE_PRIMARY_PID}"
  terminate_background_process "${SSE_UNRELATED_PID}"

  SSE_PRIMARY_PID=""
  SSE_UNRELATED_PID=""
  if declare -F stop_secure_opensearch >/dev/null; then stop_secure_opensearch || true; fi
}

trap cleanup_background_processes EXIT INT TERM
