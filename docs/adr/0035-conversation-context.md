# ADR 0035: Versioned Conversation Context, Signed Context Tokens, Optional Redis

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 20: QB-139 – QB-146). It follows the
audit's recommendation H5 (Ed25519, as for signed packs).

## Context

- **No contract.** Conversation state existed only as server-held sessions
  (`CommerceSessions`), with no versioned contract.
- **No stateless mode.** A client could not carry context between stateless
  calls; only an unsigned paging cursor existed.
- **Redis by default.** The platform store used Redis outside tests, and memory
  mode was a test switch.
- **Limits.** Sessions were limited by TTL and an entry count, not by size.

## Decision

1. **Contract (QB-139).** `ConversationContext` (`conversation-context/1.0`,
   in Core) holds:
   - the conversation id, tenant, subject, revision, issue time and expiry;
   - the constraints in force, each with its provenance (`origin`: USER_TEXT,
     CONTEXT, ACCEPTED_PROPOSAL, SUGGESTION or FEEDBACK) and the `turn` that
     introduced it;
   - locked fields, suppressed result ids and a short turn history.

   It carries no documents, no free text beyond stated values, and no
   credentials. `GET /api/v2/sessions/{id}/context` returns it for a session.
2. **Untrusted client context (QB-140).** `POST /api/v2/conversations/turns`
   takes `{query, context | contextToken, backend?, limit?}`, and the server
   keeps nothing between turns. `ContextValidator` re-checks every context:
   - **Checks:** the version; that tenant and subject are the caller's; expiry,
     issue time and maximum lifetime; count and byte limits; and every field and
     operator against the Commerce schema.
   - **Downgrade:** an unsigned context keeps its values, but its constraints
     are downgraded to CONTEXT, so a client cannot claim the user said
     something.
   - **Re-validation:** the merged plan is then validated like any other plan.
3. **Signed tokens (QB-141).** A token is `v1.<payload>.<signature>`, base64url,
   signed with Ed25519.
   - **Payload:** `iss`, `aud`, `kid`, `sub`, `tenant`, `iat`, `exp` and the
     context.
   - **Verification:** the signature with the named key, the issuer, the
     audience, the expiry, and the claims against the context.
   - **Optional:** signing is enabled by `qwenbridge.context.signing.private-key`
     and `public-key` (PKCS#8 and X.509, base64). Without them no tokens are
     issued and plain context still works. Half a key pair or an invalid key
     stops startup.
4. **Optional Redis (QB-142).** `PlatformStore` has two adapters, selected by
   `qwenbridge.platform.store=redis|memory`. The earlier
   `qwenbridge.platform.persistent` switch still applies when the selector is
   not set. The Redis client is resolved only for the Redis adapter; a test
   proves the memory adapter never touches it.
5. **Limits (QB-143).**
   - **Context:** at most 50 turns, 32 constraints, 32 locked fields, 100
     suppressed ids, 16 KiB and one hour of lifetime (`qwenbridge.context.ttl`,
     default 30 minutes). Older turns are dropped as new ones arrive.
   - **Store values:** limited to `qwenbridge.platform.max-value-bytes`
     (default 1 MiB, HTTP 413) on both adapters; nothing oversized is written.
6. **Preserved constraints (QB-144).** Follow-ups merge through the Core
   `ContextMerge`:
   - EQ is replaced by a new EQ;
   - exclusions accumulate;
   - a new lower bound keeps an earlier upper bound;
   - cleared fields go.

   Carried constraints keep their origin and turn: their evidence source is
   `context:<origin>:<turn>`, so the next context reports them faithfully.
7. **No leakage (QB-145).** A context or token that belongs to another user,
   even in the same tenant, or to another tenant is refused with 403 FOREIGN.
   It is never ignored and never merged. An edited token fails its signature
   (400), and session context is readable only by its owner (404 otherwise).
   Refusal statuses:

   | Reason | Status |
   |---|---|
   | FOREIGN | 403 |
   | EXPIRED | 410 |
   | Size or turn limit | 413 |
   | Anything else | 400, with the reason |

8. **Tests (QB-146).** The main suite is Redis-free.
   - **Core:** contract, limits, downgrade, merge provenance, and every token
     failure mode.
   - **API, end to end** on the memory store and in-process backend, with two
     users in one tenant and one in another:
     - signed stateless turns carrying brand, price and currency from turn 1 and
       colour from turn 2;
     - plain context downgraded;
     - a forged field refused;
     - an oversized history refused;
     - every cross-user and cross-tenant case refused;
     - session context for its owner only.
   - **Unit:** adapter selection and limits; the memory adapter never resolves
     Redis.
   - **With Redis** (`QWENBRIDGE_RUN_REDIS_IT`, in the master run): shared
     atomic state, as before, plus the size limit writing nothing.

## Consequences

- **Readiness and Redis (QB-149).** Analytics, evidence, the schema registry
  and the analysis cache still use Redis directly, and the Quarkus Redis health
  check remains. Making those optional is QB-149 (Section 3).
- **Mapper quirk.** The generic 400 mapper replaces messages with "Malformed
  JSON request body". Context refusals therefore use the argument mapper, which
  keeps the sanitized reason.
- **Tokens and sessions.** Tokens expire with the context. Server sessions keep
  their own TTL and are unaffected.
