# Developer Playground and local end-to-end testing

## Run the Playground locally

The Playground is served by the server at `/playground/`. It needs no Shopify, database or search
service:

```bash
QWENBRIDGE_COMMERCE_BACKEND=memory \
QWENBRIDGE_PLATFORM_STORE=memory \
QWENBRIDGE_DEMO_DATASETS=commerce-demo \
QWENBRIDGE_ANALYSIS_CACHE_ENABLED=false \
QWENBRIDGE_ABUSE_PROTECTION_REDIS_ENABLED=false \
java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar
# open http://localhost:8080/playground/
```

- **Data.** `commerce-demo` loads the synthetic catalog below into the in-process backend, for the
  anonymous tenant `public`; set `QWENBRIDGE_DEMO_TENANT` to choose another tenant.
- **Safety.** Demo datasets refuse to load into any backend other than `memory`.
- **Model.** Analysis needs a language model (`QWENBRIDGE_AI_OLLAMA_BASE_URL`). Without one, every
  search fails closed to `REVIEW` ("Model safety decision unavailable"), and the Playground shows
  exactly that; it does not guess.

## What it shows

Everything shown is the server's recorded response, or the result of a real follow-up request.

| Section | Source |
|---|---|
| QueryIR, Translation, Decision, Evidence, Stage timings, Products | The response fields, verbatim |
| Constraints: explicit or inferred | Each constraint's evidence span. **Explicit** means the quote occurs in the user's input (with character offsets); anything else is **inferred**, with its evidence source |
| Retrieval plan | `retrieval` (requested and executed strategy, reasons, degradations, providers, ranking policy). apiVersion 2 responses have no plan, and the page says so |
| Confidence | `calibrated` decides the label: "Calibrated probability", or "Heuristic score — not a calibrated probability". Product scores are labelled backend relevance scores |
| Strategy comparison | The same query executed with KEYWORD, VECTOR, HYBRID and the planned contract on the selected backend: ranks per result, the executed strategy, and typed refusals for unsupported strategies |
| Executed filter clauses | `GET /api/v2/search/{id}/debug` (needs `debug:read`) |

**Mode, contract and backend** are chosen in the connection form. The backend list comes from
`GET /api/v2/backends`.

## Datasets

| Dataset | Where | Used by |
|---|---|---|
| `commerce-demo` 1.0 | `qwenbridge-server/src/main/resources/datasets/commerce-demo-1.0.json` | Playground demo, `ServiceFreeE2ETest` |
| `library` 1.0 | `qwenbridge-core/src/test/resources/datasets/library-1.0.json` | `LibraryDatasetTest` (Core only) |

- **Records.** All records are synthetic, with stable ids. Each edge case is named in the file:
  - refurbished, over budget, other colour, brand or size;
  - out of stock, other currency, condition unstated;
  - case and language variants, a price boundary;
  - Persian and Swedish titles.
  - The library set adds structured filters, exclusions on missing fields, Arabic-script and Swedish
    text, ranking, and tenant isolation.
- **Expectations.** Each dataset carries its expected results and the reason for each; the tests
  only execute them.
- **Two Commerce expectations.** The demo dataset has one per contract, because exclusions differ:
  - **apiVersion 2:** "not refurbished" needs a stated condition.
  - **commerce-search/1.0:** an exclusion keeps a record that lacks the field (ADR 0025), so
    `demo-shoe-09` (condition unstated) is included.

## End-to-end tests without external services

`qwenbridge-server/src/test/java/io/qwenbridge/e2e/ServiceFreeE2ETest` runs complete workflows. In
that test:
- Redis and OpenSearch are unreachable or unconfigured, and no database exists;
- the platform store, evidence and analytics run in process (`qwenbridge.platform.store=memory`);
- the model is a controlled local provider: an Ollama-compatible stub on 127.0.0.1 with fixed,
  grounded answers.

**What runs for real:** the client, prompt, parser, grounding checks and safety gate. Covered
workflows:
- search with the exact expected products, grounded QueryIR and evidence;
- the planned contract's recorded plan;
- refinement, streaming and sessions;
- tenant isolation;
- the error paths: a model `BLOCK`, a model that is unavailable (fails closed to `REVIEW`), invalid
  input, and a missing credential.

## Live external targets

Shopify and other authorized providers are optional end-to-end targets of the dual-SDK suite; see
[e2e/dual-sdk/README.md](../../e2e/dual-sdk/README.md).
