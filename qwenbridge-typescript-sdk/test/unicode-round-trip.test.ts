import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";
import { QwenBridgeClient } from "../src/index.js";

// QB-002: the TypeScript SDK sends and receives multilingual text without loss, using the
// shared corpus owned by qwenbridge-core.
const here = dirname(fileURLToPath(import.meta.url));
const corpusPath = resolve(
  here,
  "../../qwenbridge-core/src/test/resources/corpus/multilingual-input-corpus.json"
);

interface CorpusCase {
  id: string;
  query?: string;
  expect: string;
}

const corpus = JSON.parse(readFileSync(corpusPath, "utf-8")) as { cases: CorpusCase[] };
const validCases = corpus.cases.filter((c) => c.expect === "VALID");

describe("Unicode round trip", () => {
  it("covers the shared corpus", () => {
    expect(validCases.length).toBeGreaterThan(10);
  });

  it.each(validCases.map((c) => [c.id, c.query as string]))(
    "%s survives request encoding and response decoding",
    async (_id, query) => {
      const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (_url, init) => {
        // Encode the request body exactly as fetch would put it on the wire, then decode strictly.
        const wire = new TextEncoder().encode(String(init?.body));
        const decoded = new TextDecoder("utf-8", { fatal: true }).decode(wire);
        const sent = JSON.parse(decoded) as { query: string; requestId: string };
        expect(sent.query).toBe(query);
        return new Response(
          new TextEncoder().encode(
            JSON.stringify({ requestId: sent.requestId, originalQuery: sent.query })
          ),
          { status: 200, headers: { "Content-Type": "application/json; charset=utf-8" } }
        );
      });

      const client = new QwenBridgeClient({ baseUrl: "http://localhost:8080", fetch: fetchMock });
      const response = await client.analyze({ requestId: "ts-unicode", query });

      expect(response.originalQuery).toBe(query);
      expect(Array.from(new TextEncoder().encode(response.originalQuery))).toEqual(
        Array.from(new TextEncoder().encode(query))
      );
    }
  );
});
