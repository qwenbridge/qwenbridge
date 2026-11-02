# QwenBridge benchmark run bench-full-20261101-202339

Profile `full`, target http://localhost:8080, 2026-11-01T20:23:42+0100 → 2026-11-01T21:32:59+0100.
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
| warm-up | {"vus": 1} | 31 | 0.6 | 50.0 | 4113.7 | 4203.2 | 0 | 0 | 0 | 0 | ok:31 | 0.05 | 125 | 386 | 26.94 |
| warm-up-retrieval | {"vus": 1} | 6 | 63.6 | 15.3 | 17.6 | 17.9 | 0 | 0 | 0 | 0 | ok:6 | 0.00 | 143 | 393 | 26.17 |
| strategy-keyword-5vu | {"vus": 5} | 13006 | 650.2 | 6.9 | 11.5 | 16.7 | 0 | 0 | 0 | 0 | ok:13006 | 0.44 | 144 | 504 | 26.0 |
| strategy-keyword-20vu | {"vus": 20} | 31807 | 1589.8 | 12.1 | 18.0 | 21.7 | 0 | 0 | 0 | 0 | ok:31807 | 0.41 | 138 | 546 | 26.0 |
| strategy-keyword-50vu | {"vus": 50} | 31695 | 1583.0 | 30.6 | 50.4 | 59.7 | 0 | 0 | 0 | 0 | ok:31695 | 0.42 | 160 | 562 | 26.0 |
| strategy-vector-5vu | {"vus": 5} | 1027 | 51.2 | 88.8 | 139.9 | 230.6 | 0 | 0 | 0 | 0 | ok:1027 | 0.05 | 173 | 566 | 26.0 |
| strategy-vector-20vu | {"vus": 20} | 1076 | 53.0 | 359.1 | 452.0 | 780.1 | 0 | 0 | 0 | 0 | ok:1076 | 0.03 | 175 | 567 | 26.01 |
| strategy-vector-50vu | {"vus": 50} | 1151 | 55.2 | 887.6 | 992.1 | 1046.2 | 0 | 0 | 0 | 0 | ok:1151 | 0.03 | 175 | 574 | 26.0 |
| strategy-hybrid-5vu | {"vus": 5} | 1079 | 53.7 | 86.6 | 131.5 | 171.4 | 0 | 0 | 0 | 0 | ok:1079 | 0.03 | 136 | 574 | 26.0 |
| strategy-hybrid-20vu | {"vus": 20} | 1065 | 52.4 | 359.6 | 497.5 | 782.2 | 0 | 0 | 0 | 0 | ok:1065 | 0.03 | 162 | 574 | 26.01 |
| strategy-hybrid-50vu | {"vus": 50} | 1128 | 54.4 | 912.8 | 1017.3 | 1057.9 | 0 | 0 | 0 | 0 | ok:1128 | 0.03 | 187 | 581 | 26.0 |
| spike-baseline | {"arrival_rate_per_s": 20} | 601 | 20.0 | 10.6 | 13.5 | 14.6 | 0 | 0 | 0 | 0 | ok:601 | 0.02 | 186 | 581 | 26.01 |
| spike-peak | {"arrival_rate_per_s": 300} | 9001 | 300.0 | 5.2 | 7.1 | 9.6 | 0 | 0 | 0 | 0 | ok:9001 | 0.11 | 185 | 580 | 26.0 |
| spike-recovery | {"arrival_rate_per_s": 20} | 601 | 20.0 | 10.4 | 13.5 | 15.2 | 0 | 0 | 0 | 0 | ok:601 | 0.01 | 190 | 579 | 26.01 |
| soak | {"arrival_rate_per_s": 50} | 29949 | 49.9 | 7.7 | 10.0 | 128.3 | 0 | 52 | 0 | 0 | ok:29949 | 0.04 | 210 | 610 | 26.01 |
| stress-100 | {"arrival_rate_per_s": 100} | 2001 | 100.0 | 6.0 | 7.8 | 9.3 | 0 | 0 | 0 | 0 | ok:2001 | 0.04 | 210 | 604 | 26.0 |
| stress-200 | {"arrival_rate_per_s": 200} | 4001 | 200.0 | 5.3 | 7.7 | 9.2 | 0 | 0 | 0 | 0 | ok:4001 | 0.07 | 200 | 606 | 26.0 |
| stress-400 | {"arrival_rate_per_s": 400} | 8000 | 399.9 | 5.6 | 7.6 | 9.4 | 0 | 0 | 0 | 0 | ok:8000 | 0.15 | 205 | 609 | 26.0 |
| stress-800 | {"arrival_rate_per_s": 800} | 16001 | 799.8 | 6.5 | 10.5 | 15.4 | 0 | 0 | 0 | 0 | ok:16001 | 0.29 | 211 | 619 | 26.0 |
| stress-1200 | {"arrival_rate_per_s": 1200} | 24001 | 1199.6 | 8.0 | 19.1 | 39.4 | 0 | 0 | 0 | 0 | ok:24001 | 0.36 | 200 | 636 | 26.0 |
| stress-1600 | {"arrival_rate_per_s": 1600} | 17383 | 730.1 | 541.3 | 4486.0 | 6589.8 | 0 | 14617 | 0 | 0 | ok:17383 | 0.39 | 416 | 965 | 26.0 |
| multilingual-keyword-2vu | {"vus": 2} | 9 | 0.3 | 7167 | 8392.2 | 8445.9 | 0 | 0 | 0 | 0 | ok:9 | 0.02 | 220 | 965 | -567342.11 |
| multilingual-keyword-5vu | {"vus": 5} | 16 | 0.3 | 16878.1 | 19327.3 | 21407.1 | 0 | 0 | 0 | 0 | ok:16 | 0.00 | 284 | 943 | 27.81 |
| combined | {"arrival_rate_per_s": 20} | 410 | 4.6 | 42133.7 | 60001.6 | 60001.9 | 0.3805 | 736 | 0 | 0 | ok:202, status-clarification_required:7, status-review:7, status-unknown:15, transport:145 | 0.02 | 298 | 994 | 17.0 |
| cold-keyword | {"vus": 1} | 6 | 0.3 | 3393.1 | 3775.2 | 3786.9 | 0 | 0 | 0 | 0 | ok:3, status-clarification_required:3 | 0.02 | 331 | 978 | 26.5 |
| outage-ollama-before-hybrid | {"arrival_rate_per_s": 5} | 8 | 0.1 | 21737.2 | 32033.1 | 33016.5 | 0 | 73 | 0 | 0 | ok:8 | 0.00 | 325 | 977 | 58.88 |
| outage-ollama-before-vector | {"arrival_rate_per_s": 5} | 10 | 0.2 | 24995.9 | 43255.0 | 44770.5 | 0 | 71 | 0 | 0 | ok:10 | 0.00 | 296 | 978 | 50.8 |
| outage-ollama-before-memory | {"arrival_rate_per_s": 5} | 121 | 4.8 | 14.8 | 5657.9 | 8242.2 | 0 | 5 | 0 | 0 | ok:121 | 0.01 | 323 | 978 | 26.15 |
| outage-ollama-during-hybrid | {"arrival_rate_per_s": 5} | 125 | 5.0 | 10.6 | 14.9 | 18.9 | 1 | 0 | 125 | 0 | error5xx-typed:125 | 0.01 | 324 | 972 | 7.08 |
| outage-ollama-during-vector | {"arrival_rate_per_s": 5} | 126 | 5.0 | 12.7 | 17.0 | 21.9 | 0 | 0 | 0 | 0 | status-review:126 | 0.01 | 324 | 973 | 19.06 |
| outage-ollama-during-memory | {"arrival_rate_per_s": 5} | 126 | 5.0 | 13.0 | 16.7 | 18.1 | 0 | 0 | 0 | 0 | status-review:126 | 0.01 | 304 | 976 | 19.06 |
| outage-ollama-after-hybrid | {"arrival_rate_per_s": 5} | 7 | 0.1 | 31566.5 | 37602.9 | 38370.1 | 0 | 73 | 0 | 0 | ok:7 | 0.00 | 331 | 982 | 58.14 |
| outage-ollama-after-vector | {"arrival_rate_per_s": 5} | 13 | 0.2 | 14747.9 | 35348.6 | 37277.6 | 0 | 68 | 0 | 0 | ok:13 | 0.02 | 332 | 983 | 42.23 |
| outage-ollama-after-memory | {"arrival_rate_per_s": 5} | 120 | 4.8 | 14.5 | 5146.1 | 8606.3 | 0 | 6 | 0 | 0 | ok:120 | 0.01 | 319 | 983 | 23.23 |
| outage-opensearch-before-keyword | {"arrival_rate_per_s": 5} | 126 | 5.0 | 16.3 | 22.1 | 24.0 | 0 | 0 | 0 | 0 | ok:126 | 0.01 | 322 | 981 | 23.06 |
| outage-opensearch-before-memory | {"arrival_rate_per_s": 5} | 126 | 5.0 | 11.4 | 15.2 | 18.1 | 0 | 0 | 0 | 0 | ok:126 | 0.01 | 328 | 980 | 23.06 |
| outage-opensearch-during-keyword | {"arrival_rate_per_s": 5} | 126 | 5.0 | 8.9 | 13.1 | 16.7 | 1 | 0 | 126 | 0 | error5xx-typed:126 | 0.02 | 309 | 980 | 9.06 |
| outage-opensearch-during-memory | {"arrival_rate_per_s": 5} | 125 | 5.0 | 12.6 | 16.2 | 19.0 | 0 | 0 | 0 | 0 | ok:125 | 0.01 | 321 | 980 | 23.05 |
| outage-opensearch-after-keyword | {"arrival_rate_per_s": 5} | 126 | 5.0 | 18.7 | 26.3 | 46.4 | 0 | 0 | 0 | 0 | ok:126 | 0.02 | 309 | 988 | 23.06 |
| outage-opensearch-after-memory | {"arrival_rate_per_s": 5} | 125 | 5.0 | 12.3 | 17.3 | 19.1 | 0 | 0 | 0 | 0 | ok:125 | 0.01 | 332 | 984 | 23.06 |
| outage-redis-before-keyword | {"arrival_rate_per_s": 5} | 125 | 5.0 | 14.9 | 21.7 | 24.0 | 0 | 0 | 0 | 0 | ok:125 | 0.01 | 320 | 984 | 23.05 |
| outage-redis-before-analyze | {"arrival_rate_per_s": 5} | 126 | 5.0 | 10.6 | 16.5 | 24.3 | 0 | 0 | 0 | 0 | ok:126 | 0.01 | 313 | 986 | 3.06 |
| outage-redis-during-keyword | {"arrival_rate_per_s": 5} | 126 | 5.0 | 5.1 | 9.3 | 15.7 | 1 | 0 | 126 | 0 | error5xx-typed:126 | 0.03 | 327 | 986 | n/a |
| outage-redis-during-analyze | {"arrival_rate_per_s": 5} | 100 | 2.9 | 6934.7 | 13213.9 | 15628.9 | 0 | 26 | 0 | 0 | ok:100 | 0.01 | 329 | 989 | n/a |
| outage-redis-after-keyword | {"arrival_rate_per_s": 5} | 126 | 4.9 | 18.9 | 23.8 | 1039.0 | 0 | 0 | 0 | 0 | ok:126 | 0.01 | 321 | 989 | 23.06 |
| outage-redis-after-analyze | {"arrival_rate_per_s": 5} | 126 | 5.0 | 13.9 | 18.2 | 20.0 | 0 | 0 | 0 | 0 | ok:126 | 0.02 | 312 | 990 | 3.06 |
| redis-on | {"vus": 1} | 20 | 0.3 | 4011.7 | 4498.5 | 4699.0 | 0 | 0 | 0 | 0 | ok:20 | 0.02 | 330 | 990 | 4.9 |
| redis-off | {"vus": 1, "iterations": 20} | 20 | 0.3 | 4122.9 | 4456.1 | 4461.1 | 0 | 0 | 0 | 0 | ok:20 | 0.02 | 197 | 990 | 0.75 |
| redis-unavailable | {"vus": 1, "iterations": 20} | 20 | 0.2 | 4249.8 | 4848.3 | 4899.1 | 0 | 0 | 0 | 0 | ok:20 | 0.02 | 262 | 989 | 0.7 |

