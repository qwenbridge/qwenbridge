# ADR-0005

## Title

V2 AI-native Semantic Search Core

## Status

Accepted

## Context

V1 introduced a modular search pipeline with AI rewrite capabilities.

The next iteration focuses on transforming the pipeline into an AI-native search understanding engine while keeping the project focused on search.

## Decision

V2 introduces:

- AI Semantic Understanding
- AI Intent Detection
- Embedding generation
- In-memory vector retrieval
- Hybrid retrieval
- Improved confidence scoring

The project explicitly excludes:

- Forms
- Agentic workflows
- External AI providers
- Production vector databases
- UI
- User management

## Consequences

The architecture remains lightweight, testable, and focused on semantic search.
