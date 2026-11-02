#!/usr/bin/env bash
# Sourced by scripts/verify-release.sh (not executed directly).
v9_path_exists() {
  local path="$1"
  [[ -e "${path}" ]]
}

v9_repo_structure_validation() {
  [[ -f "pom.xml" ]] \
    && [[ -d "${SERVER_DIR}" ]] \
    && [[ -f "${SERVER_DIR}/pom.xml" ]] \
    && [[ -d "${JAVA_SDK_DIR}" ]] \
    && [[ -f "${JAVA_SDK_DIR}/pom.xml" ]] \
    && [[ -d "${TS_SDK_DIR}" ]] \
    && [[ -f "${TS_SDK_DIR}/package.json" ]] \
    && [[ -d "e2e/dual-sdk/java-consumer" ]] \
    && [[ -d "e2e/dual-sdk/typescript-consumer" ]]
}

v9_parent_modules_validation() {
  grep -q "<module>${SERVER_DIR}</module>" pom.xml \
    && grep -q "<module>qwenbridge-plugin-sdk</module>" pom.xml \
    && grep -q "<module>qwenbridge-commerce-pack</module>" pom.xml \
    && grep -q "<module>qwenbridge-library-pack</module>" pom.xml \
    && grep -q "<module>${JAVA_SDK_DIR}</module>" pom.xml \
    && ! grep -q "<module>examples/" pom.xml
}

v9_docs_validation() {
  [[ -f "docs/roadmap/V9.md" ]] \
    && [[ -f "docs/release/V9-release-checklist.md" ]] \
    && [[ -f "docs/release/V9-release-evidence.md" ]] \
    && grep -qi "Java SDK" docs/roadmap/V9.md \
    && grep -qi "TypeScript" docs/roadmap/V9.md \
    && grep -qi "SSE" docs/release/V9-release-evidence.md \
    && grep -qi "publishing" docs/release/V9-release-evidence.md
}

v9_maven_reactor_validation() {
  mvn -B clean install
}

v9_java_sdk_source_validation() {
  [[ -f "${JAVA_SDK_DIR}/src/main/java/io/qwenbridge/sdk/QwenBridgeClient.java" ]] \
    && [[ -f "${JAVA_SDK_DIR}/src/main/java/io/qwenbridge/sdk/streaming/QwenBridgeStreamingClient.java" ]] \
    && [[ -f "${JAVA_SDK_DIR}/src/main/java/io/qwenbridge/sdk/streaming/StreamingPayloadMapper.java" ]] \
    && [[ -f "${JAVA_SDK_DIR}/src/main/java/io/qwenbridge/sdk/retry/RetryPolicy.java" ]] \
    && [[ -f "${JAVA_SDK_DIR}/README.md" ]]
}

v9_java_sdk_tests_validation() {
  mvn -pl "${JAVA_SDK_DIR}" test
}

# QB-287: the examples/ applications were replaced by the independent E2E consumers, which run the
# same v1 analyze (sync and async) and typed-streaming calls against the real server (step 24).
v9_java_sdk_examples_validation() {
  [[ -f "e2e/dual-sdk/java-consumer/pom.xml" ]] \
    && grep -q "analyzeAsync" e2e/dual-sdk/java-consumer/src/main/java/io/qwenbridge/e2e/consumer/JavaConsumer.java \
    && grep -q "streamTyped" e2e/dual-sdk/java-consumer/src/main/java/io/qwenbridge/e2e/consumer/JavaConsumer.java
}

v9_typescript_sdk_source_validation() {
  [[ -f "${TS_SDK_DIR}/package.json" ]] \
    && [[ -f "${TS_SDK_DIR}/README.md" ]] \
    && [[ -f "${TS_SDK_DIR}/PUBLISHING.md" ]] \
    && [[ -f "${TS_SDK_DIR}/LICENSE" ]] \
    && [[ -f "${TS_SDK_DIR}/NOTICE" ]] \
    && [[ -f "${TS_SDK_DIR}/src/qwenbridge-client.ts" ]] \
    && [[ -f "${TS_SDK_DIR}/src/streaming/qwenbridge-streaming-client.ts" ]] \
    && [[ -f "${TS_SDK_DIR}/src/streaming/streaming-payload-mapper.ts" ]] \
    && [[ -f "${TS_SDK_DIR}/src/retry/retry-policy.ts" ]] \
    && [[ -f "e2e/dual-sdk/typescript-consumer/package.json" ]] \
    && grep -q "streamTyped" e2e/dual-sdk/typescript-consumer/src/main.ts
}

