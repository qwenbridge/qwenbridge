# Security Policy

## Supported versions

Security fixes are made on the active release line: currently **0.9.x (QwenBridge 1.0 release
candidate)**. Older lines (V1–V9 records in `docs/release/`) are historical and unsupported.

## Reporting a vulnerability

Report suspected vulnerabilities **privately** to the maintainers, through the repository's private
vulnerability reporting (GitHub Security Advisories). Do not open a public issue with exploit
details.

Include:
- the affected module and version or commit;
- reproduction steps;
- the expected impact;
- relevant logs or payloads, with secrets removed.

## Secrets

Never commit secrets, credentials, tokens, API keys, passwords, private endpoints or `.env` files.
API principals and provider credentials are supplied through the environment. See the
[secrets handling policy](docs/security/secrets-handling-policy.md).

## Built-in protections

The server provides:
- request validation and size limits;
- 12 threat-detector families;
- central policy;
- per-IP, per-key and quota rate limiting;
- tenant isolation.

The `prod` profile requires API keys. See [abuse protection](docs/security/abuse-protection.md)
and the [1.0 security audit](docs/security/QwenBridge-1.0-Security-Audit.md).

## Before a release

The steps are in the [release process](docs/publishing/release-process.md):
- `mvn clean verify`;
- the code-quality profile;
- the dependency and container scans (CI jobs `dependency-scan`, `container-scan`);
- the master verification.

Review every finding before publishing.
