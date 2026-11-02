# ADR 0042: Framework-Agnostic Web Component

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 28: QB-193 – QB-198)

## Context

`<qwenbridge-search>` was a Commerce shopper UI:
- session turns only;
- product wording and fields (`price`, `currency`) fixed in code;
- configuration by script only;
- no empty, partial or degraded states beyond a count;
- stale responses guarded only by aborting the previous request (and `resume()` had no guard);
- no Escape key or focus management;
- the textarea disabled while loading, so keyboard focus was lost.

## Decision

1. **Domain-neutral (QB-193).**
   - **Display.** Title and detail fields are configurable, as is every visible text (`labels`).
   - **Declarative setup.** Attributes (`base-url`, `mode`, `transport`, `backend`, `contract`,
     `title-field`, `detail-fields`) make it work in a plain page without script.
   - **Compatibility.** Commerce defaults stay, so existing pages and the Playground are unchanged.
2. **Public API only (QB-194).**
   - **Modes.** `mode="search"` uses stateless `POST /api/v2/search` (optionally the planned
     contract); `session` keeps owned conversations with explicit consent for alternatives.
   - **SDK.** All calls go through the SDK, so they inherit typed errors, timeouts, and server-side
     cancellation on abort.
   - **Credentials** come through `configure()` only.
3. **Rendering (QB-195).** Results are a labelled region with a list of articles. Suggestions and
   clarification options are buttons that run the suggested query. All server text is set with
   `textContent`.
4. **States (QB-196).**
   - **Exposed states:** `initial`, `loading`, `success`, `empty`, `partial`, `clarification`,
     `review`, `error` and `cancelled`, as `data-state` and the `qwenbridge-state` event.
   - **Stale-response guard.** Every operation, `configure()`, **New search** and removal start a new
     generation; responses from older generations are discarded.
5. **Accessibility (QB-197).**
   - **Focus.** The shadow root delegates focus. The box is read-only rather than disabled while
     loading. Focus returns to the box after cancel or **New search**, and moves to the summary after
     accepting an alternative.
   - **Keyboard and announcements.** Escape cancels; `aria-busy` is set on results; polite status and
     alert regions announce progress and errors.
6. **Tests (QB-198).** Six new headless-Chromium tests, plus the five existing ones, run against a
   controlled local API and the exact assets the server ships.
   - **Startup:** declarative startup with no framework.
   - **Accessible tree:** the expected roles and names are present.
   - **Interaction:** suggestions operated by keyboard; every state; out-of-order responses.
   - **Keyboard and cancel:** the keyboard-only path, with Escape reaching the server.
   - **Lifecycle:** cleanup after removal.
   - **Injection:** attempts through titles, suggestions and labels render as text.

## Consequences

- **Removal.** Removing the element now cancels its request on the server.
- **Read-only textarea.** It is read-only, not disabled, while loading, so tests or pages that waited
  for `disabled` should use `data-state`.
- **No automated audit.** No WCAG audit tool (axe) is part of the build; this is a known gap, and the
  accessible tree and keyboard tests are the evidence.
- **Browser coverage.** Tests use Chromium only.
