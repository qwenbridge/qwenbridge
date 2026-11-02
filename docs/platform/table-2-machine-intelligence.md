# Table 2 — Machine-to-Human Intelligence

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

This table follows Table 1 and has not been started or authorized for implementation
in the current step. All Java work will continue on Quarkus/CDI and Java 25.

| Batch | Phases | Acceptance | Status |
|---|---|---|---|
| 1 | 40, 41 | Parser SPI, raw source preservation, malformed inputs and versioned typed events | Pending |
| 2 | 46, 47 | Correlation, deduplication/bursts/late arrivals and occurrence/receipt timeline uncertainty | Pending |
| 3 | 42, 43 + integration with 44 | Evidence-linked explanations with consistent facts, uncertainty and audience permissions | Pending |
| 4 | 45, 48 | Ranked supported/opposed hypotheses and reference root causes; observed/predicted impact graph | Pending |
| 5 | 49, 50 | Evidence-backed actions, prerequisites/risks/verification and attributed revision history | Pending |

Each batch requires its own implementation and test evidence. Existing commerce
evidence storage does not by itself establish machine explanation acceptance.
