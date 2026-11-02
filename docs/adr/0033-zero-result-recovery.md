# ADR 0033: Typed Zero-Result Recovery with Consent and Validated Alternatives

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 18: QB-125 – QB-131)

## Context

`ZeroResultRecovery` already held proposals on the server and broadened a query
only when its owner accepted one. Its rules were hard-coded (audit,
`ZeroResultRecovery:71-115`):

- raise a price cap by 10%;
- drop size;
- drop colour.

It also had these gaps:

- **Failures.** It could not tell zero results from a provider failure.
- **Explanations.** It explained decisions only as free text.
- **Security constraints.** It protected them only by construction.
- **Acceptance.** It did not re-check a held alternative when it was accepted.
- **Tests.** Its tests used mocks rather than a real backend.

## Decision

The new types live in Core (`io.qwenbridge.core.recovery`) and are
domain-neutral.

1. **Assessment (QB-125).** `ResultAssessment` has five outcomes: RESULTS,
   LOW_RESULTS (optional threshold), ZERO_RESULTS, PROVIDER_FAILED and
   NOT_EXECUTED. Only zero or low results from a working provider are
   recoverable. A failure is never answered by broadening.
   - **Commerce 1.0 responses** report `retrieval.outcome`. When every provider
     failed, the status is `UNAVAILABLE` instead of `OK` with zero results.
2. **Typed policy (QB-126).** `RelaxationPolicy` (id, version) holds ordered
   rules, protected fields, a proposal limit and a low-result threshold. A rule
   is one of:
   - `raise` a bound, factor in (1, 2];
   - `lower` a bound, factor in [0.5, 1);
   - `drop` an equality.

   The Commerce policy is configurable:
   - `qwenbridge.commerce.relaxation.rules`, default
     `price:raise:1.10,size:drop,color:drop` (the previous behaviour);
   - `protected-fields`, default `currency`;
   - `max-proposals`, default 3.

   Invalid configuration stops startup.
3. **Never relaxed (QB-127).**
   - **Protected fields.** `tenantId`, `tenant` and `domain` are always
     protected, plus any configured fields. A rule on a protected field is
     refused when the policy is built.
   - **Never relaxed at all:** exclusions (NE, NOT_IN, NOT_CONTAINS,
     NOT_EXISTS), constraints added by a domain or policy rule (DOMAIN_RULE),
     and fields the user locked.
   - **Currency.** Currency is protected in Commerce: changing it would be a
     conversion, not a relaxation.
4. **Consent (QB-128).**
   - **One change per proposal.** Each proposal changes exactly one constraint
     and keeps every other.
   - **HARD needs consent.** A relaxed HARD constraint is offered with
     `requiresConsent: true` and changed only by an explicit accept of the
     server-held offer, as before.
   - **Rounding.** Bounds are rounded outward to the currency's minor units, so
     a relaxed bound is never tighter.
5. **Validated alternatives (QB-129).** `AlternativeValidator` checks any
   alternative query, whether from the planner, a stored offer or a future model
   suggestion, against the original and the policy. An alternative is valid only
   if:
   - every change is a permitted relaxation within the rule's factor;
   - nothing is added (no new constraint, no different operator);
   - exclusions, protected fields, domain-rule constraints and locked fields are
     unchanged;
   - money keeps its currency.

   The planner checks its own proposals with it. Accepting an offer re-validates
   the held alternative before execution, and a tampered offer gets 409 with the
   reasons.
6. **Explanations (QB-130).** Recovery decisions use typed `RecoveryReason`
   codes. `GET /api/v2/search/{id}/recovery` returns:
   - the outcome and count;
   - the policy key;
   - the reasons;
   - the proposals, with their rule kind and consent flag;
   - for every constraint left alone, its field, operator and reason: for
     example `EXCLUSION_NOT_RELAXED`, `PROTECTED_FIELD`, `LOCKED_BY_USER`,
     `NO_PERMITTED_RELAXATION`.

   The existing `/relaxations` endpoints keep their shape, and proposals add
   `kind` and `requiresConsent`.
7. **Tests (QB-131).**
   - **Core (5):**
     - assessment of every outcome;
     - policy validation, including protected fields;
     - one change per proposal, outward rounding, consent, and a reason for
       every skip;
     - SOFT preferences and limits;
     - validation of 13 kinds of alternatives.
   - **Planner:** a failed provider gives UNAVAILABLE and PROVIDER_FAILED, not
     an empty catalog.
   - **End to end on the real in-process backend and synthetic catalog:**
     - a zero-result search is explained;
     - offers are made for colour, size and price, while the exclusion,
       currency and brand are kept with their reasons;
     - nothing broadens before consent;
     - accepting the budget offer ("45 to 49.50 EUR") returns the one matching
       product, with the exclusion and currency intact;
     - a held offer with its exclusion removed is refused with 409.
   - **Regression:** the existing acceptance tests pass unchanged on the Core
     planner.

## Consequences

- **Model rewrites.** Model rewrite suggestions are still rendered texts.
  Validating them as alternatives requires extracting their constraints first.
  The validator is ready for that.
- **Low results.** Low-result recovery is available in Core, but Commerce keeps
  a threshold of 0 (zero results only) as before.
