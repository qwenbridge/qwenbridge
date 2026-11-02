#!/usr/bin/env bash
# QwenBridge 1.0 Section 1 gate checks (Model B): Core isolation, Spring Boot elimination, startup
# without optional infrastructure, and a live Java SDK analyze call. Each check returns non-zero
# on failure; none of them modify Docker services or data.

SECTION1_STANDALONE_PORT="${SECTION1_STANDALONE_PORT:-18089}"
SECTION1_WORK_DIR="${SECTION1_WORK_DIR:-${VERIFY_LOG_DIR:-target}/section1-gate}"

# Maven resolves -DoutputFile against each module's directory, so every path handed to Maven must
# be absolute; otherwise the files land inside the modules and the checks read nothing.
section1_work_dir() {
  mkdir -p "${SECTION1_WORK_DIR}" && (cd "${SECTION1_WORK_DIR}" && pwd)
}

# Fails unless the file exists and lists at least one resolved artifact.
section1_require_listing() {
  if [[ ! -s "$1" ]] || ! grep -qE '^[[:space:]]+[a-z]' "$1"; then
    echo "Dependency listing missing or empty: $1" >&2
    return 1
  fi
}

# Core may depend on Jackson only (ADR 0015) and must not import any framework or connector.
section1_core_dependency_isolation_validation() {
  local dir
  dir="$(section1_work_dir)" || return 1
  local deps="${dir}/core-runtime-dependencies.txt"
  rm -f "${deps}"
  mvn -B -q dependency:list -pl qwenbridge-core -DincludeScope=runtime \
    -DoutputFile="${deps}" || return 1
  section1_require_listing "${deps}" || return 1
  local unexpected
  unexpected="$(grep -E '^[[:space:]]+[a-z]' "${deps}" | grep -v 'com.fasterxml.jackson.core:' || true)"
  if [[ -n "${unexpected}" ]]; then
    echo "Core has runtime dependencies beyond Jackson:" >&2
    echo "${unexpected}" >&2
    return 1
  fi
  local forbidden
  forbidden="$(grep -rEn '^import (io\.quarkus|jakarta\.|javax\.|org\.springframework|io\.vertx|org\.opensearch|co\.elastic|org\.postgresql|com\.mongodb|io\.lettuce|redis\.|io\.qwenbridge\.(commerce|platform|pipeline|connectors|plugins|api|security|translation|threat|explanation)\b)' \
    qwenbridge-core/src/main/java || true)"
  if [[ -n "${forbidden}" ]]; then
    echo "Core imports framework, connector or server code:" >&2
    echo "${forbidden}" >&2
    return 1
  fi
  echo "Core runtime dependencies: $(grep -cE '^[[:space:]]+com.fasterxml' "${deps}") Jackson artifact(s), no forbidden imports."
}

# No Spring artifact or import remains anywhere in the reactor.
section1_spring_boot_elimination_validation() {
  local dir
  dir="$(section1_work_dir)" || return 1
  local hits
  hits="$(grep -rlE 'org\.springframework|spring-boot' --include=pom.xml --include='*.java' \
    --include='*.yaml' --include='*.yml' --include='*.properties' --include=Dockerfile \
    qwenbridge-* examples pom.xml 2>/dev/null | grep -v '/target/' || true)"
  if [[ -n "${hits}" ]]; then
    echo "Spring references found:" >&2
    echo "${hits}" >&2
    return 1
  fi
  # The console output lists every module's resolved artifacts (-DoutputFile does not accumulate
  # across the reactor); require one listing per module so a partial run cannot pass.
  local tree="${dir}/reactor-dependencies.txt"
  # compile in the same run: the reactor modules then resolve from their build output, so the
  # gate also works on a fresh machine where they are not installed yet (CI).
  mvn -B -DskipTests compile dependency:list >"${tree}" 2>&1 || return 1
  local modules listings
  modules="$(grep -c '<module>' pom.xml)"
  listings="$(grep -c 'The following files have been resolved' "${tree}")"
  if [[ "${listings}" -lt "${modules}" ]]; then
    echo "Expected ${modules} module dependency listings, found ${listings}." >&2
    return 1
  fi
  if grep -q 'org.springframework' "${tree}"; then
    echo "Spring artifacts are still resolved:" >&2
    grep 'org.springframework' "${tree}" | sort -u >&2
    return 1
  fi
  echo "No Spring sources, configuration or resolved artifacts (${listings} module listings checked)."
}

