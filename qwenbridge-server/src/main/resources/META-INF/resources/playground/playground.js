import '../web/web-component.js';
import { CommerceClient } from '../web/commerce-client.js';

// QB-199 – QB-202: a developer view of what the server recorded for a search. Every value shown
// comes from the response (or a real follow-up execution); the page never invents evidence,
// explanations or results. All text is inserted with textContent.
const form = document.querySelector('#connection');
const search = document.querySelector('qwenbridge-search');
form.elements.base.value = location.origin;
let options = { baseUrl: location.origin };
let client = new CommerceClient(options);
let latest;

const el = (tag, text, attrs = {}) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; Object.assign(node, attrs); return node; };
const json = value => JSON.stringify(value ?? null, null, 2);

async function loadBackends() {
  const select = form.elements.backend;
  try {
    const headers = options.bearerToken ? { Authorization: `Bearer ${options.bearerToken}` } : {};
    const response = await fetch(new URL('/api/v2/backends', options.baseUrl), { headers });
    if (!response.ok) return;
    const backends = await response.json();
    const chosen = select.value; select.replaceChildren(el('option', 'Server default', { value: '' }));
    for (const backend of backends) {
      const label = `${backend.id} — ${backend.configured ? (backend.modes ?? []).join('/') : 'not configured'}`;
      select.append(el('option', label, { value: backend.id, disabled: !backend.configured }));
    }
    select.value = chosen;
  } catch { /* the list is optional; the server default still works */ }
}

form.addEventListener('submit', event => {
  event.preventDefault();
  options = {
    baseUrl: form.elements.base.value, bearerToken: form.elements.token.value || options.bearerToken,
    transport: form.elements.transport.value, mode: form.elements.mode.value,
    contract: form.elements.contract.value || undefined, backend: form.elements.backend.value || undefined
  };
  try {
    search.configure(options); client = new CommerceClient(options); form.elements.token.value = '';
    document.querySelector('#connection-status').textContent = 'Connected. Credential stays in memory for this page.';
    void loadBackends();
  } catch (error) { document.querySelector('#connection-status').textContent = error.message; }
});
void loadBackends();

function section(container, name, ...children) {
  const box = el('section'); box.append(el('h2', name), ...children); container.append(box); return box;
}
const raw = value => el('pre', typeof value === 'string' ? value : json(value), { dir: 'auto' });

/** QB-200: each constraint with where it came from. Explicit = a quote from the user's input. */
function constraintsView(response) {
  const list = el('ul');
  const original = response.translation?.original ?? '';
  for (const c of response.queryIR?.constraints ?? []) {
    const span = c.evidence;
    const explicit = span && span.source === 'original' && span.quote && original.includes(span.quote);
    const item = el('li');
    item.append(el('code', `${c.field} ${c.operator} ${c.value}`), el('span', explicit
      ? ` — explicit: “${span.quote}” (characters ${span.start}–${span.end} of the input)`
      : ` — inferred (${span ? `evidence source: ${span.source}` : 'no evidence span'})`));
    item.dataset.origin = explicit ? 'explicit' : 'inferred';
    list.append(item);
  }
  if (!list.childElementCount) list.append(el('li', 'No constraints were recorded for this response.'));
  const statuses = response.interpretation?.constraints;
  return statuses?.length ? [list, el('p', 'Schema mapping (QueryIR ' + response.interpretation.queryIrVersion + '):'), raw(statuses)] : [list];
}

/** QB-200: the recorded retrieval plan, or an explicit statement that none was recorded. */
function planView(response) {
  const r = response.retrieval;
  if (!r) return [el('p', 'No retrieval plan was recorded: this response used apiVersion 2 behaviour, where the client chooses the strategy. Choose the commerce-search/1.0 contract to see the planner’s decision.')];
  const facts = el('dl');
  for (const [term, value] of [['Requested', r.requested], ['Executed', r.executed], ['Outcome', r.outcome], ['Reasons', (r.reasons ?? []).join(', ') || '—'], ['Degradations', (r.degradations ?? []).join(', ') || 'none'], ['Providers', (r.providers ?? []).join(', ')], ['Ranking policy', r.rankingPolicy], ['Reranking', r.reranking], ['Removed by validation', String(r.removedByValidation)]])
    facts.append(el('dt', term), el('dd', String(value ?? '—')));
  return [facts];
}

