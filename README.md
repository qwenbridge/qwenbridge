# OmniSearch AI

**AI-native search intelligence for search boxes.**

OmniSearch AI is a lightweight, extensible intelligence layer that sits between a user-facing search box and a search engine. It analyzes, validates, rewrites, and classifies user queries before they reach the search backend.

V1 focuses only on **Search Box Intelligence**.

---

## Why OmniSearch AI?

Traditional search flows often look like this:

```text
User Query
  -> Search Engine
  -> No Results / Poor Results
```

OmniSearch AI improves the flow:

```text
User Query
  -> Understand
  -> Validate
  -> Rewrite
  -> Check Policy
  -> Decide
  -> Search Engine
```

The goal is to make search boxes smarter, safer, multilingual, and easier to debug.

---

## V1 Scope

V1 includes only search-box intelligence.

### Included

- Language detection
- Intent analysis
- Threat analysis
- Query rewrite
- Semantic validation
- Policy evaluation
- Confidence scoring
- Decision engine
- Pipeline trace
- REST API
- Unit tests
- API integration tests
- Docker support
- GitHub Actions CI

### Out of Scope

- Forms
- Payments
- Checkout flows
- AI Prompt Gateway
- API Gateway
- Product catalog
- Database
- Repository layer
- Search engine implementation

---

## Core Decisions

Every request must end with one final decision:

| Decision | Meaning |
|---|---|
| `ALLOW` | Query can continue as-is |
| `REWRITE` | Query should be rewritten before search |
| `CLARIFY` | Query is too ambiguous or low-confidence |
| `BLOCK` | Query is unsafe or violates policy |

---

## Architecture

OmniSearch AI uses a generic step-based pipeline engine.

```text
User Query
  |
  v
LanguageStep
  |
  v
IntentStep
  |
  v
ThreatStep
  |
  v
RewriteStep
  |
  v
SemanticStep
  |
  v
PolicyStep
  |
  v
ConfidenceStep
  |
  v
DecisionStep
  |
  v
Final Decision
```

Each step returns an independent result object.

The `ExecutionContext` is a typed result store, not a God Object.

```text
ExecutionContext
  |
  +-- RequestContext
  +-- LanguageResult
  +-- IntentResult
  +-- ThreatResult
  +-- RewriteResult
  +-- SemanticResult
  +-- PolicyResult
  +-- ConfidenceResult
  +-- DecisionResult
```

The engine is generic and does not know business logic.

---

## Pipeline Trace

Every response includes a `pipelineTrace` field.

This shows which steps were executed and which were skipped.

Example for a blocked query:

```json
"pipelineTrace": [
  {"step": "LanguageStep", "status": "EXECUTED", "durationMs": 0},
  {"step": "IntentStep", "status": "EXECUTED", "durationMs": 0},
  {"step": "ThreatStep", "status": "EXECUTED", "durationMs": 0},
  {"step": "RewriteStep", "status": "SKIPPED", "durationMs": 0},
  {"step": "SemanticStep", "status": "SKIPPED", "durationMs": 0},
  {"step": "PolicyStep", "status": "SKIPPED", "durationMs": 0},
  {"step": "ConfidenceStep", "status": "SKIPPED", "durationMs": 0},
  {"step": "DecisionStep", "status": "SKIPPED", "durationMs": 0}
]
```

This makes the system easier to debug, audit, and observe.

---

## AI Stack

V1 currently uses mock implementations.

Planned AI stack:

| Component | Responsibility |
|---|---|
| Ollama | Local model runtime |
| Qwen | Query rewrite, expansion, intent support, JSON generation |
| BGE-M3 | Semantic validation and multilingual meaning preservation |

Responsibility split:

```text
Qwen
  -> Generate candidate rewrites

BGE-M3
  -> Validate semantic preservation

Rules and Policy
  -> Reject unsafe or invalid candidates
```

Qwen is creative.  
BGE-M3 is precise.

---

## REST API

### Analyze Search Query

