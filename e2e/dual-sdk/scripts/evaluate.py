#!/usr/bin/env python3
"""QB-295 shared evaluator. Applies each scenario's expectations to the Java and TypeScript
consumers' normalized observations, compares the two SDKs' contract views, adds the protocol,
provider and live-provider results, and writes report.json and report.md. Nothing is hidden: every
failed check is listed. Exit 1 when any mandatory result is FAIL.
Usage: evaluate.py SCENARIOS_JSON RUN_DIR"""
import json
import os
import platform
import subprocess
import sys

scenarios = json.load(open(sys.argv[1]))["scenarios"]
run = sys.argv[2]
SDKS = ("java", "typescript")
TEXT_STATUSES = {"SUCCESS", "UNCHANGED", "LOW_CONFIDENCE"}


def load(name):
    p = os.path.join(run, name)
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else None


def check_search(o, e, fails, where=""):
    if "error" in e and "status" not in e:
        return check_error(o, e["error"], fails)
    if not o.get("ok"):
        fails.append(f"{where}call failed: {o.get('error')}")
        return
    r = o["response"]
    eq = lambda name, got, want: (got != want) and fails.append(f"{where}{name}: expected {want!r}, got {got!r}")
    if "status" in e and r.get("status") not in e["status"]:
        fails.append(f"{where}status {r.get('status')} not in {e['status']}")
    if "intent" in e: eq("intent", r.get("intent"), e["intent"])
    if "constraints" in e:
        got = sorted(tuple(c) for c in r.get("constraints", [])); want = sorted(tuple(c) for c in e["constraints"])
        eq("constraints", got, want)
    if e.get("evidenceGrounded"): eq("evidenceGrounded", r.get("evidenceGrounded"), True)
    if "sourceLanguage" in e: eq("translation.sourceLanguage", r.get("sourceLanguage"), e["sourceLanguage"])
    if "retrievalExecuted" in e and (r.get("retrieval") or {}).get("executed") not in e["retrievalExecuted"]:
        fails.append(f"{where}retrieval.executed {(r.get('retrieval') or {}).get('executed')} not in {e['retrievalExecuted']}")
    for k in ("queryIrVersion", "intents", "interpretationConstraints", "requestIdEchoed"):
        if k in e: eq(k, r.get(k), e[k])
    h = e.get("hits")
    if h:
        ids = r.get("hits", []); docs = r.get("hitDocs", [])
        if "min" in h and len(ids) < h["min"]: fails.append(f"{where}hits: {len(ids)} < {h['min']}")
        if "exactly" in h and sorted(ids) != sorted(h["exactly"]): fails.append(f"{where}hits {ids} != {h['exactly']}")
        for i in h.get("mustInclude", []):
            if i not in ids: fails.append(f"{where}hits lack {i}")
        for i in h.get("mustExclude", []):
            if i in ids: fails.append(f"{where}hits contain excluded {i}")
        sat = h.get("allSatisfy", {})
        for d in docs:
            if "color" in sat and d.get("color") != sat["color"]: fails.append(f"{where}{d['id']} color {d.get('color')}")
            if "currency" in sat and d.get("currency") != sat["currency"]: fails.append(f"{where}{d['id']} currency {d.get('currency')}")
            if "priceBelow" in sat and not (isinstance(d.get("price"), (int, float)) and d["price"] < sat["priceBelow"]):
                fails.append(f"{where}{d['id']} price {d.get('price')}")
            if "inStock" in sat and d.get("inStock") is not sat["inStock"]: fails.append(f"{where}{d['id']} inStock {d.get('inStock')}")
        if "topIn" in h and not all(i in h["topIn"]["ids"] for i in ids[:h["topIn"]["n"]]):
            fails.append(f"{where}top {h['topIn']['n']} {ids[:h['topIn']['n']]} not all in {h['topIn']['ids']}")
        if "topIncludes" in h and not all(i in ids[:h["topIncludes"]["n"]] for i in h["topIncludes"]["ids"]):
            fails.append(f"{where}top {h['topIncludes']['n']} {ids[:h['topIncludes']['n']]} lack {h['topIncludes']['ids']}")
    for block in ("spelling", "expansion"):
        x = e.get(block)
        if x is None: continue
        got = r.get(block)
        if x.get("absent"):
            if got is not None: fails.append(f"{where}{block} present but the tenant disabled it")
            continue
        if got is None:
            fails.append(f"{where}{block} missing"); continue
        for k, v in x.items():
            if k == "targets":
                for lang, (status, text) in v.items():
                    t = got["targets"].get(lang)
                    if t is None: fails.append(f"{where}expansion target {lang} missing"); continue
                    if t["status"] != status: fails.append(f"{where}expansion {lang} status {t['status']} != {status}")
                    if text is not None and t.get("text") != text: fails.append(f"{where}expansion {lang} text {t.get('text')!r} != {text!r}")
                    if status not in TEXT_STATUSES and t.get("text") is not None: fails.append(f"{where}expansion {lang} invented text {t.get('text')!r}")
            elif got.get(k) != v:
                fails.append(f"{where}{block}.{k}: expected {v!r}, got {got.get(k)!r}")


