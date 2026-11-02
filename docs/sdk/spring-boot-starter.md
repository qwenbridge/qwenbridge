# Spring Boot Starter (historical)

**Status: discontinued.** The Spring Boot Starter is not part of QwenBridge 1.0 and is not
maintained. Spring Boot is not a dependency of the server, the Core or the SDKs, and it will not
return as one.

## What it was

`io.qwenbridge:qwenbridge-spring-boot-starter` was Spring Boot auto-configuration for the Java SDK,
added in V9 (`docs/roadmap/V9.md`).

- **Beans.** It created `QwenBridgeClientConfig`, `QwenBridgeClient` and
  `QwenBridgeStreamingClient`, each `@ConditionalOnMissingBean`.
- **Health.** It added an optional Actuator health indicator.
- **Properties.** These lived under `qwenbridge.*`:
  - `base-url` (default `http://localhost:8080`);
  - `connect-timeout` (2 s);
  - `request-timeout` (30 s).
- **Requirements.** Java 21+, Spring Boot 3.5+; it was built against Spring Boot 3.5.16.
- **Scope.** It covered only the v1 API (analyze and pipeline SSE); there was no v2 client.

| Release (Git tag) | Starter in the repository |
|---|---|
| v0.8.1 and earlier | no |
| v0.9.0, v0.9.1 | yes (`qwenbridge-spring-boot-starter/`, example in `examples/spring-boot-starter-example/`) |
| after v0.9.1 (Quarkus migration) | removed |

The v0.9 publishing guide listed the starter as a Maven Central artifact. This repository holds no
record of whether it was actually published, so check Maven Central before relying on it.

## Recommended integration now

Use the Java SDK directly; it has no framework dependency. In a Spring application, declare the
clients as beans yourself:

```java
@Configuration
class QwenBridgeConfiguration {

  @Bean(destroyMethod = "close")
  CommerceClient qwenBridge(
      @Value("${qwenbridge.base-url}") URI baseUrl,
      @Value("${qwenbridge.api-key:}") String apiKey) {
    return new CommerceClient(CommerceClientConfig.of(baseUrl, apiKey));
  }
}
```

- **v2.** `CommerceClient` covers search, sessions, streams, cancellation and version checks.
- **v1.** `QwenBridgeClient` and `QwenBridgeStreamingClient` remain available for the single-tenant
  v1 API.

To migrate from the starter:
1. Remove the starter dependency.
2. Add `io.qwenbridge:qwenbridge-java-sdk`.
3. Create the beans as shown above. The former `qwenbridge.base-url`, `connect-timeout` and
   `request-timeout` properties map to `CommerceClientConfig` / `QwenBridgeClientConfig`.

If a team needs the starter for a 0.9 deployment, build it from the `v0.9.1` tag. Keep it outside
this repository's Quarkus build.
