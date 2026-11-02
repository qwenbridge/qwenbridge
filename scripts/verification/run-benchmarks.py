#!/usr/bin/env python3
"""QwenBridge 1.0 benchmark suite (QB-216 - QB-224).

Runs real workloads against the running Docker application and records every result with its
workload definition and environment. Nothing here is estimated: phases that cannot run are reported
as BLOCKED with the reason.

    BENCH_PROFILE=full|smoke   durations (smoke = a few seconds per phase, to check the harness)
    BASE_URL                   default http://localhost:8080
    BENCH_INCLUDE_JAVA=true    also run the Core retrieval and AI transformation benchmarks

Docker safety: outage phases only `docker stop` and `docker start` the existing service containers
(always restarted in a finally block); nothing is removed or reconfigured.
"""
import importlib.util
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("perf", ROOT / "scripts/verification/run-performance.py")
perf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perf)

PROFILE = os.environ.get("BENCH_PROFILE", "full")
# BENCH_ONLY=strategy,spike,soak,stress,model,outage,redis,java runs a subset (default: all).
ONLY = {g for g in os.environ.get("BENCH_ONLY", "").split(",") if g}


def want(group):
    return not ONLY or group in ONLY
FULL = PROFILE == "full"
BASE = os.environ.get("BASE_URL", "http://localhost:8080")
APP, REDIS, OLLAMA, OPENSEARCH = (os.environ.get(k, d) for k, d in [
    ("APP_CONTAINER", "qwenbridge-app"), ("REDIS_CONTAINER", "qwenbridge-redis"),
    ("OLLAMA_CONTAINER", "qwenbridge-ollama"), ("OPENSEARCH_CONTAINER", "qwenbridge-opensearch")])


def seconds(full, smoke):
    return f"{full if FULL else smoke}s"


def healthy(url, timeout=180):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=5) as r:
                if r.status == 200:
                    return True
        except OSError:
            pass
        time.sleep(2)
    return False


def container_healthy(name, timeout=240):
    deadline = time.time() + timeout
    while time.time() < deadline:
        state = subprocess.run(["docker", "inspect", "--format", "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}", name],
                               capture_output=True, text=True).stdout.strip()
        if state.startswith("running") and (state.endswith("|healthy") or state.endswith("|")):
            return True
        time.sleep(3)
    return False


def app_settings():
    """The running app's compose-interpolated settings, so it can be recreated exactly."""
    env = dict(line.split("=", 1) for line in subprocess.check_output(
        ["docker", "inspect", "--format", "{{range .Config.Env}}{{.}}\n{{end}}", APP], text=True).splitlines() if "=" in line)
    port = subprocess.run(["docker", "port", APP, "8080/tcp"], capture_output=True, text=True).stdout.strip().rsplit(":", 1)[-1] or "8080"
    return {"APP_PORT": port, "OPENSEARCH_INDEX": env.get("QWENBRIDGE_SEARCH_OPENSEARCH_INDEX", "qwenbridge-products"),
            "QWENBRIDGE_ANALYSIS_CACHE_KEY_PREFIX": env.get("QWENBRIDGE_ANALYSIS_CACHE_KEY_PREFIX", "qwenbridge:analysis"),
            "QWENBRIDGE_AI_OLLAMA_BASE_URL": env.get("QWENBRIDGE_AI_OLLAMA_BASE_URL", "http://qwenbridge-ollama:11434"),
            "JAVA_TOOL_OPTIONS": env.get("JAVA_TOOL_OPTIONS", ""),
            "QWENBRIDGE_ABUSE_PER_IP_LIMIT": env.get("QWENBRIDGE_ABUSE_PER_IP_LIMIT", "60"),
            "QWENBRIDGE_ABUSE_AI_REQUEST_QUOTA": env.get("QWENBRIDGE_ABUSE_AI_REQUEST_QUOTA", "120")}


