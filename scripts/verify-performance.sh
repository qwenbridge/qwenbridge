#!/usr/bin/env bash
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

if [[ "${1:-}" == "--help" ]]; then
  echo "Usage: bash scripts/verify-performance.sh"
  echo "Defaults: 1,000,000 total requests, 250 concurrent VUs; cold latency and staged cached throughput."
  echo "Example smoke run: PERF_TOTAL_REQUESTS=1000 PERF_VUS=10 bash scripts/verify-performance.sh"
  echo "PERF_RUNNER=auto|native|docker; PERF_MAX_DURATION=30m. No volume reset."
  exit 0
fi
if [[ "$#" -ne 0 ]]; then
  echo "Unknown arguments. Use --help; workload settings are environment variables." >&2
  exit 2
fi

export RUN_V9_PERFORMANCE=true
export PERF_MANAGED=true
export RELEASE_LIGHTWEIGHT=false
export RUN_DOCKER_RUNTIME=true
export RUN_LOCAL_VERIFICATION="${RUN_LOCAL_VERIFICATION:-true}"
export PULL_DOCKER_IMAGES="${PULL_DOCKER_IMAGES:-false}"
export PERF_RUNNER="${PERF_RUNNER:-docker}"
python3 -c 'import sys; n=int(sys.argv[1]); v=int(sys.argv[2]); assert 1 <= v <= n <= 2000000000' \
  "${PERF_TOTAL_REQUESTS:-1000000}" "${PERF_VUS:-250}" || exit 1
export VERIFY_RUN_ID="${VERIFY_RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
# QB-293: OpenSearch is an optional profile; the verification enables it and selects it.
export COMPOSE_PROFILE="${COMPOSE_PROFILE:-production,opensearch}"
export QWENBRIDGE_COMMERCE_BACKEND="${QWENBRIDGE_COMMERCE_BACKEND:-opensearch}"
export QWENBRIDGE_SEARCH_BACKEND="${QWENBRIDGE_SEARCH_BACKEND:-opensearch}"
export OPENSEARCH_INDEX="${OPENSEARCH_INDEX:-qwenbridge-verify-${VERIFY_RUN_ID}}"
export QWENBRIDGE_ANALYSIS_CACHE_KEY_PREFIX="qwenbridge:performance:${VERIFY_RUN_ID}"

restore_limits() {
  echo "Restoring normal application rate limits after the benchmark."
  local args=(-f "${COMPOSE_FILE:-docker-compose.yml}") profile profiles=()
  IFS=',' read -r -a profiles <<<"${COMPOSE_PROFILE}"
  for profile in "${profiles[@]}"; do args+=(--profile "${profile}"); done
  docker compose "${args[@]}" up -d --no-deps qwenbridge-app
}
trap 'restore_limits || exit 1' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
bash "${SCRIPT_DIR}/verify-release.sh"
verification_rc=$?
restore_limits || { trap - EXIT; exit 1; }
trap - EXIT
exit "${verification_rc}"
