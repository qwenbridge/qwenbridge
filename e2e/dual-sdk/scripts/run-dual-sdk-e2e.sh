#!/usr/bin/env bash
# QB-295 dual-SDK end-to-end orchestrator. Starts the real packaged Quarkus server, runs the
# independent Java and TypeScript SDK consumers against it with the shared scenarios, evaluates and
# compares their results, and writes reports. Stops only the processes it started; touches no
# Docker service and no data outside its own run directory.
#
# Usage: e2e/dual-sdk/scripts/run-dual-sdk-e2e.sh [--mode MODE]... [--report-dir DIR] [--no-build]
#   MODE: OFFLINE_BASELINE (default, mandatory), LOCAL_PROVIDER_INTEGRATION,
#         AUTHORIZED_LIVE_PROVIDER, ALL
# Environment: QB295_PORT (base port, default 18295), QB295_REDIS (redis URL for catalog sync in
#   LOCAL_PROVIDER_INTEGRATION, default redis://127.0.0.1:6379), QB295_PERF_REQUESTS (default 200).
#   (Catalog synchronization keeps its checkpoint and lock in that Redis under
#   qwenbridge:catalog:e2e-provider:<digest of the per-run endpoint>; nothing else is written.)
#   AUTHORIZED_LIVE_PROVIDER needs QWENBRIDGE_E2E_LIVE_AUTHORIZED=yes plus provider credentials
#   (see README); without them the live results are BLOCKED, never simulated.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
E2E="${ROOT}/e2e/dual-sdk"
cd "${ROOT}" || exit 2
MODES=()
BUILD=1
REPORT_DIR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODES+=("$2"); shift 2 ;;
    --report-dir) REPORT_DIR="$2"; shift 2 ;;
    --no-build) BUILD=0; shift ;;
    *) echo "Unknown argument $1" >&2; exit 2 ;;
  esac
