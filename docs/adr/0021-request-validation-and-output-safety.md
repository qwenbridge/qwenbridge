# ADR 0021: Ordered Request Validation, Output Safety and Redaction

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 9: QB-057 – QB-063)

## Context

The audit found these gaps:

- The 64 KiB body limit, including the chunked-body check, ran only when abuse
  protection was enabled. It is disabled in `%test`, and an operator could
  disable it in production. The same limit also rejected signed pack uploads
  (about 2.9 MB) in production.
- Error mappers echoed raw exception messages. These can include internal
  class names, upstream hosts or user input. Jackson binding errors were
  rendered by the framework: in dev/test mode with internal class and
  attribute names, in production as an empty body.
- Nothing redacted secrets in logs, and log messages could contain user text
  with line breaks.
- The rate-limit bucket for an API key was its `String.hashCode()`, so keys
  could be crafted to collide (audit S5).
- There were no fuzz tests.

## Decision

1. **Ordered pipeline (QB-057).** Cheap checks run first and always run. They
   do not depend on abuse protection.
   1. Transport limits: header size and a 4 MB body ceiling.
   2. A safe request ID (`RequestIdFilter`, priority 2).
   3. The **request envelope** (`RequestEnvelopeFilter`, priority 4):
      - a request with a body needs a JSON content type, otherwise 415;
      - its declared size must fit the route limit, otherwise 413;
      - the limit is `qwenbridge.request.limits.max-body-bytes`, which defaults
        to the former abuse limit of 64 KiB;
      - only the listed `upload-paths` (pack and marketplace uploads) may send
        up to `max-upload-body-bytes` (4 MB);
      - chunked bodies are bounded while they are read
        (`RequestSizeInterceptor`), and a JSON `null` document is rejected
        there.
   4. Rate limits (`AbuseProtectionFilter`, now priority 5, when enabled), then
      authentication.
   5. Parser structure limits.
   6. The universal request contract.
   7. Deterministic threat rules, then model safety, before any retrieval or
      generation.
2. **Structural limits (QB-058).** Jackson `StreamReadConstraints` now bound:
   - nesting depth: 64;
   - string length: 4 MB;
   - number literal length: 64;
   - property name length: 256.

   These limits also apply to responses from upstream services that are read
   with the same mapper.
3. **Injection prevention (QB-059).** The connector query compilers already
   bind values as parameters or as typed structure leaves. Table names are
   pattern-validated and operators come from enums. Tests now pin this:
   - SQL text is identical whatever the values are;
   - search queries never use a user value as a key;
   - no module contains `ProcessBuilder`, `Runtime.exec` or a script engine.
4. **Context-aware sanitization (QB-060).** Core `Sanitizer` encodes the policy.
   User input is never "sanitized"; it stays exactly as sent. Text is made safe
   where it leaves the system, by the rules of the sink:

   | Sink | Treatment |
   |---|---|
   | `LOG` | Secrets masked; control and bidirectional characters escaped; length bounded. |
   | `ERROR_MESSAGE` | As `LOG`, plus class names removed and a tighter bound. |
   | `HEADER` | Printable ASCII only. |
   | `PROMPT_DATA` | Encoded as a JSON string literal. |

   Backend values are bound, never escaped (point 3).
5. **Redaction (QB-061).** Core `Redactor` masks:
   - bearer and basic credentials;
   - JSON Web Tokens and common API-token formats;
   - secret `key=value` pairs (the key is kept);
   - URL user information;
   - PEM private keys;
   - Luhn-valid card numbers.

   The Quarkus log filter `qwenbridge-redaction` applies the `LOG` sink to every
   console log message.
6. **Malformed requests (QB-062).**
   - Every JSON parse or binding failure returns the standard `ApiError` with a
     fixed message. Mappers cover the exact Jackson types, including an
     override of the framework's `MismatchedInputException` mapper.
   - Validation messages pass through the `ERROR_MESSAGE` sink.
   - AI and search provider errors return fixed messages; the details are only
     logged.
   - An unexpected error returns a generic message with no stack trace.
   - Request IDs are revalidated before they are echoed.
7. **Fuzz and property tests (QB-063).** The seed is fixed by default and can be
   overridden with `-Dqwenbridge.fuzz.seed` (iterations with
   `-Dqwenbridge.fuzz.iterations`). A failure reports the seed, the iteration
   and the escaped input. Properties covered:
   - Core: extraction always yields a valid, grounded QueryIR;
   - Core: a QueryIR survives a serialization round trip;
   - Core: malformed QueryIR JSON is rejected as a value, not an exception;
   - Core: normalization is idempotent;
   - Core: parser violations never echo submitted values;
   - Core: each sanitizer sink's output is safe;
   - Core: a text is a faithful translation of itself;
   - Server: random request bodies never cause a 5xx response, a stack trace
     or an echo of the body.

   The server fuzz test found a real defect: a JSON `null` body caused a 500 in
   the analyze endpoint.
8. **Rate-limit key.** API-key buckets now use a truncated SHA-256 of the key.

## Consequences

- Size and content-type limits hold in every profile. With the default
  configuration, pack uploads in production are no longer cut at 64 KiB.
- Error bodies are stable and generic. Clients that parsed AI or search
  provider error texts see fixed messages instead.
- Exception stack traces are written by the log handler and are not rewritten.
  Exception messages must not carry secrets; the redaction filter covers only
  the message.
- Sanitization is an output policy. Search and understanding always see the
  original text.