v9_typescript_sdk_package_metadata_validation() {
  jq -e '
    .name == "@qwenbridge/sdk"
    and .type == "module"
    and .main == "./dist/index.js"
    and .types == "./dist/index.d.ts"
    and .exports["."].import == "./dist/index.js"
    and .exports["."].types == "./dist/index.d.ts"
    and .publishConfig.access == "public"
    and .engines.node == ">=20"
    and (.files | index("dist"))
    and (.files | index("README.md"))
    and (.files | index("LICENSE"))
    and (.files | index("NOTICE"))
  ' "${TS_SDK_DIR}/package.json" >/dev/null
}

v9_typescript_sdk_build_test_pack_validation() {
  npm --prefix "${TS_SDK_DIR}" ci \
    && npm --prefix "${TS_SDK_DIR}" run build \
    && npm --prefix "${TS_SDK_DIR}" test \
    && npm --prefix "${TS_SDK_DIR}" run pack:check
}

v9_typescript_sdk_audit_validation() {
  npm --prefix "${TS_SDK_DIR}" audit --audit-level=moderate
}

v9_typescript_sdk_exports_validation() {
  npm --prefix "${TS_SDK_DIR}" run build || return 1

  node --input-type=module <<'NODE'
import {
  QwenBridgeClient,
  QwenBridgeStreamingClient,
  QwenBridgeApiError,
  QwenBridgeTransportError,
  RetryPolicy,
  RetryClassifier,
  RetryExecutor,
  StreamingPayloadMapper
} from "./qwenbridge-typescript-sdk/dist/index.js";

const exportsToCheck = [
  QwenBridgeClient,
  QwenBridgeStreamingClient,
  QwenBridgeApiError,
  QwenBridgeTransportError,
  RetryPolicy,
  RetryClassifier,
  RetryExecutor,
  StreamingPayloadMapper
];

for (const item of exportsToCheck) {
  if (typeof item !== "function") {
    throw new Error("Expected exported symbol to be a function/class");
  }
}
NODE
}

v9_typescript_sdk_runtime_analyze_validation() {
  if [[ "${RUN_V9_RUNTIME_SDK_TESTS}" != "true" ]]; then
    info "RUN_V9_RUNTIME_SDK_TESTS=false, skipping TypeScript runtime analyze validation."
    return 0
  fi

  npm --prefix "${TS_SDK_DIR}" run build || return 1

  BASE_URL="${BASE_URL}" TEST_QUERY="${TEST_QUERY}" node --input-type=module <<'NODE'
import { QwenBridgeClient } from "./qwenbridge-typescript-sdk/dist/index.js";

const requestId = "verify-v9-ts-sdk-analyze-" + Date.now();

const client = new QwenBridgeClient({
  baseUrl: process.env.BASE_URL,
  retry: {
    maxAttempts: 2,
    initialDelayMs: 0
  }
});

const response = await client.analyze({
  requestId,
  query: process.env.TEST_QUERY
});

if (response.requestId !== requestId) {
  throw new Error(`Unexpected requestId: ${response.requestId}`);
}

if (!response.originalQuery) {
  throw new Error("Missing originalQuery in TypeScript SDK analyze response");
}

console.log(JSON.stringify({
  requestId: response.requestId,
  originalQuery: response.originalQuery,
  decision: response.decision,
  confidence: response.confidence
}, null, 2));
NODE
}

