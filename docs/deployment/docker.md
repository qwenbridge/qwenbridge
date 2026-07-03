# Docker Deployment Guide

## Overview

QwenBridge V6 runs as a Docker Compose stack with:

- QwenBridge API
- Ollama
- Redis
- OpenSearch

## Quick Start

    cp .env.example .env
    docker compose up -d --build

## Health Checks

    curl -fsS http://localhost:8080/actuator/health | jq .
    curl -fsS http://localhost:8080/api/v1/health | jq .

Expected result:

    {
      "status": "UP"
    }

## Runtime Security

The QwenBridge application container runs as the non-root `qwenbridge` user.

    docker exec qwenbridge-app id -un

Expected result:

    qwenbridge

## Persistent Volumes

Docker Compose defines persistent volumes for:

- `ollama-data`
- `opensearch-data`

## Release Verification

Run:

    FORCE_FRESH=false PULL_DOCKER_IMAGES=false ./scripts/verify-release.sh

Expected result:

    RESULT: RELEASE VERIFICATION PASSED
