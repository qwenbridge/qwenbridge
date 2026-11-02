#!/usr/bin/env bash
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == "--help" ]]; then
  echo "Usage: bash scripts/qwenbridge-final-verification.sh"
  echo "Complete verification: Docker, seed, Java 25/JUnit with live integrations, TypeScript,"
  echo "code quality, release/API/SSE, all k6 scripts and staged million-request performance."
  echo "PERF_TOTAL_REQUESTS=1000 PERF_VUS=10 selects a shorter load run."
  exit 0
fi
export RUN_COMPLETE_VERIFICATION=true
export RUN_LOCAL_VERIFICATION=true
export RUN_V9_RUNTIME_SDK_TESTS=true
export RUN_V8_RUNTIME_VALIDATIONS=true
exec bash "${SCRIPT_DIR}/verify-performance.sh" "$@"
