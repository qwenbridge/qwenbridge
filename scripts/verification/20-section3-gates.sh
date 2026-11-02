#!/usr/bin/env bash
# Section 3, Phase 30 (QB-224): regression gates against approved baselines (evaluation/baselines).

section3_regression_gate_validation() {
  local perf="${VERIFY_LOG_DIR:-logs/verification}/performance-${VERIFY_RUN_ID}/results.json"
  local args=(evaluation/baselines/baseline.json)
  [[ -f "${perf}" ]] && args+=(--perf "${perf}")
  python3 scripts/verification/regression_gate.py "${args[@]}"
}

# Scenario J: the gate must report an injected, measured slowdown (5 ms added to every keyword
# call in the Core benchmark, core-retrieval-slowed.json) as a failure.
section3_regression_gate_detects_slowdown_validation() {
  python3 scripts/verification/regression_gate.py evaluation/baselines/baseline.json \
    --substitute qwenbridge-core/target/benchmarks/core-retrieval.json=qwenbridge-core/target/benchmarks/core-retrieval-slowed.json \
    --expect-fail core.keyword.10k.c8.p95
}
