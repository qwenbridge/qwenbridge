# ADR 0016: In-Memory Schema Registry and Domain Schema Contract

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 7: QB-043 – QB-049)

## Context

Three schema contracts existed:

- `intelligence.schema.QuerySchemaRegistry` hard-coded price, BRAND and
  PRODUCT, with no id or version.
- `commerce.CommerceSchemaRegistry` was tenant-scoped and persisted to Redis
  whenever Redis was present.
- The plugin SDK's `DomainPack.Definition` (what the real Commerce and
  Library packs declare) mixed backend details (`backendField`, `caseFold`)
  into the schema.

A further copy of the operator enum existed in each.

## Decision

### Contract (`io.qwenbridge.core.schema`)

- `DomainSchema(id, SchemaVersion, fields, entityTypes, capabilities)`, the
  same contract for every domain.
- `FieldDefinition(name, FieldType, operators, nullable, unit, min, max,
  allowedValues)`:
  - Operators are the QueryIR `Constraint.Operator` set, so there is no
    separate operator enum.
  - Field types are `TEXT`, `IDENTIFIER`, `NUMBER`, `QUANTITY` (requires a
    unit), `MONEY`, `BOOLEAN`, `TEMPORAL`.
  - The schema describes meaning only; storage names belong to connectors.
  - Inconsistent definitions are rejected on construction: QUANTITY without a
    unit, bounds on non-numeric fields, a vocabulary on non-text fields, or
    EXISTS on a non-nullable field.
- One shared naming rule (`queryir.Names`) applies to schema fields, entity
  types and QueryIR names: up to 8 dot-separated segments, each starting with
  a lowercase letter, in camelCase or snake_case. CamelCase is allowed so the
  existing pack fields (`inStock`, `availableAt`) need no rename.

### Registry (`SchemaRegistry`)

- In memory and thread-safe: reads are lock-free over immutable snapshots and
  writes are serialized. It needs no database, Redis or network.
- **Versions are immutable.** Re-registering identical content is idempotent;
  different content fails with `IMMUTABLE_VERSION`.
- **Minor versions must be backward compatible** with the previous minor of
  the same major. A field may not be removed, retyped, lose operators, change
  unit or become non-nullable; vocabularies and bounds may not narrow; entity
  types and capabilities may not be removed. Breaking changes need a new
  major. Minors must arrive in ascending order, so no version can bypass the
  check.
- **Ownership.** The first owner (`core` or `pack:<id>`) owns every version
  of an id. Other owners cannot add or remove versions, and deactivating a
  pack (`unregisterAll`) removes only its own schemas.
- **Typed errors.** `IMMUTABLE_VERSION`, `INCOMPATIBLE_CHANGE`,
  `OWNERSHIP_VIOLATION`, `NOT_FOUND`, `INVALID_OWNER`.

### QueryIR to schema validation (`SchemaValidator`)

- Unknown fields are reported and are never executable, whoever produced them
  (a model cannot invent fields).
- Operators, value types, quantity units, numeric bounds and closed
  vocabularies are checked, including inside ranges and lists.
- Entity types must be declared.
- Semantic signals are not executable and are not checked. `extensions` are
  validated by their owning pack.

### Server wiring

`SchemaRegistryProducer` creates the singleton registry and registers every
`DomainPack` found through `ServiceLoader`, owned by `pack:<id>`. Packs are
not hard-coded. A pack whose definition cannot be converted is skipped and
logged.

## Consequences

- The legacy `QuerySchemaRegistry` (price path) remains until Phase 8 unifies
  filter validation on this registry.
- `CommerceSchemaRegistry` (tenant schemas over REST, Redis) remains until
  QB-149 moves Commerce onto it.
- Activation and deactivation of *externally installed* packs
  (`ManagedPacks`) is not yet wired to `register`/`unregisterAll`. That is
  QB-233 (Section 4); the registry API already supports it.
- Pack definitions map `DECIMAL` to `NUMBER`. Commerce declares price and
  currency as two fields, so Commerce prices are numbers in this schema until
  the Commerce pack adopts the `MONEY` type (Section 2).
