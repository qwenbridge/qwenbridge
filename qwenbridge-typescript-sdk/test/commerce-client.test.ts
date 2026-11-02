import { expect, it } from "vitest";
import { CommerceClient, type CommerceEvent } from "../src/commerce-client.js";

it("uses the versioned API and keeps bearer credentials in headers", async () => {
  const client = new CommerceClient({ baseUrl: "http://localhost:8080", bearerToken: "test-only", fetch: async (url, init) => {
    expect(String(url)).toBe("http://localhost:8080/api/v2/search");
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer test-only");
    expect(JSON.parse(String(init?.body)).query).toBe("کفش مشکی");
    return Response.json({ apiVersion: 2, status: "CLARIFICATION_REQUIRED", clarificationQuestions: ["Which size?"] });
  }});
  const response = await client.search({ query: "کفش مشکی" });
  expect(response.clarificationQuestions).toEqual(["Which size?"]);
});

it("decodes typed SSE across byte and line boundaries and stops on the terminal event", async () => {
  const events: CommerceEvent[] = [];
  const text = `event:search.started\ndata:${JSON.stringify({ version: 2, kind: "search.started", requestId: "r", response: null, message: "کفش" })}\n\nevent:search.completed\ndata:${JSON.stringify({ version: 2, kind: "search.completed", requestId: "r", response: { apiVersion: 2, status: "OK" }, message: "" })}\n\n`;
  const bytes = new TextEncoder().encode(text);
  const client = new CommerceClient({ baseUrl: "http://localhost", fetch: async () => new Response(new ReadableStream({ start(controller) {
    for (let i = 0; i < bytes.length; i += 3) controller.enqueue(bytes.slice(i, i + 3));
    controller.close();
  }})) });
  await client.stream({ query: "کفش" }, event => events.push(event));
  expect(events.map(event => event.kind)).toEqual(["search.started", "search.completed"]);
  expect(events[0]?.message).toBe("کفش");
});

it("rejects failed HTTP calls and truncated streams", async () => {
  const client = new CommerceClient({ baseUrl: "http://localhost", fetch: async () => new Response("", { status: 403 }) });
  await expect(client.search({ query: "shoe" })).rejects.toThrow("403");
  const truncated = new CommerceClient({ baseUrl: "http://localhost", fetch: async () => new Response("event:search.started\n\n") });
  await expect(truncated.stream({ query: "shoe" }, () => {})).rejects.toThrow("terminal");
});


it("binds session acceptance to an owned resource and revision without client supplied filters", async () => {
  const calls: Array<{ url: string; method: string; body: unknown }> = [];
  const client = new CommerceClient({ baseUrl: "http://localhost", fetch: async (url, init) => {
    calls.push({ url: String(url), method: init?.method ?? "GET", body: init?.body ? JSON.parse(String(init.body)) : null });
    return Response.json(String(url).endsWith("/relaxations") ? [{ id: "proposal", status: "OFFERED" }] : { id: "session", revision: 2 });
  }});
  await client.createSession({ lockedFields: ["price"] }); await client.session("session");
  await client.turn("session", { query: "size 42", expectedRevision: 1 });
  expect((await client.proposals("session"))[0]?.id).toBe("proposal");
  await client.accept("session", "proposal", 2);
  expect(calls.at(-1)).toEqual({ url: "http://localhost/api/v2/sessions/session/relaxations/proposal/accept", method: "POST", body: { expectedRevision: 2 } });
  expect(() => client.session("../other")).toThrow("identifier");
});

// QB-185: the shared 1.0 contract fixtures (also parsed by the Java SDK and checked against the
// server's real events): 1.0 fields, heartbeat comments and typed failures must stay consumable.
it("consumes 1.0 progress streams with heartbeats and typed failures", async () => {
  const { readFileSync } = await import("node:fs");
  const { fileURLToPath } = await import("node:url");
  for (const name of ["v2-search-stream-completed.sse", "v2-search-stream-failed.sse"]) {
    const text = readFileSync(fileURLToPath(new URL(`../../qwenbridge-java-sdk/src/test/resources/contract/${name}`, import.meta.url)), "utf8");
    const events: CommerceEvent[] = [];
    const client = new CommerceClient({ baseUrl: "http://localhost", fetch: async () => new Response(text) });
    await client.stream({ query: "shoes" }, event => events.push(event));
    expect(events.map(event => event.kind)[0]).toBe("search.started");
    expect(events).toHaveLength(2);
  }
});
