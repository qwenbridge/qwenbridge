import { readFileSync } from "node:fs";
import { expect, it } from "vitest";
import { CommerceClient, COMMERCE_RESPONSE_FIELDS } from "../src/commerce-client.js";
import { QwenBridgeClient } from "../src/qwenbridge-client.js";
import type { Expansion } from "../src/index.js";

/** The expansion object of a real server response (QB-286), shared with the Java SDK. */
const expansion: Expansion = JSON.parse(
  readFileSync(new URL("../../qwenbridge-java-sdk/src/test/resources/contract/v2-search-expansion.json", import.meta.url), "utf8")
).expansion;

it("exposes one result per configured language on v2 search and the stream", async () => {
  expect(COMMERCE_RESPONSE_FIELDS.expansion).toBe(true);
  const client = new CommerceClient({ baseUrl: "http://localhost:8080", fetch: async (url) => {
    if (String(url).endsWith("/search/stream")) {
      const body = `event:search.completed\ndata:${JSON.stringify({ version: 2, kind: "search.completed", requestId: "r", response: { apiVersion: 2, requestId: "r", status: "OK", expansion }, message: "", sequence: 2, terminal: true })}\n\n`;
      return new Response(body, { headers: { "Content-Type": "text/event-stream" } });
    }
    return Response.json({ apiVersion: 2, requestId: "r", status: "OK", expansion });
  }});
  const response = await client.search({ query: expansion.original });
  const e = response.expansion!;
  expect(e.original).toBe("I need a red winter jacket under 100 euros");
  expect(e.requestedLanguages).toEqual(["en", "FA", "sv", "nb", "nn", "ja", "DK"]);
  expect(e.targets.map(t => t.status)).toEqual(["UNCHANGED", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "UNSUPPORTED_LANGUAGE", "INVALID_LANGUAGE"]);
  expect(e.targets.find(t => t.language === "sv")?.text).toBe("jag behöver en röd vinterjacka under 100 euro");
  expect(e.targets[5].text).toBeUndefined();
  expect(e.unsupportedLanguages).toEqual(["ja", "DK"]);
  expect(e.source.basis).toBe("VOCABULARY");
  const events: Array<{ response: { expansion?: Expansion } | null }> = [];
  await client.stream({ query: expansion.original }, ev => events.push(ev));
  expect(events.at(-1)?.response?.expansion?.status).toBe("PARTIAL");
});

it("exposes expansion on v1 analyze and tolerates its absence", async () => {
  const withExpansion = new QwenBridgeClient({ baseUrl: "http://localhost:8080", fetch: async () => Response.json({ requestId: "r", originalQuery: "q", expansion }) });
  expect((await withExpansion.analyze({ query: "q" })).expansion?.targets.length).toBe(7);
  const without = new QwenBridgeClient({ baseUrl: "http://localhost:8080", fetch: async () => Response.json({ requestId: "r", originalQuery: "q" }) });
  expect((await without.analyze({ query: "q" })).expansion).toBeUndefined();
});
