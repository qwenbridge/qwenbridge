import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080"\;

export const options = {
  scenarios: {
    language_quality: {
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
  { lang: "en", query: "What are the best wireless headphones for working from home?" },
  { lang: "fa", query: "بهترین هدفون بی‌سیم برای کار کردن در خانه چیست؟" },
  { lang: "ar", query: "ما هي أفضل سماعات لاسلكية للعمل من المنزل؟" },
  { lang: "ja", query: "自宅で仕事をするための最高のワイヤレスヘッドホンは何ですか？" },
  { lang: "zh", query: "哪些无线耳机最适合在家办公？" },
  { lang: "sv", query: "Vilka trådlösa hörlurar är bäst för att arbeta hemifrån?" },
  { lang: "de", query: "Welche kabellosen Kopfhörer eignen sich am besten für die Arbeit zu Hause?" },
  { lang: "fr", query: "Quels écouteurs sans fil sont les meilleurs pour travailler à domicile ?" },
  { lang: "es", query: "¿Qué auriculares inalámbricos son mejores para trabajar desde casa?" },
  { lang: "tr", query: "Evden çalışmak için en iyi kablosuz kulaklıklar hangileridir?" },
  { lang: "nl", query: "Welke draadloze hoofdtelefoon is het beste om thuis te werken?" }
];

export default function () {
  const selected = cases[Math.floor(Math.random() * cases.length)];
  const requestId = `k6-language-${selected.lang}-${Date.now()}-${Math.random()}`;

  const response = http.post(
    `${baseUrl}/api/v1/search/analyze`,
    JSON.stringify({ requestId, query: selected.query }),
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
    "language matches": r => {
      try {
        return r.json("language") === selected.lang;
      } catch {
        return false;
      }
    }
  });

  sleep(0.01);
}
