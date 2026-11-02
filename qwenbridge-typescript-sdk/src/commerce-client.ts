import { QwenBridgeApiError, QwenBridgeIncompatibleServerError, QwenBridgeTransportError } from "./errors.js";
import type { Expansion, Spelling } from "./types.js";
import { RetryClassifier } from "./retry/retry-classifier.js";
import { RetryPolicy } from "./retry/retry-policy.js";

/** The planned Commerce Search Contract: the server chooses the retrieval strategy. */
export const COMMERCE_CONTRACT_V1 = "commerce-search/1.0";

export interface CommerceRequest {
  requestId?: string; query: string; catalogLanguage?: string;
  schemaId?: string; schemaVersion?: number; mode?: "KEYWORD" | "VECTOR" | "HYBRID" | null; rerank?: boolean;
  /** A configured backend id: "memory" (built in, the default) or "opensearch" (optional). */
  backend?: string; limit?: number; cursor?: string; lockedFields?: string[];
  /** `commerce-search/1.0` for planned requests (omit `mode`); absent for apiVersion 2 behaviour. */
  contract?: typeof COMMERCE_CONTRACT_V1;
}
export interface EvidenceSpan { source: string; start: number; end: number; quote: string; }
export interface CommerceConstraint { field: string; operator: "EQ" | "NE" | "LT" | "LTE" | "GT" | "GTE"; value: string; evidence: EvidenceSpan; }
export interface CommerceFieldSpec { type: string; operators: string[]; indexField: string; normalization: string; }
export interface CommerceHit { id: string; score: number; document: Record<string, unknown>; metadata?: Record<string, unknown>; }
export interface CommerceResults { hits: CommerceHit[]; totalHits: number; tookMillis: number; empty?: boolean; }
export interface CommerceRetrieval {
  requested: string; executed: string; reasons: string[]; degradations: string[]; degraded: boolean; removedByValidation: number;
  providers: string[]; warnings: string[]; rankingPolicy: string; reranking: string; outcome: string;
}
export interface CommerceInterpretation {
  queryIrVersion: string; intents: string[]; retrievalNeeds: string[];
  semanticSignals: Array<{ concept: string; polarity: string; quote: string }>;
  constraints: Array<{ id: string; field: string; operator: string; status: string; note: string; scope: string | null }>;
  commerce: Record<string, unknown>;
}
export interface CommerceSubQuery { id: string; objective: string; status: string; constraints: CommerceConstraint[]; retrieval: CommerceRetrieval; results: CommerceResults; }
export interface CommerceClarification { kind: string; field: string; question: string; options: string[]; }
export interface CommerceResponse {
  apiVersion: 2; requestId: string; status: "OK" | "CLARIFICATION_REQUIRED" | "BLOCKED" | "REVIEW" | "PARTIAL" | "UNAVAILABLE";
  translation: { version: number; original: string; sourceLanguage: string; targetLanguage: string; translated: string; constraintsPreserved: boolean; issues: string[] } | null;
  intent: string; queryIR: { version: number; schemaId: string; schemaVersion: number; tenant: string; constraints: CommerceConstraint[]; fields?: Record<string, CommerceFieldSpec> } | null;
  clarificationQuestions: string[];
  results: CommerceResults;
  confidence: { value: number; method: string; calibrated: boolean; signals: Record<string, number> };
  evidence: Array<{ id: string; tenant: string; requestId?: string; source: string; occurredAt: string; observedAt: string; claim: string; spans: EvidenceSpan[]; parentId: string | null }>;
  suggestions: string[]; reranking: string; tookMs: number;
  nextCursor?: string | null; timings?: Record<string, number>;
  policies?: Array<{ pack: string; version: string; rule: string; effect: string; reason: string }>;
  lockedFields?: string[];
  /** 1.0 (planned contract) fields. */
  contract?: string; retrieval?: CommerceRetrieval; interpretation?: CommerceInterpretation;
  subqueries?: CommerceSubQuery[]; clarifications?: CommerceClarification[];
  /** QB-291: typo processing, when enabled for the caller's tenant. */
  spelling?: Spelling;
  /** QB-286: multilingual expansion, when enabled for the caller's tenant. */
  expansion?: Expansion;
}
/** Every CommerceResponse field; the compiler keeps this list equal to the type (QB-189). */
export const COMMERCE_RESPONSE_FIELDS: Readonly<Record<keyof CommerceResponse, true>> = {
  apiVersion: true, requestId: true, status: true, translation: true, intent: true, queryIR: true, clarificationQuestions: true,
  results: true, confidence: true, evidence: true, suggestions: true, reranking: true, tookMs: true, nextCursor: true, timings: true,
  policies: true, lockedFields: true, contract: true, retrieval: true, interpretation: true, subqueries: true, clarifications: true,
  spelling: true, expansion: true
};
/** The typed error of a failed stream: same codes and categories as REST errors. */
export interface StreamError { code: string; category: string; message: string; retryable: boolean; }
/** 1.0 fields of every progress event; absent from older servers. */
export interface ProgressEventFields { sequence?: number; terminal?: boolean; error?: StreamError; traceId?: string; }
export interface CommerceEvent extends ProgressEventFields { version: 2; kind: "search.started" | "search.completed" | "search.failed"; requestId: string; response: CommerceResponse | null; message: string; }
export interface CommerceSession { id: string; tenant: string; subject: string; backend: string; lockedFields: string[]; createdAt: string; expiresAt: string; revision: number; last: CommerceResponse | null; turns: Array<{ requestId: string; query: string; status: string; at: string }>; }
export interface SessionTurn { query: string; expectedRevision?: number; resetContext?: boolean; mode?: string; rerank?: boolean; }
export interface RelaxationProposal { id: string; description: string; changedField: string; expiresAt: string; status: string; }
export interface SessionEvent extends ProgressEventFields { version: 2; kind: "session.started" | "session.completed" | "session.failed"; requestId: string; session: CommerceSession | null; message: string; }
export interface ServerVersion { name: string; version: string; apiVersions: string[]; contracts: Record<string, string>; limits: Record<string, unknown>; }
export interface CommerceClientOptions {
  baseUrl: string; bearerToken?: string; fetch?: typeof fetch;
  /** Per call (default 120 000 ms); also sent as X-Request-Timeout-Ms so the server stops too. */
  timeoutMs?: number;
  /** Applied to safe operations only (search, refine, reads). Default: 3 attempts. */
  retry?: RetryPolicy;
}