v9_typescript_sdk_runtime_sse_validation() {
  if [[ "${RUN_V9_RUNTIME_SDK_TESTS}" != "true" ]]; then
    info "RUN_V9_RUNTIME_SDK_TESTS=false, skipping TypeScript runtime SSE validation."
    return 0
  fi

  npm --prefix "${TS_SDK_DIR}" run build || return 1

  BASE_URL="${BASE_URL}" TEST_QUERY="${TEST_QUERY}" node --input-type=module <<'NODE'
import {
  QwenBridgeClient,
  QwenBridgeStreamingClient
} from "./qwenbridge-typescript-sdk/dist/index.js";

const requestId = "verify-v9-ts-sdk-sse-" + Date.now();

const client = new QwenBridgeClient({
  baseUrl: process.env.BASE_URL,
  retry: {
    maxAttempts: 2,
    initialDelayMs: 0
  }
});

const streamingClient = new QwenBridgeStreamingClient({
  baseUrl: process.env.BASE_URL
});

const payloadKinds = [];
let terminal = false;

const streamPromise = streamingClient.streamTyped(requestId, event => {
  payloadKinds.push(event.payload.kind);

  if (
    event.event === "pipeline.completed"
    || event.event === "pipeline.failed"
    || event.event === "pipeline.stopped"
    || event.payload.kind === "ai.completed"
    || event.payload.kind === "ai.failed"
  ) {
    terminal = true;
  }
});

await new Promise(resolve => setTimeout(resolve, 500));

await client.analyze({
  requestId,
  query: process.env.TEST_QUERY
});

let timeout;
try {
  await Promise.race([
    streamPromise,
    new Promise((_, reject) => {
      timeout = setTimeout(() => reject(new Error("Timed out waiting for TypeScript SDK SSE stream")), 65000);
    })
  ]).catch(error => {
    if (!terminal) throw error;
  });
} finally {
  clearTimeout(timeout);
}

if (payloadKinds.length === 0) {
  throw new Error("No typed SSE payloads received");
}
if (!terminal) throw new Error("SSE stream ended without a terminal event");

console.log(JSON.stringify({
  requestId,
  payloadKinds,
  terminal
}, null, 2));
NODE
}

v9_java_sdk_runtime_compile_validation() {
  mvn -pl "${JAVA_SDK_DIR}" -am test \
    && mvn -B -q -f e2e/dual-sdk/java-consumer/pom.xml compile
}

v9_release_docs_no_stale_v8_only_validation() {
  grep -qi "V9" docs/roadmap/V9.md \
    && grep -qi "TypeScript SDK" docs/release/V9-release-evidence.md
}

v9_performance_script_validation() {
  [[ -f "scripts/performance/v9-k6-load-test.js" ]] \
    && grep -q "shared-iterations" scripts/performance/v9-k6-load-test.js \
    && grep -q "PERF_TOTAL_REQUESTS" scripts/performance/v9-k6-load-test.js \
    && grep -q "mixed_language_users" scripts/performance/v9-k6-load-test.js
}

v9_dockerized_k6_compose_validation() {

  compose_with_profiles production performance -- config >/tmp/qwenbridge-compose-expanded.yml || return 1

  compose_with_profiles production performance -- config --services \
    | tee /tmp/qwenbridge-compose-services.txt

  grep -qx "qwenbridge-k6" /tmp/qwenbridge-compose-services.txt \
    && grep -q "http://qwenbridge-app:8080" /tmp/qwenbridge-compose-expanded.yml \
    && grep -q "scripts/performance" /tmp/qwenbridge-compose-expanded.yml
}

v9_optional_performance_validation() {

  if [[ "${FAILED}" -gt 0 ]]; then
    info "Skipping load escalation because an earlier verification step failed."
    return 77
  fi

  if [[ "${RUN_V9_PERFORMANCE}" != "true" ]]; then
    info "RUN_V9_PERFORMANCE=false, skipped performance execution."
    info "To run real dockerized k6:"
    info "RUN_V9_PERFORMANCE=true PERF_TOTAL_REQUESTS=1000000 PERF_VUS=250 PERF_MAX_DURATION=30m bash scripts/verify-release.sh"
    return 77
  fi

  info "Running cold-cache and staged cached-throughput verification."
  info "PERF_TOTAL_REQUESTS=${PERF_TOTAL_REQUESTS}"
  info "PERF_VUS=${PERF_VUS}"
  info "PERF_MAX_DURATION=${PERF_MAX_DURATION}"

  export VERIFY_RUN_ID VERIFY_LOG_DIR BASE_URL COMPOSE_FILE COMPOSE_PROFILE REDIS_CONTAINER
  export PERF_TOTAL_REQUESTS PERF_VUS PERF_MAX_DURATION PERF_RUNNER
  python3 scripts/verification/run-performance.py
}
