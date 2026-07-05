# Configuration

QwenBridge configuration is environment-aware and must be explicit for production deployments.

## Configuration sources

Configuration can be supplied through:

- `application.yml`
- environment variables
- container environment
- deployment platform configuration
- local uncommitted overrides during development

Do not commit secrets or environment-specific private values.

## Core configuration areas

QwenBridge configuration is grouped around:

- server runtime
- AI providers
- search providers
- Redis cache and rate limiting
- OpenSearch
- streaming
- abuse protection
- observability
- production validation

## AI provider configuration

The default local AI provider is Ollama.

Typical values include:

- provider ID
- base URL
- chat model
- embedding model
- timeout values

Provider-specific code must stay behind the AI Provider SPI.

## Search provider configuration

QwenBridge supports pluggable search providers through the Search Provider SPI.

The default production-oriented search backend is OpenSearch.

Typical values include:

- provider ID
- OpenSearch base URL
- index name
- connection timeout
- request timeout

## Redis configuration

Redis can be used for:

- AI analysis cache
- distributed rate limiting
- operational resilience scenarios

If Redis is unavailable, QwenBridge should either degrade according to the configured policy or fail fast where production safety requires it.

## Production validation

Production configuration must be validated before serving traffic.

Invalid production configuration should be treated as a deployment failure, not as a runtime surprise.
