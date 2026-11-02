# Domain Packs and Connector Packs

QwenBridge adds domains and external systems through packs. Packs reuse the Core contracts: the
schema registry, QueryIR extensions, the central policy engine and the Connector SPI. The Core
needs no pack. ADR 0047 records the decisions.

## Domain Packs

A Domain Pack implements `io.qwenbridge.pack.DomainPack` from the plugin SDK. Commerce
(`qwenbridge-commerce-pack`) and Library (`qwenbridge-library-pack`) use the same contract.

| Contribution | Method | Where it goes |
|---|---|---|
| Domain schema | `definition()` | The in-memory schema registry, owned by `pack:<pack id>` and recorded with the pack version |
| QueryIR extension | `queryExtension()`, `validateExtension(...)` | The namespace of the domain-specific QueryIR payload, the payload versions the pack can read, and its validation |
| Policies | `policies()` | Rules in the central policy engine, applied only to resources in the pack's domain |

**Lifecycle.** `DomainPacks` activates every Domain Pack on the classpath at startup.
`qwenbridge.domains.disabled` lists pack ids that are not activated.
- **Activation:** one unit. A domain or schema id owned by someone else, an extension namespace that
  is not the pack's own domain, or duplicate rule ids refuse the activation and leave nothing
  behind.
- **Deactivation:** removes exactly the schemas, extension namespace and rules the pack registered.
  Other domains are unaffected.

**QueryIR extensions.** A payload sits under `extensions.<domain>` with its own version. The
universal QueryIR fields never change. `DomainPacks.validate(query)` checks every payload:
- unknown namespace: refused;
- version the pack cannot read: refused as incompatible;
- otherwise the owning pack validates the content.

**Domain policies only restrict.** They run after the central checks (known operation,
authentication, tenant, scope).

| A rule returns | Effect |
|---|---|
| `DENY` | Denies |
| `REVIEW` | Requires an approval |
| `ALLOW`, `ABSTAIN` | Nothing |
| Throws an exception | Denies (`POLICY_ERROR`) |

A rule can never grant what the base checks refused. Per-tenant policy packs (`PolicyPack`)
continue to work as before.

## Connector Packs

A Connector Pack is a `ConnectorPackProvider` (Core, `io.qwenbridge.core.connector.pack`), found
with `ServiceLoader`. It describes Core `Connector`s; it does not replace the Connector SPI.

**No Connector Pack ships with QwenBridge 1.0** (QB-287, ADR 0058). The Elasticsearch,
PostgreSQL, MongoDB and Shopify connector modules were removed. This is the extension point for
separately developed and installed packs. The server tests exercise it with a test-only `example`
pack (`ExampleConnectorPack`), covering discovery, disabled by default, configuration and failure
isolation.

**Manifest.** Identity, version, publisher, the supported Connector SPI range (the runtime
implements `1.0.0`), and per connector its system and the most it may ever do. A manifest holds
no configuration values or credentials.

**Discovery and capabilities.**
- **Rejected packs:** invalid manifests, packs for another SPI, and duplicate pack or connector ids
  are rejected.
- **No operations needed:** declared capabilities (access, retrieval modes, streaming) are visible
  without running anything.
- **Capabilities are capped:** a running connector's effective capabilities are the intersection
  of its declaration and what it reports. A connector that starts with more than it declared
  fails.
- **Writes are opt-in:** mutating access needs `access=read-write` in the connector's
  configuration, and is still authorized per call.

**Enabling.** All packs are disabled by default.
- **Disabled packs create nothing:** no connector object and no client, and no credentials are
  needed.
- **`qwenbridge.connector-packs.enabled`:** lists the packs to enable.
- **`qwenbridge.connector-packs.config`:** a JSON object keyed by connector id, for example:

  ```json
  {"my-connector": {"settings": {"url": "https://index.example:9200", "username": "app",
                               "table": "products"},
                  "secrets": {"password": "env:PG_PASSWORD"}}}
  ```

  Settings and secrets follow each connector's `configSchema()` and the `connector-config/1`
  contract:
  - secrets are `env:` or `file:` references only (`qwenbridge.connector-packs.secrets-dir`);
  - a secret written as a plain setting is refused;
  - values never appear in statuses or logs.
- **Guard settings:** `qwenbridge.connector-packs.timeout` (10 s),
  `qwenbridge.connector-packs.failure-threshold` (5) and `qwenbridge.connector-packs.open-for`
  (30 s) configure each connector's own guard.

**Health.** Each connector reports its Connector SPI state with a reason: `DISABLED` (not
configured, or the pack is disabled), `READY`, `DEGRADED` (unhealthy, or circuit open) or `FAILED`.
A pack reports:

| Pack state | Meaning |
|---|---|
| `DISABLED` | Not enabled |
| `IDLE` | Enabled, but no connector configured |
| `READY` | All configured connectors ready |
| `DEGRADED` | Some configured connectors not ready |
| `FAILED` | No configured connector working |
| `REJECTED` | Refused at discovery |

Each connector has its own runtime, timeout and circuit breaker, so one pack's failure never
changes another pack's state.

**Scope in 1.0.** These packs run beside the built-in Commerce backends (`qwenbridge.connectors.*`),
which are unchanged. Commerce search still uses its built-in backends (follow-up FU-12).

## Discovery endpoints

`GET /api/v2/domain-packs` and `GET /api/v2/connector-packs` (permission `packs:read`) list the
active Domain Packs with what they registered, and every Connector Pack with its state,
capabilities and configuration key names.