# The packaged server starts and answers safely with Redis, OpenSearch and Ollama all unreachable.
section1_standalone_startup_validation() {
  local work
  work="$(section1_work_dir)" || return 1
  if curl -s -o /dev/null "http://localhost:${SECTION1_STANDALONE_PORT}/"; then
    echo "Port ${SECTION1_STANDALONE_PORT} is in use; set SECTION1_STANDALONE_PORT." >&2
    return 1
  fi
  mvn -B -q package -pl qwenbridge-server -am -DskipTests || return 1
  local log="${work}/standalone-startup.log"
  QUARKUS_HTTP_PORT="${SECTION1_STANDALONE_PORT}" \
    QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false \
    QWENBRIDGE_AI_OLLAMA_BASE_URL=http://127.0.0.1:9 \
    QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL=http://127.0.0.1:9 \
    QUARKUS_REDIS_HOSTS=redis://127.0.0.1:9 \
    QWENBRIDGE_ANALYSIS_CACHE_ENABLED=false \
    QWENBRIDGE_ABUSE_PROTECTION_REDIS_ENABLED=false \
    QWENBRIDGE_ANALYSIS_CACHE_REDIS_HOST=127.0.0.1 \
    QWENBRIDGE_ANALYSIS_CACHE_REDIS_PORT=9 \
    java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar >"${log}" 2>&1 &
  local pid=$!
  local base="http://localhost:${SECTION1_STANDALONE_PORT}"
  local status=0 live="" i
  for i in $(seq 1 90); do
    live="$(curl -s -o /dev/null -w '%{http_code}' "${base}/q/health/live" || true)"
    [[ "${live}" == "200" ]] && break
    kill -0 "${pid}" 2>/dev/null || break
    sleep 1
  done
  local analyze="${work}/standalone-analyze.json"
  local health code
  health="$(curl -s -o /dev/null -w '%{http_code}' "${base}/api/v1/health" || true)"
  code="$(curl -s -m 60 -o "${analyze}" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d '{"query":"black shoes under 100 EUR"}' "${base}/api/v1/search/analyze" || true)"
  terminate_background_process "${pid}"
  echo "liveness=${live} v1-health=${health} analyze=${code}"
  [[ "${live}" == "200" && "${health}" == "200" && "${code}" == "200" ]] || status=1
  if [[ "${status}" -eq 0 ]]; then
    python3 - "${analyze}" <<'PY' || status=1
import json, sys
d = json.load(open(sys.argv[1]))
assert d["decision"] in ("BLOCK", "CLARIFY", "ALLOW", "REVIEW"), d.get("decision")
assert d["threatAssessment"]["modelStatus"] == "UNAVAILABLE", d["threatAssessment"]
assert d["confidenceReport"]["overall"]["kind"] == "UNKNOWN", d["confidenceReport"]
print("degraded safely: decision=%s modelStatus=%s" % (d["decision"], d["threatAssessment"]["modelStatus"]))
PY
  fi
  if grep -q '"level":"ERROR"' "${log}"; then
    echo "Errors logged during standalone startup:" >&2
    grep '"level":"ERROR"' "${log}" | cut -c1-300 >&2
    status=1
  fi
  return "${status}"
}

# A live analyze call through the Java SDK against the running Docker application.
section1_java_sdk_live_analyze_validation() {
  local dir
  dir="$(section1_work_dir)" || return 1
  local cp_file="${dir}/java-sdk-classpath.txt"
  rm -f "${cp_file}"
  mvn -B -q install -pl qwenbridge-java-sdk -am -DskipTests || return 1
  mvn -B -q dependency:build-classpath -pl qwenbridge-java-sdk \
    -Dmdep.outputFile="${cp_file}" || return 1
  if [[ ! -s "${cp_file}" ]]; then
    echo "SDK classpath was not written: ${cp_file}" >&2
    return 1
  fi
  local sdk_jar
  sdk_jar="$(ls qwenbridge-java-sdk/target/qwenbridge-java-sdk-*.jar | grep -v -- '-sources\|-javadoc' | head -1)"
  [[ -n "${sdk_jar}" ]] || return 1
  BASE_URL="${BASE_URL}" java -cp "${sdk_jar}:$(cat "${cp_file}")" \
    scripts/verification/sdk/JavaSdkLiveAnalyze.java
}
