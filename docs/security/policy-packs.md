# Policy Packs and Evaluation Order

Policy Packs add rules to the central policy engine (ADR 0023). They never replace it. Every path
that acts for a caller (endpoints, connectors, plugins, agents) asks the same engine. ADR 0049
records the Policy Pack decisions.

## Contract (QB-249, QB-252)

A Policy Pack implements `io.qwenbridge.pack.PolicyPack` from the plugin SDK. It is installed as a
signed pack and activated per tenant (needs the `tenant-policy` entitlement and `packs:write`).

| Member | Meaning | Checked at install and activation |
|---|---|---|
| `contract()` | Evaluation contract (`PolicyPack.CONTRACT` = 1) | Any other value is refused |
| `operations()` | Operations the rules apply to, or `*` | Every name must be in the central operation catalog |
| `domains()` | Domains the rules apply to, or `*` | Non-empty; valid domain ids |
| `order()` | Evaluation order | Within -10000..10000 |
| `evaluate(Input)` | Returns `ALLOW`, `ABSTAIN`, `DENY` or `REVIEW`, with a rule id and reason | Missing evidence is refused by the `Decision` type |

A refused pack never becomes installed or active. Installed versions are immutable. An upgrade
activates a new version and keeps up to 8 previous versions for rollback. Every decision's trace
names each pack as `id@version`, `Outcome.versions()` lists them, and a denial or review is logged
with those versions.

## Evaluation order (QB-250)

The order is fixed. The same inputs and the same active pack versions always produce the same
decision and the same trace order.

1. **Mandatory Core checks (cannot be changed by any pack):**
   - unknown operation → DENY;
   - SENSITIVE or HIGH_RISK operation without authentication → DENY;
   - resource in another tenant → DENY;
   - no scope covering the operation → DENY.
2. **Tenant Policy Packs.** Every active pack whose domains and operations match is evaluated,
   ordered by `(order, pack id)`.
3. **Domain Pack rules** (ADR 0047) for resources in their domain, in pack activation order. The
   first `DENY` from step 2 or 3 ends evaluation.
4. **Combining** (the most restrictive result wins):

   | Any of the evaluated packs returns | Pack outcome |
   |---|---|
   | `DENY` | `DENY` |
   | `REVIEW` (and no `DENY`) | `REVIEW` |
   | Only `ALLOW` and `ABSTAIN` | `ALLOW` |
   | An error, or the pack is unavailable | `DENY` (fail-closed) |

   `ALLOW` and `ABSTAIN` never broaden anything decided in step 1. When `ALLOW` meets `DENY` or
   `REVIEW`, the outcome is the restrictive one and `conflict` is true.
5. **Approvals:**
   - **Required for:** HIGH_RISK operations, and anything a pack sent to `REVIEW`. A current
     approval for the same operation, resource and tenant, given by someone else, is required.
   - **Enforcement:** with `qwenbridge.policy.approvals-enforced=false` the request is allowed and
     reported as `APPROVAL_NOT_ENFORCED`.
   - **Denials stand:** an approval never overrides a `DENY` from any step.

## Conflicts and invalid rules (QB-251)

Conflicts are always settled toward the more restrictive outcome and are reported. They are
never settled by order alone. The table shows how other problems are handled:

| Problem | Handling |
|---|---|
| Unknown operations, an unsupported contract, missing domains, an order out of range | Refused before activation |
| A rule that throws or cannot be loaded at evaluation | `DENY`, with rule `policy-unavailable` |
| Equal order | Tie broken by pack id; the result is the same either way, because the restrictive result wins |

## Disabling packs (QB-253)

Disabling or rolling back an optional Policy Pack removes only that pack's rules. The Core checks
in step 1, approval requirements and Domain Pack rules are unaffected. Changing packs is itself a
HIGH_RISK operation (`packs:write`).
