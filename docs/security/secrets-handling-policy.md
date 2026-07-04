# V8 Secrets Handling Policy

Secrets must never be committed to the repository, test resources, Docker images, logs, release artifacts, screenshots, or documentation examples.

## Approved storage

- GitHub Actions secrets for CI/CD credentials.
- Runtime environment variables for deployment secrets.
- Local `.env` files ignored by Git.

## Required controls

- GitHub secret scanning enabled on the repository.
- Push protection enabled when available.
- CodeQL workflow enabled.
- Dependency and container scanning enabled in CI.
- Any leaked secret must be revoked, rotated, and documented in release evidence.

## Disclosure workflow

Security reports must follow `SECURITY.md`. Every release must verify that the disclosure workflow is still accurate.
