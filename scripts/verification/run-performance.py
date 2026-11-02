#!/usr/bin/env python3
"""Run distinct cold-cache and cached throughput checks; retain every phase's result."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import platform
import threading
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def metric(data, name):
    item = data.get("metrics", {}).get(name, {})
    return item.get("values", item)


def inspect_result(data, expected, exit_code, requests_per_iteration=1):
    completed = int(metric(data, "iterations").get("count", 0))
    requests = int(metric(data, "http_reqs").get("count", 0))
    checks = metric(data, "checks")
    errors = metric(data, "http_req_failed").get("rate",
        metric(data, "http_req_failed").get("value", 1))
    return {
        "expected_requests": expected * requests_per_iteration,
        "expected_iterations": expected,
        "completed_requests": requests,
        "completed_iterations": completed,
        "exit_code": exit_code,
        "passed": exit_code == 0 and completed == expected and requests == expected * requests_per_iteration
            and checks.get("fails", 1) == 0 and errors < 0.05,
        "checks_passed": checks.get("passes", 0),
        "checks_failed": checks.get("fails", 0),
        "http_error_rate": errors,
        "p95_ms": metric(data, "http_req_duration").get("p(95)"),
        "p99_ms": metric(data, "http_req_duration").get("p(99)"),
        "cache_hits": metric(data, "cache_hits").get("count", 0),
        "cache_misses": metric(data, "cache_misses").get("count", 0),
        "ai_p95_ms": metric(data, "ai_analysis_ms").get("p(95)"),
    }


def compose():
    args = ["docker", "compose", "-f", os.environ.get("COMPOSE_FILE", "docker-compose.yml")]
    for profile in os.environ.get("COMPOSE_PROFILE", "production,opensearch").split(","):
        if profile:
            args += ["--profile", profile]
    args += ["--profile", "performance"]
    return args


def pool_metrics(base_url):
    with urllib.request.urlopen(base_url.rstrip("/") + "/q/metrics", timeout=10) as response:
        body = response.read().decode("utf-8")
    rejected = fallbacks = 0
    for line in body.splitlines():
        if line.startswith("redis_pool_rejected_total{"):
            rejected += float(line.rsplit(" ", 1)[1])
        if line.startswith("qwenbridge_redis_cache_events_total{") and 'result="fallback"' in line:
            fallbacks += float(line.rsplit(" ", 1)[1])
    return {"redis_pool_rejected": rejected, "cache_fallbacks": fallbacks}


def prometheus(base_url):
    """Sums of the Prometheus samples we report, keyed by metric (and selected labels)."""
    with urllib.request.urlopen(base_url.rstrip("/") + "/q/metrics", timeout=10) as response:
        body = response.read().decode("utf-8")
    out = {}
    def add(key, value):
        out[key] = out.get(key, 0.0) + value
    for line in body.splitlines():
        if not line or line.startswith("#"):
            continue
        name, _, value = line.rpartition(" ")
        try:
            number = float(value)
        except ValueError:
            continue
        metric_name = name.split("{", 1)[0]
        if metric_name in ("process_cpu_usage", "system_cpu_usage", "jvm_threads_live_threads", "qwenbridge_api_inflight",
                           "jvm_gc_pause_seconds_count", "jvm_gc_pause_seconds_sum"):
            add(metric_name, number)
        elif metric_name in ("jvm_memory_used_bytes", "jvm_memory_max_bytes") and 'area="heap"' in name:
            add(metric_name + "_heap", number)
        elif metric_name == "qwenbridge_api_errors_total":
            add("errors_" + ("operational" if 'class="operational"' in name else "expected"), number)
        elif metric_name == "qwenbridge_api_interruptions_total":
            add("interruptions", number)
    return out


def container_stats(container):
    """docker stats for one container: CPU percent and memory in bytes (includes native memory)."""
    try:
        raw = subprocess.check_output(["docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}|{{.MemUsage}}", container],
                                      text=True, timeout=20).strip()
        cpu, mem = raw.split("|")
        used = mem.split("/")[0].strip()
        units = {"KiB": 1024, "MiB": 1024 ** 2, "GiB": 1024 ** 3, "B": 1}
        for unit, factor in units.items():
            if used.endswith(unit):
                return {"cpu_percent": float(cpu.rstrip("%")), "memory_bytes": float(used[: -len(unit)]) * factor}
    except (subprocess.SubprocessError, ValueError, OSError):
        pass
    return {}


class ResourceSampler(threading.Thread):
    """QB-214: samples JVM and container resources every interval while a phase runs."""

    def __init__(self, base_url, container, interval=2.0):
        super().__init__(daemon=True)
        self.base_url, self.container, self.interval = base_url, container, interval
        self.samples, self.stop_event = [], threading.Event()

    def run(self):
        while not self.stop_event.is_set():
            sample = {"t": time.time()}
            try:
                sample.update(prometheus(self.base_url))
            except (OSError, ValueError):
                sample["scrape_error"] = True
            sample.update(container_stats(self.container))
            self.samples.append(sample)
            self.stop_event.wait(self.interval)

    def summary(self):
        def values(key):
            return [s[key] for s in self.samples if key in s]
        def stat(key, fn):
            v = values(key)
            return fn(v) if v else None
        heap = values("jvm_memory_used_bytes_heap")
        gc_count, gc_sum = values("jvm_gc_pause_seconds_count"), values("jvm_gc_pause_seconds_sum")
        return {
            "samples": len(self.samples),
            "process_cpu_avg": stat("process_cpu_usage", lambda v: sum(v) / len(v)),
            "process_cpu_max": stat("process_cpu_usage", max),
            "heap_used_max_bytes": max(heap) if heap else None,
            "heap_used_growth_bytes": heap[-1] - heap[0] if len(heap) > 1 else None,
            "heap_max_bytes": stat("jvm_memory_max_bytes_heap", max),
            "gc_pauses": gc_count[-1] - gc_count[0] if len(gc_count) > 1 else None,
            "gc_pause_seconds": gc_sum[-1] - gc_sum[0] if len(gc_sum) > 1 else None,
            "threads_max": stat("jvm_threads_live_threads", max),
            "inflight_max": stat("qwenbridge_api_inflight", max),
            "container_cpu_percent_avg": stat("cpu_percent", lambda v: sum(v) / len(v)),
            "container_memory_max_bytes": stat("memory_bytes", max),
        }


def redis_stats(container):
    """QB-215: Redis server counters (all clients) or None when Redis is not running."""
    try:
        raw = subprocess.check_output(["docker", "exec", container, "redis-cli", "INFO", "stats"], text=True, timeout=10, stderr=subprocess.DEVNULL)
    except (subprocess.SubprocessError, OSError):
        return None
    stats = {}
    for line in raw.splitlines():
        if ":" in line:
            key, value = line.strip().split(":", 1)
            if key in ("total_commands_processed", "keyspace_hits", "keyspace_misses"):
                stats[key] = int(value)
    return stats


def environment(app_container):
    """Benchmark methodology: where and with what the measurements were taken."""
    def run(cmd):
        try:
            return subprocess.check_output(cmd, text=True, timeout=20, stderr=subprocess.STDOUT).strip()
        except (subprocess.SubprocessError, OSError):
            return None
    cpu = run(["sysctl", "-n", "machdep.cpu.brand_string"]) or run(["sh", "-c", "grep -m1 'model name' /proc/cpuinfo | cut -d: -f2"])
    memory = run(["sysctl", "-n", "hw.memsize"]) or run(["sh", "-c", "grep MemTotal /proc/meminfo"])
    inspect = run(["docker", "inspect", "--format", "{{.HostConfig.Memory}}|{{.HostConfig.NanoCpus}}|{{.Config.Image}}", app_container])
    quarkus = re.search(r"<quarkus.platform.version>([^<]+)<", (ROOT / "pom.xml").read_text())
    return {
        "host_cpu": cpu, "host_memory": memory, "os": platform.platform(),
        "docker": run(["docker", "version", "--format", "{{.Server.Version}}"]),
        "docker_cpus": run(["docker", "info", "--format", "{{.NCPU}}"]),
        "docker_memory_bytes": run(["docker", "info", "--format", "{{.MemTotal}}"]),
        "app_container_limits": inspect,
        "app_java": run(["docker", "exec", app_container, "java", "-version"]),
        "app_java_opts": run(["docker", "exec", app_container, "sh", "-c", "echo ${JAVA_OPTS:-}${JAVA_TOOL_OPTIONS:-}"]),
        "quarkus": quarkus.group(1) if quarkus else None,
    }


def main():
    os.chdir(ROOT)
    count = int(os.environ.get("PERF_TOTAL_REQUESTS", "1000000"))
    vus = int(os.environ.get("PERF_VUS", "250"))
    if not 1 <= vus <= count or count > 2_000_000_000:
        raise ValueError("Invalid request count or concurrency")
    run_id = os.environ["VERIFY_RUN_ID"]
    folder = (ROOT / os.environ.get("VERIFY_LOG_DIR", "logs/verification") / ("performance-" + run_id)).resolve()
    folder.mkdir(parents=True, exist_ok=True)
    prefix = os.environ["QWENBRIDGE_ANALYSIS_CACHE_KEY_PREFIX"]
    if not re.fullmatch(r"qwenbridge:(performance|verification):[A-Za-z0-9:_-]+", prefix):
        raise ValueError("Performance verification requires an isolated verification cache prefix")
    redis = os.environ.get("REDIS_CONTAINER", "qwenbridge-redis")
    keys = subprocess.check_output(["docker", "exec", redis, "redis-cli", "--scan", "--pattern", prefix + ":*"], text=True)
    for key in keys.splitlines():
        if key:
            subprocess.run(["docker", "exec", redis, "redis-cli", "UNLINK", key], check=True, stdout=subprocess.DEVNULL)

    runner = os.environ.get("PERF_RUNNER", "auto")
    if runner not in ("auto", "native", "docker"):
        raise ValueError("PERF_RUNNER must be auto, native or docker")
    native = runner != "docker" and shutil.which("k6") is not None
    if runner == "native" and not native:
        raise ValueError("PERF_RUNNER=native requires k6")
    phases = [("cold", 10, 1, "miss")]
    if count > 1000:
        phases.append(("warm-1000", 1000, min(vus, 10), "hit"))
    if count > 10000:
        phases.append(("warm-10000", 10000, min(vus, 50), "hit"))
        if vus > 50:
            phases.append(("warm-concurrency", 10000, vus, "hit"))
    phases.append(("cached-load", count, vus, "hit"))
    quality_scripts = {"security-input": 9, "language-quality": 11, "retrieval-quality": 5, "resilience": 4,
                       "commerce-quality": len(json.loads((ROOT / "qwenbridge-server/src/test/resources/commerce/language-cases.json").read_text())), "commerce-throughput": min(count, 10000), "table1-sessions": min(count, 20)}
    if os.environ.get("RUN_COMPLETE_VERIFICATION") == "true":
        phases.extend((name, requests, min(vus, 100) if name == "commerce-throughput" else min(vus, 2) if name == "table1-sessions" else 1, "any") for name, requests in quality_scripts.items())
    results = {
        "run_id": run_id, "model_placement": os.environ.get("QWENBRIDGE_AI_OLLAMA_BASE_URL", "http://qwenbridge-ollama:11434"),
        "runner": "native" if native else "docker", "phases": {},
        "note": "Cold uses cache misses. Throughput phases repeat ten queries; concurrency is VUs, not total requests.",
        "methodology": "Closed workload: each VU sends its next request when the previous one finishes, so offered load is the VU count and achieved_rps is measured. Client latency from k6; server-side histograms at /q/metrics. Resources sampled every 2 s.",
        "environment": environment(os.environ.get("APP_CONTAINER", "qwenbridge-app")),
    }
    for name, requests, users, expectation in phases:
        script = "k6-" + name + "-test.js" if name in quality_scripts else "v9-k6-load-test.js"
        summary = folder / (name + ".json")
        log = folder / (name + ".log")
        env = os.environ.copy()
        env.update(PERF_TOTAL_REQUESTS=str(requests), PERF_VUS=str(users), PERF_CACHE_EXPECTATION=expectation,
                   K6_SUMMARY_TREND_STATS="avg,min,med,max,p(90),p(95),p(99)")
        env["BASE_URL"] = os.environ.get("BASE_URL", "http://localhost:" + os.environ.get("APP_PORT", "8080"))
        if name == "commerce-throughput": env["COMMERCE_THROUGHPUT"] = "true"
        before_pool = pool_metrics(env["BASE_URL"])
        host_base = os.environ.get("BASE_URL", "http://localhost:" + os.environ.get("APP_PORT", "8080"))
        before_server = prometheus(host_base)
        before_redis = redis_stats(redis)
        sampler = ResourceSampler(host_base, os.environ.get("APP_CONTAINER", "qwenbridge-app"))
        sampler.start()
        phase_started = time.time()
        if native:
            cmd = ["k6", "run", "--summary-export", str(summary), "scripts/performance/" + script]
        else:
            env["BASE_URL"] = "http://qwenbridge-app:8080"
            cmd = compose() + ["run", "--rm", "--no-deps", "-T", "--user", f"{os.getuid()}:{os.getgid()}",
                "-v", str(folder) + ":/reports"]
            for key in ["BASE_URL", "PERF_TOTAL_REQUESTS", "PERF_VUS", "PERF_MAX_DURATION", "PERF_CACHE_EXPECTATION", "K6_SUMMARY_TREND_STATS", "COMMERCE_THROUGHPUT", "QWENBRIDGE_TEST_API_TOKEN"]:
                if key in env:
                    cmd += ["-e", key]
            cmd += ["qwenbridge-k6", "run", "--summary-export", "/reports/" + summary.name,
                    "/scripts/performance/" + script]
        factor = 8 if name == "table1-sessions" else 1
        print(f"PERFORMANCE {name}: {requests * factor} HTTP requests, {requests} iterations, {users} VUs, cache={expectation}; log={log}", flush=True)
        with log.open("w") as out:
            completed = subprocess.run(cmd, env=env, stdout=out, stderr=subprocess.STDOUT)
        try:
            data = json.loads(summary.read_text())
        except (OSError, ValueError):
            data = {}
        sampler.stop_event.set()
        sampler.join(timeout=30)
        phase = inspect_result(data, requests, completed.returncode, factor)
        phase["duration_s"] = round(time.time() - phase_started, 1)
        phase["achieved_rps"] = metric(data, "http_reqs").get("rate")
        after_server = prometheus(host_base)
        phase["server_errors"] = {key: after_server.get(key, 0) - before_server.get(key, 0)
                                  for key in ("errors_expected", "errors_operational", "interruptions")}
        phase["resources"] = sampler.summary()
        after_redis = redis_stats(redis)
        if before_redis is None or after_redis is None:
            phase["redis"] = "not applicable: Redis not running"
        else:
            delta = {key: after_redis.get(key, 0) - before_redis.get(key, 0) for key in after_redis}
            done = max(1, phase["completed_requests"])
            delta["commands_per_request"] = round(delta.get("total_commands_processed", 0) / done, 2)
            phase["redis"] = delta
        after_pool = pool_metrics(os.environ.get("BASE_URL", "http://localhost:" + os.environ.get("APP_PORT", "8080")))
        phase.update({key: value - before_pool[key] for key, value in after_pool.items()})
        phase["passed"] = phase["passed"] and all(phase[key] == 0 for key in before_pool)
        phase["vus"] = users
        results["phases"][name] = phase
        (folder / "results.json").write_text(json.dumps(results, indent=2) + "\n")
        print(json.dumps({"phase": name, **phase}), flush=True)
        # A cold latency miss remains a failure, but cached capacity is measured separately.
        # Contract errors or a failing cached stage stop escalation.
        if not phase["passed"] and (name != "cold" or phase["checks_failed"] or phase["http_error_rate"] or phase["completed_requests"] != requests or phase["redis_pool_rejected"] or phase["cache_fallbacks"]):
            break
    ok = len(results["phases"]) == len(phases) and all(p["passed"] for p in results["phases"].values())
    results["passed"] = ok
    (folder / "results.json").write_text(json.dumps(results, indent=2) + "\n")
    print(f"Performance report: {folder / 'results.json'}", flush=True)
    return 0 if ok else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"Performance verification failed: {error}", file=sys.stderr)
        sys.exit(1)
