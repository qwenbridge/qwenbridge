# ADR 0056: Quarkus-Only Architecture Certification

## Status

Accepted (QB-294).

## Context

The server was migrated from Spring Boot to Quarkus earlier (ADR 0041 and the historical release
records). Section 1 added a gate that greps sources and the `org.springframework` group. That gate
missed several cases:
- Quarkus' own Spring compatibility extensions (`io.quarkus:quarkus-spring-*`);
- build plugins;
- framework configuration keys (`spring:`, `management.*`) and `SPRING_*` environment variables;
- executable-jar packaging;
- documentation.

## Decision

1. **Quarkus is the only application framework**, on Java 25. There is no compatibility layer of
   any kind (Spring Boot, Spring compatibility extensions).
2. **Guards at two levels:**
   - **Every build:** `QuarkusOnlyArchitectureTest` covers all reactor modules (main and test
     sources, resources, POMs), the Dockerfile, Compose files and `.env.example`.
   - **Master verification:** step 23 checks Java 25 for the JVM and Maven, every module's resolved
     dependency tree (all scopes, transitive), every build plugin and its dependencies, the
     repository audit, the packaged fast-jar and the running container.
3. **The audit classifies every reference.** It distinguishes:
   - `GUARD` (files implementing the checks);
   - reviewed documentation (`HISTORICAL`, `NEGATIVE_STATEMENT`, `CERTIFICATION`, …);
   - `ACTIVE` (a failure);
   - `UNREVIEWED_DOCUMENT` (a failure).

   A historical document must say that it is historical. A new mention in documentation must be
   reviewed and classified before the gate passes.
4. **Version pins stay.** The Quarkus and OpenTelemetry BOMs pin versions of Spring-related
   extensions in `dependencyManagement`; these are not dependencies. The guards check what is
   resolved, not what is managed.
5. **History is kept.** Historical records and ADRs stay. Three roadmaps that lacked the
   "historical" banner received it.

## Consequences

- **The guards themselves name what they forbid.** They are listed explicitly; Java guards
  assemble the names from parts so that the source scan does not match them.
- **No remediation was needed in code, dependencies, configuration or packaging.** The
  certification found the earlier migration complete. See `docs/architecture/quarkus-only.md` for
  the inventory and evidence.
