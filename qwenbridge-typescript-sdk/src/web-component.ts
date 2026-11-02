import { QwenBridgeApiError } from "./errors.js";
import { CommerceClient, type CommerceResponse, type CommerceSession, type RelaxationProposal } from "./commerce-client.js";

/** Texts shown by the component; `{count}` is replaced in `results`. */
export interface SearchElementLabels {
  prompt: string; placeholder: string; search: string; newSearch: string; cancel: string;
  searching: string; results: string; empty: string; partial: string; unavailable: string; review: string;
  cancelled: string; failed: string; suggestions: string; alternatives: string; resultsRegion: string;
}

export const DEFAULT_LABELS: Readonly<SearchElementLabels> = {
  prompt: "Describe what you want", placeholder: "Black shoes under 100 EUR, size 42, in stock",
  search: "Search", newSearch: "New search", cancel: "Cancel", searching: "Searching…",
  results: "{count} matching results", empty: "No results match every condition.",
  partial: "Some sources were unavailable; these results may be incomplete.",
  unavailable: "Search is temporarily unavailable. Please try again.", review: "This request needs review.",
  cancelled: "Search cancelled. Your conversation will reload before the next request.",
  failed: "Search could not complete.", suggestions: "Try instead", alternatives: "Suggested alternatives",
  resultsRegion: "Search results"
};

export interface SearchElementOptions {
  baseUrl: string; bearerToken?: string; backend?: string; lockedFields?: string[]; transport?: "rest" | "sse";
  /** `session` (default): owned conversation with consent for alternatives. `search`: stateless v2 search. */
  mode?: "session" | "search";
  /** `search` mode: `commerce-search/1.0` lets the server plan the retrieval strategy. */
  contract?: "commerce-search/1.0";
  /** Document field shown as each result's title (default `title`, falling back to the id). */
  titleField?: string;
  /** Document fields shown under the title, joined by spaces (default `price`, `currency`). */
  detailFields?: string[];
  limit?: number; timeoutMs?: number;
  labels?: Partial<SearchElementLabels>;
}

/** The documented states, exposed as the host's `data-state` and the `qwenbridge-state` event. */
export type SearchElementState = "initial" | "loading" | "success" | "empty" | "partial" | "clarification" | "review" | "error" | "cancelled";

/**
 * `<qwenbridge-search>`: a framework-free natural-language search box for the public v2 API
 * (QB-193 – QB-197). Untrusted text is only ever inserted with `textContent`. Credentials are set
 * with `configure()` only, never read from attributes. Out-of-order responses are discarded.
 */
export class QwenBridgeSearch extends HTMLElement {
  static readonly observedAttributes = ["base-url", "mode", "transport", "backend", "title-field", "detail-fields", "contract"];
  private options: SearchElementOptions = { baseUrl: location.origin, transport: "rest" };
  private client = new CommerceClient(this.options);
  private sessionState: CommerceSession | null = null;
  private operation: AbortController | null = null;
  /** Bumped by every new operation, reset and disconnect; a result is shown only if still current. */
  private generation = 0;
  private reload = false;
  private lastQuery = "";
  private readonly root = this.attachShadow({ mode: "open", delegatesFocus: true });
  public get sessionId(): string | null { return this.sessionState?.id ?? null; }
  public get state(): SearchElementState { return (this.dataset.state as SearchElementState | undefined) ?? "initial"; }
  private get labels(): SearchElementLabels { return { ...DEFAULT_LABELS, ...this.options.labels }; }

