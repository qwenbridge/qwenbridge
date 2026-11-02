#!/usr/bin/env bash
# QB-286 (and QB-291 typo correction): multilingual query expansion on the real packaged Quarkus
# application, database-free,
# with two real local providers: the glossary provider (phrase tables: the test fixtures in
# qwenbridge-server/src/test/resources/multilingual/glossary) and the configured local chat model
# (Ollama) for languages no table covers. Eight configured identifiers, one of them invalid. REST,
# SSE, the Java SDK and the TypeScript SDK. The model's translations are recorded as evidence; their
# quality is not asserted beyond the engine's own preservation checks. Nothing here starts, stops
# or changes Docker services or data.

QB286_PORT="${QB286_PORT:-18286}"

qb286_multilingual_expansion_validation() {
  local work state log base pid status=0
  work="$(section1_work_dir)/qb286" || return 1
  mkdir -p "${work}" || return 1
  state="$(mktemp -d "${work}/state.XXXXXX")" || return 1
  log="${work}/multilingual-expansion.log"
  base="http://localhost:${QB286_PORT}"
  if curl -s -o /dev/null "${base}/"; then
    echo "Port ${QB286_PORT} is in use; set QB286_PORT." >&2
    return 1
  fi
  [[ -f qwenbridge-server/target/quarkus-app/quarkus-run.jar ]] \
    || mvn -B -q package -pl qwenbridge-server -am -DskipTests || return 1

  QUARKUS_HTTP_PORT="${QB286_PORT}" \
    QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false \
    QWENBRIDGE_AI_OLLAMA_BASE_URL="${OLLAMA_URL:-http://127.0.0.1:11434}" \
    QWENBRIDGE_AI_OLLAMA_READ_TIMEOUT=120s \
    QWENBRIDGE_COMMERCE_BACKEND=memory \
    QWENBRIDGE_SEARCH_BACKEND=in-memory \
    QWENBRIDGE_DEMO_DATASETS=commerce-demo \
    QWENBRIDGE_PLATFORM_STORE=file \
    QWENBRIDGE_PLATFORM_FILE_DIR="${state}" \
    QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL=http://127.0.0.1:9 \
    QUARKUS_REDIS_HOSTS=redis://127.0.0.1:9 \
    QWENBRIDGE_ANALYSIS_CACHE_ENABLED=false \
    QWENBRIDGE_ANALYSIS_CACHE_ANALYSIS_TIMEOUT=120s \
    QWENBRIDGE_ABUSE_PROTECTION_REDIS_ENABLED=false \
    QWENBRIDGE_ANALYSIS_CACHE_REDIS_HOST=127.0.0.1 \
    QWENBRIDGE_ANALYSIS_CACHE_REDIS_PORT=9 \
    QWENBRIDGE_MULTILINGUAL_ENABLED=true \
    QWENBRIDGE_MULTILINGUAL_TARGET_LANGUAGES="en,sv,fa,nb,nn,de,ja,DK" \
    QWENBRIDGE_MULTILINGUAL_PROVIDERS="glossary,model" \
    QWENBRIDGE_MULTILINGUAL_GLOSSARY_DIR="${PWD}/qwenbridge-server/src/test/resources/multilingual/glossary" \
    QWENBRIDGE_MULTILINGUAL_PER_LANGUAGE_TIMEOUT=90s \
    QWENBRIDGE_MULTILINGUAL_TIMEOUT=180s \
    QWENBRIDGE_TYPO_ENABLED=true \
    QWENBRIDGE_TYPO_LANGUAGES=en,sv \
    QWENBRIDGE_TYPO_MODE=SAFE_AUTO_CORRECT \
    QWENBRIDGE_TYPO_DICTIONARY_DIR="${PWD}/qwenbridge-server/src/test/resources/typo" \
    java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar >"${log}" 2>&1 &
  pid=$!
  local i ready=""
  for i in $(seq 1 120); do
    ready="$(curl -s -o /dev/null -w '%{http_code}' "${base}/q/health/ready" || true)"
    [[ "${ready}" == "200" ]] && break
    kill -0 "${pid}" 2>/dev/null || break
    sleep 1
  done
  if [[ "${ready}" != "200" ]]; then
    echo "Not ready (readiness ${ready}); log tail:" >&2
    tail -20 "${log}" | cut -c1-300 >&2
    terminate_background_process "${pid}"
    return 1
  fi

  BASE="${base}" WORK="${work}" python3 - <<'PY' || status=1
import json, os, urllib.request, urllib.error
base, work = os.environ["BASE"], os.environ["WORK"]
QUERY = "I need a red winter jacket under 100 euros"
def call(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=400) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
results = []
def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + (" - " + detail if detail else ""))

