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
    && [[ -d "${STARTER_DIR}" ]] \
    && [[ -f "${STARTER_DIR}/pom.xml" ]] \
    && [[ -d "${TS_SDK_DIR}" ]] \
    && [[ -f "${TS_SDK_DIR}/package.json" ]] \
    && [[ -d "${JAVA_SDK_EXAMPLE_DIR}" ]] \
    && [[ -d "${STARTER_EXAMPLE_DIR}" ]]
}

v9_parent_modules_validation() {
  grep -q "<module>${SERVER_DIR}</module>" pom.xml \
    && grep -q "<module>${JAVA_SDK_DIR}</module>" pom.xml \
    && grep -q "<module>${STARTER_DIR}</module>" pom.xml \
    && grep -q "<module>${JAVA_SDK_EXAMPLE_DIR}</module>" pom.xml \
    && grep -q "<module>${STARTER_EXAMPLE_DIR}</module>" pom.xml
}

v9_docs_validation() {
  [[ -f "docs/roadmap/V9.md" ]] \
    && [[ -f "docs/release/V9-release-checklist.md" ]] \
    && [[ -f "docs/release/V9-release-evidence.md" ]] \
    && grep -qi "Java SDK" docs/roadmap/V9.md \
    && grep -qi "Spring Boot" docs/roadmap/V9.md \
    && grep -qi "TypeScript" docs/roadmap/V9.md \
    && grep -qi "SSE" docs/release/V9-release-evidence.md \
    && grep -qi "publishing" docs/release/V9-release-evidence.md
}

