# ADR 0002 - Result-Based Execution Context

## Status

Accepted

## Context

A mutable context with many setters can become a God Object.

That would make the pipeline harder to test, harder to extend, and harder to reason about.

## Decision

Use ExecutionContext as a typed result store.

Steps do not mutate the context directly.

Each step returns a Result object.

The PipelineEngine stores the result by type.

Example:

    context.store(LanguageResult.class, result)

Other steps can read previous results:

    context.get(LanguageResult.class)

## Consequences

- ExecutionContext does not become a God Object.
- Result objects stay independent.
- Steps remain focused.
- The engine does not need to know business result types.
- Plugin architecture becomes possible later.