s, b = call("GET", "/api/v2/multilingual")
cfg = json.loads(b) if s == 200 else {}
providers = {p["id"]: p["status"] for p in cfg.get("providers", [])}
check("configuration endpoint: enabled, eight identifiers, both local providers ready",
      s == 200 and cfg.get("enabled") is True and len(cfg.get("targets", [])) == 8
      and providers == {"glossary": "READY", "model-chat": "READY"}, f"{s} providers={providers}")

s, b = call("POST", "/api/v2/search", {"query": QUERY, "requestId": "qb286-live-1"})
open(os.path.join(work, "search-expansion.json"), "w").write(b)
d = json.loads(b) if s == 200 else {}
e = d.get("expansion") or {}
t = {x.get("language") or x["requested"]: x for x in e.get("targets", [])}
check("one explicit result per configured language, in order",
      s == 200 and e.get("requestedLanguages") == ["en", "sv", "fa", "nb", "nn", "de", "ja", "DK"] and len(e.get("targets", [])) == 8,
      f"{s} status={d.get('status')} requested={e.get('requestedLanguages')}")
check("original preserved and source established", e.get("original") == QUERY and (e.get("source") or {}).get("language") == "en",
      f"source={e.get('source')}")
glossary = {"sv": "jag behöver en röd vinterjacka under 100 euro", "fa": "من نیاز دارم به یک ژاکت زمستانی قرمز زیر 100 یورو",
            "nb": "jeg trenger en rød vinterjakke under 100 euro", "nn": "eg treng ein raud vinterjakke under 100 euro"}
check("same language is unchanged", t.get("en", {}).get("status") == "UNCHANGED" and t.get("en", {}).get("text") == QUERY)
check("glossary provider translations (sv, fa, nb, nn)",
      all(t.get(k, {}).get("status") == "SUCCESS" and t.get(k, {}).get("text") == v and t.get(k, {}).get("provider") == "glossary" for k, v in glossary.items()),
      str({k: (t.get(k, {}).get("status"), t.get(k, {}).get("text")) for k in glossary}))
model_ok = True
for lang in ("de", "ja"):
    x = t.get(lang, {})
    produced = x.get("text")
    honest = x.get("status") in ("SUCCESS", "LOW_CONFIDENCE", "FAILED") and x.get("provider") == "model-chat" \
        and (produced is None or (produced != QUERY and "100" in produced or "１００" in produced))
    model_ok = model_ok and honest
    print(f"  model translation {lang}: status={x.get('status')} reason={x.get('reason')} preservation={(x.get('preservation') or {}).get('status')} text={produced!r}")
check("local model provider answers de and ja explicitly (text only when produced, number kept)", model_ok)
check("invalid identifier reported, nothing invented", t.get("DK", {}).get("status") == "INVALID_LANGUAGE" and "text" not in t.get("DK", {}))
check("every result references the original", all(x.get("originalRef") == e.get("originalRef") for x in e.get("targets", [])))

s, b = call("POST", "/api/v2/search", {"query": "show errors from redis03-pt1 in the last 30 minutes", "requestId": "qb286-live-2"})
open(os.path.join(work, "search-technical.json"), "w").write(b)
d2 = json.loads(b) if s == 200 else {}
e2 = d2.get("expansion") or {}
t2 = {x.get("language") or x["requested"]: x for x in e2.get("targets", [])}
if d2.get("status") in ("BLOCKED", "REVIEW"):
    # The model's safety decision held the query: it must not reach any translation provider.
    check("a query held by the safety checks is never expanded", s == 200 and "expansion" not in d2, f"status={d2.get('status')}")
