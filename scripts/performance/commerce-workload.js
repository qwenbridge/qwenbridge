import http from "k6/http";
import { check } from "k6";
import execution from "k6/execution";
import { commerceCases } from "./commerce-corpus.js";

const iterations = Number(__ENV.PERF_TOTAL_REQUESTS || String(commerceCases.length));
export function configure(throughput) { return {
  scenarios: { commerce_constraints: { executor: "shared-iterations", vus: Number(__ENV.PERF_VUS || "1"), iterations, maxDuration: __ENV.PERF_MAX_DURATION || "15m" } },
  thresholds: { checks: ["rate==1"], iterations: [`count==${iterations}`], http_req_failed: ["rate==0"], http_req_duration: [throughput ? "p(95)<5000" : "p(95)<30000"] }
}; }
export function runCommerce(throughput) {
  const reference = commerceCases[throughput ? 0 : execution.scenario.iterationInTest % commerceCases.length];
  const query = reference.query;
  const requestId = `commerce-${Date.now()}-${__VU}-${__ITER}`;
  const headers = { "Content-Type": "application/json" };
  if (__ENV.QWENBRIDGE_TEST_API_TOKEN) headers.Authorization = `Bearer ${__ENV.QWENBRIDGE_TEST_API_TOKEN}`;
  const response = http.post(`${__ENV.BASE_URL || "http://localhost:8080"}/api/v2/search`, JSON.stringify({ requestId, query }), { headers, timeout: "120s" });
  let data = null; try { data = response.json(); } catch (_) {}
  const hit = data?.results?.hits?.[0]?.document;
  const fields = new Set((data?.queryIR?.constraints || []).map(c => c.field));
  check(response, {
    "v2 success": r => r.status === 200 && data?.apiVersion === 2 && data?.status === "OK",
    "all required constraints extracted": () => reference.requiredFields.every(field => fields.has(field)),
    "only reference products returned": () => JSON.stringify((data?.results?.hits || []).map(h => h.id).sort()) === JSON.stringify([...reference.expectedIds].sort()),
    "returned product satisfies every hard condition": () => reference.expectedIds.includes("shoe-good")
      ? hit?.brand === "Nike" && hit.color === "black" && hit.size === "42" && hit.price < 100 && hit.currency === "EUR" && hit.inStock === true && hit.condition !== "refurbished" && hit.tenantId === "public"
      : hit?.brand === "Lenovo" && hit.category === "laptop" && hit.price < 1500 && hit.currency === "EUR" && hit.tenantId === "public",
    "translation and source attribution": () => data?.translation?.constraintsPreserved === true && data?.evidence?.length >= 2 && data?.confidence?.calibrated === false,
    "request identity retained": () => data?.requestId === requestId
  });
}