  public configure(options: SearchElementOptions): void {
    const url = new URL(options.baseUrl, location.href);
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) throw new Error("Invalid Search API URL");
    if (options.mode && !["session", "search"].includes(options.mode)) throw new Error("Invalid search mode");
    this.stop(); this.sessionState = null; this.reload = false;
    this.options = { ...options, baseUrl: url.href };
    this.client = new CommerceClient({ baseUrl: url.href, bearerToken: options.bearerToken, timeoutMs: options.timeoutMs });
    if (this.isConnected) { this.render(); this.setState("initial"); }
  }

  /** Declarative setup: `<qwenbridge-search base-url="…" mode="search">` works without script. */
  attributeChangedCallback(): void {
    const base = this.getAttribute("base-url");
    if (!base) return;
    const list = this.getAttribute("detail-fields");
    this.configure({
      ...this.options, baseUrl: base,
      mode: (this.getAttribute("mode") as SearchElementOptions["mode"]) ?? this.options.mode,
      transport: (this.getAttribute("transport") as SearchElementOptions["transport"]) ?? this.options.transport,
      backend: this.getAttribute("backend") ?? this.options.backend,
      titleField: this.getAttribute("title-field") ?? this.options.titleField,
      detailFields: list === null ? this.options.detailFields : list.split(",").map(f => f.trim()).filter(Boolean),
      contract: (this.getAttribute("contract") as SearchElementOptions["contract"]) ?? this.options.contract
    });
  }

  public async resume(id: string): Promise<void> {
    const { operation, generation } = this.start();
    try {
      const session = await this.client.session(id, operation.signal);
      if (generation !== this.generation) return;
      this.sessionState = session; this.render();
      if (session.last) this.show(session.last);
    } catch (error) { if (generation === this.generation) this.error(error); }
    finally { this.finish(operation); }
  }
  connectedCallback(): void { this.render(); }
  /** Lifecycle cleanup: in-flight work is aborted (and cancelled on the server); nothing renders later. */
  disconnectedCallback(): void { this.stop(); }

  private stop(): void { this.generation++; this.operation?.abort(); this.operation = null; }
  private start(): { operation: AbortController; generation: number } {
    this.stop();
    const operation = new AbortController(); this.operation = operation; this.busy(true);
    return { operation, generation: this.generation };
  }
  private finish(operation: AbortController): void {
    if (this.operation === operation) { this.operation = null; this.busy(false); }
  }
  private element<T extends HTMLElement>(selector: string): T { return this.root.querySelector<T>(selector)!; }
  private setState(state: SearchElementState): void {
    this.dataset.state = state;
    this.dispatchEvent(new CustomEvent("qwenbridge-state", { detail: { state }, bubbles: true, composed: true }));
  }

  private render(): void {
    // Static markup only; every configurable or server-provided text is set with textContent.
    this.root.innerHTML = `<link rel="stylesheet" href="${new URL("search-element.css", import.meta.url).href}">
      <form><label for="query"></label><textarea id="query" dir="auto" rows="3" required maxlength="4096" aria-describedby="status"></textarea>
      <div class="actions"><button type="submit" data-search></button><button type="button" data-new></button><button type="button" data-cancel hidden></button></div></form>
      <p id="status" role="status" aria-live="polite"></p><p role="alert" hidden></p>
      <section data-results aria-busy="false"><p data-summary tabindex="-1"></p><ol data-list></ol></section>
      <section data-suggestions hidden><p data-suggestions-title></p><div data-suggestion-list></div></section>
      <section data-alternatives></section>`;
    const labels = this.labels;
    this.element("label").textContent = labels.prompt;
    const input = this.element<HTMLTextAreaElement>("textarea"); input.placeholder = labels.placeholder;
    this.element("[data-search]").textContent = labels.search;
    this.element("[data-new]").textContent = labels.newSearch;
    this.element("[data-cancel]").textContent = labels.cancel;
    this.element("[data-results]").setAttribute("aria-label", labels.resultsRegion);
    this.element("[data-alternatives]").setAttribute("aria-label", labels.alternatives);
    this.element("[data-suggestions]").setAttribute("aria-label", labels.suggestions);
    this.element("[data-suggestions-title]").textContent = labels.suggestions;
    this.element<HTMLFormElement>("form").addEventListener("submit", event => { event.preventDefault(); void this.submit(); });
    input.addEventListener("input", () => { const first = input.value.match(/\p{Letter}/u)?.[0]; this.dir = first && /[\p{Script=Arabic}\p{Script=Hebrew}]/u.test(first) ? "rtl" : "ltr"; });
    input.addEventListener("keydown", event => { if (event.key === "Enter" && !event.shiftKey && !event.isComposing) { event.preventDefault(); this.element<HTMLFormElement>("form").requestSubmit(); } });
    // Escape cancels a running search from anywhere inside the component.
    this.root.addEventListener("keydown", event => { if ((event as KeyboardEvent).key === "Escape" && this.operation) { event.preventDefault(); this.cancel(); } });
    this.element<HTMLButtonElement>("[data-new]").addEventListener("click", () => { this.stop(); this.sessionState = null; this.reload = false; this.render(); this.setState("initial"); this.element<HTMLTextAreaElement>("textarea").focus(); });
    this.element<HTMLButtonElement>("[data-cancel]").addEventListener("click", () => this.cancel());
    if (!this.dataset.state) this.setState("initial");
  }
  private cancel(): void {
    this.operation?.abort();
    // Keep the keyboard user in the input after cancelling.
    this.element<HTMLTextAreaElement>("textarea").focus();
  }
  private busy(value: boolean): void {
    if (!this.root.querySelector("textarea")) return;
    this.element<HTMLTextAreaElement>("textarea").readOnly = value;
    this.element<HTMLTextAreaElement>("textarea").setAttribute("aria-disabled", String(value));
    this.root.querySelectorAll<HTMLButtonElement>("button:not([data-cancel])").forEach(button => { button.disabled = value; });
    this.element<HTMLButtonElement>("[data-cancel]").hidden = !value;
    this.element("[data-results]").setAttribute("aria-busy", String(value));
    this.element("[role=status]").textContent = value ? this.labels.searching : "";
    if (value) { this.element("[role=alert]").hidden = true; this.setState("loading"); }
  }
  private error(error: unknown): void {
    const alert = this.element("[role=alert]"); alert.hidden = false;
    const cancelled = error instanceof DOMException && error.name === "AbortError";
    alert.textContent = cancelled ? this.labels.cancelled
      : error instanceof QwenBridgeApiError ? `${error.message} (HTTP ${error.status})`
      : error instanceof Error ? error.message : this.labels.failed;
    this.setState(cancelled ? "cancelled" : "error");
  }

  private async submit(query?: string): Promise<void> {
    const input = this.element<HTMLTextAreaElement>("textarea");
    const text = (query ?? input.value).trim(); if (!text || this.operation) return;
    this.lastQuery = text;
    const { operation, generation } = this.start();
    try {
      const response = this.options.mode === "search" ? await this.searchOnce(text, operation.signal) : await this.sessionTurn(text, operation.signal);
      if (generation !== this.generation) return;
      if (response) this.show(response);
      if (this.options.mode !== "search") await this.alternatives(operation.signal, generation);
    } catch (error) {
      if (generation !== this.generation) return;
      this.reload = true; this.error(error);
    } finally { this.finish(operation); }
  }

  private async searchOnce(query: string, signal: AbortSignal): Promise<CommerceResponse> {
    const request = { query, backend: this.options.backend, limit: this.options.limit, ...(this.options.contract ? { contract: this.options.contract, mode: null } : {}) };
    if (this.options.transport !== "sse") return this.client.search(request, signal);
    let completed: CommerceResponse | null = null;
    await this.client.stream(request, event => {
      if (event.kind === "search.failed") throw new Error(event.error?.message ?? event.message);
      if (event.kind === "search.completed") completed = event.response;
    }, signal);
    if (!completed) throw new Error("Search stream ended without a result");
    return completed;
  }

  private async sessionTurn(text: string, signal: AbortSignal): Promise<CommerceResponse | null> {
    if (!this.sessionState) this.sessionState = await this.client.createSession({ backend: this.options.backend, lockedFields: this.options.lockedFields }, signal);
    if (this.reload) { this.sessionState = await this.client.session(this.sessionState.id, signal); this.reload = false; }
    const turn = { query: text, expectedRevision: this.sessionState.revision };
    if (this.options.transport === "sse") {
      let completed: CommerceSession | null = null;
      await this.client.streamTurn(this.sessionState.id, turn, event => {
        if (event.kind === "session.failed") throw new Error(event.error?.message ?? event.message);
        if (event.kind === "session.completed") { if (!event.session) throw new Error("Missing completed session"); completed = event.session; }
      }, signal);
      if (!completed) throw new Error("Session stream ended without a result"); this.sessionState = completed;
    } else this.sessionState = await this.client.turn(this.sessionState.id, turn, signal);
    return this.sessionState.last;
  }

  private stateOf(response: CommerceResponse): SearchElementState {
    if (response.status === "CLARIFICATION_REQUIRED") return "clarification";
    if (response.status === "BLOCKED" || response.status === "REVIEW") return "review";
    if (response.status === "UNAVAILABLE") return response.results?.hits?.length ? "partial" : "error";
    if (response.status === "PARTIAL" || response.retrieval?.degraded) return "partial";
    return response.results.totalHits ? "success" : "empty";
  }

  private show(response: CommerceResponse): void {
    const labels = this.labels; const state = this.stateOf(response);
    const summary = this.element("[data-summary]");
    const count = labels.results.replace("{count}", String(response.results.totalHits));
    summary.textContent = state === "clarification" ? response.clarificationQuestions.join(" ") || labels.review
      : state === "review" ? labels.review
      : state === "error" ? labels.unavailable
      : state === "partial" ? `${count} ${labels.partial}`
      : state === "empty" ? labels.empty : count;
    const list = this.element("[data-list]"); list.replaceChildren();
    const titleField = this.options.titleField ?? "title";
    const detailFields = this.options.detailFields ?? ["price", "currency"];
    for (const hit of response.results?.hits ?? []) {
      const item = document.createElement("li"); const article = document.createElement("article");
      const heading = document.createElement("h3"); heading.dir = "auto";
      const title = hit.document[titleField]; heading.textContent = title === undefined || title === null ? hit.id : String(title);
      const details = document.createElement("p"); details.dir = "auto";
      details.textContent = detailFields.map(field => hit.document[field]).filter(value => value !== undefined && value !== null).map(String).join(" ");
      article.append(heading, details); item.append(article); list.append(item);
    }
    this.suggestions(response);
    this.setState(state);
    this.dispatchEvent(new CustomEvent("qwenbridge-result", { detail: response, bubbles: true, composed: true }));
  }

  /** Server suggestions and clarification options: each one runs as a new query when chosen. */
  private suggestions(response: CommerceResponse): void {
    const section = this.element("[data-suggestions]"); const list = this.element("[data-suggestion-list]"); list.replaceChildren();
    const options = [
      ...(response.suggestions ?? []),
      ...(response.clarifications ?? []).flatMap(c => c.options.map(option => `${this.lastQuery} ${option}`))
    ].filter((value, index, all) => value.trim() && all.indexOf(value) === index).slice(0, 8);
    for (const option of options) {
      const button = document.createElement("button"); button.type = "button"; button.dir = "auto"; button.textContent = option;
      button.addEventListener("click", () => { this.element<HTMLTextAreaElement>("textarea").value = option; void this.submit(option); });
      list.append(button);
    }
    section.hidden = options.length === 0;
  }

  private async alternatives(signal: AbortSignal, generation: number): Promise<void> {
    const section = this.element("[data-alternatives]"); section.replaceChildren();
    if (!this.sessionState?.last || this.sessionState.last.status !== "OK" || this.sessionState.last.results.totalHits !== 0) return;
    const proposals = await this.client.proposals(this.sessionState.id, signal);
    if (generation !== this.generation) return;
    for (const proposal of proposals) {
      if (proposal.status === "ACCEPTED") continue;
      const button = document.createElement("button"); button.type = "button"; button.textContent = `Accept change: ${proposal.description}`;
      button.addEventListener("click", () => { void this.accept(proposal); }); section.append(button);
    }
  }

  private async accept(proposal: RelaxationProposal): Promise<void> {
    if (!this.sessionState || this.operation) return;
    const { operation, generation } = this.start();
    try {
      const session = await this.client.accept(this.sessionState.id, proposal.id, this.sessionState.revision, operation.signal);
      if (generation !== this.generation) return;
      this.sessionState = session;
      if (session.last) this.show(session.last);
      await this.alternatives(operation.signal, generation);
      // The chosen button is gone: move focus to the new result summary.
      if (generation === this.generation) this.element<HTMLElement>("[data-summary]").focus();
    } catch (error) { if (generation === this.generation) { this.reload = true; this.error(error); } }
    finally { this.finish(operation); }
  }
}
if (!customElements.get("qwenbridge-search")) customElements.define("qwenbridge-search", QwenBridgeSearch);
