# Testing

QwenBridge uses layered verification: unit tests, integration tests, architecture tests, release verification scripts, and k6 performance checks.

## Maven test suite

Run all Java tests:

```bash
mvn clean verify
```

Run only the server tests:

```bash
mvn -pl qwenbridge-server test
```

Run SDK tests:

```bash
mvn -pl qwenbridge-java-sdk test
mvn -pl qwenbridge-spring-boot-starter test
```

## TypeScript SDK tests

```bash
cd qwenbridge-typescript-sdk
npm ci
npm test
npm run build
```

## Release verification

```bash
bash scripts/verify-release.sh
```

The release script validates repository structure, Docker dependencies, OpenSearch and Ollama availability, API runtime behavior, SSE behavior, V8/V9 checks, security input checks, retrieval quality, resilience, and performance quality.

## Performance checks

The k6 scripts are under `scripts/performance/`. Run them only against an explicitly selected local or non-production environment.
