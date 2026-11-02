#!/usr/bin/env python3
"""Performance and quality regression gate (QB-224).

    regression_gate.py BASELINE [--perf RESULTS_JSON] [--bench BENCH_REPORT_JSON] [--substitute FROM=TO ...] [--expect-fail GATE_ID]

Compares current measurements with approved baselines and exits 1 if any gate fails. A gate whose
input is missing is reported as NOT MEASURED and fails too (nothing passes silently); performance
gates for another workload than the baseline's are reported as NOT GATED. --expect-fail inverts
the result for one gate: used to prove the gate catches an injected slowdown (Scenario J).
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def dig(data, path):
    for part in path.split("."):
        data = data[part]
    return data


def worse(gate, current):
    base = gate["baseline"]
    delta = (base - current) if gate["direction"] == "higher" else (current - base)
    if "max_regression_abs" in gate:
        return delta > gate["max_regression_abs"] + 1e-12, f"{delta:+.4f} (limit {gate['max_regression_abs']})"
    pct = 100 * delta / base if base else (float("inf") if delta > 0 else 0)
    return pct > gate["max_regression_pct"], f"{pct:+.1f} % (limit {gate['max_regression_pct']} %)"


def evaluate(baseline, perf_results, substitutes, bench=None):
    rows = []
    for gate in baseline["gates"]:
        row = {"id": gate["id"], "baseline": gate["baseline"], "source": gate.get("source", "")}
        try:
            if gate["kind"] in ("file", "core"):
                path = substitutes.get(gate["file"], gate["file"])
                data = json.loads((ROOT / path).read_text())
                if gate["kind"] == "file":
                    current = dig(data, gate["path"])
                else:
                    match = [r for r in data["results"] if all(r.get(k) == v for k, v in gate["select"].items())]
                    if not match:
                        raise KeyError("no result for " + json.dumps(gate["select"]))
                    current = match[0][gate["field"]]
            elif gate["kind"] == "bench":
                if bench is None:
                    row.update(status="NOT GATED", detail="no benchmark-suite report given (--bench)")
                    rows.append(row)
                    continue
                phase = next((p for p in bench["phases"] if p.get("name") == gate["phase"]), None)
                if phase is None or "blocked" in phase:
                    raise KeyError("phase " + gate["phase"] + " missing or blocked")
                current = phase
                for part in gate["field"].split("."):
                    current = current[part]
            elif perf_results is None:
                row.update(status="NOT GATED", detail="no performance results given")
                rows.append(row)
                continue
            elif gate["kind"] == "perf-all":
                current = max((p.get(gate["field"]) or 0) for p in perf_results["phases"].values())
            else:
                phase = perf_results["phases"].get(gate["phase"])
                if phase is None or any(phase.get(k) != v for k, v in gate["workload"].items()):
                    row.update(status="NOT GATED", detail="no approved baseline for this workload")
                    rows.append(row)
                    continue
                current = phase[gate["field"]]
        except (OSError, KeyError, ValueError, TypeError) as e:
            row.update(status="NOT MEASURED", detail=str(e))
            rows.append(row)
            continue
        failed, detail = worse(gate, current)
        row.update(current=current, status="FAIL" if failed else "PASS", detail=detail)
        rows.append(row)
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline")
    parser.add_argument("--perf")
    parser.add_argument("--bench")
    parser.add_argument("--substitute", action="append", default=[])
    parser.add_argument("--expect-fail")
    args = parser.parse_args()
    baseline = json.loads(Path(args.baseline).read_text())
    perf_results = json.loads(Path(args.perf).read_text()) if args.perf else None
    bench = json.loads(Path(args.bench).read_text()) if args.bench else None
    substitutes = dict(s.split("=", 1) for s in args.substitute)
    rows = evaluate(baseline, perf_results, substitutes, bench)
    for r in rows:
        current = r.get("current")
        shown = f"{current:.4f}" if isinstance(current, float) else str(current)
        print(f"{r['status']:<12} {r['id']:<36} baseline={r['baseline']} current={shown} {r.get('detail', '')}")
    failing = [r for r in rows if r["status"] in ("FAIL", "NOT MEASURED")]
    if args.expect_fail:
        target = [r for r in rows if r["id"] == args.expect_fail]
        caught = bool(target) and target[0]["status"] == "FAIL"
        print(f"Scenario J: injected regression in {args.expect_fail} {'detected' if caught else 'NOT detected'}")
        return 0 if caught else 1
    print(f"Regression gate: {len(failing)} failing of {len(rows)}")
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main())
