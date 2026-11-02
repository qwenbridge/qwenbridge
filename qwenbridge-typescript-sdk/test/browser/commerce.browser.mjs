import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const assets = new URL('../../../qwenbridge-server/src/main/resources/META-INF/resources/', import.meta.url);
let server, browser, base;
before(async () => {
  server = createServer(async (request, response) => {
    try {
      const path = decodeURIComponent(new URL(request.url, 'http://local').pathname); if (path.includes('..')) throw new Error('Invalid path');
      const file = path === '/fixture' ? null : path.endsWith('/') ? path + 'index.html' : path;
      const content = file ? await readFile(new URL('.' + file, assets)) : '<!doctype html><meta charset="utf-8"><script type="module" src="/web/web-component.js"></script><qwenbridge-search></qwenbridge-search>';
      response.setHeader('Content-Type', file?.endsWith('.js') ? 'text/javascript' : file?.endsWith('.css') ? 'text/css' : 'text/html');response.end(content);
    } catch { response.writeHead(404); response.end(); }
  }).listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve)); base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true });
});
after(async () => { await browser?.close(); if (server) await new Promise(resolve => server.close(resolve)); });
const response = (status='OK', hits=[{id:'shoe-good',score:1,document:{title:'کفش مشکی',price:99,currency:'EUR'}}]) => ({apiVersion:2,requestId:'request-1',status,translation:{version:1,original:'کفش مشکی زیر ۱۰۰ یورو',sourceLanguage:'fa',targetLanguage:'en',translated:'Black shoes under 100 EUR',constraintsPreserved:true,issues:[]},intent:'SEARCH',queryIR:{version:1,schemaId:'commerce',schemaVersion:1,tenant:'shop',constraints:[]},clarificationQuestions:status==='CLARIFICATION_REQUIRED'?['What is your maximum budget?']:[],results:{hits,totalHits:hits.length,tookMillis:2},confidence:{value:.8,method:'heuristic',calibrated:false,signals:{}},evidence:[{id:'evidence-1',source:'urn:test',claim:'Explicit price',spans:[]}],suggestions:[],reranking:'none',tookMs:10,timings:{analysis:5,execution:2},policies:[],lockedFields:[]});
const session = (last=null, revision=0) => ({id:'session-1',tenant:'shop',subject:'alice',backend:'opensearch',lockedFields:[],revision,last,createdAt:new Date().toISOString(),expiresAt:new Date(Date.now()+3600000).toISOString(),turns:[]});
async function fixture(t, handler, path='/fixture') {
  const context = await browser.newContext(); context.setDefaultTimeout(5000); const page=await context.newPage(); page.on("pageerror", e=>console.error("Browser error:",e.message)); t.after(async()=>{ await context.close(); });
  await page.route('**/api/v2/**', handler); await page.goto(base+path); await page.waitForFunction(()=>customElements.get('qwenbridge-search')); return page;
}
async function configure(page, transport='rest') { await page.locator('qwenbridge-search').evaluate((element,transport)=>element.configure({baseUrl:location.origin,bearerToken:'browser-test-only-credential',transport}),transport); }

