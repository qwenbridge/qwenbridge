// QB-193 – QB-198: <qwenbridge-search> in a real browser (headless Chromium), served exactly as the
// server ships it (/web/), against a controlled local API. No Shopify, theme, framework or credential.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const assets = new URL('../../../qwenbridge-server/src/main/resources/META-INF/resources/', import.meta.url);
const pages = {
  '/plain': '<!doctype html><html lang="en"><meta charset="utf-8"><title>Plain page</title><main><h1>Library</h1><script type="module" src="/web/web-component.js"></script><qwenbridge-search base-url="/" mode="search" title-field="name" detail-fields="author, year" bearer-token="must-be-ignored"></qwenbridge-search></main></html>',
  '/manual': '<!doctype html><html lang="en"><meta charset="utf-8"><title>Manual</title><main><script type="module" src="/web/web-component.js"></script><qwenbridge-search></qwenbridge-search></main></html>'
};
let server, browser, base;
before(async () => {
  server = createServer(async (request, response) => {
    try {
      const path = decodeURIComponent(new URL(request.url, 'http://local').pathname); if (path.includes('..')) throw new Error('Invalid path');
      if (pages[path]) { response.setHeader('Content-Type', 'text/html'); return response.end(pages[path]); }
      response.setHeader('Content-Type', path.endsWith('.js') ? 'text/javascript' : 'text/css'); response.end(await readFile(new URL('.' + path, assets)));
    } catch { response.writeHead(404); response.end(); }
  }).listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve)); base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true });
});
after(async () => { await browser?.close(); if (server) await new Promise(resolve => server.close(resolve)); });

const book = (name, extra = {}) => ({ id: name.toLowerCase().replace(/\W+/g, '-'), score: 1, document: { name, author: 'Ursula K. Le Guin', year: 1969 }, ...extra });
const result = (overrides = {}) => ({ apiVersion: 2, requestId: 'r', status: 'OK', translation: null, intent: 'search', queryIR: null, clarificationQuestions: [], results: { hits: [book('The Left Hand of Darkness')], totalHits: 1, tookMillis: 1 }, confidence: { value: 0.9, method: 'test', calibrated: false, signals: {} }, evidence: [], suggestions: [], reranking: 'none', tookMs: 1, ...overrides });
const state = page => page.locator('qwenbridge-search').evaluate(element => element.dataset.state);
async function open(t, handler, path = '/plain') {
  const context = await browser.newContext(); context.setDefaultTimeout(5000); const page = await context.newPage();
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  t.after(async () => { await context.close(); assert.deepEqual(errors, [], 'no uncaught page errors'); });
  await page.route('**/api/v2/**', handler); await page.goto(base + path); await page.waitForFunction(() => customElements.get('qwenbridge-search'));
  return page;
}
const searchBox = page => page.getByRole('textbox', { name: 'Describe what you want' });

test('initializes declaratively without a framework and searches the public v2 API', async t => {
  const requests = [];
  const page = await open(t, async route => { requests.push({ url: route.request().url(), headers: route.request().headers(), body: route.request().postDataJSON() }); await route.fulfill({ json: result() }); });
  assert.equal(await state(page), 'initial');
  assert.equal(await page.evaluate(() => typeof window.React + typeof window.Vue + typeof window.Shopify), 'undefinedundefinedundefined');
  await searchBox(page).fill('ambiguous gender science fiction'); await searchBox(page).press('Enter');
  await page.getByRole('heading', { name: 'The Left Hand of Darkness' }).waitFor();
  assert.equal(new URL(requests[0].url).pathname, '/api/v2/search');
  assert.equal(requests[0].body.query, 'ambiguous gender science fiction');
  assert.match(requests[0].body.requestId, /^[A-Za-z0-9._-]+$/);
  assert.equal(requests[0].headers.authorization, undefined, 'credentials are never read from attributes');
  assert.equal(await page.locator('qwenbridge-search').getByText('Ursula K. Le Guin 1969').count(), 1);
  assert.equal(await state(page), 'success');
  const tree = await page.locator('qwenbridge-search').ariaSnapshot();
  for (const expected of ['textbox "Describe what you want"', 'button "Search"', 'button "New search"', 'region "Search results"', 'listitem', 'heading "The Left Hand of Darkness"'])
    assert.ok(tree.includes(expected), `accessible tree has ${expected}:\n${tree}`);
});

