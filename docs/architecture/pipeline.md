# Pipeline

QwenBridge processes a request through an ordered, typed pipeline.

## Core stages

1. normalization
2. language detection
3. intent analysis
4. policy and threat analysis
5. rewrite
6. semantic analysis
7. AI decision
8. confidence calculation
9. execution-plan creation
10. execution

Each step reads and writes typed values in `ExecutionContext`. The context prevents accidental string-key coupling and makes pipeline evolution testable.

## Execution

The execution engine maps an `ExecutionPlan` to explicit operations such as direct answer, keyword search, vector search, hybrid search, facet, rerank, and return results.

Pipeline events are emitted independently of HTTP delivery. This allows REST execution and SSE observation to evolve without coupling the pipeline to a transport.
