#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

if [[ "${1:-}" == "--help" ]]; then
  echo "Usage: bash scripts/verify-local.sh"
  echo "Runs Java 25 Maven/JUnit, TypeScript, packaging and verification-tool regression checks."
  echo "External tests: QWENBRIDGE_RUN_OLLAMA_IT=true QWENBRIDGE_RUN_OPENSEARCH_IT=true QWENBRIDGE_RUN_REDIS_IT=true."
  exit 0
fi

source "${SCRIPT_DIR}/verification/java-env.sh"
configure_java25 || exit 1
export RELEASE_LIGHTWEIGHT=true
source "${SCRIPT_DIR}/verification/00-env.sh"
source "${SCRIPT_DIR}/verification/01-common.sh"
source "${SCRIPT_DIR}/verification/02-git-project.sh"
source "${SCRIPT_DIR}/verification/99-summary.sh"

run_step "Required local tools" check_required_tools || { print_summary; exit 1; }
run_step "Versions, Java 25 and Quarkus contracts" python3 scripts/verification/check-versions.py
run_step "Verification script regression tests" bash scripts/tests/verification-test.sh
run_step "Shared multilingual corpus consistency" python3 scripts/verification/generate-language-corpus.py
run_step "Complete Maven reactor and JUnit suite" mvn -B clean install
run_step "TypeScript reproducible install" npm --prefix qwenbridge-typescript-sdk ci
run_step "TypeScript tests" npm --prefix qwenbridge-typescript-sdk test
run_step "TypeScript build" npm --prefix qwenbridge-typescript-sdk run build
run_step "Browser asset consistency" npm --prefix qwenbridge-typescript-sdk run web:check
run_step "Chromium test browser availability" npm --prefix qwenbridge-typescript-sdk exec -- playwright install chromium
run_step "Web Component and Playground Chromium acceptance" npm --prefix qwenbridge-typescript-sdk run test:browser
run_step "TypeScript package contents" npm --prefix qwenbridge-typescript-sdk run pack:check
print_summary
exit "$?"