const KINDS = new Set(["search.started", "search.completed", "search.failed", "session.started", "session.completed", "session.failed"]);
const IDENTIFIER = /^[A-Za-z0-9._-]{1,128}$/;

function randomId(): string {
  const c = globalThis.crypto;
  if (typeof c?.randomUUID === "function") return c.randomUUID();
  const bytes = new Uint8Array(16); c.getRandomValues(bytes);
  return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
}

/** One signal that aborts when the caller's does or the time runs out; `dispose` releases the timer. */
function deadline(timeoutMs: number, outer?: AbortSignal): { signal: AbortSignal; dispose: () => void; timedOut: () => boolean } {
  const controller = new AbortController();
  let expired = false;
  const timer = setTimeout(() => { expired = true; controller.abort(new DOMException("Request timed out", "TimeoutError")); }, timeoutMs);
  const forward = () => controller.abort(outer?.reason);
  if (outer?.aborted) forward(); else outer?.addEventListener("abort", forward, { once: true });
  return { signal: controller.signal, timedOut: () => expired, dispose: () => { clearTimeout(timer); outer?.removeEventListener("abort", forward); } };
}

/**
 * Typed client for the tenant-scoped v2 API (QB-187, QB-188, QB-190).
 *
 * - Failures are typed: QwenBridgeApiError (code, category, retryable), QwenBridgeTransportError
 *   (network, timeout), QwenBridgeIncompatibleServerError (unsupported contract version).
 * - Every call has a timeout, also sent as X-Request-Timeout-Ms so the server stops its work.
 * - Only safe operations are retried (search, refine, reads), with the same request ID each time.
 *   Session turns, acceptances, streams and cancellations are never retried.
 * - Aborting a search or stream also asks the server to cancel the request.
 *
 * Works in browsers and Node.js 20+; it does not depend on the Web Component or any framework.
 */
