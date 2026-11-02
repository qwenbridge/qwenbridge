# ADR 0047: Domain Packs and Connector Packs on the Core Contracts

## Status

Accepted (QwenBridge 1.0, Section 4 Phases 32 and 33: QB-232 – QB-242)


> **QB-287 (ADR 0058):** superseded in part. The three Connector Packs (elastic, postgresql, mongodb) described here were removed from the repository; the generic contracts (Connector Pack SPI, Domain Packs) remain the extension point for future adapters.

## Context

Commerce and Library already implemented the plugin SDK `DomainPack` interface. However:
- their schemas were registered once at startup, with no lifecycle;
- QueryIR extensions had no owner or compatibility rule;
- domain-specific policy existed only as per-tenant policy packs.

The Elasticsearch, OpenSearch, PostgreSQL and MongoDB connectors implemented the Core
`Connector` SPI, but there was no way to distribute, discover, enable or disable them as optional
packages.

## Decision

1. **One Domain Pack contract.** `DomainPack` gains `queryExtension()`, `validateExtension()` and
   `policies()`, all optional. No second Commerce mechanism is added: Commerce declares its
   existing `commerce` 1.0 payload.
2. **Domain Pack lifecycle** (`DomainPacks`):
   - **Activation:** registers the pack's schema (owner `pack:<id>`, with the pack version), its
     extension namespace (which must equal its domain) and its rules, as one unit.
   - **Conflicts:** refused with nothing left behind.
   - **Deactivation:** removes only what the pack registered.
   - **Startup:** `qwenbridge.domains.disabled` keeps packs inactive.
3. **QueryIR extensions.** Payloads are versioned. A pack lists the versions it reads; anything
   else is incompatible and refused. The universal schema is untouched.
4. **Domain policies through the central engine.** Rules become `PolicyRule`s scoped to the
   pack's domain:
   - DENY denies and REVIEW requires an approval;
   - ALLOW and ABSTAIN change nothing;
   - a failing rule denies.

   They run after the base checks, so they cannot bypass authentication, tenant or scope.
5. **Connector Packs** (`ConnectorPackProvider`, `ConnectorPackManifest` and `ConnectorPacks` in
   Core):
   - **Not a new SPI:** a manifest plus a factory of Core `Connector`s.
   - **Manifest:** identity, version, Connector SPI range and declared capabilities.
   - **Enabling:** packs are discovered as data and created only when enabled.
   - **Configuration:** reuses `connector-config/1` with secret references.
   - **Capabilities:** capped by the declaration; streaming is added to `ConnectorCapabilities`,
     and false for all 1.0 connectors.
   - **Isolation:** each connector has its own runtime, timeout and circuit breaker.
   - **Health:** pack health is aggregated from the pack's own connectors only.
6. **Packaging.** The four existing adapters ship as the `elastic`, `postgresql` and `mongodb`
   packs. All packs are disabled by default.
7. **Discovery API.** `/api/v2/domain-packs` and `/api/v2/connector-packs` (`packs:read`).

## Consequences

- **Optional packs.** The server starts and serves with every Connector Pack disabled and with any
  subset of Domain Packs.
- **Commerce backends unchanged.** Commerce search still runs on the built-in backends
  (`qwenbridge.connectors.*`, FU-12). Moving it onto Connector Packs is a later change.
- **Extension validation is opt-in at the call site.** `DomainPacks.validate` is the contract for
  QueryIR payloads from outside. The server's own Commerce interpretation writes payloads the
  Commerce pack accepts (tested), but does not yet call it on every request.
- **Same trust as plugins.** Packs are trusted code in the server JVM (see
  `docs/security/plugin-isolation.md`).
