# Quarkus-Only Architecture Certification (QB-294)

**Status: PASS**, on 2026-11-02, for branch `quarkus-branch` with local, uncommitted changes.

**Evidence:**
- master verification `qb294-master-20261102-131535`: 91 passed, 0 failed, 2 warnings;
- `mvn clean verify`: 12 of 12 reactor modules succeeded.

ADR 0056 records the decisions.

Quarkus on Java 25 is the only application framework. No Spring Boot (or other Spring)
dependency, plugin, startup code, annotation, test support, configuration key, environment
variable, packaging or runtime library is active. The earlier migration was found complete. This
task removed no dependency or code, because none was active. It added guards and fixed one
documentation gap.

## 1. Reference inventory

The audit `scripts/verification/quarkus_only_audit.py` covers every tracked and untracked file,
except `node_modules`, `target`, `dist`, `logs` and `.git`. In the master run it classified 84
references:

| Class | Count | What |
|---|---|---|
| `GUARD` | 31 | Files implementing the checks: this audit, step 23, the Section 1 gate, `check-versions.py`, two error-leak checks, the step title, the self-test, two JUnit guards |
| `HISTORICAL` | 29 | Release records V1–V9, the release README, the discontinued Spring Boot Starter page. Each says "historical" |
| `NEGATIVE_STATEMENT` | 12 | Statements that there is no Spring (testing guide, database-free runtime, phase audit, section reports) |
| `CERTIFICATION` | 6 | This document and ADR 0056 |
| `HISTORICAL_DECISION` | 3 | ADR 0041 (the starter's removal) |
| `HISTORICAL_TITLE` | 1 | `docs/platform/phase-acceptance.json` ("historical Spring Boot Starter") |
| `POINTER_TO_HISTORICAL` | 1 | `docs/sdk/README.md` link to the historical starter page |
| `COMMENT_OR_NAME` | 1 | `QwenBridgeConfigProducer` Javadoc: "Replaces the former Spring `@ConfigurationProperties` binding" (explains history; no code) |
| `ACTIVE` | **0** | — |
| `UNREVIEWED_DOCUMENT` | **0** | — |

**Other scans:**

| Scan | Result |
|---|---|
| Spring annotations in sources (`@SpringBootApplication`, `@RestController`, `@*Mapping`, `@Autowired`, `@Component`, `@Service`, `@Configuration`, `@Bean`, `@SpringBootTest`, `@MockBean`, …) | 0 |
| `SpringApplication`, `ApplicationContext`, `BeanFactory`, `RestTemplate`, `WebClient` | 0 |
| `spring.*`, `management.*` or `server.port` configuration keys; `SPRING_*` or `MANAGEMENT_*` environment variables | 0 |
| `BOOT-INF`, `JarLauncher`, `spring-boot-maven-plugin`, `repackage` | 0 |

The 56 `server.` matches of a first, naive key scan were local variables of the JDK test HTTP server
in Java tests, not configuration.

**Removed:** two empty leftover directories, `qwenbridge-server/src/{main,test}/java/io/qwenbridge/event/spring/`.
They were left after the earlier replacement of `SpringPipelineEventPublisher` by
`CdiPipelineEventPublisher`. The deletion of the old files already shows as uncommitted (`D`) in
the working tree, from before this task.

**Historical documents retained:** all of them. Three roadmaps (V1, V4, V5) mentioned Spring Boot
without the historical banner the other records carry; they received it.

## 2. Maven dependency audit

| Check | Before | After |
|---|---|---|
| Resolved artifacts with "spring" in their coordinates (`mvn dependency:tree`, 12 module trees, all scopes, transitive; 3,402 artifact lines) | 0 | 0 |
| Build plugins and their dependencies (`mvn dependency:resolve-plugins`, 12 listings, 303 distinct artifacts) | 0 | 0 |
| Section 1 gate (`dependency:list`, 12 listings) | PASS | PASS |
| Spring entries in the effective POM | 660 | 660 |

**The 660 effective-POM entries** are all inside `dependencyManagement`. They are 55 managed
version pins imported from the Quarkus BOM (`io.quarkus:quarkus-spring-*` compatibility extensions)
and the OpenTelemetry instrumentation BOM (`opentelemetry-spring-*`). They are not dependencies,
and none is resolved. The guards check what is resolved, including any `quarkus-spring-*`
compatibility extension, not what a BOM manages.

**SDK dependencies:**
- **Java SDK** (compile/runtime scope): only `jackson-databind`, `jackson-annotations`,
  `jackson-core` and `jackson-datatype-jsr310`, plus the JDK.
- **TypeScript SDK:** no runtime dependencies (`dependencies: {}`, `peerDependencies: {}`).

## 3. Active source, CDI, REST and SSE

- **No Spring code:** none of the 12 reactor modules contains Spring code, in main or test.
- **Server stack:**
  - dependency injection: Jakarta CDI (ArC);
  - REST: Jakarta REST on Quarkus;
  - SSE: the existing Quarkus SSE implementation;
  - configuration: MicroProfile Config (`@ConfigProperty`, `QwenBridgeConfigProducer`);
  - health: SmallRye Health (`/q/health`).
- **Behaviour verified, not just grep-checked:** the master's server suite (1,036 tests) exercises
  CDI wiring, REST and SSE contracts, validation, exception mapping, security, cancellation and
  timeouts. The live steps run against the packaged application.

## 4. Configuration and tests

- **Configuration:** no active Spring configuration existed, so nothing was removed.
  `QuarkusOnlyArchitectureTest` checks all module resources, `.env.example`, the Compose files and
  the Dockerfile.
- **Tests:** none uses Spring test support. They use JUnit 5, `@QuarkusTest`, Quarkus test
  profiles, RestAssured and `@InjectMock`.

## 5. Docker and packaging

- **Dockerfile:** builds the Quarkus fast-jar on Java 25 and starts `java -jar
  /app/quarkus-run.jar`. `check-versions.py` and `QuarkusOnlyArchitectureTest` both check this.
- **Packaged application** (`qwenbridge-server/target/quarkus-app`): `quarkus-run.jar` and 191
  libraries, none of another framework.
- **Running Compose container `qwenbridge-app`:** entry point
  `["java","-jar","/app/quarkus-run.jar"]`; 195 jars, 0 framework files; Quarkus
  `/q/health/ready` returns 200.
- **Database-free baseline:** kept. PostgreSQL, MongoDB, Elasticsearch and OpenSearch remain
  optional connectors (QB-293, master step 21).

## 6. Runtime verification

These steps ran against the real packaged Quarkus application, with no database, in the master
run:

| Step | Coverage |
|---|---|
| 21 (database-free runtime) | Health, language processing, QueryIR, REST v1 and v2, security and policy, SSE, Java and TypeScript SDK v2 clients |
| 22 (multilingual expansion and typo correction) | 11 of 11 checks: typo correction live, expansion to 8 configured identifiers with the glossary and the real local model provider, SSE terminal event, Java SDK and TypeScript SDK reading the live result |
| Section 1 to 3 gates | Startup without Redis, OpenSearch and Ollama; live Java SDK analyze; SDK on Node.js 20 |

**SDK independence:** the Java SDK's live checks run as plain Java 25 single-file programs
(`java -cp sdk.jar:jackson… JavaSdkLive*.java`), with no framework. The TypeScript SDK runs on
plain Node.js.

**Finding fixed during runtime verification.** The detector labelled the English query "I need a
red winter jacket under 100 euros" as Swedish. With typo correction enabled for `en` and `sv`, the
English word "red" was then "corrected" to the Swedish "röd".

Fix: `TypoCorrector` now never corrects a word that any enabled language knows. The regression
test is `TypoCorrectorTest.aWordKnownInAnyEnabledLanguageIsNeverCorrected`. Live step 22 now runs
with both typo correction and expansion enabled.

## 7. Automated guards

| Guard | Runs | Fails when |
|---|---|---|
| `QuarkusOnlyArchitectureTest` (5 tests) | Every server test run | Any reactor module (main and test sources, resources, POMs), Dockerfile, Compose or `.env.example` contains the other framework's dependencies, annotations, startup code, test support, configuration keys, environment variables or executable-jar packaging; or the server POM lacks `quarkus-maven-plugin`; or Docker does not run `quarkus-run.jar` on Java 25 |
| `ArchitectureNeutralityTest.springBootIsNotReintroduced` | Every server test run | POMs or runtime-module sources reference the framework |
| Master step 23, `qb294_quarkus_only_validation` | Master verification | Java is not 25 (JVM or Maven); any resolved dependency or plugin artifact is Spring-named (including `quarkus-spring-*`), or a module listing is missing; the audit finds an `ACTIVE` reference, an unreviewed document, or a historical document that does not say so; the packaged fast-jar is missing or contains such libraries; the container does not run `quarkus-run.jar`, contains such files or is not healthy |
| Section 1 gate (`section1_spring_boot_elimination_validation`) | Master verification | Spring sources or configuration, or `org.springframework` resolved |
| `scripts/tests/verification-test.sh` | Master verification | The audit fails to reject any of 11 planted violations (annotation, startup, test support, `spring:` key, `management.*` key, `SPRING_*` variable, JarLauncher packaging, Boot Maven plugin, unreviewed document, resolved Spring artifact, `quarkus-spring-*` compatibility layer), or rejects a clean tree |

## 8. Evidence

| Item | Result |
|---|---|
| `java -version` | OpenJDK 25.0.4.1 (BellSoft) |
| `mvn -version` | Maven 3.9.12 on Java 25.0.4.1 |
| `mvn -B clean verify` | BUILD SUCCESS, 12 of 12 modules. Server 1,003 tests run, 0 failures (40 skipped outside the master's service environment). Java SDK 76 tests |
| Master `qb294-master-20261102-131535` | 91 passed, 0 failed, 2 warnings (working tree not clean; tag not on HEAD) |
| Core | 643 tests |
| Server | 1,036 tests, 1 skipped |
| Java SDK | 76 tests |
| TypeScript SDK | 70 tests |
| Steps 21, 22 and 23; Section 1 Spring Boot elimination gate | PASS |

The full audit report (`quarkus-only-audit.json`) is in the master run's Section 1 work directory,
with every classified reference.

## 9. Remaining issues and release blockers

- **No remaining Spring issue.**
- **Release decision unchanged.** `QwenBridge-1.0-Release-Decision.md` remains NO-GO for its
  existing blockers (offline dependency CVE audit, open findings, owner decisions, PARTIAL tasks,
  uncommitted and untagged artifact). This certification removes none of them and adds none.