export class CommerceClient {
  private readonly fetchFn: typeof fetch;
  private readonly timeoutMs: number;
  private readonly retry: RetryPolicy;
  private readonly classifier = new RetryClassifier();
  public constructor(private readonly options: CommerceClientOptions) {
    this.fetchFn = options.fetch ?? globalThis.fetch.bind(globalThis);
    this.timeoutMs = options.timeoutMs ?? 120_000;
    if (!Number.isInteger(this.timeoutMs) || this.timeoutMs < 1) throw new TypeError("timeoutMs must be a positive integer");
    this.retry = options.retry ?? new RetryPolicy();
  }
  public search(request: CommerceRequest, signal?: AbortSignal): Promise<CommerceResponse> {
    const identified = this.identified(request);
    return this.cancellable(identified.requestId!, signal, () => this.retrying(() => this.call<CommerceResponse>("POST", "/api/v2/search", identified, signal)).then(r => this.checkResponse(r)));
  }
  public refine(previous: string, request: CommerceRequest, signal?: AbortSignal): Promise<CommerceResponse> {
    if (!IDENTIFIER.test(previous)) throw new Error("Invalid previous request identifier");
    const identified = this.identified(request);
    return this.cancellable(identified.requestId!, signal, () => this.retrying(() => this.call<CommerceResponse>("POST", `/api/v2/search/${previous}/refine`, identified, signal)).then(r => this.checkResponse(r)));
  }
  public createSession(input: { ttlSeconds?: number; backend?: string; lockedFields?: string[] } = {}, signal?: AbortSignal): Promise<CommerceSession> { return this.call("POST", "/api/v2/sessions", input, signal); }
  public session(id: string, signal?: AbortSignal): Promise<CommerceSession> { const path = `/api/v2/sessions/${this.id(id)}`; return this.retrying(() => this.call("GET", path, undefined, signal)); }
  public turn(id: string, input: SessionTurn, signal?: AbortSignal): Promise<CommerceSession> { return this.call("POST", `/api/v2/sessions/${this.id(id)}/turns`, input, signal); }
  public proposals(session: string, signal?: AbortSignal): Promise<RelaxationProposal[]> { const path = `/api/v2/sessions/${this.id(session)}/relaxations`; return this.retrying(() => this.call("GET", path, undefined, signal)); }
  public accept(session: string, proposal: string, expectedRevision: number, signal?: AbortSignal): Promise<CommerceSession> { return this.call("POST", `/api/v2/sessions/${this.id(session)}/relaxations/${this.id(proposal)}/accept`, { expectedRevision }, signal); }
  public debug(request: string, signal?: AbortSignal): Promise<{ response: CommerceResponse; compiledFilters: unknown[] }> { const path = `/api/v2/search/${this.id(request)}/debug`; return this.retrying(() => this.call("GET", path, undefined, signal)); }
  /** Cancels the caller's own in-flight request; false when the server knows no such request. */
  public async cancel(requestId: string, signal?: AbortSignal): Promise<boolean> {
    const path = `/api/v2/requests/${this.id(requestId)}`;
    try { await this.call("DELETE", path, undefined, signal); return true; }
    catch (error) { if (error instanceof QwenBridgeApiError && error.status === 404) return false; throw error; }
  }
  /** The server's API and contract versions and limits. */
  public version(signal?: AbortSignal): Promise<ServerVersion> { return this.retrying(() => this.call("GET", "/api/v2/version", undefined, signal)); }
  /** Rejects unless the server implements the v2 contracts this SDK speaks. */
  public async requireCompatibleServer(signal?: AbortSignal): Promise<ServerVersion> {
    const version = await this.version(signal);
    if (!version.apiVersions?.includes("v2") || version.contracts?.sse !== "progress-event/2" || version.contracts?.error !== "api-error/2")
      throw new QwenBridgeIncompatibleServerError(`Server ${version.version} does not implement v2 with progress-event/2 and api-error/2`);
    return version;
  }
  public stream(request: CommerceRequest, handler: (event: CommerceEvent) => void, signal?: AbortSignal): Promise<void> {
    const identified = this.identified(request);
    return this.cancellable(identified.requestId!, signal, () => this.events("/api/v2/search/stream", identified, handler, signal));
  }
  public streamTurn(session: string, request: SessionTurn, handler: (event: SessionEvent) => void, signal?: AbortSignal): Promise<void> {
    return this.events(`/api/v2/sessions/${this.id(session)}/turns/stream`, request, handler, signal);
  }
  private identified(request: CommerceRequest): CommerceRequest {
    const requestId = request.requestId ?? randomId();
    if (!IDENTIFIER.test(requestId)) throw new Error("Invalid request identifier");
    return { ...request, requestId };
  }
  private id(value: string): string { if (!IDENTIFIER.test(value)) throw new Error("Invalid resource identifier"); return value; }
  /** `accept` is `text/event-stream` for streams; the server refuses others (406). */
  private headers(hasBody: boolean, accept = "application/json"): Record<string, string> {
    const headers: Record<string, string> = { Accept: accept, "X-Request-Timeout-Ms": String(this.timeoutMs) };
    if (hasBody) headers["Content-Type"] = "application/json";
    if (this.options.bearerToken) headers.Authorization = `Bearer ${this.options.bearerToken}`;
    return headers;
  }
  /** When the caller aborts, the server is asked to cancel the request too (best effort). */
  private async cancellable<T>(requestId: string, signal: AbortSignal | undefined, work: () => Promise<T>): Promise<T> {
    const onAbort = () => { void this.cancel(requestId).catch(() => undefined); };
    signal?.addEventListener("abort", onAbort, { once: true });
    try { return await work(); } finally { signal?.removeEventListener("abort", onAbort); }
  }
  private async retrying<T>(operation: () => Promise<T>): Promise<T> {
    for (let attempt = 1; ; attempt += 1) {
      try { return await operation(); }
      catch (error) {
        if (attempt >= this.retry.maxAttempts || !this.classifier.isRetryable(error)) throw error;
        const delay = this.retry.delayBeforeAttempt(attempt + 1);
        if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
  }
  private checkResponse(response: CommerceResponse): CommerceResponse {
    if (response.apiVersion !== 2) throw new QwenBridgeIncompatibleServerError(`Unsupported commerce apiVersion ${String(response.apiVersion)}`);
    return response;
  }
  private async send(method: string, path: string, body: unknown, signal: AbortSignal | undefined, accept?: string): Promise<Response> {
    const limit = deadline(this.timeoutMs, signal);
    let response: Response;
    try {
      response = await this.fetchFn(new URL(path, this.options.baseUrl), { method, headers: this.headers(body !== undefined, accept), body: body === undefined ? undefined : JSON.stringify(body), signal: limit.signal });
    } catch (error) {
      limit.dispose();
      if (signal?.aborted) throw error;
      throw new QwenBridgeTransportError(limit.timedOut() ? "Commerce request timed out" : "Commerce transport failed", error);
    }
    if (!response.ok) {
      limit.dispose();
      const text = await response.text().catch(() => "");
      let parsed: Partial<QwenBridgeApiError["body"]> = {};
      try { parsed = JSON.parse(text) as Partial<QwenBridgeApiError["body"]>; } catch { /* not JSON */ }
      throw new QwenBridgeApiError({ ...parsed, status: typeof parsed.status === "number" && parsed.status > 0 ? parsed.status : response.status, message: typeof parsed.message === "string" ? parsed.message : `HTTP ${response.status}`, path });
    }
    (response as Response & { dispose?: () => void }).dispose = limit.dispose;
    return response;
  }
  private async call<T>(method: string, path: string, request: unknown, signal?: AbortSignal): Promise<T> {
    const response = await this.send(method, path, request, signal);
    try { return await response.json() as T; }
    catch (error) { throw new QwenBridgeTransportError("Invalid commerce response", error); }
    finally { (response as Response & { dispose?: () => void }).dispose?.(); }
  }
  private async events<T extends ProgressEventFields & { version: number; kind: string }>(path: string, request: unknown, handler: (event: T) => void, signal?: AbortSignal): Promise<void> {
    const response = await this.send("POST", path, request, signal, "text/event-stream");
    if (!response.body) throw new QwenBridgeTransportError("Commerce stream has no body");
    const reader = response.body.getReader(); const decoder = new TextDecoder();
    let pending = ""; let data: string[] = []; let lastSequence = 0;
    try {
      while (true) {
        let chunk: ReadableStreamReadResult<Uint8Array>;
        try { chunk = await reader.read(); }
        catch (error) { if (signal?.aborted) throw error; throw new QwenBridgeTransportError("Commerce stream interrupted", error); }
        if (chunk.done) throw new QwenBridgeTransportError("Commerce stream ended without terminal event");
        pending += decoder.decode(chunk.value, { stream: true });
        if (pending.length + data.join("\n").length > 4_194_304) throw new QwenBridgeTransportError("SSE event too large");
        let newline: number;
        while ((newline = pending.indexOf("\n")) >= 0) {
          const line = pending.slice(0, newline).replace(/\r$/, ""); pending = pending.slice(newline + 1);
          // Comment lines (":heartbeat") and other fields are ignored, as SSE requires.
          if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
          if (line === "" && data.length) {
            let event: T;
            try { event = JSON.parse(data.join("\n")) as T; } catch (error) { throw new QwenBridgeTransportError("Invalid commerce event", error); }
            data = [];
            if (event.version !== 2 || !KINDS.has(event.kind)) throw new QwenBridgeIncompatibleServerError("Unsupported commerce event");
            if (typeof event.sequence === "number") {
              if (event.sequence <= lastSequence) throw new QwenBridgeIncompatibleServerError("SSE events out of order");
              lastSequence = event.sequence;
            }
            handler(event);
            const terminal = typeof event.terminal === "boolean" ? event.terminal : event.kind.endsWith(".completed") || event.kind.endsWith(".failed");
            if (terminal) return;
          }
        }
      }
    } finally {
      (response as Response & { dispose?: () => void }).dispose?.();
      await reader.cancel().catch(() => undefined); reader.releaseLock();
    }
  }
}