def recreate_app(settings):
    """Recreates only the app container with the given settings (as verify-performance.sh does)."""
    env = os.environ.copy()
    # The benchmark suite measures the OpenSearch connector path (QB-293: not the default backend).
    env.setdefault("QWENBRIDGE_COMMERCE_BACKEND", "opensearch")
    env.setdefault("QWENBRIDGE_SEARCH_BACKEND", "opensearch")
    env.update(settings)
    profiles = [a for p in os.environ.get("COMPOSE_PROFILE", "production,opensearch").split(",") if p
                for a in ("--profile", p)]
    subprocess.run(["docker", "compose", "-f", os.environ.get("COMPOSE_FILE", "docker-compose.yml"), *profiles,
                    "up", "-d", "--no-deps", "qwenbridge-app"],
                   env=env, cwd=ROOT, check=True, capture_output=True)
    return healthy(BASE + "/q/health/ready", 240)


def stage_histograms(base):
    """Cumulative bucket counts of qwenbridge_search_stage_seconds per stage (all contracts)."""
    with urllib.request.urlopen(base.rstrip("/") + "/q/metrics", timeout=10) as r:
        body = r.read().decode()
    out = {}
    for line in body.splitlines():
        m = re.match(r'qwenbridge_search_stage_seconds_(bucket|count|sum)\{([^}]*)\} (\S+)', line)
        if not m:
            continue
        labels = dict(re.findall(r'(\w+)="([^"]*)"', m.group(2)))
        stage = out.setdefault(labels.get("stage", "?"), {"buckets": {}, "count": 0.0, "sum": 0.0})
        value = float(m.group(3))
        if m.group(1) == "bucket":
            le = float("inf") if labels["le"] == "+Inf" else float(labels["le"])
            stage["buckets"][le] = stage["buckets"].get(le, 0.0) + value
        else:
            stage[m.group(1)] += value
    return out


def stage_breakdown(before, after):
    """Per-stage count, mean and histogram p50/p95 (ms) of the requests in a phase."""
    result = {}
    for stage, a in after.items():
        b = before.get(stage, {"buckets": {}, "count": 0.0, "sum": 0.0})
        count = a["count"] - b["count"]
        if count <= 0:
            continue
        cumulative = sorted((le, a["buckets"][le] - b["buckets"].get(le, 0.0)) for le in a["buckets"])
        def quantile(q):
            target, prev_le, prev_c = q * count, 0.0, 0.0
            for le, c in cumulative:
                if c >= target:
                    if le == float("inf"):
                        return None
                    width = le - prev_le
                    return round(1000 * (prev_le + (width * (target - prev_c) / (c - prev_c) if c > prev_c else 0)), 2)
                prev_le, prev_c = le, c
            return None
        result[stage] = {"count": int(count), "mean_ms": round(1000 * (a["sum"] - b["sum"]) / count, 2),
                         "p50_ms": quantile(0.5), "p95_ms": quantile(0.95)}
    return result


def safe(fn, default):
    """A metrics read that may time out under saturation; a failed read is recorded, not fatal."""
    for _ in range(3):
        try:
            return fn()
        except (OSError, ValueError):
            time.sleep(2)
    return default


def drain_model(limit_s=300):
    """Waits until the model server answers a tiny embedding within 1 s (no queued work from
    earlier phases: requests abandoned by client timeouts keep running in Ollama). Returns seconds."""
    url = os.environ.get("OLLAMA_URL", "http://localhost:11434") + "/api/embed"
    body = json.dumps({"model": os.environ.get("BENCH_EMBEDDING_MODEL", "bge-m3"), "input": "drain"}).encode()
    started = time.time()
    while time.time() - started < limit_s:
        t0 = time.time()
        try:
            urllib.request.urlopen(urllib.request.Request(url, body, {"Content-Type": "application/json"}), timeout=120).read()
            if time.time() - t0 < 1:
                return round(time.time() - started, 1)
        except OSError:
            time.sleep(2)
    return None


