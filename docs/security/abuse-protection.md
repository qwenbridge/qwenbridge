# Abuse Protection

QwenBridge includes abuse-protection controls to reduce accidental overload, scripted abuse, and unsafe AI-facing input patterns.

## Protection areas

QwenBridge protects the public API through:

- request-size limits
- fixed-window rate limiting
- optional Redis-backed distributed rate limiting
- input normalization
- threat detection
- threat correlation
- safe API error contracts
- controlled SSE session lifecycle

## Rate limiting

The rate limiter returns a decision before expensive pipeline work is executed.

Rate-limit decisions should include:

- allowed or rejected state
- configured limit
- remaining allowance where available
- retry-after information where available

For production, Redis-backed rate limiting is preferred when multiple application instances serve traffic.

## Input normalization

Normalization runs before downstream analysis. It reduces ambiguity caused by encoding tricks, control characters, Unicode normalization issues, HTML entities, URL encoding, and repeated whitespace.

## Threat detection

Threat detectors are modular and rule-based. Current detector categories include:

- SQL injection
- NoSQL injection
- LDAP injection
- command injection
- path traversal
- server-side request forgery
- template injection
- cross-site scripting
- prompt injection
- jailbreak attempts
- secret leakage
- Unicode obfuscation

## Correlation

Single low-severity findings may not be enough to block a request. Correlation rules combine multiple findings into a higher-level risk profile and final threat decision.

## Operational guidance

For public deployments:

- keep request limits explicit
- keep rate-limit behavior documented
- monitor rejected requests
- do not log secrets or raw sensitive payloads
- validate production configuration before serving traffic
