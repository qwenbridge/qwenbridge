# V8 Release Checklist

## Security automation

- [ ] Dependabot is enabled.
- [ ] CodeQL workflow passes.
- [ ] OWASP dependency check passes or has audited suppressions.
- [ ] Container image scan passes or has accepted risk notes.
- [ ] Secret scanning and push protection are enabled in repository settings.
- [ ] `SECURITY.md` disclosure flow is verified.

## Abuse protection

- [ ] Per-IP limits are enforced.
- [ ] Per-API-key limits are enforced.
- [ ] Request-size limit is enforced.
- [ ] Concurrent stream limit is enforced.
- [ ] AI request quota is enforced.
- [ ] 429 response body and headers match the public contract.
- [ ] Redis fallback behavior is tested.

## CI/CD quality gates

- [ ] Unit tests pass.
- [ ] Integration tests pass.
- [ ] Architecture tests pass.
- [ ] API compatibility check passes.
- [ ] Dependency scan passes.
- [ ] Container scan passes.
- [ ] Benchmark smoke test passes.
- [ ] Docker release verification passes.
- [ ] Release evidence is uploaded.
