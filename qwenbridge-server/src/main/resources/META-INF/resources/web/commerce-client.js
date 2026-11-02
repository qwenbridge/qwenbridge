import { QwenBridgeApiError, QwenBridgeIncompatibleServerError, QwenBridgeTransportError } from "./errors.js";
import { RetryClassifier } from "./retry/retry-classifier.js";
import { RetryPolicy } from "./retry/retry-policy.js";
/** The planned Commerce Search Contract: the server chooses the retrieval strategy. */
export const COMMERCE_CONTRACT_V1 = "commerce-search/1.0";
/** Every CommerceResponse field; the compiler keeps this list equal to the type (QB-189). */
export const COMMERCE_RESPONSE_FIELDS = {
    apiVersion: true, requestId: true, status: true, translation: true, intent: true, queryIR: true, clarificationQuestions: true,
    results: true, confidence: true, evidence: true, suggestions: true, reranking: true, tookMs: true, nextCursor: true, timings: true,
    policies: true, lockedFields: true, contract: true, retrieval: true, interpretation: true, subqueries: true, clarifications: true,
    spelling: true, expansion: true
};
const KINDS = new Set(["search.started", "search.completed", "search.failed", "session.started", "session.completed", "session.failed"]);
const IDENTIFIER = /^[A-Za-z0-9._-]{1,128}$/;
function randomId() {
    const c = globalThis.crypto;
    if (typeof c?.randomUUID === "function")
        return c.randomUUID();
    const bytes = new Uint8Array(16);
    c.getRandomValues(bytes);
    return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
}
/** One signal that aborts when the caller's does or the time runs out; `dispose` releases the timer. */
function deadline(timeoutMs, outer) {
    const controller = new AbortController();
    let expired = false;
    const timer = setTimeout(() => { expired = true; controller.abort(new DOMException("Request timed out", "TimeoutError")); }, timeoutMs);
    const forward = () => controller.abort(outer?.reason);
    if (outer?.aborted)
        forward();
    else
        outer?.addEventListener("abort", forward, { once: true });
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
    options;
    fetchFn;
    timeoutMs;
    retry;
    classifier = new RetryClassifier();
    constructor(options) {
        this.options = options;
        this.fetchFn = options.fetch ?? globalThis.fetch.bind(globalThis);
        this.timeoutMs = options.timeoutMs ?? 120_000;
        if (!Number.isInteger(this.timeoutMs) || this.timeoutMs < 1)
            throw new TypeError("timeoutMs must be a positive integer");
        this.retry = options.retry ?? new RetryPolicy();
    }
    search(request, signal) {
        const identified = this.identified(request);
        return this.cancellable(identified.requestId, signal, () => this.retrying(() => this.call("POST", "/api/v2/search", identified, signal)).then(r => this.checkResponse(r)));
    }
    refine(previous, request, signal) {
        if (!IDENTIFIER.test(previous))
            throw new Error("Invalid previous request identifier");
        const identified = this.identified(request);
        return this.cancellable(identified.requestId, signal, () => this.retrying(() => this.call("POST", `/api/v2/search/${previous}/refine`, identified, signal)).then(r => this.checkResponse(r)));
    }
    createSession(input = {}, signal) { return this.call("POST", "/api/v2/sessions", input, signal); }
    session(id, signal) { const path = `/api/v2/sessions/${this.id(id)}`; return this.retrying(() => this.call("GET", path, undefined, signal)); }
    turn(id, input, signal) { return this.call("POST", `/api/v2/sessions/${this.id(id)}/turns`, input, signal); }
    proposals(session, signal) { const path = `/api/v2/sessions/${this.id(session)}/relaxations`; return this.retrying(() => this.call("GET", path, undefined, signal)); }
    accept(session, proposal, expectedRevision, signal) { return this.call("POST", `/api/v2/sessions/${this.id(session)}/relaxations/${this.id(proposal)}/accept`, { expectedRevision }, signal); }
    debug(request, signal) { const path = `/api/v2/search/${this.id(request)}/debug`; return this.retrying(() => this.call("GET", path, undefined, signal)); }
    /** Cancels the caller's own in-flight request; false when the server knows no such request. */
    async cancel(requestId, signal) {
        const path = `/api/v2/requests/${this.id(requestId)}`;
        try {
            await this.call("DELETE", path, undefined, signal);
            return true;
        }
        catch (error) {
            if (error instanceof QwenBridgeApiError && error.status === 404)
                return false;
            throw error;
        }
    }
    /** The server's API and contract versions and limits. */
    version(signal) { return this.retrying(() => this.call("GET", "/api/v2/version", undefined, signal)); }
    /** Rejects unless the server implements the v2 contracts this SDK speaks. */
    async requireCompatibleServer(signal) {
        const version = await this.version(signal);
        if (!version.apiVersions?.includes("v2") || version.contracts?.sse !== "progress-event/2" || version.contracts?.error !== "api-error/2")
            throw new QwenBridgeIncompatibleServerError(`Server ${version.version} does not implement v2 with progress-event/2 and api-error/2`);
        return version;
    }
    stream(request, handler, signal) {
        const identified = this.identified(request);
        return this.cancellable(identified.requestId, signal, () => this.events("/api/v2/search/stream", identified, handler, signal));
    }
    streamTurn(session, request, handler, signal) {
        return this.events(`/api/v2/sessions/${this.id(session)}/turns/stream`, request, handler, signal);
    }
    identified(request) {
        const requestId = request.requestId ?? randomId();
        if (!IDENTIFIER.test(requestId))
            throw new Error("Invalid request identifier");
        return { ...request, requestId };
    }
    id(value) { if (!IDENTIFIER.test(value))
        throw new Error("Invalid resource identifier"); return value; }
    /** `accept` is `text/event-stream` for streams; the server refuses others (406). */
    headers(hasBody, accept = "application/json") {
        const headers = { Accept: accept, "X-Request-Timeout-Ms": String(this.timeoutMs) };
        if (hasBody)
            headers["Content-Type"] = "application/json";
        if (this.options.bearerToken)
            headers.Authorization = `Bearer ${this.options.bearerToken}`;
        return headers;
    }
    /** When the caller aborts, the server is asked to cancel the request too (best effort). */
    async cancellable(requestId, signal, work) {
        const onAbort = () => { void this.cancel(requestId).catch(() => undefined); };
        signal?.addEventListener("abort", onAbort, { once: true });
        try {
            return await work();
        }
        finally {
            signal?.removeEventListener("abort", onAbort);
        }
    }
    async retrying(operation) {
        for (let attempt = 1;; attempt += 1) {
            try {
                return await operation();
            }
            catch (error) {
                if (attempt >= this.retry.maxAttempts || !this.classifier.isRetryable(error))
                    throw error;
                const delay = this.retry.delayBeforeAttempt(attempt + 1);
                if (delay > 0)
                    await new Promise(resolve => setTimeout(resolve, delay));
            }
        }
    }
    checkResponse(response) {
        if (response.apiVersion !== 2)
            throw new QwenBridgeIncompatibleServerError(`Unsupported commerce apiVersion ${String(response.apiVersion)}`);
        return response;
    }
    async send(method, path, body, signal, accept) {
        const limit = deadline(this.timeoutMs, signal);
        let response;
        try {
            response = await this.fetchFn(new URL(path, this.options.baseUrl), { method, headers: this.headers(body !== undefined, accept), body: body === undefined ? undefined : JSON.stringify(body), signal: limit.signal });
        }
        catch (error) {
            limit.dispose();
            if (signal?.aborted)
                throw error;
            throw new QwenBridgeTransportError(limit.timedOut() ? "Commerce request timed out" : "Commerce transport failed", error);
        }
        if (!response.ok) {
            limit.dispose();
            const text = await response.text().catch(() => "");
            let parsed = {};
            try {
                parsed = JSON.parse(text);
            }
            catch { /* not JSON */ }
            throw new QwenBridgeApiError({ ...parsed, status: typeof parsed.status === "number" && parsed.status > 0 ? parsed.status : response.status, message: typeof parsed.message === "string" ? parsed.message : `HTTP ${response.status}`, path });
        }
        response.dispose = limit.dispose;
        return response;
    }
    async call(method, path, request, signal) {
        const response = await this.send(method, path, request, signal);
        try {
            return await response.json();
        }
        catch (error) {
            throw new QwenBridgeTransportError("Invalid commerce response", error);
        }
        finally {
            response.dispose?.();
        }
    }
    async events(path, request, handler, signal) {
        const response = await this.send("POST", path, request, signal, "text/event-stream");
        if (!response.body)
            throw new QwenBridgeTransportError("Commerce stream has no body");
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let pending = "";
        let data = [];
        let lastSequence = 0;
        try {
            while (true) {
                let chunk;
                try {
                    chunk = await reader.read();
                }
                catch (error) {
                    if (signal?.aborted)
                        throw error;
                    throw new QwenBridgeTransportError("Commerce stream interrupted", error);
                }
                if (chunk.done)
                    throw new QwenBridgeTransportError("Commerce stream ended without terminal event");
                pending += decoder.decode(chunk.value, { stream: true });
                if (pending.length + data.join("\n").length > 4_194_304)
                    throw new QwenBridgeTransportError("SSE event too large");
                let newline;
                while ((newline = pending.indexOf("\n")) >= 0) {
                    const line = pending.slice(0, newline).replace(/\r$/, "");
                    pending = pending.slice(newline + 1);
                    // Comment lines (":heartbeat") and other fields are ignored, as SSE requires.
                    if (line.startsWith("data:"))
                        data.push(line.slice(5).trimStart());
                    if (line === "" && data.length) {
                        let event;
                        try {
                            event = JSON.parse(data.join("\n"));
                        }
                        catch (error) {
                            throw new QwenBridgeTransportError("Invalid commerce event", error);
                        }
                        data = [];
                        if (event.version !== 2 || !KINDS.has(event.kind))
                            throw new QwenBridgeIncompatibleServerError("Unsupported commerce event");
                        if (typeof event.sequence === "number") {
                            if (event.sequence <= lastSequence)
                                throw new QwenBridgeIncompatibleServerError("SSE events out of order");
                            lastSequence = event.sequence;
                        }
                        handler(event);
                        const terminal = typeof event.terminal === "boolean" ? event.terminal : event.kind.endsWith(".completed") || event.kind.endsWith(".failed");
                        if (terminal)
                            return;
                    }
                }
            }
        }
        finally {
            response.dispose?.();
            await reader.cancel().catch(() => undefined);
            reader.releaseLock();
        }
    }
}
