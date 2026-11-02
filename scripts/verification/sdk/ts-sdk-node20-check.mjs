// Section 3 (QB-187): the built TypeScript SDK on the oldest supported Node.js (engines: >=20).
// Run in a throwaway node:20 container with no network; the SDK directory is mounted read-only.
import assert from "node:assert/strict";
import { CommerceClient, QwenBridgeApiError, QwenBridgeTransportError, RetryPolicy } from "/sdk/dist/index.js";
const seen = [];
const fetch = async (url, init) => {
  const path = new URL(String(url)).pathname; seen.push([init.method, path]);
  if (path === "/api/v2/search") return Response.json({ apiVersion: 2, requestId: JSON.parse(init.body).requestId, status: "OK", results: { hits: [], totalHits: 0, tookMillis: 1 } });
  if (path === "/api/v2/search/stream") return new Response('event:search.started\ndata:{"version":2,"kind":"search.started","sequence":1,"terminal":false}\n\n:heartbeat\n\nevent:search.completed\ndata:{"version":2,"kind":"search.completed","sequence":2,"terminal":true,"response":{"apiVersion":2,"status":"OK"}}\n\n');
  if (path === "/api/v2/sessions/slow") return new Promise((_, reject) => init.signal.addEventListener("abort", () => reject(init.signal.reason)));
  return Response.json({ status: 409, code: "CONFLICT", category: "CONFLICT", retryable: false, message: "no" }, { status: 409 });
};
const client = new CommerceClient({ baseUrl: "http://localhost", fetch, timeoutMs: 200, retry: new RetryPolicy({ maxAttempts: 2, initialDelayMs: 1, maxDelayMs: 2 }) });
const response = await client.search({ query: "کفش مشکی ۴۲" });
assert.match(response.requestId, /^[0-9a-f-]{32,36}$/);
const kinds = []; await client.stream({ query: "shoes" }, e => kinds.push(e.kind));
assert.deepEqual(kinds, ["search.started", "search.completed"]);
await assert.rejects(client.turn("s1", { query: "x" }), e => e instanceof QwenBridgeApiError && e.category === "CONFLICT");
await assert.rejects(client.session("slow"), e => e instanceof QwenBridgeTransportError);
console.log(`node ${process.version}: SDK search, stream, typed errors and timeout OK`);
