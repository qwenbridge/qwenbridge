#!/usr/bin/env bash
# QB-295: the dual-SDK end-to-end suite (e2e/dual-sdk). The independent Java and TypeScript SDK
# consumers run the shared scenarios against the real packaged Quarkus server:
#   OFFLINE_BASELINE (mandatory): deterministic local catalog and model stand-in, no external
#     service, no database, no Redis;
#   LOCAL_PROVIDER_INTEGRATION: catalog synchronization from a local HTTP provider (needs the
#     local Redis for the sync checkpoint; BLOCKED, not failed, without it);
#   AUTHORIZED_LIVE_PROVIDER: Shopify mock API always; live providers only with explicit
#     authorization and credentials, otherwise BLOCKED.
# The suite starts and stops only its own processes (ports QB295_PORT..+3, default 18295).

qb295_dual_sdk_e2e_validation() {
  local report
  report="$(section1_work_dir)/qb295" || return 1
  rm -rf "${report}" && mkdir -p "${report}" || return 1
  bash e2e/dual-sdk/scripts/run-dual-sdk-e2e.sh \
    --mode OFFLINE_BASELINE --mode LOCAL_PROVIDER_INTEGRATION --mode AUTHORIZED_LIVE_PROVIDER \
    --report-dir "${report}"
  local status=$?
  python3 -I - "${report}/report.json" <<'PY' || true
import json, sys
r = json.load(open(sys.argv[1]))
print("summary:", json.dumps(r["summary"]))
for c in r["provider"] + r["live"]:
    print(f"  {c['status']:8} {c['id']}")
PY
  return "${status}"
}