test('RTL, keyboard, owned session continuation, no filter controls and safe product text', async t => {
  let turns=[]; const page=await fixture(t,async route=>{
    assert.equal(route.request().headers().authorization,'Bearer browser-test-only-credential');
    if(route.request().url().endsWith('/sessions')) return route.fulfill({json:session()});
    const body=route.request().postDataJSON(); turns.push(body); assert.equal(body.expectedRevision,turns.length-1);
    const result=response();result.results.hits[0].document.title='<img src=x onerror="window.injected=true">';
    await route.fulfill({json:session(result,turns.length)});
  });await configure(page);
  const input=page.getByRole('textbox');await input.fill('کفش مشکی زیر ۱۰۰ یورو');await input.dispatchEvent('input');
  assert.equal(await input.evaluate(element=>getComputedStyle(element).direction),'rtl');
  await input.press('Shift+Enter');assert.equal(turns.length,0);await input.press('Enter');await page.getByRole('heading').waitFor();
  assert.equal(await page.locator('select,[role=combobox],input[type=checkbox]').count(),0);
  assert.equal(await page.getByRole('heading').textContent(),'<img src=x onerror="window.injected=true">');assert.equal(await page.locator('qwenbridge-search img').count(),0);
  await input.fill('سایز ۴۲');await input.press('Enter');await page.waitForFunction(()=>document.querySelector('qwenbridge-search').dataset.state!=='loading');
  assert.equal(turns.length,2);assert.equal(await page.evaluate(()=>localStorage.length),0);assert.equal(await page.locator('qwenbridge-search').getAttribute('bearer-token'),null);
});
test('clarification and HTTP failures remain visible and usable', async t=>{
  let calls=0;const page=await fixture(t,async route=>{
    if(route.request().url().endsWith('/sessions'))return route.fulfill({json:session()});
    if(++calls===1)return route.fulfill({json:session(response('CLARIFICATION_REQUIRED',[]),1)});
    return route.fulfill({status:403,json:{message:'Denied'}});
  });await configure(page);await page.getByRole('textbox').fill('cheap shoes');await page.getByRole('textbox').press('Enter');await page.getByText('What is your maximum budget?').waitFor();
  await page.getByRole('textbox').fill('under 100 EUR');await page.getByRole('textbox').press('Enter');await page.getByRole('alert').waitFor();assert.match(await page.getByRole('alert').textContent(),/403/);assert.equal(await page.getByRole('textbox').isEnabled(),true);
});
test('zero-result alternative executes only after explicit acceptance', async t=>{
  let accepted=0;const page=await fixture(t,async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/sessions'))return route.fulfill({json:session()});
    if(path.endsWith('/turns'))return route.fulfill({json:session(response('OK',[]),1)});
    if(path.endsWith('/relaxations'))return route.fulfill({json:[{id:'offer-1',description:'Raise the budget to 110 EUR; preserve every other condition.',changedField:'price',status:'OFFERED',expiresAt:new Date(Date.now()+300000).toISOString()}]});
    assert.match(path,/session-1\/relaxations\/offer-1\/accept$/);assert.deepEqual(route.request().postDataJSON(),{expectedRevision:1});accepted++;return route.fulfill({json:session(response(),2)});
  });await configure(page);await page.getByRole('textbox').fill('shoes under 100 EUR');await page.getByRole('textbox').press('Enter');
  const offer=page.getByRole('button',{name:/Accept change:/});await offer.waitFor();assert.equal(accepted,0);await offer.click();await page.getByRole('heading',{name:'کفش مشکی'}).waitFor();assert.equal(accepted,1);
});
test('SSE terminal session is rendered and truncated streams show an error', async t=>{
  let count=0;const page=await fixture(t,async route=>{
    if(route.request().url().endsWith('/sessions'))return route.fulfill({json:session()});
    assert.match(route.request().url(),/turns\/stream$/);count++;
    const data={version:2,kind:count===1?'session.completed':'session.started',requestId:'stream-1',session:count===1?session(response(),1):null,message:''};
    return route.fulfill({contentType:'text/event-stream',body:`event:${data.kind}\ndata:${JSON.stringify(data)}\n\n`});
  });await configure(page,'sse');await page.getByRole('textbox').fill('black shoes');await page.getByRole('textbox').press('Enter');await page.getByRole('heading').waitFor();
  await page.getByRole('textbox').fill('size 42');await page.getByRole('textbox').press('Enter');await page.getByRole('alert').waitFor();assert.match(await page.getByRole('alert').textContent(),/terminal/);
});
test('Playground displays original, translation, IR, filters, decision, evidence, timings and products', async t=>{
  const page=await fixture(t,async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/sessions'))return route.fulfill({json:session()});
    if(path.endsWith('/debug'))return route.fulfill({json:{response:response(),compiledFilters:[{term:{tenantId:'shop'}}]}});
    return route.fulfill({json:session(response(),1)});
  },'/playground/');
  await page.locator('[name=token]').fill('browser-test-only-credential');await page.getByRole('button',{name:'Connect',exact:true}).click();assert.equal(await page.locator('[name=token]').inputValue(),'');
  await page.locator('qwenbridge-search').getByRole('textbox').fill('کفش مشکی');await page.locator('qwenbridge-search').getByRole('textbox').press('Enter');await page.getByRole('heading',{name:'Executed filter clauses'}).waitFor();
  for(const title of ['Original input','Translation','QueryIR','Decision','Confidence','Evidence','Stage timings (ms)','Products'])assert.equal(await page.getByRole('heading',{name:title,exact:true}).count(),1);
  await mkdir(new URL('../../target/',import.meta.url),{recursive:true});await page.screenshot({path:new URL('../../target/playground.png',import.meta.url).pathname,fullPage:true});
  await writeFile(new URL('../../target/browser-acceptance.json',import.meta.url),JSON.stringify({browser:await browser.version(),tests:5,merchantConnection:'local deterministic API fixture; live merchant acceptance is separate'},null,2));
});
test('Playground separates explicit from inferred input, shows the recorded plan and compares strategies honestly', async t=>{
  const planned=response();
  planned.queryIR.constraints=[{field:'brand',operator:'EQ',value:'Nike',evidence:{source:'original',start:0,end:4,quote:'کفش'}},{field:'currency',operator:'EQ',value:'EUR',evidence:{source:'default',start:0,end:0,quote:''}}];
  planned.retrieval={requested:'HYBRID',executed:'KEYWORD',reasons:['LEXICAL_NEED'],degradations:['vector:UNAVAILABLE'],degraded:true,removedByValidation:0,providers:['memory'],warnings:[],rankingPolicy:'qwenbridge-baseline@1.0',reranking:'none',outcome:'DEGRADED'};
  planned.confidence={value:0.42,method:'model-signal-min-v1',calibrated:false,signals:{}};
  const modes=[];
  const page=await fixture(t,async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/backends'))return route.fulfill({json:[{id:'memory',configured:true,modes:['KEYWORD'],selected:true}]});
    if(path.endsWith('/sessions'))return route.fulfill({json:session()});
    if(path.endsWith('/debug'))return route.fulfill({status:403,json:{status:403,message:'Denied'}});
    if(path.endsWith('/search')){const body=route.request().postDataJSON();modes.push(body.contract??body.mode);
      if(body.mode==='VECTOR'||body.mode==='HYBRID')return route.fulfill({status:422,json:{status:422,code:'VALIDATION_ERROR',category:'VALIDATION',message:`memory does not support ${body.mode}`}});
      return route.fulfill({json:response()});}
    return route.fulfill({json:session(planned,1)});
  },'/playground/');
  await page.getByRole('button',{name:'Connect',exact:true}).click();
  await page.locator('qwenbridge-search').getByRole('textbox').fill('کفش مشکی');await page.locator('qwenbridge-search').getByRole('textbox').press('Enter');
  await page.getByRole('heading',{name:'Constraints: explicit or inferred'}).waitFor();
  assert.match(await page.locator('li[data-origin=explicit]').textContent(),/explicit: “کفش”/);
  assert.match(await page.locator('li[data-origin=inferred]').textContent(),/inferred \(evidence source: default\)/);
  assert.equal(await page.getByText('DEGRADED').count(),1);
  assert.match(await page.getByText(/not a calibrated probability/).textContent(),/0\.42/);
  await page.getByRole('button',{name:'Compare retrieval strategies'}).click();
  await page.getByRole('table').waitFor();
  assert.deepEqual(modes,['KEYWORD','VECTOR','HYBRID','commerce-search/1.0']);
  assert.match(await page.getByRole('table').textContent(),/unavailable: memory does not support VECTOR \(HTTP 422\)/);
  assert.equal(await page.getByRole('row',{name:/shoe-good/}).getByRole('cell').allTextContents().then(c=>c.join('|')),'1|—|—|1');
});
