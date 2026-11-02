# QwenBridge benchmark run bench-redis-20261101-213417

Profile `full`, target http://localhost:8080, 2026-11-01T21:34:20+0100 → 2026-11-01T21:38:24+0100.
All values are measurements from this run. Offered load is the configured VUs or arrival rate; achieved RPS is what was served.

## Environment

- **host_cpu**: Apple M4 Pro
- **host_memory**: 51539607552
- **os**: macOS-26.6.2-arm64-arm-64bit-Mach-O
- **docker**: 28.5.1
- **docker_cpus**: 8
- **docker_memory_bytes**: 24899309568
- **app_container_limits**: 1073741824|0|quarkus-qwenbridge-app
- **app_java**: Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=55 -XX:InitialRAMPercentage=25 -XX:+ExitOnOutOfMemoryError
- **app_java_opts**: -XX:MaxRAMPercentage=55 -XX:InitialRAMPercentage=25 -XX:+ExitOnOutOfMemoryError
- **quarkus**: 3.40.1

## Phases

| Phase | Offered | Requests | Achieved RPS | p50 ms | p95 ms | p99 ms | HTTP error rate | Dropped | Operational errors | Expected errors | Outcomes | CPU max | Heap max MB | Container MB | Redis cmds/req |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| redis-on-fill | {"vus": 1} | 20 | 0.3 | 3999.2 | 4380.1 | 4508.3 | 0 | 0 | 0 | 0 | ok:20 | 0.04 | 141 | 386 | 4.95 |
| redis-on | {"vus": 1} | 20 | 90.1 | 10.1 | 15.6 | 18.7 | 0 | 0 | 0 | 0 | ok:20 | 0.05 | 126 | 386 | 3.05 |
| redis-off | {"vus": 1, "iterations": 20} | 20 | 0.3 | 4092.5 | 4385.1 | 4618.1 | 0 | 0 | 0 | 0 | ok:20 | 0.02 | 161 | 392 | 0.8 |
| redis-unavailable | {"vus": 1, "iterations": 20} | 20 | 0.3 | 4181.9 | 4761.7 | 4762.6 | 0 | 0 | 0 | 0 | ok:20 | 0.02 | 170 | 393 | 0.85 |

## Server-side stage latency (histogram deltas per phase)

| Phase | Stage | Count | Mean ms | p50 ms | p95 ms |
|---|---|---|---|---|---|