test('renders suggestions and clarification options as safe, keyboard-operable actions', async t => {
  const queries = [];
  const page = await open(t, async route => {
    const body = route.request().postDataJSON(); queries.push(body.query);
    if (queries.length === 1) return route.fulfill({ json: result({ status: 'CLARIFICATION_REQUIRED', results: { hits: [], totalHits: 0, tookMillis: 1 }, clarificationQuestions: ['Which <b>edition</b>?'], suggestions: ['le guin <img src=x onerror="window.injected=1">'], clarifications: [{ kind: 'MISSING_VALUE', field: 'format', question: 'Which format?', options: ['paperback'] }] }) });
    return route.fulfill({ json: result() });
  });
  await searchBox(page).fill('le guin'); await searchBox(page).press('Enter');
  await page.getByText('Which <b>edition</b>?').waitFor();
  assert.equal(await state(page), 'clarification');
  const suggestion = page.getByRole('button', { name: 'le guin <img src=x onerror="window.injected=1">' });
  assert.equal(await suggestion.count(), 1);
  assert.equal(await page.locator('qwenbridge-search img, qwenbridge-search b').count(), 0);
  assert.equal(await page.evaluate(() => window.injected), undefined);
  const option = page.getByRole('button', { name: 'le guin paperback' });
  await option.focus(); await page.keyboard.press('Enter');
  await page.getByRole('heading', { name: 'The Left Hand of Darkness' }).waitFor();
  assert.deepEqual(queries, ['le guin', 'le guin paperback']);
});

test('shows empty, partial, unavailable and error states accurately', async t => {
  const replies = [
    { json: result({ results: { hits: [], totalHits: 0, tookMillis: 1 } }) },
    { json: result({ retrieval: { requested: 'HYBRID', executed: 'KEYWORD', reasons: [], degradations: ['vector:UNAVAILABLE'], degraded: true, removedByValidation: 0, providers: [], warnings: [], rankingPolicy: 'p', reranking: 'none', outcome: 'DEGRADED' } }) },
    { json: result({ status: 'UNAVAILABLE', results: { hits: [], totalHits: 0, tookMillis: 1 } }) },
    { status: 503, json: { status: 503, code: 'DEPENDENCY_UNAVAILABLE', category: 'DEPENDENCY', retryable: false, message: 'A dependency is unavailable' } }
  ];
  const page = await open(t, async route => route.fulfill(replies.shift()));
  const run = async () => { await searchBox(page).fill('anything'); await searchBox(page).press('Enter'); await page.waitForFunction(() => document.querySelector('qwenbridge-search').dataset.state !== 'loading'); };
  await run(); assert.equal(await state(page), 'empty'); assert.equal(await page.getByText('No results match every condition.').count(), 1);
  await run(); assert.equal(await state(page), 'partial'); assert.match(await page.locator('qwenbridge-search').getByText(/may be incomplete/).textContent(), /^1 matching results/);
  await run(); assert.equal(await state(page), 'error'); assert.equal(await page.getByText('Search is temporarily unavailable. Please try again.').count(), 1);
  await run(); assert.equal(await state(page), 'error'); assert.equal(await page.getByRole('alert').textContent(), 'A dependency is unavailable (HTTP 503)');
  assert.equal(await searchBox(page).isEditable(), true);
});