def k6_phase(folder, name, description, env_extra, offered):
    """Runs one k6 workload with resource sampling and server-side deltas."""
    drained = drain_model() if os.environ.get("BENCH_DRAIN", "true") == "true" else None
    summary, log = folder / f"{name}.json", folder / f"{name}.log"
    env = os.environ.copy()
    env.update({"BASE_URL": BASE, **env_extra})
    args = ["k6", "run", "--quiet", "--summary-export", str(summary)]
    for key, value in env_extra.items():
        args += ["-e", f"{key}={value}"]
    args += ["-e", f"BASE_URL={BASE}", "scripts/performance/k6-bench.js"]
    before_server, before_stages, before_redis = safe(lambda: perf.prometheus(BASE), {}), safe(lambda: stage_histograms(BASE), {}), perf.redis_stats(REDIS)
    sampler = perf.ResourceSampler(BASE, APP)
    sampler.start()
    started = time.time()
    print(f"BENCH {name}: {description}", flush=True)
    with log.open("w") as out:
        rc = subprocess.run(args, env=env, stdout=out, stderr=subprocess.STDOUT, cwd=ROOT).returncode
    sampler.stop_event.set()
    sampler.join(timeout=30)
    data = json.loads(summary.read_text()) if summary.exists() else {}
    m = data.get("metrics", {})
    def metric(key):
        item = m.get(key, {})
        return item.get("values", item)
    duration = metric("http_req_duration")
    after_server, after_redis = safe(lambda: perf.prometheus(BASE), None), perf.redis_stats(REDIS)
    phase = {
        "name": name, "description": description, "offered_load": offered, "k6_exit": rc,
        "model_backlog_drain_s": drained,
        "duration_s": round(time.time() - started, 1),
        "requests": int(metric("http_reqs").get("count", 0)), "achieved_rps": metric("http_reqs").get("rate"),
        "dropped_iterations": int(metric("dropped_iterations").get("count", 0)),
        "http_error_rate": metric("http_req_failed").get("rate", metric("http_req_failed").get("value")),
        "latency_ms": {k: duration.get(k) for k in ("med", "p(95)", "p(99)", "max", "avg")},
        "outcomes": {k.split(":", 1)[1].rstrip("}"): int(v.get("count", 0)) for k, v in m.items()
                     if k.startswith("bench_outcomes{outcome:") and v.get("count")},
        "per_workload": {k.split(":", 1)[1].rstrip("}"): {"p50_ms": v.get("med"), "p95_ms": v.get("p(95)"), "p99_ms": v.get("p(99)")}
                         for k, v in m.items() if k.startswith("http_req_duration{workload:")},
        "server_errors": ({k: after_server.get(k, 0) - before_server.get(k, 0) for k in ("errors_expected", "errors_operational", "interruptions")}
                          if after_server is not None else "metrics scrape timed out"),
        "stages": stage_breakdown(before_stages, safe(lambda: stage_histograms(BASE), {})),
        "resources": sampler.summary(),
        "redis": ("not applicable: Redis not running" if before_redis is None or after_redis is None else
                  {k: after_redis.get(k, 0) - before_redis.get(k, 0) for k in after_redis}),
    }
    if isinstance(phase["redis"], dict) and phase["requests"]:
        phase["redis"]["commands_per_request"] = round(phase["redis"].get("total_commands_processed", 0) / phase["requests"], 2)
    print(json.dumps({k: phase[k] for k in ("name", "requests", "achieved_rps", "dropped_iterations", "http_error_rate", "latency_ms", "outcomes")}), flush=True)
    return phase


def outage(folder, name, container, workloads, window):
    """Before / during / after a dependency outage, with the same workload in each window."""
    phases = []
    try:
        for label in ("before", "during", "after"):
            if label == "during":
                subprocess.run(["docker", "stop", container], check=True, capture_output=True)
            if label == "after":
                subprocess.run(["docker", "start", container], check=True, capture_output=True)
                if not container_healthy(container):
                    phases.append({"name": f"{name}-after", "blocked": f"{container} did not become healthy"})
                    return phases
                time.sleep(5)
            for mode in workloads:
                phases.append(k6_phase(folder, f"{name}-{label}-{mode.lower()}", f"{mode} at 5 rps, {label} {container} outage",
                                       {"BENCH_EXECUTOR": "rate", "BENCH_RATE": "5", "BENCH_DURATION": window, "BENCH_MODE": mode, "BENCH_POOL": "retrieval"},
                                       {"arrival_rate_per_s": 5}))
            auth = subprocess.run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "-H", "Authorization: Bearer invalid-credential",
                                   "-H", "Content-Type: application/json", "-d", '{"query":"shoes"}', BASE + "/api/v2/search"],
                                  capture_output=True, text=True).stdout
            phases[-1]["invalid_credential_status"] = auth
    finally:
        subprocess.run(["docker", "start", container], capture_output=True)
        container_healthy(container)
    return phases


