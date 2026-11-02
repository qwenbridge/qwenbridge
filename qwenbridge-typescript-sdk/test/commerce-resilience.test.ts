import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { expect, it } from "vitest";
import { COMMERCE_CONTRACT_V1, COMMERCE_RESPONSE_FIELDS, CommerceClient, type CommerceEvent } from "../src/commerce-client.js";
import { QwenBridgeApiError, QwenBridgeIncompatibleServerError, QwenBridgeTransportError } from "../src/errors.js";
import { RetryPolicy } from "../src/retry/retry-policy.js";

// QB-187, QB-188, QB-190: typed errors, safe-only retries, timeouts, cancellation, stream rules.

interface Seen { method: string; path: string; timeout: string | null; body: string; }
const OK = { apiVersion: 2, requestId: "r", status: "OK", results: { hits: [], totalHits: 0, tookMillis: 1 } };
const UNAVAILABLE = { status: 503, code: "DEPENDENCY_UNAVAILABLE", category: "DEPENDENCY", retryable: true, message: "A dependency is unavailable" };

function client(reply: (seen: Seen, init?: RequestInit) => Response | Promise<Response>, options: { timeoutMs?: number; attempts?: number } = {}) {
  const seen: Seen[] = [];
  const fetch: typeof globalThis.fetch = async (url, init) => {
    const entry = { method: init?.method ?? "GET", path: new URL(String(url)).pathname, timeout: new Headers(init?.headers).get("X-Request-Timeout-Ms"), body: String(init?.body ?? "") };
    seen.push(entry);
    return reply(entry, init);
  };
  return { seen, client: new CommerceClient({ baseUrl: "http://localhost", bearerToken: "test-only-secret", fetch, timeoutMs: options.timeoutMs ?? 5_000, retry: new RetryPolicy({ maxAttempts: options.attempts ?? 3, initialDelayMs: 1, maxDelayMs: 5 }) }) };
}

it("types errors with code, category and retryability, also for non-JSON bodies", async () => {
  const { client: c, seen } = client(s => s.path.endsWith("/turns") ? new Response("<html>bad gateway</html>", { status: 502 })
    : Response.json({ status: 400, code: "BAD_REQUEST", category: "VALIDATION", retryable: false, message: "Page size must be at most 100", futureField: 1 }, { status: 400 }));
  const error = await c.search({ query: "x" }).catch(e => e);
  expect(error).toBeInstanceOf(QwenBridgeApiError);
  expect([error.status, error.code, error.category, error.retryable]).toEqual([400, "BAD_REQUEST", "VALIDATION", false]);
  expect(seen).toHaveLength(1);
  const opaque = await c.turn("s1", { query: "x" }).catch(e => e);
  expect(opaque).toBeInstanceOf(QwenBridgeApiError);
  expect(opaque.status).toBe(502);
  expect(String(opaque.message)).not.toContain("test-only-secret");
});

it("retries only safe operations, always with the same request ID", async () => {
  const { client: c, seen } = client(() => Response.json(UNAVAILABLE, { status: 503 }));
  await expect(c.search({ query: "shoes" })).rejects.toBeInstanceOf(QwenBridgeApiError);
  expect(seen).toHaveLength(3);
  expect(new Set(seen.map(s => JSON.parse(s.body).requestId)).size).toBe(1);
  seen.length = 0;
  await expect(c.turn("s1", { query: "x" })).rejects.toBeInstanceOf(QwenBridgeApiError);
  await expect(c.accept("s1", "p1", 1)).rejects.toBeInstanceOf(QwenBridgeApiError);
  await expect(c.createSession()).rejects.toBeInstanceOf(QwenBridgeApiError);
  expect(seen).toHaveLength(3);
});

it("recovers from transient failures, sends its deadline and enforces it", async () => {
  let calls = 0;
  const { client: c, seen } = client((s, init) => {
    if (s.path === "/api/v2/search") return ++calls === 1 ? Response.json(UNAVAILABLE, { status: 503 }) : Response.json(OK);
    return new Promise<Response>((_, reject) => init?.signal?.addEventListener("abort", () => reject(init.signal?.reason)));
  }, { timeoutMs: 300, attempts: 2 });
  expect((await c.search({ query: "shoes" })).status).toBe("OK");
  expect(seen[0]?.timeout).toBe("300");
  const started = Date.now();
  const error = await c.session("slow").catch(e => e);
  expect(error).toBeInstanceOf(QwenBridgeTransportError);
  expect(String(error.message)).toContain("timed out");
  expect(Date.now() - started).toBeLessThan(2_000);
});