```http
POST /api/v1/search/analyze
Content-Type: application/json
```

Request:

```json
{
  "query": "میز"
}
```

Response:

```json
{
  "requestId": "01067c3e-f379-4fb2-8ea6-17fb6ff7d183",
  "processingTimeMs": 1,
  "originalQuery": "میز",
  "language": "fa",
  "intent": "PRODUCT_SEARCH",
  "decision": "REWRITE",
  "confidence": 0.94,
  "rewrites": ["desk", "table", "office desk"],
  "threatReasons": [],
  "semanticValidated": true,
  "semanticScore": 0.96,
  "policyPassed": true,
  "policyViolations": [],
  "pipelineTrace": [
    {
      "step": "LanguageStep",
      "status": "EXECUTED",
      "durationMs": 0
    }
  ]
}
```

Threat example:

```json
{
  "query": "desk union select password from users"
}
```

Expected decision:

```json
{
  "decision": "BLOCK",
  "threatReasons": ["SQL_INJECTION"]
}
```

---

## Requirements

- Java 21
- Maven 3.9+
- Docker optional

---

## Run Locally

Run tests:

```bash
mvn clean test
```

Run the application:

```bash
mvn spring-boot:run
```

Test the API:

```bash
curl -X POST http://localhost:8080/api/v1/search/analyze \
  -H "Content-Type: application/json" \
  -d '{"query":"میز"}'
```

---

## Docker

Start Ollama:

```bash
docker compose up -d ollama
```

Build and run the full stack:

```bash
docker compose up --build
```

Pull planned local models:

```bash
./scripts/pull-models.sh
```

---

## Tests

The current test suite includes:

- Unit tests for pipeline steps
- Pipeline engine stop/skip behavior tests
- API integration tests
- Validation tests

Run:

```bash
mvn clean test
```

Current status:

```text
22 tests
0 failures
```

---

## Documentation

| Area | Link |
|---|---|
| Architecture Overview | [docs/architecture/overview.md](docs/architecture/overview.md) |
| Pipeline Architecture | [docs/architecture/pipeline.md](docs/architecture/pipeline.md) |
| AI Stack | [docs/architecture/ai-stack.md](docs/architecture/ai-stack.md) |
| Modules | [docs/architecture/modules.md](docs/architecture/modules.md) |
| REST API | [docs/api/rest-api.md](docs/api/rest-api.md) |
| ADRs | [docs/adr](docs/adr) |
| Roadmap V1 | [docs/roadmap/V1.md](docs/roadmap/V1.md) |
| Roadmap V2 | [docs/roadmap/V2.md](docs/roadmap/V2.md) |
| Roadmap V3 | [docs/roadmap/V3.md](docs/roadmap/V3.md) |
| Roadmap V4 | [docs/roadmap/V4.md](docs/roadmap/V4.md) |
| Roadmap V5 | [docs/roadmap/V5.md](docs/roadmap/V5.md) |

---

## Roadmap

### V1 - Search Box Core

Search-box intelligence foundation.

### V2 - Real AI Integration

Ollama, Qwen, BGE-M3, provider abstraction, timeout and retry policies.

### V3 - Search Intelligence Expansion

Suggestions, typo correction, synonym engine, search analytics, feedback loop.

### V4 - Intelligent Forms

Apply the pipeline to form input validation, normalization, and policy checks.

### V5 - AI Gateway

Generalize the pipeline into an AI Gateway for prompts, routing, policy, safety, and audit.

---

## Architecture Decision Records

- [ADR 0001 - Step-Based Pipeline](docs/adr/0001-step-based-pipeline.md)
- [ADR 0002 - Result-Based Execution Context](docs/adr/0002-result-based-execution-context.md)
- [ADR 0003 - Qwen and BGE-M3 Responsibility Split](docs/adr/0003-qwen-bge-responsibility-split.md)

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Security

See [SECURITY.md](SECURITY.md).

---

## Code of Conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

---

## License

This project is licensed under the terms in [LICENSE](LICENSE).
