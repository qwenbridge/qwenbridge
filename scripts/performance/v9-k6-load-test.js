import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import execution from "k6/execution";

const cacheHits = new Counter("cache_hits");
const cacheMisses = new Counter("cache_misses");
const cacheHitRate = new Rate("cache_hit_rate");
const aiAnalysisTime = new Trend("ai_analysis_ms");
const executionTime = new Trend("execution_ms");
const uuidv4 = () => `${Date.now()}-${__VU}-${__ITER}-${Math.random()}`;

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const totalRequests = Number(__ENV.PERF_TOTAL_REQUESTS || "1000000");
const vus = Number(__ENV.PERF_VUS || "250");
const maxDuration = __ENV.PERF_MAX_DURATION || "30m";
if (!Number.isSafeInteger(totalRequests) || totalRequests < 1 ||
    !Number.isSafeInteger(vus) || vus < 1 || vus > totalRequests) {
  throw new Error("PERF_TOTAL_REQUESTS and PERF_VUS must be positive integers; VUs cannot exceed requests");
}
const cacheExpectation = __ENV.PERF_CACHE_EXPECTATION || "any";
// Latency limits; run-performance.py lowers or raises them per phase (PERF_COLD_P95_MS, for a
// cold phase on a CPU-only model).
const p95Limit = Number(__ENV.PERF_P95_MS || "5000");
const p99Limit = Number(__ENV.PERF_P99_MS || "15000");
const thresholds = {
  checks: ["rate==1"],
  iterations: [`count==${totalRequests}`],
  http_req_failed: ["rate<0.05"],
  http_req_duration: [`p(95)<${p95Limit}`, `p(99)<${p99Limit}`]
};
if (cacheExpectation === "miss") thresholds.cache_hit_rate = ["rate==0"];
if (cacheExpectation === "hit") thresholds.cache_hit_rate = ["rate>0.99"];

export const options = {
  scenarios: {
    mixed_language_users: {
      executor: "shared-iterations",
      vus,
      iterations: totalRequests,
      maxDuration
    }
  },
  thresholds
};

const queries = [
  { lang: "en", query: "best gaming laptop under 1500 euro" },
  { lang: "sv", query: "bästa gaming laptop under 15000 kronor" },
  { lang: "fa", query: "بهترین لپ تاپ گیمینگ زیر ۱۵۰۰ یورو" },
  { lang: "de", query: "bester gaming laptop unter 1500 euro" },
  { lang: "fr", query: "meilleur ordinateur portable gaming moins de 1500 euros" },
  { lang: "es", query: "mejor portátil gaming por menos de 1500 euros" },
  { lang: "ar", query: "أفضل لابتوب ألعاب أقل من 1500 يورو" },
  { lang: "tr", query: "1500 euro altı en iyi oyun laptopu" },
  { lang: "nl", query: "beste gaming laptop onder 1500 euro" },
  { lang: "ja", query: "1500ユーロ以下の最高のゲーミングノートPC" }
];

export default function () {
  const selected = queries[execution.scenario.iterationInTest % queries.length];
  const requestId = `k6-v9-${selected.lang}-${uuidv4()}`;

  const response = http.post(
    `${baseUrl}/api/v1/search/analyze`,
    JSON.stringify({
      requestId,
      query: selected.query
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Request-ID": requestId,
        "X-Test-Language": selected.lang
      },
      timeout: "60s"
    }
  );

  let data = null;
  try { data = response.json(); } catch (_) {}
  const hit = Boolean(data && data.cache && data.cache.enabled && data.cache.hit);
  const miss = Boolean(data && data.cache && data.cache.enabled && data.cache.miss);
  cacheHits.add(hit ? 1 : 0);
  cacheMisses.add(miss ? 1 : 0);
  cacheHitRate.add(hit);
  for (const step of (data && data.pipelineTrace) || []) {
    if (step.step === "AIAnalysisStep") aiAnalysisTime.add(step.durationMs);
    if (step.step === "DecisionStep") executionTime.add(step.durationMs);
  }
  const analysed = data !== null && data.semanticValidated === true &&
    data.confidenceAssessment && data.confidenceAssessment.method === "model-signal-min-v1";
  if (!analysed || !(data && data.search && data.search.available === true)) {
    // Evidence for a failed check: which query, and what the server reported instead.
    console.warn(JSON.stringify({
      requestId, lang: selected.lang, query: selected.query, status: response.status,
      semanticValidated: data && data.semanticValidated,
      confidenceMethod: data && data.confidenceAssessment && data.confidenceAssessment.method,
      search: data && data.search, degraded: data && data.degraded,
      warnings: data && data.warnings,
      steps: ((data && data.pipelineTrace) || []).map(s => `${s.step}:${s.status || ""}:${s.durationMs}`)
    }));
  }
  check(response, {
    "status is 200": r => r.status === 200,
    "has request id": () => data !== null && data.requestId === requestId,
    "has pipeline trace": () => data !== null && Array.isArray(data.pipelineTrace),
    "has model analysis": () => Boolean(analysed),
    "has search execution": () => data !== null && data.search && data.search.available === true,
    "cache contract": () => data !== null && data.cache && data.cache.enabled === true &&
      (cacheExpectation !== "miss" || miss)
  });

  sleep(0.01);
}
