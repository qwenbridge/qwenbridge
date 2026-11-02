// QB-216 – QB-222: one parameterised benchmark workload (driven by scripts/verification/run-benchmarks.py).
//   BENCH_EXECUTOR = vus (constant VUs: BENCH_VUS, BENCH_DURATION)
//                  | rate (constant arrival rate: BENCH_RATE per second, BENCH_DURATION)
//                  | iterations (BENCH_ITERATIONS at BENCH_VUS)
//                  | combined (fixed traffic mix, BENCH_RATE total per second)
//   BENCH_MODE     = KEYWORD | VECTOR | HYBRID | PLANNED | MEMORY | ANALYZE
//   BENCH_UNIQUE   = true: every query is unique (cold analysis cache)
import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";
import { commerceCases } from "./commerce-corpus.js";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const executor = __ENV.BENCH_EXECUTOR || "vus";
const duration = __ENV.BENCH_DURATION || "20s";
const rate = Number(__ENV.BENCH_RATE || "20");
const outcomes = new Counter("bench_outcomes");
// BENCH_POOL=retrieval: English queries measured to need no translation (Lingua detects no other
// language), so phases measure retrieval and query embedding, not the model. Default: the
// 31-query multilingual Commerce corpus (15 languages; non-English queries are translated by the
// model on every request, uncached).
const RETRIEVAL = ["running shoes under 120 EUR", "black sneakers size 43", "Nike shoes in stock",
  "waterproof hiking boots", "white shoes size 42", "refurbished laptop under 800 EUR"];
const queries = __ENV.BENCH_POOL === "retrieval" ? RETRIEVAL : commerceCases.map(c => c.query);
const headers = { "Content-Type": "application/json", Accept: "application/json" };
if (__ENV.QWENBRIDGE_TEST_API_TOKEN) headers.Authorization = `Bearer ${__ENV.QWENBRIDGE_TEST_API_TOKEN}`;

// Combined workload: documented proportions of total arrival rate (QB-220).
export const MIX = { keyword: 0.40, vector: 0.12, hybrid: 0.12, planned: 0.10, suggestions: 0.08, memory: 0.08, analyze: 0.06, sessions: 0.04 };

function rateScenario(name, share) {
  const r = Math.max(1, Math.round(rate * share));
  return { executor: "constant-arrival-rate", rate: r, timeUnit: "1s", duration, preAllocatedVUs: Math.max(5, r * 2), maxVUs: Math.max(20, r * 20), exec: name, tags: { workload: name } };
}

// Every outcome gets a (never failing) threshold so its count appears in the summary export.
export const OUTCOMES = ["ok", "ok-degraded", "status-review", "status-unavailable", "status-blocked", "status-partial",
  "status-clarification_required", "status-unknown", "error400", "error401", "error403", "error404", "error409", "error422",
  "error429", "error5xx-typed", "error5xx-untyped", "transport"];

