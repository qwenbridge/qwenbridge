#!/usr/bin/env python3
"""Renders a benchmark run (report.json from run-benchmarks.py) as report.md (QB-224)."""
import json
import sys
from pathlib import Path


def f(value, digits=1):
    if value is None:
        return "—"
    if isinstance(value, float):
        return f"{value:.{digits}f}"
    return str(value)


def mb(value):
    return "—" if value is None else f"{value / 1e6:.0f}"


def main(path):
    report = json.loads(Path(path).read_text())
    lines = [f"# QwenBridge benchmark run {report['run_id']}", "",
             f"Profile `{report['profile']}`, target {report['base_url']}, {report.get('started')} → {report.get('finished', 'unfinished')}.",
             "All values are measurements from this run. Offered load is the configured VUs or arrival rate; achieved RPS is what was served.", "",
             "## Environment", ""]
    for key, value in report.get("environment", {}).items():
        lines.append(f"- **{key}**: {str(value).splitlines()[0] if value else '—'}")
    lines += ["", "## Phases", "",
              "| Phase | Offered | Requests | Achieved RPS | p50 ms | p95 ms | p99 ms | HTTP error rate | Dropped | Operational errors | Expected errors | Outcomes | CPU max | Heap max MB | Container MB | Redis cmds/req |",
              "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for p in report["phases"]:
        if "blocked" in p:
            lines.append(f"| {p['name']} | BLOCKED: {p['blocked']} |" + " |" * 14)
            continue
        lat, r, se = p["latency_ms"], p.get("resources", {}), p["server_errors"]
        redis = p["redis"]["commands_per_request"] if isinstance(p.get("redis"), dict) and "commands_per_request" in p["redis"] else ("n/a" if isinstance(p.get("redis"), str) else "—")
        outcomes = ", ".join(f"{k}:{v}" for k, v in sorted(p.get("outcomes", {}).items()))
        lines.append(f"| {p['name']} | {json.dumps(p['offered_load'])} | {p['requests']} | {f(p['achieved_rps'])} | {f(lat.get('med'))} | {f(lat.get('p(95)'))} | {f(lat.get('p(99)'))} | {f(p['http_error_rate'], 4)} | {p['dropped_iterations']} | {f(se.get('errors_operational'), 0)} | {f(se.get('errors_expected'), 0)} | {outcomes} | {f(r.get('process_cpu_max'), 2)} | {mb(r.get('heap_used_max_bytes'))} | {mb(r.get('container_memory_max_bytes'))} | {redis} |")
    lines += ["", "## Server-side stage latency (histogram deltas per phase)", "", "| Phase | Stage | Count | Mean ms | p50 ms | p95 ms |", "|---|---|---|---|---|---|"]
    for p in report["phases"]:
        for stage, s in sorted(p.get("stages", {}).items()):
            lines.append(f"| {p['name']} | {stage} | {s['count']} | {f(s['mean_ms'], 2)} | {f(s['p50_ms'], 2)} | {f(s['p95_ms'], 2)} |")
    workloads = [(p["name"], w, v) for p in report["phases"] for w, v in p.get("per_workload", {}).items()]
    if workloads:
        lines += ["", "## Per-workload latency (combined and outage phases)", "", "| Phase | Workload | p50 ms | p95 ms | p99 ms |", "|---|---|---|---|---|"]
        for name, w, v in workloads:
            lines.append(f"| {name} | {w} | {f(v['p50_ms'])} | {f(v['p95_ms'])} | {f(v['p99_ms'])} |")
    java = report.get("java", {})
    core = java.get("RetrievalBenchmarkTest", {})
    if "results" in core:
        lines += ["", "## Core retrieval engine (in-process, precomputed vectors)", "", "| Workload | Docs | Dims | Top-K | Concurrency | p50 ms | p95 ms | p99 ms | QPS |", "|---|---|---|---|---|---|---|---|---|"]
        for r in core["results"]:
            lines.append(f"| {r['workload']} | {r['documents']} | {r['dimensions']} | {r['topK']} | {r['concurrency']} | {f(r['p50Ms'], 3)} | {f(r['p95Ms'], 3)} | {f(r['p99Ms'], 3)} | {f(r['throughputQps'], 0)} |")
    ai = java.get("AiTransformationBenchmarkTest", {})
    if "results" in ai:
        lines += ["", "## AI and language stages", "", f"Providers: {json.dumps(ai.get('providers'))}", "",
                  "| Stage | Provider | Concurrency | Batch | Calls | Failures | p50 ms | p95 ms | Throughput/s |", "|---|---|---|---|---|---|---|---|---|"]
        for r in ai["results"]:
            lines.append(f"| {r['stage']} | {r['provider']} | {r['concurrency']} | {r['batch']} | {r['calls']} | {r['failures']} | {f(r['p50Ms'], 2)} | {f(r['p95Ms'], 2)} | {f(r['throughputPerSecond'], 1)} |")
    for name, value in java.items():
        if isinstance(value, dict) and "blocked" in value:
            lines.append(f"\n{name}: BLOCKED ({value['blocked']})")
    out = Path(path).with_suffix(".md")
    out.write_text("\n".join(lines) + "\n")
    print(f"Benchmark markdown: {out}")


if __name__ == "__main__":
    main(sys.argv[1])