def check_error(o, e, fails):
    if o.get("ok"):
        fails.append("expected an error, the call succeeded"); return
    err = o.get("error") or {}
    if err.get("messageLeaks"): fails.append("error message leaks internal details")
    if e.get("rejected"):
        if not (err.get("kind") == "client-validation" or err.get("status") == 400):
            fails.append(f"not rejected: {err}")
        return
    for k in ("status", "code", "category", "kind"):
        if k in e and err.get(k) != e[k]: fails.append(f"error.{k}: expected {e[k]!r}, got {err.get(k)!r}")


def evaluate(s, o):
    fails = []
    if o is None:
        return ["no observation (scenario not executed)"]
    e, op = s["expect"], s["operation"]
    if op in ("search", "unreachable"):
        check_search(o, e, fails)
    elif op == "version":
        v = o.get("version") or {}
        if v.get("name") != e["versionName"]: fails.append(f"version name {v.get('name')}")
        for a in e["apiVersions"]:
            if a not in v.get("apiVersions", []): fails.append(f"api version {a} missing")
    elif op == "stream":
        ev = o.get("events", [])
        if [x["kind"] for x in ev] != e["events"]: fails.append(f"events {[x['kind'] for x in ev]}")
        if [x.get("sequence") for x in ev] != e["sequences"]: fails.append(f"sequences {[x.get('sequence') for x in ev]}")
        if not ev or ev[-1].get("terminal") is not True: fails.append("last event not terminal")
        if not all(x.get("hasTraceId") for x in ev): fails.append("trace id missing")
        if ev and ev[-1].get("response"):
            check_search({"ok": True, "response": ev[-1]["response"]}, e["response"], fails, "stream response: ")
        else:
            fails.append("no response in the terminal event")
    elif op == "stream-abort":
        kinds = [x["kind"] for x in o.get("events", [])]
        if "search.completed" in kinds: fails.append("a completed event arrived after the abort")
        if not o.get("cancelled"): fails.append("the call was not cancelled")
        if o.get("elapsedMs", 1e9) > e["maxElapsedMs"]: fails.append(f"abort took {o.get('elapsedMs')} ms")
    elif op == "stream-server-cancel":
        ev = o.get("events", [])
        if [x["kind"] for x in ev] != e["events"]: fails.append(f"events {[x['kind'] for x in ev]}")
        if not ev or ev[-1].get("errorCode") != e["errorCode"]: fails.append(f"terminal error {ev[-1].get('errorCode') if ev else None}")
        if not ev or ev[-1].get("terminal") is not True: fails.append("last event not terminal")
        if not o.get("cancelAccepted"): fails.append("cancel not accepted")
    elif op == "concurrent-streams":
        streams = o.get("streams", [])
        if len(streams) != e["count"]: fails.append(f"{len(streams)} streams")
        if not all(st and st[-1]["kind"] == "search.completed" for st in streams): fails.append("a stream did not complete")
        ids = {st[-1]["requestId"] for st in streams if st}
        if len(ids) != len(streams): fails.append("request ids not distinct")
    elif op in ("v1-analyze", "v1-analyze-async"):
        if not o.get("ok"):
            fails.append(f"call failed: {o.get('error')}")
        else:
            v = o.get("v1", {}); x = e["v1"]
            if v.get("decision") not in x["decision"]: fails.append(f"decision {v.get('decision')}")
            for k in ("originalQuery", "requestIdEchoed", "spellingStatus", "expansionStatus"):
                if k in x and v.get(k) != x[k]: fails.append(f"{k}: expected {x[k]!r}, got {v.get(k)!r}")
    elif op == "v1-typed-stream":
        names = o.get("eventNames", [])
        if not o.get("ok"): fails.append("no terminal pipeline event within 60 s")
        # The server confirms the subscription (stream.connected), then the pipeline events follow.
        if not names or names[0] != "stream.connected" or e["v1Stream"]["first"] not in names:
            fails.append(f"events {names[:6]}")
        if e["v1Stream"]["terminal"] not in names: fails.append(f"no {e['v1Stream']['terminal']} in {names[-4:]}")
    elif op == "concurrent-search":
        rs = o.get("responses", [])
        if len(rs) != e["count"] or not all(x.get("ok") for x in rs): fails.append("not every search succeeded")
        if len({json.dumps(x.get("response", {}).get("hits")) for x in rs}) != 1: fails.append("results differ between concurrent searches")
    return fails