function buildOptions() {
  const thresholds = { checks: ["rate>=0"] };
  for (const o of OUTCOMES) thresholds[`bench_outcomes{outcome:${o}}`] = ["count>=0"];
  if (executor === "combined") {
    const scenarios = {};
    for (const [name, share] of Object.entries(MIX)) {
      scenarios[name] = rateScenario(name, share);
      thresholds[`http_req_duration{workload:${name}}`] = ["p(95)>=0"];
      thresholds[`http_req_failed{workload:${name}}`] = ["rate>=0"];
    }
    return { scenarios, thresholds, summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"] };
  }
  let scenario;
  if (executor === "rate") scenario = { executor: "constant-arrival-rate", rate, timeUnit: "1s", duration, preAllocatedVUs: Number(__ENV.BENCH_PREALLOC || Math.max(10, rate)), maxVUs: Number(__ENV.BENCH_MAXVUS || Math.max(50, rate * 4)), exec: "single" };
  else if (executor === "iterations") scenario = { executor: "shared-iterations", vus: Number(__ENV.BENCH_VUS || "1"), iterations: Number(__ENV.BENCH_ITERATIONS || "10"), maxDuration: "30m", exec: "single" };
  else scenario = { executor: "constant-vus", vus: Number(__ENV.BENCH_VUS || "5"), duration, exec: "single" };
  return { scenarios: { single: scenario }, thresholds, summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"] };
}
export const options = buildOptions();

function query(i) {
  const q = queries[i % queries.length];
  return __ENV.BENCH_UNIQUE === "true" ? `${q} ${__VU}-${__ITER}-${Date.now()}` : q;
}

/** Classifies a search response so outages can be judged by what the server actually said. */
function record(response, workload) {
  let data = null; try { data = response.json(); } catch (_) {}
  let outcome;
  if (response.status === 0) outcome = "transport";
  else if (response.status >= 500) outcome = data && data.code ? `error5xx-typed` : "error5xx-untyped";
  else if (response.status >= 400) outcome = `error${response.status}`;
  else if (data && data.status === "OK") outcome = data.retrieval && data.retrieval.degraded ? "ok-degraded" : "ok";
  else outcome = `status-${(data && data.status) || "unknown"}`.toLowerCase();
  outcomes.add(1, { outcome, workload });
  check(response, { "answered (any typed outcome)": () => outcome !== "transport" && outcome !== "error5xx-untyped" });
  return data;
}

function search(body, workload) {
  return record(http.post(`${BASE}/api/v2/search`, JSON.stringify(body), { headers, timeout: "60s", tags: { workload } }), workload);
}

const MODE = __ENV.BENCH_MODE || "KEYWORD";
export function single() {
  const q = query(__ITER + __VU * 7);
  if (MODE === "ANALYZE") return analyze();
  if (MODE === "MEMORY") return search({ query: q, backend: "memory" }, "memory");
  if (MODE === "PLANNED") return search({ query: q, contract: "commerce-search/1.0" }, "planned");
  return search({ query: q, mode: MODE }, MODE.toLowerCase());
}
export function keyword() { search({ query: query(__ITER + __VU), mode: "KEYWORD" }, "keyword"); }
export function vector() { search({ query: query(__ITER + __VU), mode: "VECTOR" }, "vector"); }
export function hybrid() { search({ query: query(__ITER + __VU), mode: "HYBRID" }, "hybrid"); }
export function planned() { search({ query: query(__ITER + __VU), contract: "commerce-search/1.0" }, "planned"); }
export function memory() { search({ query: query(__ITER + __VU), backend: "memory" }, "memory"); }
export function suggestions() {
  const data = search({ query: query(__ITER + __VU), mode: "KEYWORD" }, "suggestions");
  if (data && data.requestId) record(http.get(`${BASE}/api/v2/search/${data.requestId}/suggestions`, { headers, tags: { workload: "suggestions" } }), "suggestions");
}
export function analyze() {
  const r = http.post(`${BASE}/api/v1/search/analyze`, JSON.stringify({ query: query(__ITER + __VU) }), { headers, timeout: "60s", tags: { workload: "analyze" } });
  outcomes.add(1, { outcome: r.status === 200 ? "ok" : `error${r.status}`, workload: "analyze" });
  check(r, { "answered (any typed outcome)": x => x.status > 0 && x.status < 500 });
}
export function sessions() {
  const s = http.post(`${BASE}/api/v2/sessions`, "{}", { headers, tags: { workload: "sessions" } });
  let id = null; try { id = s.json().id; } catch (_) {}
  if (!id) { outcomes.add(1, { outcome: `error${s.status}`, workload: "sessions" }); return; }
  record(http.post(`${BASE}/api/v2/sessions/${id}/turns`, JSON.stringify({ query: query(__ITER + __VU), expectedRevision: 0 }), { headers, timeout: "60s", tags: { workload: "sessions" } }), "sessions");
}
