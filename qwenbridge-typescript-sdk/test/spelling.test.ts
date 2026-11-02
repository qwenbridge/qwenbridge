import { expect, it } from "vitest";
import { CommerceClient, COMMERCE_RESPONSE_FIELDS } from "../src/commerce-client.js";
import { QwenBridgeClient } from "../src/qwenbridge-client.js";

/** The server's spelling shape (QB-291), as sent on v1 analyze, v2 search and SSE events. */
const spelling = {
  status: "CORRECTED", mode: "SAFE_AUTO_CORRECT", original: "blak shoes 42", normalized: "blak shoes 42", corrected: "black shoes 42",
  languages: [{ configured: "en", status: "SUPPORTED", providers: ["dictionary"] }, { configured: "de", status: "PROVIDER_UNAVAILABLE", providers: [] }],
  detectedLanguage: null, activeLanguages: ["en"],
  corrections: [{ token: "blak", start: 0, end: 4, language: "en", candidates: [{ form: "black", language: "en", score: 0.9 }],
    replacement: "black", confidence: 0.9, applied: true, reason: "en: edit distance 1.0" }],
  protectedTokens: 1, explanations: ["\"blak\" -> \"black\": en: edit distance 1.0"]
};

it("exposes spelling on v2 search responses and stream events", async () => {
  expect(COMMERCE_RESPONSE_FIELDS.spelling).toBe(true);
  const client = new CommerceClient({ baseUrl: "http://localhost:8080", fetch: async (url) => {
    if (String(url).endsWith("/search/stream")) {
      const body = `event:search.completed\ndata:${JSON.stringify({ version: 2, kind: "search.completed", requestId: "r", response: { apiVersion: 2, requestId: "r", status: "OK", spelling }, message: "", sequence: 2, terminal: true })}\n\n`;
      return new Response(body, { headers: { "Content-Type": "text/event-stream" } });
    }
    return Response.json({ apiVersion: 2, requestId: "r", status: "OK", spelling });
  }});
  const response = await client.search({ query: "blak shoes 42" });
  expect(response.spelling?.corrected).toBe("black shoes 42");
  expect(response.spelling?.original).toBe("blak shoes 42");
  expect(response.spelling?.languages[1].status).toBe("PROVIDER_UNAVAILABLE");
  const events: Array<{ response: { spelling?: { status: string } } | null }> = [];
  await client.stream({ query: "blak shoes 42" }, e => events.push(e));
  expect(events.at(-1)?.response?.spelling?.status).toBe("CORRECTED");
});

it("exposes spelling on v1 analyze responses and tolerates its absence", async () => {
  const withSpelling = new QwenBridgeClient({ baseUrl: "http://localhost:8080", fetch: async () => Response.json({ requestId: "r", originalQuery: "blak shoes 42", spelling }) });
  const analyzed = await withSpelling.analyze({ query: "blak shoes 42" });
  expect(analyzed.spelling?.corrections[0].applied).toBe(true);
  const without = new QwenBridgeClient({ baseUrl: "http://localhost:8080", fetch: async () => Response.json({ requestId: "r", originalQuery: "q" }) });
  expect((await without.analyze({ query: "q" })).spelling).toBeUndefined();
});
