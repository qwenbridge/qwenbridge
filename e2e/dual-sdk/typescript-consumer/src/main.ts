/**
 * QB-295 independent TypeScript consumer. Executes the shared scenarios through the public
 * @qwenbridge/sdk package (installed from its packed tarball, imported by package name only)
 * against a running QwenBridge server, and writes one normalized observation per scenario,
 * identical in shape to the Java consumer's. It judges nothing: the shared evaluator does.
 *
 * Usage: node dist/main.js --base URL --scenarios FILE --keys FILE --tag offline|provider --out FILE [--perf N]
 */
import { readFileSync, writeFileSync } from "node:fs";
import { cpus, platform, arch } from "node:os";
import { randomUUID } from "node:crypto";
import {
  CommerceClient,
  QwenBridgeClient,
  QwenBridgeStreamingClient,
  QwenBridgeApiError,
  QwenBridgeIncompatibleServerError,
  QwenBridgeTransportError,
  RetryPolicy,
  type CommerceEvent,
  type CommerceRequest,
  type CommerceResponse
} from "@qwenbridge/sdk";

type Json = Record<string, unknown>;
interface Scenario {
  id: string; tags: string[]; principal: string | null; operation: string;
  request?: { query: string; requestId?: string; mode?: string; contract?: string; catalogLanguage?: string; limit?: number };
  timeoutMs?: number; abortAfterMs?: number; cancelAfterMs?: number; concurrency?: number;
}

