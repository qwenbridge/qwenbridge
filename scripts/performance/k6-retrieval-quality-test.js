import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080"\;

export const options = {
  scenarios: {
    retrieval_quality: {
      executor: "shared-iterations",
      vus: Number(__ENV.PERF_VUS || "10"),
      iterations: Number(__ENV.PERF_TOTAL_REQUESTS || "100"),
      maxDuration: __ENV.PERF_MAX_DURATION || "2m"
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<8000"]
  }
};

const cases = [
  { query: "best iphone for photography", expected: "iPhone 16 Pro" },
  { query: "android flagship smartphone with bright display", expected: "Samsung Galaxy S25" },
  { query: "wireless noise cancelling headphones for travel", expected: "Sony WH-1000XM5" },
  { query: "ergonomic wireless mouse for office work", expected: "Logitech MX Master 3S" },
  { query: "lightweight gaming mouse for esports", expected: "Razer DeathAdder V3" }
];

export default function () {
  const selected = cases[Math.floor(Math.random() * cases.length)];
  const requestId = `k6-retrieval-${Date.now()}-${Math.random()}`;

  const response = http.post(
    `${baseUrl}/api/v1/search/analyze`,
    JSON.stringify({ requestId, query: selected.query }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Request-ID": requestId
      },
      timeout: "60s"
    }
  );

  check(response, {
    "status is 200": r => r.status === 200,
    "search available": r => {
      try {
        return r.json("search.available") === true;
      } catch {
        return false;
      }
    },
    "expected title appears": r => {
      try {
        const hits = r.json("search.hits") || [];
        return hits.some(hit => hit.document && hit.document.title === selected.expected);
      } catch {
        return false;
      }
    }
  });

  sleep(0.01);
}