## Server-side stage latency (histogram deltas per phase)

| Phase | Stage | Count | Mean ms | p50 ms | p95 ms |
|---|---|---|---|---|---|
| warm-up | analysis | 31 | 1535.42 | 1.92 | 4110.05 |
| warm-up | constraint-validation | 31 | 0.00 | 0.50 | 0.95 |
| warm-up | embedding | 31 | 0.16 | 0.52 | 0.98 |
| warm-up | execution | 31 | 6.13 | 4.12 | 6.22 |
| warm-up | input-validation | 31 | 0.39 | 0.52 | 0.98 |
| warm-up | ranking | 31 | 0.00 | 0.50 | 0.95 |
| warm-up | translation | 31 | 39.81 | 1.92 | 308.70 |
| warm-up-retrieval | analysis | 6 | 0.00 | 0.50 | 0.95 |
| warm-up-retrieval | constraint-validation | 6 | 0.00 | 0.50 | 0.95 |
| warm-up-retrieval | embedding | 6 | 0.00 | 0.50 | 0.95 |
| warm-up-retrieval | execution | 6 | 3.67 | 3.93 | 4.17 |
| warm-up-retrieval | input-validation | 6 | 0.00 | 0.50 | 0.95 |
| warm-up-retrieval | ranking | 6 | 0.00 | 0.50 | 0.95 |
| warm-up-retrieval | translation | 6 | 1.33 | 0.60 | 6.57 |
| strategy-keyword-5vu | analysis | 13006 | 0.02 | 0.50 | 0.96 |
| strategy-keyword-5vu | constraint-validation | 13006 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-5vu | embedding | 13006 | 0.02 | 0.50 | 0.95 |
| strategy-keyword-5vu | execution | 13006 | 1.44 | 0.75 | 3.08 |
| strategy-keyword-5vu | input-validation | 13006 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-5vu | ranking | 13006 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-5vu | translation | 13006 | 0.12 | 0.51 | 0.98 |
| strategy-keyword-20vu | analysis | 31807 | 0.09 | 0.51 | 0.97 |
| strategy-keyword-20vu | constraint-validation | 31807 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-20vu | embedding | 31807 | 0.07 | 0.51 | 0.96 |
| strategy-keyword-20vu | execution | 31807 | 2.25 | 1.91 | 5.03 |
| strategy-keyword-20vu | input-validation | 31807 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-20vu | ranking | 31807 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-20vu | translation | 31807 | 0.33 | 0.53 | 1.82 |
| strategy-keyword-50vu | analysis | 31695 | 0.18 | 0.51 | 0.98 |
| strategy-keyword-50vu | constraint-validation | 31695 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-50vu | embedding | 31695 | 0.13 | 0.51 | 0.97 |
| strategy-keyword-50vu | execution | 31695 | 2.75 | 2.03 | 6.56 |
| strategy-keyword-50vu | input-validation | 31695 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-50vu | ranking | 31695 | 0.00 | 0.50 | 0.95 |
| strategy-keyword-50vu | translation | 31695 | 0.54 | 0.55 | 2.80 |
| strategy-vector-5vu | analysis | 1027 | 0.03 | 0.50 | 0.96 |
| strategy-vector-5vu | constraint-validation | 1027 | 0.00 | 0.50 | 0.95 |
| strategy-vector-5vu | embedding | 1027 | 89.71 | 82.54 | 131.72 |
| strategy-vector-5vu | execution | 1027 | 2.27 | 1.87 | 5.47 |
| strategy-vector-5vu | input-validation | 1027 | 0.00 | 0.50 | 0.95 |
| strategy-vector-5vu | ranking | 1027 | 0.00 | 0.50 | 0.95 |
| strategy-vector-5vu | translation | 1027 | 0.09 | 0.51 | 0.97 |
| strategy-vector-20vu | analysis | 1076 | 0.03 | 0.50 | 0.96 |
| strategy-vector-20vu | constraint-validation | 1076 | 0.00 | 0.50 | 0.95 |
| strategy-vector-20vu | embedding | 1076 | 366.80 | 346.95 | 447.00 |
| strategy-vector-20vu | execution | 1076 | 2.05 | 1.83 | 5.01 |
| strategy-vector-20vu | input-validation | 1076 | 0.00 | 0.50 | 0.95 |
| strategy-vector-20vu | ranking | 1076 | 0.00 | 0.50 | 0.95 |
| strategy-vector-20vu | translation | 1076 | 0.07 | 0.51 | 0.97 |
| strategy-vector-50vu | analysis | 1151 | 0.04 | 0.51 | 0.96 |
| strategy-vector-50vu | constraint-validation | 1151 | 0.00 | 0.50 | 0.95 |
| strategy-vector-50vu | embedding | 1151 | 878.23 | 871.40 | 989.59 |
| strategy-vector-50vu | execution | 1151 | 2.10 | 1.79 | 5.49 |
| strategy-vector-50vu | input-validation | 1151 | 0.00 | 0.50 | 0.95 |
| strategy-vector-50vu | ranking | 1151 | 0.00 | 0.50 | 0.95 |
| strategy-vector-50vu | translation | 1151 | 0.09 | 0.51 | 0.98 |
| strategy-hybrid-5vu | analysis | 1079 | 0.03 | 0.50 | 0.96 |
| strategy-hybrid-5vu | constraint-validation | 1079 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-5vu | embedding | 1079 | 85.13 | 81.39 | 124.21 |
| strategy-hybrid-5vu | execution | 1079 | 2.26 | 1.86 | 5.67 |
| strategy-hybrid-5vu | input-validation | 1079 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-5vu | ranking | 1079 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-5vu | translation | 1079 | 0.05 | 0.51 | 0.97 |
| strategy-hybrid-20vu | analysis | 1065 | 0.01 | 0.50 | 0.95 |
| strategy-hybrid-20vu | constraint-validation | 1065 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-20vu | embedding | 1065 | 370.88 | 347.45 | 503.17 |
| strategy-hybrid-20vu | execution | 1065 | 2.23 | 1.86 | 5.27 |
| strategy-hybrid-20vu | input-validation | 1065 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-20vu | ranking | 1065 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-20vu | translation | 1065 | 0.07 | 0.51 | 0.97 |
| strategy-hybrid-50vu | analysis | 1128 | 0.10 | 0.51 | 0.97 |
| strategy-hybrid-50vu | constraint-validation | 1128 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-50vu | embedding | 1128 | 892.23 | 904.69 | 1033.18 |
| strategy-hybrid-50vu | execution | 1128 | 2.20 | 1.86 | 5.40 |
| strategy-hybrid-50vu | input-validation | 1128 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-50vu | ranking | 1128 | 0.00 | 0.50 | 0.95 |
| strategy-hybrid-50vu | translation | 1128 | 0.12 | 0.52 | 0.98 |
| spike-baseline | analysis | 601 | 0.00 | 0.50 | 0.95 |
| spike-baseline | constraint-validation | 601 | 0.00 | 0.50 | 0.95 |
| spike-baseline | embedding | 601 | 0.00 | 0.50 | 0.95 |
| spike-baseline | execution | 601 | 2.43 | 2.04 | 4.06 |
| spike-baseline | input-validation | 601 | 0.00 | 0.50 | 0.95 |
| spike-baseline | ranking | 601 | 0.00 | 0.50 | 0.95 |
| spike-baseline | translation | 601 | 0.42 | 0.50 | 0.96 |
| spike-peak | analysis | 9001 | 0.00 | 0.50 | 0.95 |
| spike-peak | constraint-validation | 9001 | 0.00 | 0.50 | 0.95 |
| spike-peak | embedding | 9001 | 0.00 | 0.50 | 0.95 |
| spike-peak | execution | 9001 | 0.78 | 0.55 | 1.91 |
| spike-peak | input-validation | 9001 | 0.00 | 0.50 | 0.95 |
| spike-peak | ranking | 9001 | 0.00 | 0.50 | 0.95 |
| spike-peak | translation | 9001 | 0.02 | 0.50 | 0.95 |
| spike-recovery | analysis | 601 | 0.00 | 0.50 | 0.95 |
| spike-recovery | constraint-validation | 601 | 0.00 | 0.50 | 0.95 |
| spike-recovery | embedding | 601 | 0.01 | 0.50 | 0.95 |
| spike-recovery | execution | 601 | 2.44 | 2.04 | 4.05 |
| spike-recovery | input-validation | 601 | 0.00 | 0.50 | 0.95 |
| spike-recovery | ranking | 601 | 0.00 | 0.50 | 0.95 |
| spike-recovery | translation | 601 | 0.40 | 0.50 | 0.96 |
| soak | analysis | 29949 | 18.04 | 0.51 | 0.96 |
| soak | constraint-validation | 29949 | 0.00 | 0.50 | 0.95 |
| soak | embedding | 29949 | 0.02 | 0.50 | 0.95 |
| soak | execution | 29949 | 1.64 | 1.76 | 2.83 |
| soak | input-validation | 29949 | 0.00 | 0.50 | 0.95 |
| soak | ranking | 29949 | 0.00 | 0.50 | 0.95 |
| soak | translation | 29949 | 0.19 | 0.51 | 0.96 |
| stress-100 | analysis | 2001 | 0.00 | 0.50 | 0.95 |
| stress-100 | constraint-validation | 2001 | 0.00 | 0.50 | 0.95 |
| stress-100 | embedding | 2001 | 0.00 | 0.50 | 0.95 |
| stress-100 | execution | 2001 | 1.13 | 0.64 | 2.03 |
| stress-100 | input-validation | 2001 | 0.00 | 0.50 | 0.95 |
| stress-100 | ranking | 2001 | 0.00 | 0.50 | 0.95 |
| stress-100 | translation | 2001 | 0.02 | 0.50 | 0.95 |
| stress-200 | analysis | 4001 | 0.00 | 0.50 | 0.95 |
| stress-200 | constraint-validation | 4001 | 0.00 | 0.50 | 0.95 |
| stress-200 | embedding | 4001 | 0.00 | 0.50 | 0.95 |
| stress-200 | execution | 4001 | 0.85 | 0.56 | 1.95 |
| stress-200 | input-validation | 4001 | 0.00 | 0.50 | 0.95 |
| stress-200 | ranking | 4001 | 0.00 | 0.50 | 0.95 |
| stress-200 | translation | 4001 | 0.02 | 0.50 | 0.95 |
| stress-400 | analysis | 8000 | 0.00 | 0.50 | 0.95 |
| stress-400 | constraint-validation | 8000 | 0.00 | 0.50 | 0.95 |
| stress-400 | embedding | 8000 | 0.00 | 0.50 | 0.95 |
| stress-400 | execution | 8000 | 0.92 | 0.56 | 1.97 |
| stress-400 | input-validation | 8000 | 0.00 | 0.50 | 0.95 |
| stress-400 | ranking | 8000 | 0.00 | 0.50 | 0.95 |
| stress-400 | translation | 8000 | 0.03 | 0.50 | 0.96 |
| stress-800 | analysis | 16001 | 0.01 | 0.50 | 0.95 |
| stress-800 | constraint-validation | 16001 | 0.00 | 0.50 | 0.95 |
| stress-800 | embedding | 16001 | 0.01 | 0.50 | 0.95 |
| stress-800 | execution | 16001 | 1.21 | 0.65 | 2.09 |
| stress-800 | input-validation | 16001 | 0.00 | 0.50 | 0.95 |
| stress-800 | ranking | 16001 | 0.00 | 0.50 | 0.95 |
| stress-800 | translation | 16001 | 0.05 | 0.51 | 0.96 |
| stress-1200 | analysis | 24001 | 0.05 | 0.50 | 0.96 |
| stress-1200 | constraint-validation | 24001 | 0.00 | 0.50 | 0.95 |
| stress-1200 | embedding | 24001 | 0.04 | 0.50 | 0.95 |
| stress-1200 | execution | 24001 | 1.63 | 0.84 | 4.00 |
| stress-1200 | input-validation | 24001 | 0.00 | 0.50 | 0.95 |
| stress-1200 | ranking | 24001 | 0.00 | 0.50 | 0.95 |
| stress-1200 | translation | 24001 | 0.14 | 0.51 | 0.98 |
| stress-1600 | analysis | 17383 | 124.42 | 0.55 | 7.44 |
| stress-1600 | constraint-validation | 17383 | 0.00 | 0.50 | 0.95 |
| stress-1600 | embedding | 17383 | 0.76 | 0.55 | 3.93 |
| stress-1600 | execution | 17383 | 4.62 | 2.81 | 16.39 |
| stress-1600 | input-validation | 17383 | 0.02 | 0.50 | 0.95 |
| stress-1600 | ranking | 17383 | 0.00 | 0.50 | 0.95 |
| stress-1600 | translation | 17383 | 2.37 | 0.59 | 7.48 |
| multilingual-keyword-2vu | analysis | 9 | 7156.89 | 6979.32 | 8428.87 |
| multilingual-keyword-2vu | constraint-validation | 9 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-2vu | embedding | 9 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-2vu | execution | 9 | 3.78 | 2.97 | 9.16 |
| multilingual-keyword-2vu | input-validation | 9 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-2vu | ranking | 9 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-2vu | translation | 9 | 1.56 | 0.90 | 2.99 |
| multilingual-keyword-5vu | analysis | 16 | 11859.13 | 15986.82 | 21379.39 |
| multilingual-keyword-5vu | constraint-validation | 16 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-5vu | embedding | 16 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-5vu | execution | 16 | 2.06 | 1.88 | 5.87 |
| multilingual-keyword-5vu | input-validation | 16 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-5vu | ranking | 16 | 0.00 | 0.50 | 0.95 |
| multilingual-keyword-5vu | translation | 16 | 310.00 | 0.80 | 4581.30 |
| combined | analysis | 196 | 408.33 | 0.57 | 4.10 |
| combined | constraint-validation | 167 | 0.01 | 0.50 | 0.95 |
| combined | embedding | 167 | 1466.35 | 1.88 | 13815.48 |
| combined | execution | 195 | 11.44 | 8.19 | 36.15 |
| combined | input-validation | 196 | 0.01 | 0.50 | 0.95 |
| combined | interpretation | 29 | 2.31 | 0.69 | 9.16 |
| combined | ranking | 167 | 0.00 | 0.50 | 0.95 |
| combined | translation | 196 | 18278.69 | 14930.12 | 57564.46 |
| cold-keyword | analysis | 3 | 3336.33 | 3400.18 | 3561.24 |
| cold-keyword | constraint-validation | 3 | 0.00 | 0.50 | 0.95 |
| cold-keyword | embedding | 3 | 0.00 | 0.50 | 0.95 |
| cold-keyword | execution | 3 | 3.00 | 2.97 | 4.14 |
| cold-keyword | input-validation | 3 | 0.00 | 0.50 | 0.95 |
| cold-keyword | ranking | 3 | 0.00 | 0.50 | 0.95 |
| cold-keyword | translation | 3 | 1.00 | 0.50 | 0.95 |
| outage-ollama-before-hybrid | analysis | 8 | 346.12 | 0.67 | 2362.23 |
| outage-ollama-before-hybrid | constraint-validation | 8 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-hybrid | embedding | 8 | 20895.25 | 22906.49 | 33214.41 |
| outage-ollama-before-hybrid | execution | 8 | 3.75 | 3.15 | 6.43 |
| outage-ollama-before-hybrid | input-validation | 8 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-hybrid | ranking | 8 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-hybrid | translation | 8 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-vector | analysis | 10 | 0.10 | 0.50 | 0.95 |
| outage-ollama-before-vector | constraint-validation | 10 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-vector | embedding | 10 | 24494.90 | 22906.49 | 44381.33 |
| outage-ollama-before-vector | execution | 10 | 2.00 | 1.82 | 6.29 |
| outage-ollama-before-vector | input-validation | 10 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-vector | ranking | 10 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-vector | translation | 10 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-memory | analysis | 121 | 827.88 | 0.70 | 5708.73 |
| outage-ollama-before-memory | constraint-validation | 121 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-memory | embedding | 121 | 0.04 | 0.51 | 0.97 |
| outage-ollama-before-memory | execution | 121 | 0.67 | 0.56 | 2.09 |
| outage-ollama-before-memory | input-validation | 121 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-memory | ranking | 121 | 0.00 | 0.50 | 0.95 |
| outage-ollama-before-memory | translation | 121 | 2.09 | 0.82 | 9.05 |
| outage-ollama-after-hybrid | analysis | 7 | 4823.71 | 5010.80 | 10952.17 |
| outage-ollama-after-hybrid | constraint-validation | 7 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-hybrid | embedding | 7 | 23990.86 | 25769.80 | 39084.20 |
| outage-ollama-after-hybrid | execution | 7 | 4.14 | 3.01 | 10.70 |
| outage-ollama-after-hybrid | input-validation | 7 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-hybrid | ranking | 7 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-hybrid | translation | 7 | 0.57 | 0.70 | 2.04 |
| outage-ollama-after-vector | analysis | 13 | 252.62 | 0.59 | 2272.75 |
| outage-ollama-after-vector | constraint-validation | 13 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-vector | embedding | 13 | 15562.54 | 15032.39 | 36364.06 |
| outage-ollama-after-vector | execution | 13 | 3.23 | 2.01 | 7.93 |
| outage-ollama-after-vector | input-validation | 13 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-vector | ranking | 13 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-vector | translation | 13 | 0.08 | 0.50 | 0.95 |
| outage-ollama-after-memory | analysis | 120 | 800.38 | 0.71 | 5726.62 |
| outage-ollama-after-memory | constraint-validation | 120 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-memory | embedding | 120 | 0.15 | 0.52 | 0.99 |
| outage-ollama-after-memory | execution | 120 | 0.52 | 0.54 | 2.01 |
| outage-ollama-after-memory | input-validation | 120 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-memory | ranking | 120 | 0.00 | 0.50 | 0.95 |
| outage-ollama-after-memory | translation | 120 | 1.42 | 0.70 | 6.99 |
| outage-opensearch-before-keyword | analysis | 126 | 0.02 | 0.50 | 0.95 |
| outage-opensearch-before-keyword | constraint-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-keyword | embedding | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-keyword | execution | 126 | 4.00 | 3.91 | 7.39 |
| outage-opensearch-before-keyword | input-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-keyword | ranking | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-keyword | translation | 126 | 0.87 | 0.62 | 2.07 |
| outage-opensearch-before-memory | analysis | 126 | 0.01 | 0.50 | 0.95 |
| outage-opensearch-before-memory | constraint-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-memory | embedding | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-memory | execution | 126 | 0.34 | 0.51 | 0.97 |
| outage-opensearch-before-memory | input-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-memory | ranking | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-before-memory | translation | 126 | 0.74 | 0.60 | 2.03 |
| outage-opensearch-during-memory | analysis | 125 | 0.02 | 0.50 | 0.96 |
| outage-opensearch-during-memory | constraint-validation | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-during-memory | embedding | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-during-memory | execution | 125 | 0.38 | 0.51 | 0.97 |
| outage-opensearch-during-memory | input-validation | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-during-memory | ranking | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-during-memory | translation | 125 | 0.88 | 0.64 | 2.03 |
| outage-opensearch-after-keyword | analysis | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-keyword | constraint-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-keyword | embedding | 126 | 0.01 | 0.50 | 0.95 |
| outage-opensearch-after-keyword | execution | 126 | 7.98 | 7.48 | 11.09 |
| outage-opensearch-after-keyword | input-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-keyword | ranking | 126 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-keyword | translation | 126 | 0.85 | 0.64 | 2.85 |
| outage-opensearch-after-memory | analysis | 125 | 0.02 | 0.50 | 0.95 |
| outage-opensearch-after-memory | constraint-validation | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-memory | embedding | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-memory | execution | 125 | 0.30 | 0.50 | 0.95 |
| outage-opensearch-after-memory | input-validation | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-memory | ranking | 125 | 0.00 | 0.50 | 0.95 |
| outage-opensearch-after-memory | translation | 125 | 1.06 | 0.69 | 2.07 |
| outage-redis-before-keyword | analysis | 125 | 0.03 | 0.50 | 0.96 |
| outage-redis-before-keyword | constraint-validation | 125 | 0.00 | 0.50 | 0.95 |
| outage-redis-before-keyword | embedding | 125 | 0.00 | 0.50 | 0.95 |
| outage-redis-before-keyword | execution | 125 | 5.53 | 5.50 | 8.80 |
| outage-redis-before-keyword | input-validation | 125 | 0.00 | 0.50 | 0.95 |
| outage-redis-before-keyword | ranking | 125 | 0.00 | 0.50 | 0.95 |
| outage-redis-before-keyword | translation | 125 | 0.74 | 0.61 | 2.01 |
| outage-redis-after-keyword | analysis | 126 | 28.03 | 0.51 | 0.97 |
| outage-redis-after-keyword | constraint-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-redis-after-keyword | embedding | 126 | 0.01 | 0.50 | 0.95 |
| outage-redis-after-keyword | execution | 126 | 6.06 | 6.31 | 9.11 |
| outage-redis-after-keyword | input-validation | 126 | 0.00 | 0.50 | 0.95 |
| outage-redis-after-keyword | ranking | 126 | 0.00 | 0.50 | 0.95 |
| outage-redis-after-keyword | translation | 126 | 0.87 | 0.63 | 2.09 |

