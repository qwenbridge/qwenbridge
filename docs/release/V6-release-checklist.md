# V6 Release Checklist

## Scope

This checklist is the final local release gate for QwenBridge V6.

## Source Control

- [x] V6 work is isolated on `feat/v6-api-contract-hardening`
- [x] All V6 changes are committed
- [x] Working tree is clean
- [x] Maven test suite passes

## Public API Contract

- [x] REST API documentation exists
- [x] SSE documentation exists
- [x] OpenAPI endpoint is available
- [x] Swagger UI endpoint is available
- [x] Validation failures return the documented error contract
- [x] Request IDs are propagated
- [x] Version headers are returned
- [x] CORS preflight is validated

## Runtime Stack

- [x] Docker Compose starts Redis
- [x] Docker Compose starts Ollama
- [x] Docker Compose starts OpenSearch
- [x] Docker Compose starts QwenBridge API
- [x] Application container healthcheck is healthy
- [x] Application runs as non-root `qwenbridge` user
- [x] Public health endpoint returns `UP`
- [x] Actuator health endpoint returns `UP`

## AI and Retrieval

- [x] Required Ollama models are available
- [x] BGE-M3 embeddings are generated successfully
- [x] OpenSearch vector mapping uses `knn_vector`
- [x] OpenSearch vector retrieval is verified
- [x] OpenSearch hybrid retrieval is verified
- [x] AI provider failure has controlled API semantics

## Reliability

- [x] Redis cache miss is verified
- [x] Redis cache hit is verified
- [x] SingleFlight concurrent request behavior is verified
- [x] SSE lifecycle includes start and terminal events
- [x] SSE request isolation is verified

## Release Evidence

- [x] Docker deployment guide exists
- [x] V6 release evidence document exists
- [x] Runtime verification script passes with zero failures

## Final Gate

V6 is complete when the following command returns a successful result:

    FORCE_FRESH=false PULL_DOCKER_IMAGES=false ./scripts/verify-release.sh

Expected final result:

    RESULT: RELEASE VERIFICATION PASSED
