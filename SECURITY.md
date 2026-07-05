# Security Policy

## Supported versions

Security fixes are handled on the active public release line.

Current public release track:

```text
V9 — Developer Platform
```

## Reporting a vulnerability

Please report suspected vulnerabilities privately. Do not open a public issue with exploit details.

Include:

- affected module
- affected version or commit
- reproduction steps
- expected impact
- any relevant logs or payload examples

## Secrets

Never commit secrets, credentials, private tokens, API keys, passwords, private endpoints, or local `.env` files.

Read:

- `docs/security/secrets-handling-policy.md`

## Abuse protection

QwenBridge includes input security checks, threat detection, and rate-limiting support.

Read:

- `docs/security/abuse-protection.md`

## Security verification

Before public release:

```bash
mvn clean verify
bash scripts/verify-release.sh
```

Dependency and vulnerability findings should be reviewed before publishing release artifacts.
