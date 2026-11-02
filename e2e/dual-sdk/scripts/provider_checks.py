#!/usr/bin/env python3
"""LOCAL_PROVIDER_INTEGRATION checks (QB-295). Drives the server's real catalog synchronization
(POST /api/v2/catalog/sync) against the local provider-neutral HTTP catalog provider in each of
its failure modes, then once successfully, and records the provider's request log as evidence.
Writes a JSON result; exit 1 on any failure.
Usage: provider_checks.py SERVER KEYS_JSON PROVIDER OUT_JSON"""
import json
import re
import sys
import urllib.error
import urllib.request

server, keys, provider, out = sys.argv[1], json.load(open(sys.argv[2])), sys.argv[3], sys.argv[4]
LEAK = re.compile(r"(?i)(\bat [a-z]+\.[a-z.]+\(|Exception|stack ?trace|/Users/|/home/|io\.qwenbridge|Bearer)")
results = []


def http(method, url, body=None, key=None):
    h = {"Content-Type": "application/json"}
    if key:
        h["Authorization"] = "Bearer " + keys[key]
    req = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(), method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def check(id, ok, detail):
    results.append({"id": id, "status": "PASS" if ok else "FAIL", "detail": detail})
    print(("PASS " if ok else "FAIL ") + id + " - " + detail)


def sync(mode):
    http("POST", provider + "/control", {"mode": mode})
    before = len(json.loads(http("GET", provider + "/requests")[1]))
    status, body = http("POST", server + "/api/v2/catalog/sync", {}, key="provider")
    log = [r for r in json.loads(http("GET", provider + "/requests")[1])[before:] if r["path"] == "/feed"]
    return status, body, log


for mode, id in (("unauthorized", "provider.authentication-failure"), ("malformed", "provider.malformed-response"),
                 ("error", "provider.server-error-retried")):
    status, body, log = sync(mode)
    statuses = [r["status"] for r in log]
    # Transient failures (5xx, unreadable bodies) are attempted three times; a 401 is not retried.
    retried = len(log) == (1 if mode == "unauthorized" else 3)
    # A failing provider must give a typed error, never a hang, a leak or a partial success.
    check(id, 400 <= status < 600 and not LEAK.search(body) and retried,
          f"sync {status} {json.loads(body).get('code') if body.startswith('{') else ''}; provider answered {statuses};"
          f" leak={bool(LEAK.search(body))}; retried={len(log)}x")
status, body, log = sync("rate-limited")
d = json.loads(body) if status == 200 else {}
pages = [r for r in log if r["status"] == 200]
check("provider.rate-limit-retry-and-pagination", status == 200 and [r["status"] for r in log][:2] == [429, 429] and len(pages) == 6
      and all(r["authorized"] for r in log),
      f"sync {status} {body[:160]}; provider answered {[r['status'] for r in log]}")
status, body, log = sync("empty")
check("provider.empty-page", status == 200, f"sync {status} {body[:120]}")
http("POST", provider + "/control", {"mode": "shutdown"})
import time
time.sleep(1)
status, body = http("POST", server + "/api/v2/catalog/sync", {}, key="provider")
check("provider.unavailable", 400 <= status < 600 and not LEAK.search(body),
      f"provider stopped; sync {status} {json.loads(body).get('code') if body.startswith('{') else ''}; leak={bool(LEAK.search(body))}")
json.dump({"checks": results}, open(out, "w"), indent=1)
sys.exit(0 if all(r["status"] == "PASS" for r in results) else 1)