test('a stale response never overwrites a newer search', async t => {
  let calls = 0;
  const page = await open(t, async route => {
    const call = ++calls;
    if (call === 1) { await new Promise(resolve => setTimeout(resolve, 800)); return route.fulfill({ json: result({ results: { hits: [book('Stale result')], totalHits: 1, tookMillis: 1 } }) }).catch(() => {}); }
    return route.fulfill({ json: result({ results: { hits: [book('Fresh result')], totalHits: 1, tookMillis: 1 } }) });
  });
  await searchBox(page).fill('first'); await searchBox(page).press('Enter');
  await page.getByRole('button', { name: 'Cancel' }).waitFor();
  await page.locator('qwenbridge-search').evaluate(element => element.configure({ baseUrl: location.origin, mode: 'search', titleField: 'name' }));
  await searchBox(page).fill('second'); await searchBox(page).press('Enter');
  await page.getByRole('heading', { name: 'Fresh result' }).waitFor();
  await page.waitForTimeout(1200);
  assert.equal(await page.getByRole('heading', { name: 'Stale result' }).count(), 0);
  assert.equal(await state(page), 'success');
});

test('works without a mouse: focus, Enter, Escape to cancel on the server, New search', async t => {
  const deletes = [];
  const page = await open(t, async route => {
    if (route.request().method() === 'DELETE') { deletes.push(new URL(route.request().url()).pathname); return route.fulfill({ json: { cancelled: true } }); }
    await new Promise(resolve => setTimeout(resolve, 3000)); return route.fulfill({ json: result() }).catch(() => {});
  });
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement?.shadowRoot?.activeElement?.id), 'query', 'the first Tab reaches the search box');
  await page.keyboard.type('slow query'); await page.keyboard.press('Enter');
  await page.waitForFunction(() => document.querySelector('qwenbridge-search').dataset.state === 'loading');
  assert.equal(await page.locator('qwenbridge-search [data-results]').getAttribute('aria-busy'), 'true');
  await page.keyboard.press('Escape');
  await page.getByRole('alert').waitFor();
  assert.equal(await state(page), 'cancelled');
  assert.equal(await page.evaluate(() => document.activeElement?.shadowRoot?.activeElement?.id), 'query', 'focus stays in the search box');
  await page.waitForFunction(() => true); await page.waitForTimeout(100);
  assert.equal(deletes.length, 1); assert.match(deletes[0], /^\/api\/v2\/requests\/[A-Za-z0-9._-]+$/);
  // Tab from the box reaches Search, then New search; activating it resets and refocuses the box.
  await page.keyboard.press('Tab'); await page.keyboard.press('Tab'); await page.keyboard.press('Enter');
  assert.equal(await state(page), 'initial');
  assert.equal(await page.evaluate(() => document.activeElement?.shadowRoot?.activeElement?.id), 'query');
});

test('cleans up when removed: in-flight work is cancelled and nothing renders afterwards', async t => {
  const deletes = [];
  const page = await open(t, async route => {
    if (route.request().method() === 'DELETE') { deletes.push(route.request().url()); return route.fulfill({ json: { cancelled: true } }); }
    await new Promise(resolve => setTimeout(resolve, 600)); return route.fulfill({ json: result() }).catch(() => {});
  }, '/manual');
  await page.locator('qwenbridge-search').evaluate(element => element.configure({ baseUrl: location.origin, mode: 'search', titleField: 'name', labels: { prompt: 'Find <b>books</b>' } }));
  assert.equal(await page.getByRole('textbox', { name: 'Find <b>books</b>' }).count(), 1, 'labels are text, never markup');
  await page.getByRole('textbox').fill('detached'); await page.getByRole('textbox').press('Enter');
  await page.waitForFunction(() => document.querySelector('qwenbridge-search').dataset.state === 'loading');
  const element = await page.evaluateHandle(() => { const e = document.querySelector('qwenbridge-search'); e.remove(); return e; });
  await page.waitForTimeout(900);
  assert.equal(await element.evaluate(e => e.shadowRoot.querySelectorAll('h3').length), 0, 'no late rendering into a removed element');
  assert.equal(deletes.length, 1, 'the server was asked to stop the request');
  await element.evaluate(e => document.querySelector('main').append(e));
  assert.equal(await page.getByRole('textbox').isEditable(), true);
});
