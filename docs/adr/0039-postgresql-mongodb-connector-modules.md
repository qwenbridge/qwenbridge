# ADR 0039: Optional PostgreSQL and MongoDB Connector Modules

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 25: QB-172 – QB-178)


> **QB-287 (ADR 0058):** superseded in part. The PostgreSQL and MongoDB connector modules and built-in backends described here were removed from the repository; the generic contracts (SearchProvider, Connector SPI, Connector Packs) remain the extension point for future adapters.

## Context

- **Location.** The PostgreSQL and MongoDB adapters lived in the server. The
  server depended on the JDBC driver and the MongoDB sync driver directly.
- **Filter translation.** `NeutralFilterTranslator` held its own PostgreSQL and
  MongoDB translation.
- **Writes.** Both built-in backends could write: they created tables and
  indexes, and upserted and deleted documents, with no access setting.

## Decision

1. **Two optional modules (QB-172, QB-173).** Both implement the Core connector
   SPI (ADR 0037). Drivers are now only reachable through these modules, which
   `DriverDependencyTest` enforces.
   - `qwenbridge-connector-postgresql`: `PostgresConnector`, `PgFilters`,
     `FieldPaths`, `PgPool`.
   - `qwenbridge-connector-mongodb`: `MongoConnector`, `MongoFilters`.
   - The server's neutral filter translation for both stores delegates to the
     modules, so there is one implementation.
2. **Settings (QB-174).**
   - **PostgreSQL:** `url`, `username`, `table`, `access`, `pool-size`,
     `acquire-timeout`, `query-timeout`, `text-fields`, `field-paths`. The
     `password` is a secret reference.
   - **MongoDB:** `database`, `collection`, `access`, `pool-size`,
     `acquire-timeout`, `query-timeout`, `text-fields`, `field-paths`. The
     `uri` is a secret reference, because it can embed credentials.
   - **Identifiers** (table, database, collection) are validated against a
     strict pattern. Values and JSON paths are always bound parameters.
3. **Bounded pools (QB-175).** When the pool is exhausted, the call fails with
   a typed outcome after `acquire-timeout`; it does not queue without limit.
4. **Read-only by default (QB-176).** `access` is `read-only` unless set to
   `read-write`.
   - **PostgreSQL:** sessions run with
     `default_transaction_read_only=on`, so the database itself refuses any
     write, DDL included (SQLSTATE 25006).
   - **MongoDB:** the WRITE and DELETE capabilities are not declared, and write
     calls are refused before they reach MongoDB. Operators should also grant a
     read-only database role.
   - **Server built-ins:** the same rule applies through
     `qwenbridge.connectors.postgresql.access` and
     `qwenbridge.connectors.mongodb.access`. A refused write returns 403.
5. **Start checks (QB-177).** Starting reaches the store; an unreachable or
   unauthorized store, or a missing table, fails the start with a reason.
   - **PostgreSQL** runs a constant, parameterized `to_regclass` check.
   - **MongoDB** runs `ping`.
6. **Tests (QB-178).**
   - **Offline:** PostgresConnectorTest (4) and MongoConnectorTest (3).
   - **Live, against Table 1:** PostgresLiveTest (1) covers read-only refusal
     by the database and pool exhaustion. MongoLiveTest (1) covers the same
     ground for MongoDB.
   - **Server:** BuiltInConnectorsTest (+2) and DriverDependencyTest (2).

## Consequences

- **Behaviour change.** The PostgreSQL and MongoDB built-ins are now read-only.
  Deployments that write through them (the Commerce catalog upsert or delete)
  must set `QWENBRIDGE_CONNECTORS_POSTGRESQL_ACCESS=read-write` or
  `QWENBRIDGE_CONNECTORS_MONGODB_ACCESS=read-write`.
- **Explicit constructors** used by tests and tools stay read-write.
- **Commerce path.** The apiVersion 2 Commerce compilation for both stores
  stays in the server, like Elasticsearch (ADR 0038).
- **Retrieval** for both stores stays keyword-only.
