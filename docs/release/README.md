# QwenBridge Release History

This document summarizes the public release evolution of QwenBridge.

## V1 — Search Box Core
Initial Spring Boot service with a step-based search analysis pipeline, REST API, language detection, intent detection, threat analysis, rewrite, semantic validation, confidence calculation, Docker support, and CI.

## V2 — AI-Native Semantic Search Core
Introduced AI-native query understanding with Ollama, embeddings, semantic analysis, hybrid retrieval foundations, and improved confidence scoring.

## V3 — Search Intelligence Expansion
Added Search Provider SPI, InMemory provider, OpenSearch provider, backend-aware provider resolution, structured search responses, and OpenSearch execution integration.

## V4 — Secure Fast AI Pipeline
Refactored the AI pipeline into a single AI analysis call, introduced Redis-backed caching, input normalization, modular threat detection, threat correlation, and detailed threat explanations.

## V5 — Public Launch Foundation
Prepared QwenBridge as a public developer platform with SDK direction, REST API documentation, Docker onboarding, streaming direction, and public launch structure.

## V6 — Public Product Hardening
Froze public REST and SSE contracts, defined provider reliability semantics, hardened validation and error contracts, verified OpenSearch retrieval, and produced release evidence.

## V7 — Intelligent Streaming and Retrieval Quality
Delivered AI token streaming, typed SSE lifecycle events, retrieval ranking policy, reranking service, benchmark dataset, and retrieval quality evidence.

## V8 — Production Safety
Added abuse protection, rate limiting, production configuration validation, security documentation, secrets policy, and safer deployment behavior.

## V8.1 — Production Operability
Added operational readiness, dependency health checks, metrics, tracing, logging, runbooks, and production verification improvements.

## V9 — Developer Platform
Delivered the Java SDK, Spring Boot Starter, TypeScript SDK, typed streaming clients, examples, publishing preparation, developer onboarding, and release verification.
