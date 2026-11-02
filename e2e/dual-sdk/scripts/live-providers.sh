#!/usr/bin/env bash
# QB-295 AUTHORIZED_LIVE_PROVIDER. Never simulated: a live result is PASS or FAIL only when a live
# call was made with explicitly authorized credentials; otherwise it is BLOCKED with the reason.
#   Shopify (an optional external target; QwenBridge has no Shopify production code): MOCK API via
#   provider-adapters/shopify_check.py against a local mock store; LIVE via the same read-only check,
#   with QWENBRIDGE_E2E_LIVE_AUTHORIZED=yes,
#   QWENBRIDGE_SHOPIFY_SANDBOX_ENDPOINT (https Admin GraphQL URL) and QWENBRIDGE_SHOPIFY_SANDBOX_TOKEN
#   (read_products).
#   Generic REST provider: LIVE read of one http-json feed page with QWENBRIDGE_E2E_LIVE_AUTHORIZED=yes,
#   QWENBRIDGE_E2E_REST_PROVIDER_URL and optional QWENBRIDGE_E2E_REST_PROVIDER_TOKEN.
# Credentials are read from the environment only and never printed.
set -uo pipefail
RUN="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${ROOT}" || exit 2
authorized="${QWENBRIDGE_E2E_LIVE_AUTHORIZED:-no}"
results=()
add() { results+=("{\"id\":\"$1\",\"level\":\"$2\",\"status\":\"$3\",\"detail\":\"$4\"}"); echo "$3 $1 ($2): $4"; }
if out="$(python3 -I "${ROOT}/e2e/dual-sdk/provider-adapters/shopify_check.py" mock 2>"${RUN}/shopify-mock.log")"; then
  add shopify.mock-api "MOCK API" PASS "read-only Admin GraphQL read against a local mock store: ${out//\"/}"
else
  add shopify.mock-api "MOCK API" FAIL "see shopify-mock.log"
fi
if [[ "${authorized}" == "yes" && "${QWENBRIDGE_SHOPIFY_SANDBOX_ENDPOINT:-}" == https://* && -n "${QWENBRIDGE_SHOPIFY_SANDBOX_TOKEN:-}" ]]; then
  if out="$(python3 -I "${ROOT}/e2e/dual-sdk/provider-adapters/shopify_check.py" live 2>"${RUN}/shopify-live.log")"; then
    add shopify.live "LIVE SHOPIFY API" PASS "read-only read of the authorized test store: ${out//\"/}"
  else
    add shopify.live "LIVE SHOPIFY API" FAIL "see shopify-live.log"
  fi
else
  add shopify.live "LIVE SHOPIFY API" BLOCKED "no explicit authorization and test-store credentials (QWENBRIDGE_E2E_LIVE_AUTHORIZED, QWENBRIDGE_SHOPIFY_SANDBOX_*)"
fi
if [[ "${authorized}" == "yes" && -n "${QWENBRIDGE_E2E_REST_PROVIDER_URL:-}" ]]; then
  if python3 -I - <<'PY'
import json, os, urllib.request
req = urllib.request.Request(os.environ["QWENBRIDGE_E2E_REST_PROVIDER_URL"])
if os.environ.get("QWENBRIDGE_E2E_REST_PROVIDER_TOKEN"):
    req.add_header("Authorization", "Bearer " + os.environ["QWENBRIDGE_E2E_REST_PROVIDER_TOKEN"])
with urllib.request.urlopen(req, timeout=30) as r:
    page = json.loads(r.read())
assert isinstance(page.get("changes"), list) and "hasMore" in page
PY
  then add rest.live "LIVE REST PROVIDER" PASS "one http-json feed page read"
  else add rest.live "LIVE REST PROVIDER" FAIL "the provider did not return an http-json feed page"; fi
else
  add rest.live "LIVE REST PROVIDER" BLOCKED "no explicit authorization and provider URL (QWENBRIDGE_E2E_REST_PROVIDER_URL)"
fi
(IFS=,; echo "{\"checks\":[${results[*]}]}") >"${RUN}/live-providers.json"
grep -q '"status":"FAIL"' "${RUN}/live-providers.json" && exit 1 || exit 0
