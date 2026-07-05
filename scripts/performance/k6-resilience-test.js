import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080"\;

export const options = {
  scenarios: {
    api_resilience: {
      executor: "shared-iterations",
      vus: Number(__ENV.PERF_VUS || "10"),
      iterations: Number(__ENV.PERF_TOTAL_REQUESTS || "100"),
      maxDuration: __ENV.PERF_MAX_DURATION || "2m"
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.20"],
    http_req_duration: ["p(95)<8000"]
  }
};

const cases = [
  {
    name: "blank",
    body: JSON.stringify({ query: "" }),
    contentType: "application/json",
    accepted: [400]
  },
  {
    name: "malformed",
    body: "{\"query\":",
    contentType: "application/json",
    accepted: [400]
  },
  {
    name: "oversized",
    body: JSON.stringify({ requestId: `k6-oversized-${Date.now()}`, query: "gaming laptop ".repeat(5000) }),
    contentType: "application/json",
    accepted: [200, 400, 413, 429, 502]
  },
  {
    name: "wrong-content-type",
    body: "query=table",
    contentType: "text/plain",
    accepted: [400, 415]
  }
];

export default function () {
  const selected = cases[Math.floor(Math.random() * cases.length)];
  const requestId = `k6-resilience-${selected.name}-${Date.now()}-${Math.random()}`;

  const response = http.post(
    `${baseUrl}/api/v1/search/analyze`,
    selected.body,
    {
      headers: {
        "Content-Type": selected.contentType,
        "X-Request-ID": requestId
      },
      timeout: "60s"
    }
  );

  check(response, {
    "status is accepted": r => selected.accepted.includes(r.status),
    "status is not 500": r => r.status !== 500
  });

  sleep(0.01);
}
