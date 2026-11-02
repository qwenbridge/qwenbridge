#!/usr/bin/env bash
# Section 3, Phase 30 (QB-224): regression gates against approved baselines (evaluation/baselines).

section3_regression_gate_validation() {
  local perf="${VERIFY_LOG_DIR:-logs/verification}/performance-${VERIFY_RUN_ID}/results.json"
  local args=(evaluation/baselines/baseline.json)
  [[ -f "${perf}" ]] && args+=(--perf "${perf}")
  python3 scripts/verification/regression_gate.py "${args[@]}"
}

# Scenario J: the gate must report an injected, measured slowdown (a fixed delay added to every
# keyword call in the Core benchmark, core-retrieval-slowed.json) as a failure. On the baseline's
# hardware the slowed run is compared with the approved baseline; on other hardware
# (QWENBRIDGE_PERF_HARDWARE) with the unslowed measurement of the same run.
section3_regression_gate_detects_slowdown_validation() {
  local core=qwenbridge-core/target/benchmarks/core-retrieval.json
  local args=(evaluation/baselines/baseline.json
    --substitute "${core}=qwenbridge-core/target/benchmarks/core-retrieval-slowed.json")
  if [[ -n "${QWENBRIDGE_PERF_HARDWARE:-}" ]] \
    && [[ "${QWENBRIDGE_PERF_HARDWARE}" != "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("hardware", ""))' evaluation/baselines/baseline.json)" ]]; then
    args+=(--rebase "${core}=${core}")
  fi
  python3 scripts/verification/regression_gate.py "${args[@]}" --expect-fail core.keyword.10k.c8.p95
}
