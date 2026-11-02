# ADR 0036: Commerce as a Domain Pack — Separation, Schema Registration, Extension, Central Policies

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 21: QB-147 – QB-152)

## Context

The audit (B2) found Commerce woven into general server code:

- **Authentication.** Security, the platform and plugins all used
  `commerce.CommerceIdentity`.
- **Analysis model.** The v1 analysis model carried Commerce's `Constraint`
  type.
- **Schemas.** Tenant Commerce schemas lived in their own Redis keys, outside
  the Core schema registry.
- **Policies.** Commerce called pack policies directly, beside the central
  policy engine; the engine's pack rule received no request attributes.
- **Tests.** The Commerce pack module had no tests.

## Decision

1. **Pack contract (QB-147).** `CommercePackContractTest`, the pack module's
   first tests, checks:
   - the manifest: identity, version, API version, kind, publisher,
     capabilities, no dependencies and no configuration;
   - the schema: the exact field set, types, operators and case folding;
   - the bundled grammar: every clause grounded in the text; prices, currency,
     negation, category and stock; questions instead of silent drops;
   - documents: one per variant, never carrying a tenant.
2. **Separation (QB-148).**
   - **Identity.** Authentication and the principal moved to
     `io.qwenbridge.security.ApiIdentity`. Configuration keys and behaviour are
     unchanged.
   - **Constraint types.** The grounded `Constraint`, `Evidence` and `Operator`
     moved to `io.qwenbridge.analysis.model.Grounded`; JSON is unchanged.
   - **Result.** Security, pipeline, plugins, policy and intelligence code no
     longer refers to Commerce.
   - **Enforcement.** `CommerceSeparationTest` scans the sources:
     - Core never refers to Commerce, its catalog, its pack or connectors.
     - In the server, only the Commerce layer and an exact allow-list may.
       Each allow-list entry names the work that removes it; a new coupling,
       or a removed one left on the list, fails the build.
3. **Schema registration (QB-149).** The bundled pack's schema is in the Core
   `SchemaRegistry` from activation (owner `pack:commerce`). A test proves it
   equals the pack definition and that the apiVersion 2 default schema is the
   same field set without the internal `domain` discriminator.

   Tenant schema versions are persisted through the platform store adapter
   (memory or Redis) instead of direct Redis calls. Versions written by
   earlier releases are still read from their legacy Redis keys and are never
   rewritten or deleted.
4. **Typed QueryIR extension (QB-150).** Under the 1.0 contract the universal
   QueryIR carries `extensions.commerce` (version 1.0):
   `{schema: "commerce@1", catalogLanguage, currencies}`.
   - **Written and read only through `CommerceQueryExtension`,** which refuses
     another version, extra keys or wrong types.
   - **Opaque to Core,** which carries it unread.
   - **Round-trips** through the strict QueryIR JSON.
   - **Returned** as `interpretation.commerce`.
5. **Central policies (QB-151).** Domain-pack policies run only inside the
   central policy engine.
   - **Inputs.** `Authorization.domainPolicies` passes the request attributes
     (query, plan, mode) to the engine's pack rule.
   - **Outputs.** It returns the pack outcome with its traces for the response;
     when the engine denies first (no scope, another tenant), it returns that
     denial.
   - **Callers.** Commerce and the domain platform no longer call
     `PackPolicies` themselves.
   - **REVIEW.** A pack REVIEW still stops execution, as before, while
     approvals stay reported but not enforced (Section 1 decision).
   - **Test.** `CentralDomainPolicyTest` installs real signed, independently
     compiled policy packs and proves attribute-dependent denial, REVIEW and
     the scope denial through the engine.
6. **Enable and disable (QB-152).** `CommercePackLifecycleTest` covers the
   lifecycle per tenant:
   - **Disabled:** every use returns 409.
   - **Isolation:** another tenant and the other bundled pack are unaffected.
   - **Scope:** activation without the scope is forbidden.
   - **Enabled again:** Commerce is restored.

## Consequences

- **Remaining couplings,** listed and enforced by the test:
  - the model-output parser canonicalizes Commerce fields (pack vocabulary
    SPI, QB-053);
  - the v1 pipeline builds a Commerce plan (v1 on the Core planner);
  - the domain platform uses the generic Schema and Plan types still in
    `CommerceContracts`;
  - the composition root and the OpenSearch connector are allowed by design.
- **Module layout.** Commerce code still lives in the server module. Moving it
  to its own module is the next step once the couplings above are gone. It was
  not done here, to avoid a large move without approval.