else:
    sv = t2.get("sv", {})
    produced_ok = all(("redis03-pt1" in x["text"] and "30" in x["text"]) for x in e2.get("targets", []) if x.get("text"))
    check("technical identifiers and numbers survive in every produced variant",
          s == 200 and sv.get("text") == "visa fel från redis03-pt1 de senaste 30 minuterna" and produced_ok,
          str({k: (x.get("status"), x.get("text")) for k, x in t2.items()}))

# QB-291 on the same runtime (QB-294 runtime verification): typo correction before expansion.
s, b = call("POST", "/api/v2/search", {"query": "blak shoes size 42", "requestId": "qb286-live-typo"})
sp = (json.loads(b) if s == 200 else {}).get("spelling") or {}
check("typo correction on the live runtime (original kept)",
      s == 200 and sp.get("corrected") == "black shoes size 42" and sp.get("original") == "blak shoes size 42",
      f"{s} spelling={sp.get('status')} corrected={sp.get('corrected')!r}")

req = urllib.request.Request(base + "/api/v2/search/stream", data=json.dumps({"query": QUERY}).encode(), method="POST",
                             headers={"Content-Type": "application/json", "Accept": "text/event-stream"})
with urllib.request.urlopen(req, timeout=400) as r:
    sse = r.read().decode()
open(os.path.join(work, "stream.sse"), "w").write(sse)
check("SSE terminal event carries the expansion", "event:search.completed" in sse and '"expansion":{' in sse and glossary["sv"] in sse)

failed = [n for n, ok in results if not ok]
print(f"{len(results) - len(failed)}/{len(results)} multilingual checks passed")
raise SystemExit(1 if failed else 0)
PY

  if [[ "${status}" -eq 0 ]]; then
    BASE_URL="${base}" qb286_java_sdk_live_expansion || status=1
    BASE_URL="${base}" node --input-type=module <<'NODE' || status=1
import assert from 'node:assert/strict';
import { CommerceClient } from './qwenbridge-typescript-sdk/dist/index.js';
const client = new CommerceClient({ baseUrl: process.env.BASE_URL });
const response = await client.search({ query: 'I need a red winter jacket under 100 euros' });
const e = response.expansion;
assert.ok(e, 'no expansion');
assert.deepEqual(e.requestedLanguages, ['en', 'sv', 'fa', 'nb', 'nn', 'de', 'ja', 'DK']);
assert.equal(e.targets.length, 8);
assert.equal(e.targets.find(t => t.language === 'sv').text, 'jag behöver en röd vinterjacka under 100 euro');
assert.equal(e.targets.find(t => t.requested === 'DK').status, 'INVALID_LANGUAGE');
console.log(`PASS: TypeScript SDK reads the live expansion (${e.status}, ${e.successfulLanguages.join(',')})`);
NODE
  fi
  terminate_background_process "${pid}"
  if grep -q '"level":"ERROR"' "${log}"; then
    echo "Errors logged by the application:" >&2
    grep '"level":"ERROR"' "${log}" | cut -c1-300 >&2
    status=1
  fi
  return "${status}"
}

qb286_java_sdk_live_expansion() {
  local dir cp_file sdk_jar
  dir="$(section1_work_dir)" || return 1
  cp_file="${dir}/java-sdk-classpath.txt"
  if [[ ! -s "${cp_file}" ]]; then
    mvn -B -q install -pl qwenbridge-java-sdk -am -DskipTests || return 1
    mvn -B -q dependency:build-classpath -pl qwenbridge-java-sdk -Dmdep.outputFile="${cp_file}" || return 1
  fi
  sdk_jar="$(ls qwenbridge-java-sdk/target/qwenbridge-java-sdk-*.jar | grep -v -- '-sources\|-javadoc' | head -1)"
  [[ -n "${sdk_jar}" ]] || return 1
  BASE_URL="${BASE_URL}" java -cp "${sdk_jar}:$(cat "${cp_file}")" scripts/verification/sdk/JavaSdkLiveExpansion.java
}
