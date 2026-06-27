# ADR 0007: Type-safe Execution Context Extensions

## Status

Accepted

## Context

QwenBridge already uses `ExecutionContext` as the shared pipeline context.

Until V2, pipeline state was stored by Java class type:

```java
context.store(LanguageResult.class, result);
context.get(LanguageResult.class);
```

This approach works well for pipeline state and remains the preferred mechanism for exchanging results between pipeline steps.

With V3, however, QwenBridge introduces a new class of data that is not pipeline state, but execution context. Examples include:

- Tenant information
- Locale
- Execution hints
- Provider hints
- Feature flags
- Telemetry
- Personalization
- Ranking signals

These values are orthogonal to the pipeline itself and should not become artificial pipeline results.

A traditional `Map<String, Object>` would provide flexibility but sacrifices type safety, introduces magic string keys, and increases the risk of runtime errors.

## Decision

`ExecutionContext` is extended with a second storage mechanism based on strongly typed keys.

The existing API remains unchanged:

```java
context.store(Class<T>, value);
context.get(Class<T>);
```

A new API is introduced for extension data:

```java
context.store(ContextKey<T>, value);
context.get(ContextKey<T>);
```

`ContextKey<T>` is an immutable value object containing:

- a unique logical name
- the Java type associated with the value

Runtime validation guarantees that only values matching the declared type may be stored.

The framework provides a central `ContextKeys` class for common keys, while feature modules are free to define their own keys without modifying the core framework.

## Rationale

This design intentionally separates two different concepts:

### Pipeline State

Represents outputs produced by pipeline steps.

Examples:

- LanguageResult
- IntentResult
- RewriteResult
- SemanticResult
- DecisionResult

These continue to use:

```java
store(Class<T>)
```

### Execution Context

Represents metadata describing how execution should happen.

Examples:

- Tenant
- Locale
- ExecutionHints
- ProviderHints
- FeatureFlags

These use:

```java
store(ContextKey<T>)
```

Keeping these concepts separate preserves a clean domain model and avoids abusing pipeline results as generic storage.

## Consequences

### Positive

- Fully backward compatible with V2.
- No existing pipeline step requires modification.
- Strong compile-time type safety.
- No string-based magic keys.
- Easy extension by future modules.
- Supports plugin-style architecture.
- Keeps pipeline state independent from execution metadata.
- Enables long-term framework evolution without breaking SPI contracts.

### Trade-offs

- `ExecutionContext` now manages two independent storage mechanisms.
- Modules should own their own context keys whenever appropriate to avoid centralization.

## Alternatives Considered

### Map<String, Object>

Rejected because it:

- loses compile-time type safety
- relies on magic strings
- increases runtime casting
- makes refactoring difficult

### Single Generic Map<Object, Object>

Rejected because it mixes unrelated concerns and weakens API clarity.

### Replace Class-based Storage

Rejected because the current result storage model is already stable, well-tested, and provides an excellent contract for pipeline state.

## Decision Summary

`ExecutionContext` becomes the single execution container for QwenBridge.

Pipeline results continue to be exchanged through class-based storage.

Framework extensions use strongly typed `ContextKey<T>` instances.

This establishes the first reusable execution context model for QwenBridge and provides a stable foundation for Search Providers, Ranking, Personalization, Policies, AI modules, and future framework capabilities without breaking existing contracts.