def standalone(folder, name, extra_env, port):
    """The packaged server as a separate process (Redis-off / Redis-unavailable configurations)."""
    jar = ROOT / "qwenbridge-server/target/quarkus-app/quarkus-run.jar"
    if not jar.exists():
        return {"name": name, "blocked": "packaged server missing; run the master verification first"}
    app_env = dict(line.split("=", 1) for line in subprocess.check_output(
        ["docker", "inspect", "--format", "{{range .Config.Env}}{{.}}\n{{end}}", APP], text=True).splitlines() if "=" in line)
    env = os.environ.copy()
    env.update({
        "QUARKUS_HTTP_PORT": str(port), "QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED": "false",
        "QWENBRIDGE_AI_OLLAMA_BASE_URL": "http://localhost:11434", "QWENBRIDGE_AI_OLLAMA_CHAT_MODEL": app_env.get("QWENBRIDGE_AI_OLLAMA_CHAT_MODEL", "qwen2.5"),
        "QWENBRIDGE_AI_OLLAMA_EMBEDDING_MODEL": app_env.get("QWENBRIDGE_AI_OLLAMA_EMBEDDING_MODEL", "bge-m3"),
        "QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL": "http://localhost:9200",
        "QWENBRIDGE_SEARCH_OPENSEARCH_INDEX": app_env.get("QWENBRIDGE_SEARCH_OPENSEARCH_INDEX", "qwenbridge-products"),
        "QWENBRIDGE_ABUSE_PROTECTION_REDIS_ENABLED": "false", "QWENBRIDGE_PLATFORM_STORE": "memory",
        **extra_env})
    log = (folder / f"{name}-server.log").open("w")
    process = subprocess.Popen(["java", "-jar", str(jar)], env=env, stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
    global BASE
    saved = BASE
    try:
        BASE = f"http://localhost:{port}"
        if not healthy(BASE + "/q/health/live"):
            return {"name": name, "blocked": "standalone server did not start"}
        phase = k6_phase(folder, name, f"v1 analyze, 20 distinct queries at 1 VU ({name})",
                         {"BENCH_EXECUTOR": "iterations", "BENCH_ITERATIONS": "20", "BENCH_VUS": "1", "BENCH_MODE": "ANALYZE"},
                         {"vus": 1, "iterations": 20})
        phase["configuration"] = extra_env
        return phase
    finally:
        BASE = saved
        process.terminate()
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()


def java_benchmarks(folder):
    out = {}
    env = os.environ.copy()
    env.update({"QWENBRIDGE_RUN_BENCHMARKS": "true", "QWENBRIDGE_BENCHMARK_PROFILE": "full" if FULL else "quick", "QWENBRIDGE_RUN_OLLAMA_IT": "true", "QWENBRIDGE_RUN_AI_BENCHMARKS": "true"})
    for module, test, file in [("qwenbridge-core", "RetrievalBenchmarkTest", "core-retrieval.json"),
                               ("qwenbridge-server", "AiTransformationBenchmarkTest", "ai-transformations.json")]:
        log = folder / f"{test}.log"
        with log.open("w") as handle:
            rc = subprocess.run(["mvn", "-B", "-o", "test", "-pl", module, f"-Dtest={test}"], env=env, cwd=ROOT,
                                stdout=handle, stderr=subprocess.STDOUT).returncode
        result = ROOT / module / "target/benchmarks" / file
        out[test] = json.loads(result.read_text()) if rc == 0 and result.exists() else {"blocked": f"exit {rc}; see {log.name}"}
    return out


def main():
    run_id = os.environ.get("BENCH_RUN_ID") or time.strftime("bench-%Y%m%d-%H%M%S")
    folder = ROOT / "logs" / "benchmarks" / run_id
    folder.mkdir(parents=True, exist_ok=True)
    if not healthy(BASE + "/q/health/live", 20):
        print(f"BLOCKED: {BASE} is not running; start the stack (master verification) first", file=sys.stderr)
        return 2
    original = app_settings()
    raised = {**original, "QWENBRIDGE_ABUSE_PER_IP_LIMIT": "100000000", "QWENBRIDGE_ABUSE_AI_REQUEST_QUOTA": "100000000"}
    print("Raising the application rate limits for the benchmark (restored afterwards).", flush=True)
    if not recreate_app(raised):
        print("BLOCKED: the application did not become ready with benchmark limits", file=sys.stderr)
        recreate_app(original)
        return 2
    try:
        return run(run_id, folder, raised)
    finally:
        print("Restoring normal application rate limits.", flush=True)
        recreate_app(original)


def run(run_id, folder, settings):
    report = {"run_id": run_id, "app_settings": {k: v for k, v in settings.items() if k != "JAVA_TOOL_OPTIONS"}, "profile": PROFILE, "base_url": BASE, "started": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
              "environment": perf.environment(APP), "phases": []}
    phases = report["phases"]
    def save():
        (folder / "report.json").write_text(json.dumps(report, indent=2) + "\n")

    # Warm the analysis cache with every pool query, so retrieval phases measure retrieval.
    if want("strategy") or want("spike") or want("soak") or want("stress") or want("model") or want("outage"):
      phases.append(k6_phase(folder, "warm-up", "each of the 31 multilingual pool queries once (analysis cache fill)",
                           {"BENCH_EXECUTOR": "iterations", "BENCH_ITERATIONS": "31", "BENCH_VUS": "1", "BENCH_MODE": "KEYWORD"}, {"vus": 1}))
      phases.append(k6_phase(folder, "warm-up-retrieval", "retrieval pool once",
                           {"BENCH_EXECUTOR": "iterations", "BENCH_ITERATIONS": "6", "BENCH_VUS": "1", "BENCH_MODE": "KEYWORD", "BENCH_POOL": "retrieval"}, {"vus": 1}))
    save()
    # QB-216 - QB-218: each strategy at low / medium / high concurrency on the retrieval pool
    # (English queries that need no translation), warm analysis cache: retrieval and query embedding.
    for mode in ("KEYWORD", "VECTOR", "HYBRID") if want("strategy") else ():
        for vus in (5, 20, 50):
            phases.append(k6_phase(folder, f"strategy-{mode.lower()}-{vus}vu", f"{mode} search, constant {vus} VUs, retrieval pool, warm cache",
                                   {"BENCH_EXECUTOR": "vus", "BENCH_VUS": str(vus), "BENCH_DURATION": seconds(20, 4), "BENCH_MODE": mode, "BENCH_POOL": "retrieval"}, {"vus": vus}))
            save()
    # QB-221: spike (baseline, spike, recovery), soak, stress on keyword retrieval.
    for label, rate, dur in (("spike-baseline", 20, 30), ("spike-peak", 300, 30), ("spike-recovery", 20, 30)) if want("spike") else ():
        phases.append(k6_phase(folder, label, f"KEYWORD at {rate} requests/s, retrieval pool", {"BENCH_EXECUTOR": "rate", "BENCH_RATE": str(rate),
                               "BENCH_DURATION": seconds(dur, 4), "BENCH_MODE": "KEYWORD", "BENCH_POOL": "retrieval", "BENCH_MAXVUS": "1000"}, {"arrival_rate_per_s": rate}))
        save()
    if want("soak"):
      phases.append(k6_phase(folder, "soak", "KEYWORD at 50 requests/s for 10 minutes, retrieval pool", {"BENCH_EXECUTOR": "rate", "BENCH_RATE": "50",
                           "BENCH_DURATION": seconds(600, 10), "BENCH_MODE": "KEYWORD", "BENCH_POOL": "retrieval"}, {"arrival_rate_per_s": 50}))
    save()
    for rate in (100, 200, 400, 800, 1200, 1600, 2400) if want("stress") else ():
        phase = k6_phase(folder, f"stress-{rate}", f"KEYWORD at {rate} requests/s (step), retrieval pool", {"BENCH_EXECUTOR": "rate", "BENCH_RATE": str(rate),
                         "BENCH_DURATION": seconds(20, 3), "BENCH_MODE": "KEYWORD", "BENCH_POOL": "retrieval", "BENCH_PREALLOC": str(min(rate, 600)), "BENCH_MAXVUS": "2000"}, {"arrival_rate_per_s": rate})
        phases.append(phase)
        save()
        offered = rate * (20 if FULL else 3)
        if (phase["http_error_rate"] or 0) > 0.01 or phase["dropped_iterations"] > 0.01 * offered or (phase["latency_ms"].get("p(95)") or 0) > 2000:
            phase["saturation"] = "stop: error rate > 1 %, dropped iterations > 1 % or p95 > 2 s"
            break
    # Model-bound traffic: multilingual queries (translated by the model on every request), the
    # combined mix, and cold analysis.
    for vus in (2, 5) if want("model") else ():
        phases.append(k6_phase(folder, f"multilingual-keyword-{vus}vu", f"KEYWORD search, constant {vus} VUs, 31-query multilingual pool (15 languages)",
                               {"BENCH_EXECUTOR": "vus", "BENCH_VUS": str(vus), "BENCH_DURATION": seconds(30, 4), "BENCH_MODE": "KEYWORD"}, {"vus": vus}))
        save()
    if want("model"):
      phases.append(k6_phase(folder, "combined", "mixed traffic at 20 requests/s, multilingual pool: keyword 40%, vector 12%, hybrid 12%, planned 10%, suggestions 8%, memory 8%, v1 analyze 6%, sessions 4%",
                           {"BENCH_EXECUTOR": "combined", "BENCH_RATE": "20", "BENCH_DURATION": seconds(60, 6)}, {"arrival_rate_per_s": 20}))
    save()
    if want("model"):
      phases.append(k6_phase(folder, "cold-keyword", "KEYWORD search with unique queries (cold analysis cache), 1 VU, retrieval pool",
                           {"BENCH_EXECUTOR": "iterations", "BENCH_ITERATIONS": "6" if FULL else "2", "BENCH_VUS": "1", "BENCH_MODE": "KEYWORD", "BENCH_UNIQUE": "true", "BENCH_POOL": "retrieval"}, {"vus": 1}))
    save()
    # QB-222: dependency outages (container stopped, then started again).
    window = seconds(25, 4)
    if want("outage"):
        phases.extend(outage(folder, "outage-ollama", OLLAMA, ["HYBRID", "VECTOR", "MEMORY"], window)); save()
        phases.extend(outage(folder, "outage-opensearch", OPENSEARCH, ["KEYWORD", "MEMORY"], window)); save()
        phases.extend(outage(folder, "outage-redis", REDIS, ["KEYWORD", "ANALYZE"], window)); save()
        healthy(BASE + "/q/health/live", 120)
    # QB-215: Redis on (running stack) vs off vs unavailable, same analyze workload.
    if want("redis"):
        # The same 20 distinct queries in every configuration. Redis on: a fill pass first, then
        # the measured pass (warm cache). Off and unavailable: no usable cache, every call reaches the model.
        phases.append(k6_phase(folder, "redis-on-fill", "v1 analyze, the 20 queries once (cache fill)",
                               {"BENCH_EXECUTOR": "iterations", "BENCH_ITERATIONS": "20", "BENCH_VUS": "1", "BENCH_MODE": "ANALYZE"}, {"vus": 1}))
        phases.append(k6_phase(folder, "redis-on", "v1 analyze, 20 distinct queries at 1 VU, Redis cache on and warm",
                               {"BENCH_EXECUTOR": "iterations", "BENCH_ITERATIONS": "20", "BENCH_VUS": "1", "BENCH_MODE": "ANALYZE"}, {"vus": 1}))
        save()
        phases.append(standalone(folder, "redis-off", {"QWENBRIDGE_ANALYSIS_CACHE_ENABLED": "false", "QUARKUS_REDIS_HOSTS": "redis://127.0.0.1:9"}, 18090)); save()
        phases.append(standalone(folder, "redis-unavailable", {"QWENBRIDGE_ANALYSIS_CACHE_ENABLED": "true", "QWENBRIDGE_ANALYSIS_CACHE_REDIS_HOST": "127.0.0.1",
                                                               "QWENBRIDGE_ANALYSIS_CACHE_REDIS_PORT": "9", "QUARKUS_REDIS_HOSTS": "redis://127.0.0.1:9"}, 18091)); save()
    if os.environ.get("BENCH_INCLUDE_JAVA") == "true" and want("java"):
        report["java"] = java_benchmarks(folder)
    report["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    save()
    subprocess.run([sys.executable, str(ROOT / "scripts/verification/benchmark_report.py"), str(folder / "report.json")], check=False)
    print(f"Benchmark report: {folder / 'report.json'}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