it("cancels on the server, both explicitly and when the caller aborts", async () => {
  const { client: c, seen } = client((s, init) => {
    if (s.method === "DELETE") return s.path.endsWith("/known") ? Response.json({ requestId: "known", cancelled: true }) : Response.json({ status: 404, code: "NOT_FOUND", message: "no" }, { status: 404 });
    return new Promise<Response>((_, reject) => init?.signal?.addEventListener("abort", () => reject(init.signal?.reason)));
  });
  expect(await c.cancel("known")).toBe(true);
  expect(await c.cancel("unknown")).toBe(false);
  await expect(c.cancel("../x")).rejects.toThrow("identifier");
  const controller = new AbortController();
  const pending = c.search({ query: "shoes", requestId: "abort-1" }, controller.signal).catch(e => e);
  await new Promise(resolve => setTimeout(resolve, 20));
  controller.abort();
  await pending;
  await new Promise(resolve => setTimeout(resolve, 20));
  expect(seen.some(s => s.method === "DELETE" && s.path === "/api/v2/requests/abort-1")).toBe(true);
});

const sse = (...events: string[]) => events.map(e => `${e}\n\n`).join("");
const started = `event:search.started\ndata:${JSON.stringify({ version: 2, kind: "search.started", requestId: "r", sequence: 1, terminal: false })}`;
const failed = `event:search.failed\ndata:${JSON.stringify({ version: 2, kind: "search.failed", requestId: "r", sequence: 2, terminal: true, message: "Search could not complete", error: { code: "TIMEOUT", category: "TIMEOUT", message: "Request deadline exceeded", retryable: true } })}`;

it("follows the stream contract and refuses disordered or incompatible events", async () => {
  const bodies = [
    sse(started, ":heartbeat", failed, `event:search.completed\ndata:${JSON.stringify({ version: 2, kind: "search.completed" })}`),
    sse(started, started),
    sse(started.replace('"search.started",', '"search.progress",')),
    sse(started.replace('"version":2', '"version":3'))
  ];
  const accepts: Array<string | null> = [];
  const { client: c } = client((_, init) => { accepts.push(new Headers(init?.headers).get("Accept")); return new Response(bodies.shift()); });
  const events: CommerceEvent[] = [];
  await c.stream({ query: "shoes" }, e => events.push(e));
  expect(events).toHaveLength(2);
  expect(events[1]?.error?.code).toBe("TIMEOUT");
  expect(accepts[0]).toBe("text/event-stream");
  for (let i = 0; i < 3; i++) await expect(c.stream({ query: "shoes" }, () => {})).rejects.toBeInstanceOf(QwenBridgeIncompatibleServerError);
});

it("refuses incompatible servers instead of silently mapping them", async () => {
  const { client: c } = client(s => s.path === "/api/v2/version"
    ? Response.json({ version: "2.0.0", apiVersions: ["v2"], contracts: { sse: "progress-event/3", error: "api-error/2" } })
    : Response.json({ ...OK, apiVersion: 3 }));
  await expect(c.search({ query: "shoes" })).rejects.toBeInstanceOf(QwenBridgeIncompatibleServerError);
  await expect(c.requireCompatibleServer()).rejects.toBeInstanceOf(QwenBridgeIncompatibleServerError);
});

// QB-189: real server responses (captured by the server's CommerceApiTest) are fully typed.
const fixture = (name: string) => JSON.parse(readFileSync(fileURLToPath(new URL(`../../qwenbridge-java-sdk/src/test/resources/contract/${name}`, import.meta.url)), "utf8"));
const NESTED: Record<string, readonly string[]> = {
  retrieval: ["requested", "executed", "reasons", "degradations", "degraded", "removedByValidation", "providers", "warnings", "rankingPolicy", "reranking", "outcome"],
  interpretation: ["queryIrVersion", "intents", "retrievalNeeds", "semanticSignals", "constraints", "commerce"],
  results: ["hits", "totalHits", "tookMillis", "empty"],
  queryIR: ["version", "schemaId", "schemaVersion", "tenant", "constraints", "fields"]
};
it("types every field of real server responses", () => {
  for (const name of ["v2-search-response.json", "v2-search-response-planned.json"]) {
    const response = fixture(name);
    expect(Object.keys(response).filter(k => !(k in COMMERCE_RESPONSE_FIELDS)), name).toEqual([]);
    for (const [field, known] of Object.entries(NESTED))
      if (response[field]) expect(Object.keys(response[field]).filter(k => !known.includes(k)), `${name} ${field}`).toEqual([]);
  }
  expect(fixture("v2-search-response-planned.json").contract).toBe(COMMERCE_CONTRACT_V1);
});
