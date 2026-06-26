# ADR-0006

## Title

AI-First Stateless Pipeline Architecture

## Status

Accepted

## Context

QwenBridge is evolving into an AI-native semantic search engine.

To keep the architecture maintainable, testable, and extensible, every pipeline step must remain independent and communicate only through the execution context.

The pipeline must not become a chain of tightly coupled services or AI calls.

## Decision

The following architectural rules apply to every pipeline step.

### Stateless execution

Each PipelineStep MUST be stateless.

Pipeline steps MUST NOT keep mutable state between requests.

### ExecutionContext as the single communication channel

Pipeline steps MUST communicate exclusively through ExecutionContext.

No step may directly invoke another step.

No step may depend on internal implementation details of another step.

### Single responsibility

Each PipelineStep owns exactly one concern.

Examples:

- Language detection
- Semantic understanding
- Intent detection
- Rewrite
- Threat detection
- Confidence scoring

A step MUST NOT perform responsibilities belonging to another step.

### Pipeline orchestration

Execution order is owned exclusively by PipelineEngine.

Pipeline steps are unaware of the overall workflow.

They only consume ExecutionContext and produce results.

### AI-first design

Whenever semantic understanding is required, AI should be considered the primary implementation.

Rule-based heuristics may exist only as:

- fallback behavior
- deterministic validation
- safety checks

AI should not be treated as an optional add-on.

It is part of the core architecture.

### Extensibility

New pipeline steps must be insertable without modifying existing steps.

Adding a new concern should require:

- implementing PipelineStep
- registering the step
- updating pipeline configuration

Existing steps should remain unchanged.

### Testability

Every PipelineStep must be independently unit testable.

The entire pipeline must also support deterministic integration tests.

## Consequences

The resulting architecture remains:

- modular
- stateless
- AI-native
- easily testable
- highly extensible
- independent of any specific AI provider

This decision establishes the architectural foundation for all future QwenBridge versions.