## Per-workload latency (combined and outage phases)

| Phase | Workload | p50 ms | p95 ms | p99 ms |
|---|---|---|---|---|
| combined | suggestions | 42720.0 | 60000.7 | 60001.1 |
| combined | planned | 45599.1 | 60000.5 | 60001.5 |
| combined | analyze | 59060.8 | 60000.4 | 60000.4 |
| combined | sessions | 24368.5 | 60001.7 | 60002.6 |
| combined | memory | 40107.5 | 60000.1 | 60000.1 |
| combined | keyword | 36956.0 | 60001.7 | 60001.9 |
| combined | hybrid | 59999.8 | 60001.4 | 60001.6 |
| combined | vector | 59999.9 | 60001.2 | 60002.1 |

## Core retrieval engine (in-process, precomputed vectors)

| Workload | Docs | Dims | Top-K | Concurrency | p50 ms | p95 ms | p99 ms | QPS |
|---|---|---|---|---|---|---|---|---|
| keyword | 1000 | 0 | 10 | 1 | 0.119 | 0.244 | 0.325 | 7750 |
| keyword | 1000 | 0 | 10 | 8 | 0.242 | 0.606 | 0.823 | 27580 |
| keyword | 1000 | 0 | 10 | 32 | 0.110 | 0.211 | 0.336 | 91064 |
| keyword | 10000 | 0 | 10 | 1 | 1.472 | 2.500 | 3.104 | 647 |
| keyword | 10000 | 0 | 10 | 8 | 1.318 | 2.704 | 3.921 | 5270 |
| keyword | 10000 | 0 | 10 | 32 | 1.429 | 14.487 | 31.773 | 7808 |
| keyword | 100000 | 0 | 10 | 1 | 21.315 | 33.520 | 38.018 | 46 |
| keyword | 100000 | 0 | 10 | 8 | 19.676 | 32.652 | 38.320 | 375 |
| keyword | 100000 | 0 | 10 | 32 | 66.624 | 112.453 | 138.150 | 449 |
| vector | 1000 | 384 | 5 | 1 | 0.246 | 0.291 | 0.362 | 3956 |
| vector | 1000 | 384 | 5 | 8 | 0.645 | 0.928 | 0.985 | 14292 |
| vector | 1000 | 384 | 5 | 32 | 0.244 | 0.535 | 1.582 | 40574 |
| vector | 1000 | 384 | 20 | 1 | 0.243 | 0.277 | 0.314 | 4049 |
| vector | 1000 | 384 | 20 | 8 | 0.245 | 0.272 | 0.324 | 31652 |
| vector | 1000 | 384 | 20 | 32 | 0.245 | 0.546 | 1.642 | 41995 |
| vector | 1000 | 384 | 100 | 1 | 0.245 | 0.280 | 0.307 | 4004 |
| vector | 1000 | 384 | 100 | 8 | 0.246 | 0.266 | 0.315 | 31755 |
| vector | 1000 | 384 | 100 | 32 | 0.247 | 0.582 | 1.755 | 38312 |
| vector | 10000 | 384 | 5 | 1 | 2.909 | 4.540 | 5.057 | 321 |
| vector | 10000 | 384 | 5 | 8 | 3.240 | 3.587 | 3.762 | 2440 |
| vector | 10000 | 384 | 5 | 32 | 3.564 | 28.169 | 39.364 | 3479 |
| vector | 10000 | 384 | 20 | 1 | 3.162 | 3.550 | 3.759 | 314 |
| vector | 10000 | 384 | 20 | 8 | 3.338 | 3.681 | 4.075 | 2364 |
| vector | 10000 | 384 | 20 | 32 | 3.553 | 28.213 | 43.343 | 3452 |
| vector | 10000 | 384 | 100 | 1 | 3.177 | 3.546 | 3.720 | 313 |
| vector | 10000 | 384 | 100 | 8 | 3.379 | 3.690 | 4.002 | 2351 |
| vector | 10000 | 384 | 100 | 32 | 3.403 | 27.983 | 41.083 | 3451 |
| vector | 50000 | 384 | 5 | 1 | 19.759 | 20.485 | 20.897 | 50 |
| vector | 50000 | 384 | 5 | 8 | 20.016 | 20.653 | 21.686 | 399 |
| vector | 50000 | 384 | 5 | 32 | 58.716 | 91.596 | 113.176 | 524 |
| vector | 50000 | 384 | 20 | 1 | 19.818 | 20.453 | 20.789 | 50 |
| vector | 50000 | 384 | 20 | 8 | 20.216 | 21.183 | 21.826 | 394 |
| vector | 50000 | 384 | 20 | 32 | 59.585 | 90.000 | 101.587 | 519 |
| vector | 50000 | 384 | 100 | 1 | 19.831 | 20.475 | 20.776 | 50 |
| vector | 50000 | 384 | 100 | 8 | 20.035 | 20.739 | 21.642 | 398 |
| vector | 50000 | 384 | 100 | 32 | 59.504 | 87.999 | 99.347 | 518 |
| vector | 10000 | 1024 | 5 | 1 | 7.683 | 9.200 | 9.574 | 127 |
| vector | 10000 | 1024 | 5 | 8 | 7.681 | 7.899 | 8.253 | 1040 |
| vector | 10000 | 1024 | 5 | 32 | 18.027 | 49.156 | 67.924 | 1432 |
| vector | 10000 | 1024 | 20 | 1 | 7.596 | 7.972 | 8.159 | 131 |
| vector | 10000 | 1024 | 20 | 8 | 7.685 | 7.904 | 8.319 | 1038 |
| vector | 10000 | 1024 | 20 | 32 | 19.084 | 47.504 | 65.190 | 1411 |
| vector | 10000 | 1024 | 100 | 1 | 7.595 | 7.971 | 8.129 | 131 |
| vector | 10000 | 1024 | 100 | 8 | 7.689 | 7.902 | 8.444 | 1039 |
| vector | 10000 | 1024 | 100 | 32 | 17.888 | 48.182 | 67.377 | 1432 |
| hybrid | 10000 | 384 | 10 | 1 | 3.868 | 4.206 | 4.384 | 257 |
| hybrid | 10000 | 384 | 10 | 8 | 4.229 | 5.533 | 6.495 | 1805 |
| hybrid | 10000 | 384 | 10 | 32 | 14.700 | 29.254 | 40.781 | 1901 |
| fusion | 60 | 0 | 10 | 1 | 0.020 | 0.023 | 0.030 | 49788 |