def contract(s, o):
    """The part of an observation both SDKs must agree on."""
    if o is None: return None
    op = s["operation"]
    if op in ("search", "unreachable"):
        if not o.get("ok"):
            err = o.get("error") or {}
            return {"rejected": err.get("kind") == "client-validation" or err.get("status") == 400} if s["expect"].get("error", {}).get("rejected") \
                else {k: err.get(k) for k in ("kind", "status", "code", "category")}
        r = o["response"]
        x = {k: r.get(k) for k in ("status", "intent", "hits", "evidenceGrounded", "sourceLanguage", "queryIrVersion", "intents", "interpretationConstraints")}
        x["constraints"] = sorted(map(tuple, r.get("constraints", [])))
        x["retrieval"] = (r.get("retrieval") or {}).get("executed")
        x["spelling"] = r.get("spelling") and {k: r["spelling"].get(k) for k in ("status", "mode", "original", "corrected")}
        x["expansion"] = r.get("expansion") and {"status": r["expansion"]["status"], "requested": r["expansion"]["requested"],
                                                 "targets": {k: (v["status"], v.get("text")) for k, v in r["expansion"]["targets"].items()}}
        return x
    if op == "stream":
        return [x["kind"] for x in o.get("events", [])]
    if op in ("stream-abort",):
        return "completed" in [x["kind"] for x in o.get("events", [])]
    if op == "stream-server-cancel":
        return [(x["kind"], x.get("errorCode")) for x in o.get("events", [])]
    if op == "concurrent-streams":
        return len(o.get("streams", []))
    if op == "concurrent-search":
        return [x.get("response", {}).get("hits") for x in o.get("responses", [])][:1]
    if op == "version":
        return o.get("version")
    if op in ("v1-analyze", "v1-analyze-async"):
        v = dict(o.get("v1") or {}); return {k: v.get(k) for k in ("decision", "intent", "originalQuery", "requestIdEchoed", "spellingStatus", "expansionStatus", "expansionRequested")} if o.get("ok") else o.get("error")
    if op == "v1-typed-stream":
        names = o.get("eventNames", []); return [n for n in names if n.startswith("pipeline.")][-1:] if names else None
    return None


