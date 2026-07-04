mkdir -p "${VERIFY_LOG_DIR}"
exec > >(tee -a "${VERIFY_LOG_FILE}") 2>&1
echo "Verification log file: ${VERIFY_LOG_FILE}"

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

compose() {
  if [[ -n "${COMPOSE_PROFILE}" ]]; then
    docker compose -f "${COMPOSE_FILE}" --profile "${COMPOSE_PROFILE}" "$@"
    return $?
  fi

  docker compose -f "${COMPOSE_FILE}" "$@"
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
}

trap cleanup_background_processes EXIT INT TERM
