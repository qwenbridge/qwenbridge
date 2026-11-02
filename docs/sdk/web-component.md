# `<qwenbridge-search>` Web Component

A standards-based Custom Element (Shadow DOM, ES modules). It needs no framework, Shopify, theme or
build step. The server serves it at `/web/web-component.js`; npm users import
`@qwenbridge/sdk/web-component`. It talks only to the public v2 API, through the TypeScript SDK's
`CommerceClient`.

## Use it

Plain HTML, with no script of your own:

```html
<script type="module" src="https://search.example.com/web/web-component.js"></script>
<qwenbridge-search base-url="https://search.example.com" mode="search"
                   title-field="name" detail-fields="author, year"></qwenbridge-search>
```

With a credential, or from a framework:

```javascript
const element = document.querySelector('qwenbridge-search');
element.configure({ baseUrl: 'https://search.example.com', bearerToken: token, mode: 'session', transport: 'sse' });
element.addEventListener('qwenbridge-result', e => console.log(e.detail));        // the v2 response
element.addEventListener('qwenbridge-state', e => console.log(e.detail.state));   // see States
```

Frameworks treat it like any element. For example, in React render `<qwenbridge-search ref={ref} />`
and call `ref.current.configure(...)`. Credentials are accepted only through `configure()`, never from
attributes, and are kept in memory only.

| Attribute / option | Meaning |
|---|---|
| `base-url` / `baseUrl` | API origin (http or https; no embedded credentials) |
| `mode` | `session` (default): an owned conversation where alternatives need explicit consent. `search`: stateless `POST /api/v2/search` |
| `transport` | `rest` (default) or `sse` |
| `backend` | A configured connector ID |
| `contract` | `commerce-search/1.0` (search mode): the server plans retrieval |
| `title-field` / `titleField` | Document field used as each result's title (default `title`, falling back to the id) |
| `detail-fields` / `detailFields` | Document fields shown under the title (default `price, currency`) |
| `limit`, `timeoutMs`, `lockedFields` | Passed to the API / SDK |
| `labels` | Every visible text, for other domains or languages (see `DEFAULT_LABELS`) |

## States

The host carries `data-state`, and each change also fires `qwenbridge-state`.

| State | When | Shown |
|---|---|---|
| `initial` | Before the first search, and after **New search** or `configure()` | The search box |
| `loading` | A request is running | "Searching…" (polite live region), `aria-busy` on the results, a **Cancel** button |
| `success` | Results | Count and the result list |
| `empty` | `OK` with no results | `labels.empty` |
| `partial` | `PARTIAL`, `UNAVAILABLE` with results, or `retrieval.degraded` | Results plus `labels.partial` |
| `clarification` | `CLARIFICATION_REQUIRED` | The questions, and each option as a button |
| `review` | `BLOCKED` / `REVIEW` | `labels.review` |
| `error` | HTTP or transport failure, or `UNAVAILABLE` without results | An alert with the server's safe message and status |
| `cancelled` | The user cancelled | An alert; the conversation reloads before the next turn |

**Out-of-order responses.** Each search, acceptance, `resume()`, `configure()`, **New search** and
removal starts a new generation and aborts the previous request. A response from an earlier
generation is discarded, so a slow old response can never replace newer results.

## Safety

- **Text only.** Server data, labels and suggestions are inserted with `textContent`. The only
  markup the component writes is its own static template.
- **Tested.** Product titles, suggestions and labels containing `<img onerror>` / `<b>` render as
  text.

## Accessibility (WCAG-oriented)

- **Structure.** A labelled text area, native buttons, a polite `role=status` for progress and
  counts, `role=alert` for errors, and a labelled results region with a list of articles.
- **Keyboard.**
  - **Tab** reaches the search box first (the shadow root delegates focus).
  - **Enter** searches; **Shift+Enter** inserts a new line.
  - **Escape** cancels a running search, and the server is told to stop.
  - Suggestions, options and alternatives are buttons in tab order.
- **Focus.**
  - While loading, the box is read-only rather than disabled, so focus is not lost.
  - After cancelling or **New search**, focus returns to the box.
  - After accepting an alternative, focus moves to the new result summary.
- **Direction.** `dir=auto` on text, and the host switches to RTL for Arabic- or Hebrew-script input.
- **Not yet done.** No automated WCAG audit (axe) runs yet. The browser tests check the accessible
  tree and keyboard-only operation.

## Tests

`qwenbridge-typescript-sdk/test/browser/*.mjs` run in headless Chromium against a local controlled
API. They serve the exact files the server ships. Coverage:
- declarative initialization with no framework, and no credential read from attributes;
- the stateless search API, plus Unicode and RTL input;
- suggestions and clarification options, operated by keyboard;
- every state, and the stale-response guard;
- Escape cancellation that reaches the server;
- the keyboard-only path and the accessible tree;
- lifecycle cleanup after removal;
- injection attempts.