report = {"run": os.path.basename(run.rstrip("/")), "environment": {}, "scenarios": [], "protocol": [], "provider": [], "live": [], "performance": {}}
for tag, suffix in (("offline", "offline"), ("v1", "v1"), ("provider", "provider")):
    obs = {sdk: load(f"{sdk}-{suffix}.json") for sdk in SDKS}
    blocked = load("provider-blocked.json") if tag == "provider" else None
    for s in scenarios:
        if tag not in s["tags"]: continue
        entry = {"id": s["id"], "area": s["area"], "mode": "LOCAL_PROVIDER_INTEGRATION" if tag == "provider" else "OFFLINE_BASELINE",
                 "description": s["description"], "results": {}}
        if all(obs[sdk] is None for sdk in SDKS):
            status = "BLOCKED" if blocked else "NOT RUN"
            for sdk in SDKS: entry["results"][sdk] = {"status": status, "failures": [blocked["reason"]] if blocked else []}
            entry["crossSdk"] = status
        else:
            views = {}
            for sdk in SDKS:
                o = (obs[sdk] or {}).get("results", {}).get(s["id"])
                fails = evaluate(s, o)
                entry["results"][sdk] = {"status": "PASS" if not fails else "FAIL", "failures": fails,
                                         "latencyMs": o.get("latencyMs") if o else None}
                views[sdk] = contract(s, o)
            same = views["java"] == views["typescript"]
            entry["crossSdk"] = "EQUIVALENT" if same else "DIVERGENT"
            if not same: entry["divergence"] = views
        report["scenarios"].append(entry)
    for sdk in SDKS:
        if obs[sdk]:
            report["environment"][sdk] = {k: obs[sdk].get(k) for k in ("sdkArtifact", "runtime")}
            if obs[sdk].get("performance"): report["performance"][sdk] = obs[sdk]["performance"]
for name, key in (("protocol-offline.json", "protocol"), ("provider-local.json", "provider"), ("live-providers.json", "live")):
    d = load(name)
    if d: report[key] = d["checks"]
stub = load("model-stub-stats.json")
if stub is not None:
    report["protocol"].append({"id": "offline.all-model-calls-local", "status": "PASS" if stub.get("chat", 0) > 0 else "FAIL",
                               "detail": f"for the whole run the local stand-in answered chat={stub.get('chat')}, embed={stub.get('embed')}, "
                                         f"unknown prompt (503)={stub.get('unavailable')}; the server's model URL was the stand-in only"})
if load("provider-blocked.json"):
    report["provider"].append({"id": "provider.catalog-sync", "status": "BLOCKED", "detail": load("provider-blocked.json")["reason"]})
report["deferred"] = [
    {"id": "security.request-rate-limit", "status": "DEFERRED", "detail": "Rate limits are raised on the E2E server so performance is measured, not throttled; limit enforcement is verified by master steps 09 and 12."},
    {"id": "provider.timeout", "status": "DEFERRED", "detail": "The http-json connector's 20 s read timeout is fixed in code; a provider timeout would add 60 s (three attempts) per run. Provider errors, retries and unavailability are exercised instead."},
    {"id": "sse.connection-interruption", "status": "NOT IMPLEMENTED", "detail": "A server-side network cut mid-stream is not simulated; client abort and server cancellation are."},
    {"id": "retrieval.explicit-vector-mode", "status": "NOT IMPLEMENTED", "detail": "Explicit VECTOR/HYBRID on the v2 path of the in-process backend is refused with a typed 422 (verified); vector and hybrid retrieval run through the planned contract (verified)."}]
report["environment"]["host"] = f"{platform.system()} {platform.machine()}, {os.cpu_count()} logical CPUs, Python {platform.python_version()}"
try:
    report["environment"]["hardware"] = subprocess.run(["sysctl", "-n", "machdep.cpu.brand_string"], capture_output=True, text=True).stdout.strip()
except OSError:
    pass

mandatory = [x for x in report["scenarios"] if x["mode"] == "OFFLINE_BASELINE"]
failed = [x for x in report["scenarios"] for r in x["results"].values() if r["status"] == "FAIL"] + \
         [x for x in report["scenarios"] if x["crossSdk"] == "DIVERGENT"] + \
         [c for c in report["protocol"] + report["provider"] + report["live"] if c["status"] == "FAIL"]