const LEAK = /(\bat [a-z]+\.[a-z.]+\(|Exception:|stack ?trace|\/Users\/|\/home\/|e2e-[0-9a-f]{32})/i;
const args = Object.fromEntries(process.argv.slice(2).reduce<string[][]>((acc, v, i, a) => (i % 2 === 0 ? [...acc, [v, a[i + 1]]] : acc), []));
for (const required of ["--base", "--scenarios", "--keys", "--tag", "--out"]) if (!args[required]) throw new Error(`missing ${required}`);
const base: string = args["--base"];
const keys: Record<string, string> = JSON.parse(readFileSync(args["--keys"], "utf8"));
const scenarios: Scenario[] = JSON.parse(readFileSync(args["--scenarios"], "utf8")).scenarios;
const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

function token(s: Scenario): string | undefined {
  if (!s.principal) return undefined;
  if (s.principal === "invalid") return `e2e-invalid-${randomUUID()}`;
  return keys[s.principal];
}

function client(s: Scenario, target = base): CommerceClient {
  return new CommerceClient({
    baseUrl: target,
    bearerToken: token(s),
    timeoutMs: s.timeoutMs ?? 120_000,
    retry: s.timeoutMs ? new RetryPolicy({ maxAttempts: 1 }) : undefined
  });
}

function request(r: NonNullable<Scenario["request"]>): CommerceRequest {
  const out: CommerceRequest = { query: r.query.replace("{sdk}", "typescript"), catalogLanguage: r.catalogLanguage ?? "en", schemaId: "commerce", schemaVersion: 1 };
  if (r.requestId) out.requestId = r.requestId.replace("{sdk}", "typescript");
  if (r.mode) out.mode = r.mode as CommerceRequest["mode"];
  if (r.contract) out.contract = r.contract as CommerceRequest["contract"];
  if (r.limit) out.limit = r.limit;
  return out;
}

/** The normalized, SDK-independent view of a response; the Java consumer builds the same. */
function observe(r: CommerceResponse, query: string, requestedId?: string): Json {
  const o: Json = {
    apiVersion: r.apiVersion, status: r.status, intent: r.intent,
    requestIdEchoed: requestedId === undefined || requestedId === r.requestId,
    hasRequestId: typeof r.requestId === "string" && r.requestId.length > 0
  };
  if (r.translation) { o.sourceLanguage = r.translation.sourceLanguage; o.translated = r.translation.translated; }
  const constraints: string[][] = [];
  let grounded = true;
  for (const c of r.queryIR?.constraints ?? []) {
    constraints.push([c.field, c.operator, c.value]);
    if (!c.evidence || c.evidence.quote == null) grounded = false;
    else if (c.evidence.source === "original" && !query.includes(c.evidence.quote)) grounded = false;
  }
  o.constraints = constraints; o.evidenceGrounded = grounded;
  const hits: string[] = []; const docs: Json[] = [];
  if (r.results) {
    o.totalHits = r.results.totalHits;
    for (const h of r.results.hits) {
      hits.push(h.id);
      const d: Json = { id: h.id };
      for (const f of ["color", "currency", "category", "condition", "size"]) if (h.document[f] != null) d[f] = String(h.document[f]);
      if (typeof h.document.price === "number") d.price = h.document.price;
      if (typeof h.document.inStock === "boolean") d.inStock = h.document.inStock;
      docs.push(d);
    }
  }
  o.hits = hits; o.hitDocs = docs;
  if (r.retrieval) o.retrieval = { requested: r.retrieval.requested, executed: r.retrieval.executed, outcome: r.retrieval.outcome, degraded: r.retrieval.degraded };
  if (r.interpretation) {
    o.queryIrVersion = r.interpretation.queryIrVersion; o.intents = r.interpretation.intents;
    o.interpretationConstraints = r.interpretation.constraints.length;
  }
  o.clarifications = (r.clarificationQuestions ?? []).length;
  if (r.spelling) {
    const sp = r.spelling;
    o.spelling = { status: sp.status, mode: sp.mode, original: sp.original, normalized: sp.normalized, corrected: sp.corrected, changed: sp.corrected !== sp.normalized };
  }
  if (r.expansion) {
    const e = r.expansion;
    const targets: Record<string, Json> = {};
    for (const t of e.targets) targets[t.language ?? t.requested] = { requested: t.requested, status: t.status, text: t.text ?? null, provider: t.provider ?? null, retrieval: t.retrieval };
    o.expansion = {
      status: e.status, original: e.original, sourceLanguage: e.source?.language ?? null, basis: e.source?.basis ?? null,
      requested: e.requestedLanguages, unsupported: e.unsupportedLanguages, retrievalTexts: e.retrievalTexts, targets
    };
  }
  if (r.tookMs > 0) o.serverTookMs = r.tookMs;
  return o;
}

function error(e: unknown): Json {
  const message = e instanceof Error ? e.message : String(e);
  let o: Json;
  if (e instanceof QwenBridgeApiError) o = { kind: "api", status: e.status, code: e.code, category: e.body.category, retryable: e.body.retryable ?? false };
  else if (e instanceof QwenBridgeTransportError) o = { kind: /timed out|timeout/i.test(message) ? "timeout" : "transport" };
  else if (e instanceof QwenBridgeIncompatibleServerError) o = { kind: "incompatible" };
  else if (e instanceof Error && /Invalid (request|resource) identifier/.test(message)) o = { kind: "client-validation" };
  else o = { kind: `other:${e instanceof Error ? e.name : typeof e}` };
  o.messageLeaks = LEAK.test(message);
  return o;
}

function events(list: CommerceEvent[], query: string): Json[] {
  return list.map(e => {
    const x: Json = { kind: e.kind, requestId: e.requestId, hasTraceId: typeof e.traceId === "string" && /^[0-9a-f]{32}$/.test(e.traceId) };
    if (e.sequence !== undefined) x.sequence = e.sequence;
    if (e.terminal !== undefined) x.terminal = e.terminal;
    if (e.error) { x.errorCode = e.error.code; x.errorCategory = e.error.category; }
    if (e.response) x.response = observe(e.response, query);
    return x;
  });
}

async function run(s: Scenario): Promise<Json> {
  const r = s.request!;
  switch (s.operation) {
    case "search": {
      const t0 = performance.now();
      try {
        const response = await client(s).search(request(r));
        return { ok: true, response: observe(response, r.query, r.requestId?.replace("{sdk}", "typescript")), latencyMs: performance.now() - t0 };
      } catch (e) { return { ok: false, error: error(e), latencyMs: performance.now() - t0 }; }
    }
    case "version": {
      const v = await client(s).requireCompatibleServer();
      return { ok: true, version: { name: v.name, version: v.version, apiVersions: v.apiVersions } };
    }
    case "stream": {
      const list: CommerceEvent[] = [];
      try { await client(s).stream(request(r), e => list.push(e)); return { ok: true, events: events(list, r.query) }; }
      catch (e) { return { ok: false, error: error(e), events: events(list, r.query) }; }
    }
    case "stream-abort": {
      const list: CommerceEvent[] = []; const controller = new AbortController();
      const t0 = performance.now();
      const done = client(s).stream(request(r), e => list.push(e), controller.signal).then(() => "completed", e => (e instanceof Error ? e.name : "error"));
      await sleep(s.abortAfterMs ?? 800); controller.abort();
      const outcome = await done; const elapsedMs = performance.now() - t0;
      await sleep(500);
      return { ok: true, cancelled: outcome !== "completed", elapsedMs, events: events(list, r.query) };
    }
    case "stream-server-cancel": {
      const list: CommerceEvent[] = []; const id = `e2e-ts-cancel-${randomUUID()}`; const c = client(s);
      const t0 = performance.now();
      const done = c.stream({ ...request(r), requestId: id }, e => list.push(e)).then(() => null, e => e);
      await sleep(s.cancelAfterMs ?? 800);
      const accepted = await c.cancel(id);
      const failure = await done;
      return { ok: failure === null, ...(failure ? { error: error(failure) } : {}), cancelAccepted: accepted, elapsedMs: performance.now() - t0, events: events(list, r.query) };
    }
    case "concurrent-streams": {
      const c = client(s); const n = s.concurrency ?? 8;
      const streams = await Promise.all(Array.from({ length: n }, async (_, i) => {
        const list: CommerceEvent[] = [];
        await c.stream({ ...request(r), requestId: `e2e-ts-stream-${i}-${randomUUID()}` }, e => list.push(e));
        return events(list, r.query);
      }));
      return { ok: true, streams };
    }
    case "concurrent-search": {
      const c = client(s); const n = s.concurrency ?? 16;
      const responses = await Promise.all(Array.from({ length: n }, () => c.search(request(r)).then(
        x => ({ ok: true, response: observe(x, r.query) }), e => ({ ok: false, error: error(e) }))));
      return { ok: true, responses };
    }
    case "unreachable": {
      const t0 = performance.now();
      try { await client(s, "http://127.0.0.1:9").search(request(r)); return { ok: true }; }
      catch (e) { return { ok: false, error: error(e), latencyMs: performance.now() - t0 }; }
    }
    case "v1-analyze":
    case "v1-analyze-async": {
      // The legacy v1 analyze API (what the removed examples showed); asynchronous by nature.
      const requestId = r.requestId!.replace("{sdk}", "typescript");
      try {
        const response = await new QwenBridgeClient({ baseUrl: base }).analyze({ requestId, query: r.query });
        const v1: Json = { decision: response.decision, intent: response.intent, originalQuery: response.originalQuery, requestIdEchoed: response.requestId === requestId };
        if (response.spelling) v1.spellingStatus = response.spelling.status;
        if (response.expansion) { v1.expansionStatus = response.expansion.status; v1.expansionRequested = response.expansion.requestedLanguages; }
        return { ok: true, v1 };
      } catch (e) { return { ok: false, error: error(e) }; }
    }
    case "v1-typed-stream": {
      const requestId = r.requestId!.replace("{sdk}", "typescript");
      const names: string[] = [];
      let ended!: () => void;
      const terminal = new Promise<void>(resolve => { ended = resolve; });
      const stream = new QwenBridgeStreamingClient({ baseUrl: base }).streamTyped(requestId, event => {
        names.push(event.event);
        if (/^pipeline\.(completed|failed|stopped)$/.test(event.event)) ended();
      }).catch(() => undefined);
      await sleep(500);
      await new QwenBridgeClient({ baseUrl: base }).analyze({ requestId, query: r.query });
      const ok = await Promise.race([terminal.then(() => true), sleep(60_000).then(() => false)]);
      void stream;
      return { ok, eventNames: names };
    }
    default: throw new Error(`unknown operation ${s.operation}`);
  }
}

function stats(values: number[], errors: number, throughput: number): Json {
  const sorted = [...values].sort((a, b) => a - b);
  const pick = (q: number) => (sorted.length ? sorted[Math.min(sorted.length - 1, Math.ceil(q * sorted.length) - 1)] : 0);
  const o: Json = { samples: values.length, errors, p50: pick(0.5), p95: pick(0.95), p99: pick(0.99), max: sorted.at(-1) ?? 0 };
  if (throughput > 0) o.throughputPerSecond = throughput;
  return o;
}

async function perf(n: number): Promise<Json> {
  const query = "black Stride running shoes size 42 under 100 EUR in stock";
  const c = new CommerceClient({ baseUrl: base, bearerToken: keys.main });
  const req: CommerceRequest = { query, catalogLanguage: "en", schemaId: "commerce", schemaVersion: 1 };
  const before = process.memoryUsage();
  for (let i = 0; i < 20; i++) await c.search(req);
  const latencies: number[] = []; const server: number[] = []; let errors = 0;
  const start = performance.now();
  for (let i = 0; i < n; i++) {
    const t0 = performance.now();
    try { server.push((await c.search(req)).tookMs); } catch { errors++; }
    latencies.push(performance.now() - t0);
  }
  const seconds = (performance.now() - start) / 1000;
  const concurrency = 8; const concurrent: number[] = []; let cerrors = 0; let next = 0;
  const cstart = performance.now();
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (next < n) {
      next++;
      const t0 = performance.now();
      try { await c.search(req); } catch { cerrors++; }
      concurrent.push(performance.now() - t0);
    }
  }));
  const cseconds = (performance.now() - cstart) / 1000;
  const after = process.memoryUsage();
  return {
    sequential: stats(latencies, errors, n / seconds), serverTookMs: stats(server, 0, 0),
    concurrent: { ...stats(concurrent, cerrors, concurrent.length / cseconds), concurrency },
    rssBeforeMb: before.rss / 1048576, rssAfterMb: after.rss / 1048576, heapUsedAfterMb: after.heapUsed / 1048576,
    query, processors: cpus().length, os: `${platform()} ${arch()}`
  };
}

const out: Json = { sdk: "typescript", sdkArtifact: "@qwenbridge/sdk (packed tarball)", runtime: `Node.js ${process.version}`, tag: args["--tag"] };
const results: Record<string, Json> = {};
for (const s of scenarios) {
  if (!s.tags.includes(args["--tag"])) continue;
  const t0 = performance.now();
  let observation: Json;
  try { observation = await run(s); } catch (e) { observation = { ok: false, error: error(e) }; }
  observation.wallMs = performance.now() - t0;
  results[s.id] = observation;
  console.log(`${s.id} ${observation.ok ? "ok" : "error"}`);
}
out.results = results;
const n = Number(args["--perf"] ?? 0);
if (n > 0 && args["--tag"] === "offline") out.performance = await perf(n);
writeFileSync(args["--out"], JSON.stringify(out, null, 1));
