# ADR 0001 - Step-Based Pipeline

## Status

Accepted

## Context

OmniSearch AI needs a pipeline that can evolve beyond V1.

V1 starts with Search Box Intelligence, but future versions may include Forms, Payments, Checkout Flows, AI Prompt Gateway, and API Gateway use cases.

A hardcoded pipeline would make future changes expensive.

## Decision

Use a step-based pipeline architecture.

Each pipeline step is independent and ordered.

Each step implements:

    PipelineStep<T>

Each step returns one Result object.

## Consequences

- New steps can be added without changing the engine.
- Each step can be tested independently.
- Pipeline execution can be traced.
- Future parallel execution is possible.
- The engine remains generic.