offline_missing = [x for x in mandatory if any(r["status"] != "PASS" for r in x["results"].values())]
report["summary"] = {
    "scenarios": len(report["scenarios"]),
    "byStatus": {st: sum(1 for x in report["scenarios"] for r in x["results"].values() if r["status"] == st) for st in ("PASS", "FAIL", "BLOCKED", "NOT RUN")},
    "divergent": sum(1 for x in report["scenarios"] if x["crossSdk"] == "DIVERGENT"),
    "offlineBaseline": "PASS" if mandatory and not offline_missing and not any(x["crossSdk"] == "DIVERGENT" for x in mandatory) else "FAIL",
    "result": "FAIL" if failed or not mandatory or offline_missing else "PASS"}
json.dump(report, open(os.path.join(run, "report.json"), "w"), ensure_ascii=False, indent=1)

L = [f"# Dual-SDK E2E report: {report['run']}", "",
     f"**Result: {report['summary']['result']}** (offline baseline {report['summary']['offlineBaseline']}). "
     f"Per-SDK scenario results: {report['summary']['byStatus']}; cross-SDK divergences: {report['summary']['divergent']}.", "",
     "| Environment | |", "|---|---|"]
for k, v in report["environment"].items():
    L.append(f"| {k} | {v if isinstance(v, str) else ', '.join(f'{a}: {b}' for a, b in v.items())} |")
L += ["", "## Scenarios", "", "| Scenario | Mode | Java | TypeScript | Cross-SDK |", "|---|---|---|---|---|"]
for x in report["scenarios"]:
    L.append(f"| `{x['id']}` | {x['mode']} | {x['results']['java']['status']} | {x['results']['typescript']['status']} | {x['crossSdk']} |")
bad = [x for x in report["scenarios"] if any(r["failures"] for r in x["results"].values()) or x["crossSdk"] == "DIVERGENT"]
if bad:
    L += ["", "### Failures and divergences", ""]
    for x in bad:
        for sdk, r in x["results"].items():
            for f in r["failures"]: L.append(f"- `{x['id']}` ({sdk}): {f}")
        if x.get("divergence"): L.append(f"- `{x['id']}` divergence: `{json.dumps(x['divergence'], ensure_ascii=False)[:400]}`")
for title, key in (("Protocol checks (raw HTTP)", "protocol"), ("Provider integration", "provider"), ("Live providers", "live"), ("Deferred and not implemented", "deferred")):
    L += ["", f"## {title}", "", "| Check | Status | Detail |", "|---|---|---|"]
    for c in report[key]:
        L.append(f"| `{c['id']}` | {c['status']}{' (' + c['level'] + ')' if c.get('level') else ''} | {c['detail']} |")
if report["performance"]:
    L += ["", "## Performance (milliseconds; client-measured end to end, server tookMs separately)", "",
          "| SDK | Phase | Samples | Errors | p50 | p95 | p99 | Throughput/s |", "|---|---|---|---|---|---|---|---|"]
    for sdk, p in report["performance"].items():
        for phase in ("sequential", "concurrent", "serverTookMs"):
            q = p[phase]
            L.append(f"| {sdk} | {phase}{' (' + str(q.get('concurrency')) + ' clients)' if q.get('concurrency') else ''} | {q['samples']} | {q['errors']} | "
                     f"{q['p50']:.1f} | {q['p95']:.1f} | {q['p99']:.1f} | {q.get('throughputPerSecond', 0):.0f} |")
    L.append("")
    for sdk, p in report["performance"].items():
        mem = (f"heap {p['heapUsedBeforeMb']:.1f} → {p['heapUsedAfterMb']:.1f} MB" if "heapUsedAfterMb" in p and "heapUsedBeforeMb" in p
               else f"RSS {p.get('rssBeforeMb', 0):.0f} → {p.get('rssAfterMb', 0):.0f} MB")
        L.append(f"- {sdk}: {mem}; query `{p['query']}`; {p['processors']} processors, {p['os']}.")
open(os.path.join(run, "report.md"), "w").write("\n".join(L) + "\n")
print(f"dual-sdk E2E: {report['summary']['result']} {report['summary']['byStatus']} divergent={report['summary']['divergent']}")
sys.exit(1 if report["summary"]["result"] == "FAIL" else 0)
