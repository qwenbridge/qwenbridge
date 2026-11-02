#!/usr/bin/env bash
# Section 3, Phase 27 (QB-186 – QB-191): both SDKs against the running Docker application, and the
# TypeScript SDK on the oldest supported Node.js. Each check returns non-zero on failure.

# The Java SDK's v2 client: compatibility, Unicode search, 1.0 stream fields, typed errors, cancel.
section3_java_sdk_live_commerce_validation() {
  local dir
  dir="$(section1_work_dir)" || return 1
  local cp_file="${dir}/java-sdk-classpath.txt"
  if [[ ! -s "${cp_file}" ]]; then
    mvn -B -q install -pl qwenbridge-java-sdk -am -DskipTests || return 1
    mvn -B -q dependency:build-classpath -pl qwenbridge-java-sdk \
      -Dmdep.outputFile="${cp_file}" || return 1
  fi
  local sdk_jar
  sdk_jar="$(ls qwenbridge-java-sdk/target/qwenbridge-java-sdk-*.jar | grep -v -- '-sources\|-javadoc' | head -1)"
  [[ -n "${sdk_jar}" ]] || return 1
  BASE_URL="${BASE_URL}" QWENBRIDGE_TEST_API_TOKEN="${QWENBRIDGE_TEST_API_TOKEN:-}" \
    java -cp "${sdk_jar}:$(cat "${cp_file}")" scripts/verification/sdk/JavaSdkLiveCommerce.java
}

# The TypeScript SDK's v2 client against the same server.
section3_typescript_sdk_live_commerce_validation() {
  [[ -f qwenbridge-typescript-sdk/dist/index.js ]] || (cd qwenbridge-typescript-sdk && npm run build >/dev/null) || return 1
  BASE_URL="${BASE_URL}" QWENBRIDGE_TEST_API_TOKEN="${QWENBRIDGE_TEST_API_TOKEN:-}" \
    node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
import { CommerceClient, QwenBridgeApiError } from './qwenbridge-typescript-sdk/dist/index.js';
const client = new CommerceClient({ baseUrl: process.env.BASE_URL, bearerToken: process.env.QWENBRIDGE_TEST_API_TOKEN || undefined });
const version = await client.requireCompatibleServer();
const requestId = `sdk-live-ts-${Date.now()}`;
const response = await client.search({ query: 'black Nike shoes size 42 under 100 EUR, not refurbished, in stock', requestId });
assert.equal(response.apiVersion, 2); assert.equal(response.requestId, requestId); assert.equal(response.status, 'OK');
const unicode = await client.search({ query: 'کفش مشکی Nike سایز ۴۲' }); assert.ok(unicode.status);
const events = []; await client.stream({ query: 'black Nike shoes size 42' }, e => events.push(e));
assert.deepEqual(events.map(e => e.sequence), [1, 2]); assert.equal(events[1].terminal, true); assert.match(events[1].traceId, /^[0-9a-f]{32}$/);
assert.throws(() => client.search({ query: 'shoes', requestId: 'bad id' }), /Invalid request identifier/);
await assert.rejects(client.search({ query: 'shoes', limit: 1000 }), error => error instanceof QwenBridgeApiError && error.status === 400 && error.category === 'VALIDATION');
assert.equal(await client.cancel('sdk-live-not-in-flight'), false);
console.log(`PASS: TypeScript SDK v2 client against ${version.version}: compatibility, Unicode search, 1.0 stream fields, typed errors, cancellation`);
NODE
}

# The built TypeScript SDK on Node.js 20 (engines: >=20), offline, SDK mounted read-only.
section3_typescript_sdk_node20_validation() {
  [[ -f qwenbridge-typescript-sdk/dist/index.js ]] || (cd qwenbridge-typescript-sdk && npm run build >/dev/null) || return 1
  docker run --rm --network none \
    -v "${PWD}/qwenbridge-typescript-sdk:/sdk:ro" \
    -v "${PWD}/scripts/verification/sdk:/check:ro" \
    node:20-alpine node /check/ts-sdk-node20-check.mjs
}

# QB-201: the Playground's strategy comparison against the real server: the same query under each
# strategy is executed, and every strategy either returns results or a typed refusal (never a
# made-up result). Prints the ranked ids per strategy as evidence.
section3_strategy_comparison_validation() {
  [[ -f qwenbridge-typescript-sdk/dist/index.js ]] || (cd qwenbridge-typescript-sdk && npm run build >/dev/null) || return 1
  BASE_URL="${BASE_URL}" QWENBRIDGE_TEST_API_TOKEN="${QWENBRIDGE_TEST_API_TOKEN:-}" \
    node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
import { CommerceClient, QwenBridgeApiError } from './qwenbridge-typescript-sdk/dist/index.js';
const client = new CommerceClient({ baseUrl: process.env.BASE_URL, bearerToken: process.env.QWENBRIDGE_TEST_API_TOKEN || undefined });
const query = 'black Nike shoes size 42 under 100 EUR, not refurbished, in stock';
const strategies = [['keyword', { mode: 'KEYWORD' }], ['vector', { mode: 'VECTOR' }], ['hybrid', { mode: 'HYBRID' }], ['planned', { contract: 'commerce-search/1.0', mode: null }]];
const lines = []; let ran = 0;
for (const [name, extra] of strategies) {
  try {
    const r = await client.search({ query, ...extra });
    assert.equal(r.apiVersion, 2); ran++;
    lines.push(`${name}: ${r.status} [${r.results.hits.map(h => h.id).join(', ')}]${r.retrieval ? ` executed=${r.retrieval.executed}` : ''}`);
  } catch (error) {
    assert.ok(error instanceof QwenBridgeApiError, `${name}: ${error}`);
    lines.push(`${name}: unavailable (${error.code ?? error.status}: ${error.message})`);
  }
}
assert.ok(ran >= 1, 'at least one strategy must run');
console.log(lines.join('\n'));
console.log(`PASS: ${ran}/${strategies.length} strategies executed on the live server; the rest refused with typed errors`);
NODE
}