/** QB-202: calibrated probabilities are labelled as such; everything else is a heuristic score. */
function confidenceView(confidence) {
  if (!confidence) return [el('p', 'No confidence was recorded.')];
  const kind = confidence.calibrated ? 'Calibrated probability' : 'Heuristic score — not a calibrated probability';
  const summary = el('p', `${kind}: ${confidence.value} (method ${confidence.method}).`);
  summary.dataset.calibrated = String(Boolean(confidence.calibrated));
  return [summary, raw(confidence)];
}

/** QB-201: the same query under every strategy, executed for real; failures are shown as such. */
async function compare(response, container) {
  const query = response.translation?.original; if (!query) return;
  const box = section(container, 'Strategy comparison');
  const status = el('p', 'Running the same query with each strategy…', { role: 'status' }); box.append(status);
  const strategies = [['Keyword', { mode: 'KEYWORD' }], ['Vector', { mode: 'VECTOR' }], ['Hybrid', { mode: 'HYBRID' }], ['Planned (commerce-search/1.0)', { contract: 'commerce-search/1.0', mode: null }]];
  const runs = [];
  for (const [name, extra] of strategies) {
    try {
      const result = await client.search({ query, backend: options.backend, ...extra });
      runs.push({ name, result, ids: result.results.hits.map(h => h.id), note: result.retrieval ? `executed ${result.retrieval.executed}${result.retrieval.degraded ? ' (degraded)' : ''}` : `status ${result.status}` });
    } catch (error) { runs.push({ name, ids: [], note: `unavailable: ${error.message}${error.status ? ` (HTTP ${error.status})` : ''}` }); }
  }
  if (latest !== response.requestId) return;
  const table = el('table'); const head = el('tr'); head.append(el('th', 'Result id', { scope: 'col' }));
  for (const run of runs) head.append(el('th', run.name, { scope: 'col' }));
  table.append(el('caption', 'Rank of each result per strategy (— = not returned)'), head);
  const notes = el('tr'); notes.append(el('th', 'Strategy metadata', { scope: 'row' }));
  for (const run of runs) notes.append(el('td', run.note));
  table.append(notes);
  for (const id of [...new Set(runs.flatMap(run => run.ids))]) {
    const row = el('tr'); row.append(el('th', id, { scope: 'row' }));
    for (const run of runs) { const rank = run.ids.indexOf(id); row.append(el('td', rank < 0 ? '—' : String(rank + 1))); }
    table.append(row);
  }
  status.textContent = `${runs.filter(r => !r.note.startsWith('unavailable')).length} of ${runs.length} strategies ran on ${options.backend ?? 'the default backend'}.`;
  box.append(table);
}

search.addEventListener('qwenbridge-result', async event => {
  const response = event.detail; latest = response.requestId;
  const container = document.querySelector('#inspection'); container.replaceChildren();
  section(container, 'Original input', raw(response.translation?.original));
  section(container, 'Translation', raw(response.translation));
  section(container, 'QueryIR', raw(response.queryIR));
  section(container, 'Constraints: explicit or inferred', ...constraintsView(response));
  section(container, 'Retrieval plan', ...planView(response));
  section(container, 'Decision', raw({ status: response.status, intent: response.intent, questions: response.clarificationQuestions, policies: response.policies }));
  section(container, 'Confidence', ...confidenceView(response.confidence));
  section(container, 'Evidence', raw(response.evidence));
  section(container, 'Stage timings (ms)', raw(response.timings));
  section(container, 'Products', el('p', 'Scores are backend relevance scores, not probabilities.'), raw(response.results));
  const button = el('button', 'Compare retrieval strategies', { type: 'button' });
  button.addEventListener('click', () => { button.disabled = true; void compare(response, container).finally(() => { button.disabled = false; }); });
  container.append(button);
  document.querySelector('#debug-status').textContent = 'Loading executed filters…';
  try { const debug = await client.debug(response.requestId); if (latest !== response.requestId) return; section(container, 'Executed filter clauses', raw(debug.compiledFilters)); document.querySelector('#debug-status').textContent = ''; }
  catch (error) { if (latest === response.requestId) document.querySelector('#debug-status').textContent = `Filter inspection unavailable: ${error.message}. The credential needs debug:read permission.`; }
});
