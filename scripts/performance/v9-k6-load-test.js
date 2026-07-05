import http from "k6/http";
import { check, sleep } from "k6";
import { randomItem, uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const totalRequests = Number(__ENV.PERF_TOTAL_REQUESTS || "1000000");
const vus = Number(__ENV.PERF_VUS || "250");
const maxDuration = __ENV.PERF_MAX_DURATION || "30m";

export const options = {
  scenarios: {
    mixed_language_users: {
      executor: "shared-iterations",
      vus,
      iterations: totalRequests,
      maxDuration
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<5000", "p(99)<15000"]
  }
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
  const selected = randomItem(queries);
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

  check(response, {
    "status is 200": r => r.status === 200,
    "has request id": r => {
      try {
        return r.json("requestId") === requestId;
      } catch {
        return false;
      }
    },
    "has pipeline trace": r => {
      try {
        return Array.isArray(r.json("pipelineTrace"));
      } catch {
        return false;
      }
    }
  });

  sleep(0.01);
}
