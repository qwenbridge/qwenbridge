# ADR 0034: Typed Grounded Suggestions, Refinement, Targeted Clarification, Negative Feedback

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 19: QB-132 – QB-138)

## Context

- **Suggestions** were the model's rewrite strings, filtered with
  `distinct().limit(3)`. They had no type, grounding or ranking.
- **Clarification questions** were generic or exposed compiler messages
  ("Conflicting equality: color").
- **Feedback.** Apart from click analytics, there was no feedback.
- **Tests.** Refinement was tested only with mocks.

## Decision

The new types live in Core (`io.qwenbridge.core.suggestion`) and are
domain-neutral.

1. **Typed, versioned suggestions (QB-132).** A `SuggestionSet`
   (`suggestions/1.0`) holds `Suggestion` items. Each has:
   - a stable id and a kind (REFINE or REPHRASE);
   - a source (RESULT_FACETS or MODEL_REWRITE);
   - its text and the exact typed `Change`s it makes;
   - `grounded`, `support`, `evidence` (the ids of supporting results) and
     `rank`.

   `GET /api/v2/search/{id}/suggestions` keeps `queries` (the apiVersion 2
   strings) and adds `version` and `items`.
2. **Refinement that preserves the rest (QB-133).** `Refinement` applies ADD,
   REPLACE or REMOVE changes. Every constraint they do not name stays the same
   object: same value, provenance and span. An exclusion leaves only through an
   explicit REMOVE that names it.

   `POST /api/v2/search/{id}/suggestions/{sid}/apply` then:
   - re-validates the refined plan against the schema;
   - runs it through the central policy;
   - records the suggestion as the evidence of the new constraint.

   A rephrasing cannot be applied: it is a new query.
3. **Grounded suggestions (QB-134).** Refinements come only from the current
   results.
   - **Which values.** For each facet the query does not already fix
     (`qwenbridge.commerce.suggestions.facets`, default
     brand, color, size, category, condition), each value present in some but
     not all returned results is suggested.
   - **Grounding.** Its support is the number of those results, so it cannot
     lead to an empty answer within them.
   - **Model rephrasings** are kept, labelled ungrounded and ranked last.
4. **Targeted clarification (QB-135).** `CommerceCompiler` now throws a typed
   `Rejected` carrying a kind, a field and the values involved. Its messages are
   unchanged, so apiVersion 2 clients see the same strings.

   Under `commerce-search/1.0`, responses carry typed `clarifications`:
   - CONFLICT: "You asked for more than one color: black or white. Which one do
     you want?", with options `[black, white]`;
   - MISSING_QUALIFIER: "Which currency is the price in?";
   - UNSUPPORTED;
   - INVALID_VALUE;
   - VAGUE, used only when nothing more specific is known.

   Blocking Core assessments become CONFLICT or UNSUPPORTED questions.
5. **Negative feedback as typed context (QB-136).**
   `POST /api/v2/search/{id}/feedback` turns a feedback signal into an explicit
   `Feedback.Update` and runs the follow-up:

   | Feedback | Typed update | Follow-up |
   |---|---|---|
   | NOT_RELEVANT on a result | Suppress it | Nothing else changes |
   | EXCLUDE_VALUE on a field | Add an NE constraint, as if the user had typed "not …" | The exclusion applies as a filter |
   | TOO_EXPENSIVE on a result | A SOFT `LT` preference at that result's price, and suppression of the result | Matching results move ahead and none is removed, because "too expensive" does not say by how much |

   Feedback must name a result of that search.
6. **Deterministic ranking (QB-137).** Suggestions are ordered:
   1. grounded before ungrounded;
   2. the most even split first (support closest to half the results);
   3. facet order;
   4. value.

   Ids derive from the base request and the text, so they are stable.
7. **Tests (QB-138).**
   - **Core (4):**
     - grounding: only values that narrow; fixed fields skipped; support and
       evidence;
     - determinism over 20 shuffles, with ungrounded suggestions last;
     - refinement keeps untouched constraints identical, and REPLACE and
       REMOVE behave as specified;
     - all three feedback kinds and their validation.
   - **End to end on the real in-process backend and synthetic catalog (4):**
     - the exact suggestion order for the seven-result query;
     - applying "color: white" returns the white shoe while brand, price and
       currency are kept;
     - each feedback kind changes the follow-up as specified;
     - targeted CONFLICT and MISSING_QUALIFIER questions under 1.0, while
       apiVersion 2 keeps its strings.

## Consequences

- **Evidence scope.** Suggestions are grounded in the returned page only; a
  value may also occur in results beyond the page.
- **Feedback persistence.** Feedback updates apply to the follow-up search
  they produce. Making them part of a conversation's state is QB-139 – QB-146,
  where sessions carry context.
- **Unvalidated rephrasings.** Model rephrasings are not yet validated as
  alternative constraint sets; the recovery validator (ADR 0033) can do that
  once their constraints are extracted.
