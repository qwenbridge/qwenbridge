# ADR 0019: One Constraint Validation Gate Before Compilation

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 8: QB-050 – QB-056)

## Context

Before this change there were two separate validation paths:

- The universal QueryIR (ADR 0015, 0018) was checked against a domain schema
  (ADR 0016) only structurally. Nothing decided which constraints could be
  executed.
- The legacy structured price path (`intelligence.schema.QuerySchemaRegistry`,
  `QuerySchemaValidator`, `QueryConstraintValidator`) used its own allowlist
  types and rules.

There was also no defined way to turn understood meaning into schema fields.
For example, Commerce stores `price` as a number with a separate `currency`,
while extraction produces a typed money value. And nothing prevented a later
transformation from dropping a negation.

## Decision

`core.validation` is the single gate between understanding and compilation.

1. **Mapping (QB-052, QB-053).** `SemanticMapper` applies only approved,
   typed `MappingRule`s that belong to a domain:
   - `FieldAlias`: a proposed or alternative name becomes a schema field.
   - `MoneyToNumberAndCurrency`: money on the declared number field becomes
     the amount plus an equality on the currency field. An unknown currency is
     never filled in, and exclusions are never split.
   - `SignalToSoftConstraint`: an approved interpretation of a subjective
     concept. It is always SOFT and INFERRED.

   Type-based binding is allowed only for extraction's generic placeholders
   (`amount`, `value`, `quantity`, `time`). A field the user named (such as
   "warranty") is never re-targeted to another field just because the value
   type fits. Every mapping is appended to the constraint's reason.
2. **Classification (QB-050, QB-051).** Every constraint is assessed. Nothing
   is silently dropped.

   | Status             | Meaning                                                 | Blocks |
   |--------------------|---------------------------------------------------------|--------|
   | `EXECUTABLE`       | Schema-valid; a filter (HARD) or a preference (SOFT)    | no     |
   | `UNCERTAIN_SOFT`   | Valid SOFT from an inferred, heuristic or model source  | no     |
   | `UNSUPPORTED_SOFT` | SOFT that the schema cannot express; reported, not used | no     |
   | `INVALID_HARD`     | HARD that the schema cannot express                     | yes    |
   | `CONTRADICTORY`    | HARD constraints on one field that cannot all hold      | yes    |

   An invalid HARD constraint is never weakened or removed to get more results.
3. **Contradictions (QB-054).** The gate detects empty numeric intervals,
   including exclusive bounds that touch and `BETWEEN` against bounds. It also
   detects equal and not-equal to the same value. Bounds are compared only
   within one kind: number, the same quantity unit, or the same currency.
   Conflicting SOFT preferences do not block.
4. **Negation (QB-055).** Every exclusion in the input must still be an
   exclusion afterwards, with the same id, operator and value. Otherwise the
   transformation is defective and execution is blocked (`lostNegations`).
5. **Compile boundary (QB-054).** `ValidatedQuery` is final and has no public
   constructor, so only the gate can create it. It holds the HARD filters and
   SOFT preferences. Section 2 compilers accept only a `ValidatedQuery`, so
   backend syntax can never come from free-form model output.
6. **Legacy migration (QB-056).** `QuerySchemaValidator` now expresses its
   allowlist as a Core schema: `price` NUMBER plus a closed `price.currency`
   vocabulary, linked by a `MoneyToNumberAndCurrency` rule. It validates each
   constraint through the gate. Its result type and messages are unchanged.
   Legacy entity types (`BRAND`, `PRODUCT`) predate the Core naming rule, so
   they stay on the legacy allowlist.

In the server, `ConstraintValidationStep` (order 32, after `QueryIrStep`) runs
the gate against the bound schema, which is the first enabled hinted domain.
It uses `DomainMappings` and publishes `constraintValidation` in the v1
response. The response holds a status, the schema key, `enforced: false` and
one assessment per constraint. Violations are given as a path and a code and
never echo user values.

## Consequences

- One rule set decides executability for every domain.
- v1 reports and does not enforce. The legacy pipeline still executes with its
  own analysis, so a BLOCKED result does not yet change the decision. That
  would alter released behaviour, and it becomes meaningful only when Section 2
  compiles from `ValidatedQuery`.
- Commerce mapping rules live in the server's `DomainMappings` until the pack
  vocabulary SPI lets packs ship them (QB-053 PARTIAL).
- Legacy constraints are validated one at a time, as before. The cross-
  constraint checks (contradictions) apply to the universal QueryIR path.
