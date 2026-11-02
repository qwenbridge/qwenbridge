#!/usr/bin/env python3
"""Raw HTTP protocol checks on the real server (QB-295): what an SDK cannot send or does not
expose (malformed JSON, oversized bodies, headers, health, configuration endpoints, the v1
refusal in authenticated deployments, information leakage), and evidence that the only model
traffic went to the local stand-in. Writes a JSON result; exit 1 on any failure.
Usage: protocol_checks.py BASE KEYS_JSON OUT_JSON STUB_URL"""
import json
import re
import sys
import urllib.error
import urllib.request

base, keys, out, stub = sys.argv[1], json.load(open(sys.argv[2])), sys.argv[3], sys.argv[4]
LEAK = re.compile(r"(?i)(\bat [a-z]+\.[a-z.]+\(|Exception|stack ?trace|/Users/|/home/|e2e-[0-9a-f]{32}|io\.qwenbridge)")
results = []


def call(method, path, body=None, key="main", raw=None, headers=None, url=None):
    data = raw if raw is not None else (None if body is None else json.dumps(body).encode())
    h = {"Content-Type": "application/json", **(headers or {})}
    if key:
        h["Authorization"] = "Bearer " + keys[key]
    req = urllib.request.Request((url or base) + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, {k.lower(): v for k, v in r.headers.items()}, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, {k.lower(): v for k, v in e.headers.items()}, e.read().decode()


def check(id, ok, detail):
    results.append({"id": id, "status": "PASS" if ok else "FAIL", "detail": detail})
    print(("PASS " if ok else "FAIL ") + id + " - " + detail)


def body(text):
    try:
        return json.loads(text)
    except ValueError:
        return {}


s, h, b = call("GET", "/q/health/ready", key=None)
check("rest.health-ready", s == 200, f"{s}")
s, h, b = call("POST", "/api/v2/search", raw=b'{"query": ', headers={"X-Request-Id": "e2e-protocol-1"})
d = body(b)
check("rest.malformed-json", s == 400 and d.get("category") == "VALIDATION" and not LEAK.search(b),
      f"{s} code={d.get('code')} category={d.get('category')}")
check("rest.correlation-id-on-errors", h.get("x-request-id") == "e2e-protocol-1" and h.get("x-correlation-id") == "e2e-protocol-1",
      f"x-request-id={h.get('x-request-id')}")
s, h, b = call("POST", "/api/v2/search", raw=json.dumps({"query": "x", "pad": "a" * 70000}).encode())
d = body(b)
check("security.request-size-limit", s == 413 and not LEAK.search(b), f"{s} code={d.get('code')} message={d.get('message')!r}")
s, h, b = call("POST", "/api/v2/search", {"query": "Show available running shoes"}, headers={"X-Request-Id": "e2e-protocol-2"})
check("rest.correlation-and-trace-headers", s == 200 and h.get("x-request-id") == "e2e-protocol-2"
      and re.fullmatch(r"00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]", h.get("traceparent", "")) is not None,
      f"{s} traceparent={h.get('traceparent')}")
s, h, b = call("POST", "/api/v1/search/analyze", {"query": "red jacket"})
d = body(b)
check("rest.v1-refused-when-authenticated", s == 409 and d.get("code") == "TENANT_API_REQUIRED", f"{s} {d.get('code')}")
s, h, b = call("GET", "/api/v2/does-not-exist")
check("security.unknown-route-no-leak", s == 404 and not LEAK.search(b), f"{s}")
for path, field in (("/api/v2/multilingual", "policy"), ("/api/v2/spelling", "mode")):
    s, h, b = call("GET", path)
    d = body(b)
    check("rest.config" + path.replace("/api/v2", "").replace("/", "."), s == 200 and d.get("enabled") is True and d.get("tenant") == "e2e",
          f"{s} enabled={d.get('enabled')} {field}={d.get(field)}")
s, h, b = call("GET", "/api/v2/multilingual", key="other")
check("security.config-is-tenant-scoped", s == 200 and body(b).get("tenant") == "e2e-other", f"{s} tenant={body(b).get('tenant')}")
s, h, b = call("GET", "/stats", key=None, url=stub)
stats = body(b)
check("offline.model-traffic-local-only", s == 200 and stats.get("chat", 0) > 0 and stats.get("embed", 0) > 0,
      f"local stand-in served chat={stats.get('chat')} embed={stats.get('embed')} unavailable={stats.get('unavailable')}")
json.dump({"checks": results}, open(out, "w"), indent=1)
sys.exit(0 if all(r["status"] == "PASS" for r in results) else 1)
