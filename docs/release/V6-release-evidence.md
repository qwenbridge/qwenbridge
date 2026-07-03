# V6 Release Evidence

## Branch

`feat/v6-api-contract-hardening`

## Verification Result

Latest V6 release verification result:

    Passed: 39
    Warnings: 0
    Failed: 0
    RESULT: RELEASE VERIFICATION PASSED

## Verified Areas

- Project root validation
- Required tooling
- Git state
- Docker readiness
- Docker Compose build and startup
- Container health checks
- Ollama model availability
- OpenSearch seed data with BGE-M3 embeddings
- Application readiness
- Docker application healthcheck
- Docker non-root runtime user
- OpenSearch vector mapping
- Ollama BGE-M3 embedding generation
- OpenSearch vector retrieval
- OpenSearch hybrid retrieval
- Actuator health endpoint
- Public health endpoint
- Version endpoint
- AI chat endpoint with controlled provider-failure semantics
- Search analyze endpoint
- SSE streaming lifecycle
- Validation error contract
- Request ID propagation
- CORS preflight
- Redis cache miss and cache hit
- SingleFlight concurrency
- OpenAPI endpoint and declared V6 paths
- Swagger UI endpoint

## Release Gate Status

The V6 local Docker release gate is satisfied.

The executable runtime verification command is:

    FORCE_FRESH=false PULL_DOCKER_IMAGES=false ./scripts/verify-release.sh