done
[[ ${#MODES[@]} -eq 0 ]] && MODES=(OFFLINE_BASELINE)
[[ " ${MODES[*]} " == *" ALL "* ]] && MODES=(OFFLINE_BASELINE LOCAL_PROVIDER_INTEGRATION AUTHORIZED_LIVE_PROVIDER)
RUN_ID="dual-sdk-$(date +%Y%m%d-%H%M%S)"
RUN="${REPORT_DIR:-${E2E}/reports/${RUN_ID}}"
mkdir -p "${RUN}" || exit 2
# Absolute, because later steps run from other directories (a relative --report-dir broke CI).
RUN="$(cd "${RUN}" && pwd)" || exit 2
PORT="${QB295_PORT:-18295}"
STUB_PORT=$((PORT + 1)); PROVIDER_PORT=$((PORT + 2)); SERVER_B_PORT=$((PORT + 3)); V1_PORT=$((PORT + 4))
PIDS=()

log() { echo "[dual-sdk] $*"; }
cleanup() {
  local pid
  for pid in "${PIDS[@]:-}"; do
    [[ -n "${pid}" ]] && kill "${pid}" 2>/dev/null && wait "${pid}" 2>/dev/null
  done
  PIDS=()
}
trap cleanup EXIT INT TERM

# ---- 1. prerequisites ---------------------------------------------------------------------------
if [[ -f "${ROOT}/scripts/verification/java-env.sh" ]]; then
  # shellcheck source=/dev/null
  source "${ROOT}/scripts/verification/java-env.sh" && configure_java25 >/dev/null
fi
java_major="$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)"
node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
for tool in mvn node npm python3 curl; do
  command -v "${tool}" >/dev/null || { echo "Missing prerequisite: ${tool}" >&2; exit 2; }
done
[[ "${java_major}" == "25" ]] || { echo "Java 25 required (found ${java_major})" >&2; exit 2; }
(( node_major >= 20 )) || { echo "Node.js 20+ required" >&2; exit 2; }
for p in "${PORT}" "${STUB_PORT}" "${PROVIDER_PORT}" "${SERVER_B_PORT}" "${V1_PORT}"; do
  if curl -s -o /dev/null --max-time 1 "http://127.0.0.1:${p}/"; then
    echo "Port ${p} is in use; set QB295_PORT." >&2; exit 2
  fi
done
log "run ${RUN}; modes ${MODES[*]}; java ${java_major}; node $(node -v)"

# ---- 2. build: server, SDKs as external artifacts, consumers -----------------------------------
JAR="${ROOT}/qwenbridge-server/target/quarkus-app/quarkus-run.jar"
if [[ "${BUILD}" -eq 1 || ! -f "${JAR}" ]]; then
  log "building server and Java SDK"
  mvn -B -q install -pl qwenbridge-server,qwenbridge-java-sdk -am -DskipTests >"${RUN}/build.log" 2>&1 \
    || { tail -30 "${RUN}/build.log" >&2; exit 1; }
fi
log "building the Java consumer against the installed SDK artifact"
mvn -B -q -f "${E2E}/java-consumer/pom.xml" package dependency:build-classpath \
  -Dmdep.outputFile="${RUN}/java-consumer.classpath" >"${RUN}/java-consumer-build.log" 2>&1 \
  || { tail -30 "${RUN}/java-consumer-build.log" >&2; exit 1; }
JAVA_CP="${E2E}/java-consumer/target/classes:$(cat "${RUN}/java-consumer.classpath")"
log "packing the TypeScript SDK and installing it into the TypeScript consumer"
(cd "${ROOT}/qwenbridge-typescript-sdk" && { [[ "${BUILD}" -eq 0 && -f dist/index.js ]] || npm run -s build; } \
  && npm pack --silent --pack-destination "${RUN}") >"${RUN}/ts-sdk-pack.log" 2>&1 \
  || { tail -30 "${RUN}/ts-sdk-pack.log" >&2; exit 1; }
TGZ="$(ls "${RUN}"/qwenbridge-sdk-*.tgz | head -1)"
# Development tools first, then the SDK tarball without saving it (a later plain install would
# prune it), then a type-checked build against the SDK's published declarations.
(cd "${E2E}/typescript-consumer" && npm install --no-audit --no-fund --prefer-offline \
  && npm install --no-audit --no-fund --prefer-offline --no-save "${TGZ}" \
  && npx tsc -p tsconfig.json) \
  >"${RUN}/ts-consumer-build.log" 2>&1 || { tail -30 "${RUN}/ts-consumer-build.log" >&2; exit 1; }

# ---- helpers -------------------------------------------------------------------------------------
key() { python3 -c 'import secrets; print("e2e-" + secrets.token_hex(16))'; }
wait_ready() {
  local url="$1" pid="$2" i code
  for i in $(seq 1 180); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "${url}/q/health/ready" || true)"
    [[ "${code}" == "200" ]] && return 0
    kill -0 "${pid}" 2>/dev/null || return 1
    sleep 1
  done
  return 1
}
principals() {
  # Fresh test-only keys for this run; written to the run directory only, never to the repository.
  python3 - "$1" <<'PY'
import json, secrets, sys
roles = {"main": ("e2e", ["search", "catalog:write"]), "other": ("e2e-other", ["search"]),
         "suggest": ("e2e-suggest", ["search"]), "off": ("e2e-off", ["search"]),
         "one": ("e2e-one", ["search"]), "none": ("e2e-none", ["search"]),
         "noscope": ("e2e", ["agents:read"]), "provider": ("e2e-provider", ["search", "catalog:write"])}
keys = {name: "e2e-" + secrets.token_hex(16) for name in roles}
json.dump(keys, open(sys.argv[1], "w"))
print(json.dumps([{"key": keys[n], "subject": n + "-user", "tenant": t, "roles": r} for n, (t, r) in roles.items()]))
PY
}
start_server() { # $1 port, $2 log, remaining: KEY=VALUE overrides
  local port="$1" logf="$2"; shift 2
  env QUARKUS_HTTP_PORT="${port}" \
    QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=true \
    QWENBRIDGE_SECURITY_API_PRINCIPALS="${API_PRINCIPALS}" \
    QWENBRIDGE_AI_OLLAMA_BASE_URL="http://127.0.0.1:${STUB_PORT}" \
    QWENBRIDGE_AI_OLLAMA_READ_TIMEOUT=30s \
    QWENBRIDGE_EMBEDDING_ENABLED=true \
    QWENBRIDGE_EMBEDDING_DIMENSIONS=256 \
    QWENBRIDGE_COMMERCE_BACKEND=memory \
    QWENBRIDGE_SEARCH_BACKEND=in-memory \
    QWENBRIDGE_PLATFORM_STORE=memory \
    QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL=http://127.0.0.1:9 \
    QWENBRIDGE_ANALYSIS_CACHE_ENABLED=false \
    QWENBRIDGE_ABUSE_PROTECTION_REDIS_ENABLED=false \
    QWENBRIDGE_ABUSE_PER_IP_LIMIT=1000000 \
    QWENBRIDGE_ABUSE_PER_API_KEY_LIMIT=1000000 \
    QWENBRIDGE_ABUSE_AI_REQUEST_QUOTA=1000000 \
    QWENBRIDGE_ABUSE_TOKEN_QUOTA=1000000000 \
    QWENBRIDGE_TYPO_ENABLED=true \
    QWENBRIDGE_TYPO_LANGUAGES=en,sv,fa \
    QWENBRIDGE_TYPO_MODE=SAFE_AUTO_CORRECT \
    QWENBRIDGE_TYPO_DICTIONARY_DIR="${E2E}/fixtures/typo" \
    QWENBRIDGE_TYPO_TENANTS='{"e2e-suggest":{"enabled":true,"languages":["en","sv","fa"],"mode":"SUGGEST_ONLY"},"e2e-off":{"enabled":false}}' \
    QWENBRIDGE_MULTILINGUAL_ENABLED=true \
    QWENBRIDGE_MULTILINGUAL_TARGET_LANGUAGES=en,fa,sv,nb,nn,ja \
    QWENBRIDGE_MULTILINGUAL_GLOSSARY_DIR="${E2E}/fixtures/glossary" \
    QWENBRIDGE_MULTILINGUAL_TENANTS='{"e2e-one":{"enabled":true,"targetLanguages":["sv"]},"e2e-none":{"enabled":true,"targetLanguages":[]},"e2e-off":{"enabled":false}}' \
    "$@" java -jar "${JAR}" >"${logf}" 2>&1 &
  echo $!
}
run_consumers() { # $1 base url, $2 tag, $3 suffix
  local base="$1" tag="$2" suffix="$3" status=0
  log "Java consumer (${tag})"
  java -cp "${JAVA_CP}" io.qwenbridge.e2e.consumer.JavaConsumer \
    --base "${base}" --scenarios "${E2E}/scenarios/scenarios.json" --keys "${RUN}/keys.json" \
    --tag "${tag}" --out "${RUN}/java-${suffix}.json" --perf "${QB295_PERF_REQUESTS:-200}" \
    >"${RUN}/java-${suffix}.log" 2>&1 || status=1
  log "TypeScript consumer (${tag})"
  node "${E2E}/typescript-consumer/dist/main.js" \
    --base "${base}" --scenarios "${E2E}/scenarios/scenarios.json" --keys "${RUN}/keys.json" \
    --tag "${tag}" --out "${RUN}/typescript-${suffix}.json" --perf "${QB295_PERF_REQUESTS:-200}" \
    >"${RUN}/typescript-${suffix}.log" 2>&1 || status=1
  return "${status}"
}

API_PRINCIPALS="$(principals "${RUN}/keys.json")"
status=0
python3 -I "${E2E}/provider-adapters/local_model_stub.py" "${STUB_PORT}" \
  "${E2E}/fixtures/model-answers.json" 256 >"${RUN}/model-stub.log" 2>&1 &
PIDS+=($!)
for i in $(seq 1 50); do
  curl -s -o /dev/null --max-time 1 "http://127.0.0.1:${STUB_PORT}/api/tags" && break
  sleep 0.2
done
curl -s -o /dev/null --max-time 1 "http://127.0.0.1:${STUB_PORT}/api/tags" \
  || { echo "The model stand-in did not start" >&2; exit 1; }

for MODE in "${MODES[@]}"; do
  case "${MODE}" in
    OFFLINE_BASELINE)
      log "OFFLINE_BASELINE: real server on ${PORT}, deterministic catalog, no external service"
      pid="$(start_server "${PORT}" "${RUN}/server-offline.log" \
        QWENBRIDGE_DEMO_DATASET_FILES="${E2E}/fixtures/catalog-e2e-1.0.json" \
        QWENBRIDGE_DEMO_TENANT=e2e QWENBRIDGE_DEMO_EMBED=true \
        QWENBRIDGE_MULTILINGUAL_PROVIDERS=glossary \
        QUARKUS_REDIS_HOSTS=redis://127.0.0.1:9)"
      PIDS+=("${pid}")
      if ! wait_ready "http://127.0.0.1:${PORT}" "${pid}"; then
        tail -30 "${RUN}/server-offline.log" >&2; exit 1
      fi
      python3 -I "${E2E}/scripts/protocol_checks.py" "http://127.0.0.1:${PORT}" "${RUN}/keys.json" \
        "${RUN}/protocol-offline.json" "http://127.0.0.1:${STUB_PORT}" >"${RUN}/protocol-offline.log" 2>&1 || status=1
      run_consumers "http://127.0.0.1:${PORT}" offline offline || status=1
      # The legacy v1 API is refused in authenticated deployments (409, checked above), so its
      # scenarios run against a second, unauthenticated offline server (QB-287: replaces examples/).
      log "OFFLINE_BASELINE v1: unauthenticated server on ${V1_PORT} for the legacy v1 API"
      pid="$(start_server "${V1_PORT}" "${RUN}/server-v1.log" \
        QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false \
        QWENBRIDGE_MULTILINGUAL_PROVIDERS=glossary \
        QUARKUS_REDIS_HOSTS=redis://127.0.0.1:9)"
      PIDS+=("${pid}")
      if ! wait_ready "http://127.0.0.1:${V1_PORT}" "${pid}"; then
        tail -30 "${RUN}/server-v1.log" >&2; exit 1
      fi
      run_consumers "http://127.0.0.1:${V1_PORT}" v1 v1 || status=1
      ;;
    LOCAL_PROVIDER_INTEGRATION)
      log "LOCAL_PROVIDER_INTEGRATION: local catalog provider on ${PROVIDER_PORT}, server on ${SERVER_B_PORT}"
      redis="${QB295_REDIS:-redis://127.0.0.1:6379}"
      if ! python3 -I "${E2E}/scripts/redis_ping.py" "${redis}"; then
        python3 -I -c 'import json,sys; json.dump({"mode":"LOCAL_PROVIDER_INTEGRATION","status":"BLOCKED","reason":"catalog synchronization needs Redis (its tenant checkpoint and lock); none reachable at '"${redis}"'"}, open(sys.argv[1],"w"))' "${RUN}/provider-blocked.json"
        continue
      fi
      token="$(key)"
      python3 -I "${E2E}/provider-adapters/catalog_provider.py" "${PROVIDER_PORT}" \
        "${E2E}/fixtures/catalog-e2e-1.0.json" "${token}" >"${RUN}/catalog-provider.log" 2>&1 &
      PIDS+=($!)
      pid="$(start_server "${SERVER_B_PORT}" "${RUN}/server-provider.log" \
        QWENBRIDGE_CATALOG_ENDPOINT="http://127.0.0.1:${PROVIDER_PORT}/feed?run=${RUN_ID}" \
        QWENBRIDGE_CATALOG_TOKEN="${token}" \
        QWENBRIDGE_MULTILINGUAL_PROVIDERS=glossary,local-mt \
        QUARKUS_REDIS_HOSTS="${redis}")"
      PIDS+=("${pid}")
      if ! wait_ready "http://127.0.0.1:${SERVER_B_PORT}" "${pid}"; then
        tail -30 "${RUN}/server-provider.log" >&2; exit 1
      fi
      python3 -I "${E2E}/scripts/provider_checks.py" "http://127.0.0.1:${SERVER_B_PORT}" "${RUN}/keys.json" \
        "http://127.0.0.1:${PROVIDER_PORT}" "${RUN}/provider-local.json" >"${RUN}/provider-local.log" 2>&1 || status=1
      run_consumers "http://127.0.0.1:${SERVER_B_PORT}" provider provider || status=1
      ;;
    AUTHORIZED_LIVE_PROVIDER)
      log "AUTHORIZED_LIVE_PROVIDER"
      bash "${E2E}/scripts/live-providers.sh" "${RUN}" >"${RUN}/live-providers.log" 2>&1 || status=1
      ;;
    *) echo "Unknown mode ${MODE}" >&2; exit 2 ;;
  esac
done
# Evidence that every model call of the run went to the local stand-in.
curl -s --max-time 2 "http://127.0.0.1:${STUB_PORT}/stats" >"${RUN}/model-stub-stats.json" || true
cleanup
python3 -I "${E2E}/scripts/evaluate.py" "${E2E}/scenarios/scenarios.json" "${RUN}" || status=1
log "report: ${RUN}/report.md"
exit "${status}"