v9_maven_reactor_validation() {
  mvn clean test
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

v9_java_sdk_examples_validation() {
  [[ -f "${JAVA_SDK_EXAMPLE_DIR}/src/main/java/io/qwenbridge/examples/SyncSearchAnalyzeExample.java" ]] \
    && [[ -f "${JAVA_SDK_EXAMPLE_DIR}/src/main/java/io/qwenbridge/examples/AsyncSearchAnalyzeExample.java" ]] \
    && [[ -f "${JAVA_SDK_EXAMPLE_DIR}/src/main/java/io/qwenbridge/examples/TypedStreamingExample.java" ]] \
    && mvn -pl "${JAVA_SDK_EXAMPLE_DIR}" -am test
}

v9_starter_source_validation() {
  [[ -f "${STARTER_DIR}/src/main/java/io/qwenbridge/starter/QwenBridgeAutoConfiguration.java" ]] \
    && [[ -f "${STARTER_DIR}/src/main/java/io/qwenbridge/starter/QwenBridgeProperties.java" ]] \
    && [[ -f "${STARTER_DIR}/src/main/java/io/qwenbridge/starter/health/QwenBridgeHealthIndicator.java" ]] \
    && [[ -f "${STARTER_DIR}/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" ]] \
    && [[ -f "${STARTER_DIR}/README.md" ]] \
    && grep -q "QwenBridgeClient" "${STARTER_DIR}/src/main/java/io/qwenbridge/starter/QwenBridgeAutoConfiguration.java" \
    && grep -q "QwenBridgeStreamingClient" "${STARTER_DIR}/src/main/java/io/qwenbridge/starter/QwenBridgeAutoConfiguration.java"
}

v9_starter_tests_validation() {
  mvn -pl "${STARTER_DIR}" -am test
}

v9_starter_example_validation() {
  [[ -f "${STARTER_EXAMPLE_DIR}/src/main/resources/application.yml" ]] \
    && grep -q "qwenbridge:" "${STARTER_EXAMPLE_DIR}/src/main/resources/application.yml" \
    && grep -q "base-url" "${STARTER_EXAMPLE_DIR}/src/main/resources/application.yml" \
    && mvn -pl "${STARTER_EXAMPLE_DIR}" -am test
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
    && [[ -f "${TS_SDK_DIR}/examples/sync-analyze.ts" ]] \
    && [[ -f "${TS_SDK_DIR}/examples/typed-stream.ts" ]]
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
  npm --prefix "${TS_SDK_DIR}" install \
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

await Promise.race([
  streamPromise,
  new Promise((_, reject) => setTimeout(() => reject(new Error("Timed out waiting for TypeScript SDK SSE stream")), 65000))
]).catch(error => {
  if (!terminal) {
    throw error;
  }
});

if (payloadKinds.length === 0) {
  throw new Error("No typed SSE payloads received");
}

console.log(JSON.stringify({
  requestId,
  payloadKinds,
  terminal
}, null, 2));
NODE
}

v9_java_sdk_runtime_compile_validation() {
  mvn -pl "${JAVA_SDK_DIR}" -am test \
    && mvn -pl "${JAVA_SDK_EXAMPLE_DIR}" -am test
}

v9_release_docs_no_stale_v8_only_validation() {
  grep -qi "V9" docs/roadmap/V9.md \
    && grep -qi "TypeScript SDK" docs/release/V9-release-evidence.md \
    && grep -qi "Spring Boot Starter" docs/release/V9-release-evidence.md
}

create_v9_performance_k6_script() {
  mkdir -p scripts/performance

  cat > scripts/performance/v9-k6-load-test.js <<'K6'
import http from "k6/http";
import { check, sleep } from "k6";
import { randomItem, uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const totalRequests = Number(__ENV.PERF_TOTAL_REQUESTS || "1000000");
const vus = Number(__ENV.PERF_VUS || "250");
const maxDuration = __ENV.PERF_MAX_DURATION || "30m";

export const options = {
  scenarios: {
    mixed_language_users: {
      executor: "shared-iterations",
      vus,
      iterations: totalRequests,
      maxDuration
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<5000", "p(99)<15000"]
  }
};

const queries = [
  { lang: "en", query: "best gaming laptop under 1500 euro" },
  { lang: "sv", query: "bästa gaming laptop under 15000 kronor" },
  { lang: "fa", query: "بهترین لپ تاپ گیمینگ زیر ۱۵۰۰ یورو" },
  { lang: "de", query: "bester gaming laptop unter 1500 euro" },
  { lang: "fr", query: "meilleur ordinateur portable gaming moins de 1500 euros" },
  { lang: "es", query: "mejor portátil gaming por menos de 1500 euros" },
  { lang: "ar", query: "أفضل لابتوب ألعاب أقل من 1500 يورو" },
  { lang: "tr", query: "1500 euro altı en iyi oyun laptopu" },
  { lang: "nl", query: "beste gaming laptop onder 1500 euro" },
  { lang: "ja", query: "1500ユーロ以下の最高のゲーミングノートPC" }
];

export default function () {
  const selected = randomItem(queries);
  const requestId = `k6-v9-${selected.lang}-${uuidv4()}`;

  const response = http.post(
    `${baseUrl}/api/v1/search/analyze`,
    JSON.stringify({
      requestId,
      query: selected.query
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Request-ID": requestId,
        "X-Test-Language": selected.lang
      },
      timeout: "60s"
    }
  );

  check(response, {
    "status is 200": r => r.status === 200,
    "has request id": r => {
      try {
        return r.json("requestId") === requestId;
      } catch {
        return false;
      }
    },
    "has pipeline trace": r => {
      try {
        return Array.isArray(r.json("pipelineTrace"));
      } catch {
        return false;
      }
    }
  });

  sleep(0.01);
}
K6
}

v9_performance_script_validation() {
  create_v9_performance_k6_script
  [[ -f "scripts/performance/v9-k6-load-test.js" ]] \
    && grep -q "shared-iterations" scripts/performance/v9-k6-load-test.js \
    && grep -q "PERF_TOTAL_REQUESTS" scripts/performance/v9-k6-load-test.js \
    && grep -q "mixed_language_users" scripts/performance/v9-k6-load-test.js
}

v9_dockerized_k6_compose_validation() {
  create_v9_performance_k6_script

  compose_with_profiles production performance -- config >/tmp/qwenbridge-compose-expanded.yml || return 1

  compose_with_profiles production performance -- config --services \
    | tee /tmp/qwenbridge-compose-services.txt

  grep -qx "qwenbridge-k6" /tmp/qwenbridge-compose-services.txt \
    && grep -q "http://qwenbridge-app:8080" /tmp/qwenbridge-compose-expanded.yml \
    && grep -q "scripts/performance" /tmp/qwenbridge-compose-expanded.yml
}

v9_optional_performance_validation() {
  create_v9_performance_k6_script

  if [[ "${RUN_V9_PERFORMANCE}" != "true" ]]; then
    info "RUN_V9_PERFORMANCE=false, generated performance script but skipped execution."
    info "To run real dockerized k6:"
    info "RUN_V9_PERFORMANCE=true PERF_TOTAL_REQUESTS=1000000 PERF_VUS=250 PERF_MAX_DURATION=30m bash scripts/verify-release.sh"
    return 0
  fi

  info "Running dockerized k6 performance validation."
  info "K6 internal BASE_URL=http://qwenbridge-app:8080"
  info "PERF_TOTAL_REQUESTS=${PERF_TOTAL_REQUESTS}"
  info "PERF_VUS=${PERF_VUS}"
  info "PERF_MAX_DURATION=${PERF_MAX_DURATION}"

  compose_with_profiles production performance -- run --rm \
    -e BASE_URL="http://qwenbridge-app:8080" \
    -e PERF_TOTAL_REQUESTS="${PERF_TOTAL_REQUESTS}" \
    -e PERF_VUS="${PERF_VUS}" \
    -e PERF_MAX_DURATION="${PERF_MAX_DURATION}" \
    qwenbridge-k6
}
