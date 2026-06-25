# AI Provider Abstraction

**Status:** Accepted

## Context

OmniSearch AI must remain independent of any specific AI provider.

The first implementation will use Ollama with local models, but the architecture must allow additional providers to be added without changing the application pipeline.

The pipeline should communicate only with provider-neutral abstractions.

## Decision

The application will communicate with AI models through a provider-neutral abstraction.

All provider-specific logic, HTTP clients, DTOs, authentication, and model-specific behavior must remain inside their own provider implementation.

The first provider implementation will be Ollama.

## Consequences

### Advantages

- The pipeline remains independent of any AI provider.
- New providers can be added without changing the pipeline.
- Provider implementations remain isolated.
- Testing and future maintenance become simpler.

### Trade-offs

- More abstraction is introduced early.
- Each provider requires its own mapping layer.

## Scope

This ADR defines the architectural boundary between the OmniSearch AI application and external AI providers.

It does not define:

- Prompt engineering
- Model selection strategy
- Tool calling
- Streaming
- Multi-agent workflows
- Cost optimization
- Provider failover

These topics will be covered by future ADRs.