## AI and language stages

Providers: {"chat": "ollama qwen2.5", "endpoint": "http://localhost:11434", "external providers": "none configured; not measured", "embedding": "ollama bge-m3", "translation (v1 pipeline)": "qwenbridge.translation.provider=none", "translation (Commerce v2)": "model, always (measured as commerce-model-translation)"}

| Stage | Provider | Concurrency | Batch | Calls | Failures | p50 ms | p95 ms | Throughput/s |
|---|---|---|---|---|---|---|---|---|
| language-detection | lingua (in-process) | 1 | 1 | 2000 | 0 | 0.03 | 0.36 | 3223.3 |
| intent-analysis | core rules (in-process) | 1 | 1 | 2000 | 0 | 0.07 | 0.15 | 11466.6 |
| constraint-extraction | core rules (in-process) | 1 | 1 | 2000 | 0 | 0.11 | 0.29 | 6574.1 |
| language-detection | lingua (in-process) | 8 | 1 | 2000 | 0 | 0.02 | 1.00 | 24311.7 |
| intent-analysis | core rules (in-process) | 8 | 1 | 2000 | 0 | 0.08 | 0.18 | 58892.3 |
| constraint-extraction | core rules (in-process) | 8 | 1 | 2000 | 0 | 0.13 | 0.55 | 31134.8 |
| model-analysis-cold | ollama qwen2.5 | 1 | 1 | 1 | 0 | 2758.77 | 2758.77 | 0.4 |
| model-analysis | ollama qwen2.5 | 1 | 1 | 8 | 0 | 2760.31 | 3796.73 | 0.3 |
| model-analysis | ollama qwen2.5 | 2 | 1 | 8 | 0 | 6171.71 | 7560.05 | 0.3 |
| model-analysis | ollama qwen2.5 | 4 | 1 | 12 | 9 | 10003.83 | 12552.44 | 0.4 |
| commerce-model-translation | ollama qwen2.5 (TranslationStages.model) | 1 | 1 | 8 | 0 | 617.58 | 4683.21 | 0.8 |
| commerce-model-translation | ollama qwen2.5 (TranslationStages.model) | 2 | 1 | 8 | 0 | 1248.23 | 1668.78 | 1.4 |
| model-backlog-drain | ollama (after abandoned analysis calls) | 1 | 1 | 1 | 0 | 1349.93 | 1349.93 | 0.0 |
| embedding-cold | ollama bge-m3 | 1 | 1 | 1 | 0 | 36.37 | 36.37 | 27.3 |
| embedding | ollama bge-m3 | 1 | 1 | 10 | 0 | 16.55 | 25.99 | 55.7 |
| embedding | ollama bge-m3 | 1 | 8 | 10 | 0 | 131.20 | 151.93 | 59.8 |
| embedding | ollama bge-m3 | 1 | 32 | 10 | 0 | 567.84 | 690.70 | 54.8 |
| embedding | ollama bge-m3 | 4 | 1 | 40 | 0 | 55.25 | 81.22 | 65.5 |
