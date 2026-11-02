# ADR 0037: Universal Connector SPI — Lifecycle, Capabilities, Configuration, Secrets, Resilience

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 22: QB-153 – QB-159)

## Context

Connectors had three overlapping shapes: the plugin SDK's `ConnectorPack`, the
server's `CommerceBackend` and Core's `SearchProvider`. They also had these
gaps:

- **Lifecycle.** Only `configured`, `initialize` and `close` existed.
- **Capabilities.** Only search modes were declared.
- **Credentials.** The built-in backends took plaintext properties (audit,
  QB-156).
- **Resilience.** Apart from per-call timeouts, there was no retry and no
  circuit breaker.

## Decision

The new types live in Core (`io.qwenbridge.core.connector`) and have no
dependencies.

1. **Connector SPI (QB-153).** A `Connector` declares its id, its
   `ConfigSchema` and its `ConnectorCapabilities`. It starts from a validated
   `ConnectorConfig` with a `SecretResolver`, reports health, stops, and exposes
   retrieval through the unified `SearchProvider`.

   A `ConnectorRuntime` owns each connector:
   - its lifecycle;
   - access checks before every operation;
   - the resilience guard;
   - typed `ConnectorResult`s: Success, or Failed with one of NOT_READY,
     ACCESS_DENIED, CIRCUIT_OPEN, TIMEOUT, CANCELLED or FAILED.

   Nothing a connector throws escapes as an exception.
2. **Capabilities (QB-154).** Data access is READ, WRITE or DELETE, alongside
   the retrieval modes. An operation needing access the connector does not
   declare is refused before the connector is called.
3. **Configuration (QB-155).** `ConnectorConfig` (`connector-config/1`) holds
   plain settings and secret references, validated against the connector's
   schema:
   - unknown or missing settings and secrets;
   - the configuration version;
   - a secret key given as a plain value.

   A disabled connector needs no settings at all. `toString` lists keys only.
4. **Secrets (QB-156).**
   - **References only.** `SecretRef` accepts `env:NAME` or `file:/path` and
     nothing else.
   - **Resolution.** `EnvironmentSecrets` resolves files only under one allowed
     directory, up to 64 KiB.
   - **Redaction.** `Secret.toString()` is `Secret[****]`. Failure messages
     name the reference, never a value, and a failed start records only the
     exception type, because messages can carry connection strings.
   - **Built-in backends.** These credential properties now accept such
     references:
     - the Elasticsearch password and bearer token;
     - the PostgreSQL password;
     - the MongoDB URI, which may embed credentials;
     - files under `qwenbridge.connectors.secrets-dir`.

     A plain value still works for compatibility and logs a warning naming
     the property, never the value.
5. **Lifecycle (QB-157).** The states are DISABLED, CREATED, STARTING, READY,
   DEGRADED, FAILED, STOPPING and STOPPED, and only legal transitions are
   allowed. The last 20 transitions are kept with their reasons.
   - **Operations** are accepted in READY and DEGRADED.
   - **The circuit** moves a connector to DEGRADED when it opens and back to
     READY when it recovers.
   - **`GET /api/v2/connectors`** reports each built-in connector's state,
     reason, access, modes, circuit and configuration version.
6. **Resilience (QB-158).**
   - **Timeout.** Each attempt runs with a timeout; a timed-out attempt is
     cancelled.
   - **Retry.** `RetryPolicy` allows at most five attempts, only for failures
     it classifies as retryable, with linear backoff. Writes are never retried.
   - **Circuit breaker.** It opens after N consecutive failures and fails fast
     while open. After the pause a single half-open trial runs: success closes
     it, failure reopens it.
   - **Built-in configuration.**
     - Every Commerce backend read goes through its connector's guard.
     - Failure threshold: `qwenbridge.connectors.circuit.failure-threshold`
       (default 5).
     - Pause: `open-for` (default 30 s).
     - Overall call timeout: `qwenbridge.connectors.call-timeout`
       (default 65 s; each backend still enforces its own, shorter timeout).
     - Retries: one, for I/O and timeouts.
   - **Caller errors** (invalid request, unsupported mode: 400 and 422) pass
     through unchanged and never count towards the circuit.
   - **Open circuit:** 503 "Connector … unavailable: CIRCUIT_OPEN".
7. **Isolation and failure tests (QB-159).**
   - **Core:**
     - configuration and secret rules;
     - lifecycle, including a start failure whose message holds a password
       that never reaches the reason;
     - access refusal before the call;
     - bounded retry, and none for non-retryable failures;
     - timeout;
     - the breaker's full cycle with a controllable clock;
     - a registry where one failing and one unconfigured connector leave the
       third working.
   - **Server:**
     - a failing backend opens its circuit, then fails fast without being
       called, while another backend keeps serving;
     - 422 and argument errors pass through with the circuit closed;
     - unconfigured connectors are DISABLED;
     - writes are attempted once;
     - secret references resolve;
     - the API reports states.

## Consequences

- **SPI convergence.** Built-in backends are wrapped as connectors; they do not
  yet implement `Connector` themselves, because their configuration still comes
  from properties validated in their constructors. External `ConnectorPack`s
  move onto the SPI with the plugin work (Section 4).
- **Access.** Every built-in connector declares READ, WRITE and DELETE today.
  Read-only defaults for the database connectors are QB-176 (Batch 25).
