#!/usr/bin/env bash
# Owned conversations over REST and SSE, explicit consent for relaxations, edition and backend
# metadata and the Playground assets, against the running Compose application.

sessions_runtime_validation() {
  BASE_URL="${BASE_URL}" QWENBRIDGE_TEST_API_TOKEN="${QWENBRIDGE_TEST_API_TOKEN:-}" \
    node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
import { CommerceClient } from './qwenbridge-typescript-sdk/dist/commerce-client.js';
const client=new CommerceClient({baseUrl:process.env.BASE_URL,bearerToken:process.env.QWENBRIDGE_TEST_API_TOKEN||undefined});
const request=async(path,body)=>{
  const headers={'Content-Type':'application/json'};if(process.env.QWENBRIDGE_TEST_API_TOKEN)headers.Authorization=`Bearer ${process.env.QWENBRIDGE_TEST_API_TOKEN}`;
  const response=await fetch(new URL(path,process.env.BASE_URL),{method:body===undefined?'GET':'POST',headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(120000)});assert.equal(response.status,200,`${path}: ${response.status}`);return response.json();
};
let session=await client.createSession({lockedFields:['price']});
session=await client.turn(session.id,{query:'black Nike shoes size 42 under 100 EUR, not refurbished, in stock',expectedRevision:0});
assert.equal(session.last.status,'OK');assert.deepEqual(session.last.results.hits.map(h=>h.id),['shoe-good']);assert(session.last.lockedFields.includes('price'));
let terminal;
await client.streamTurn(session.id,{query:'same shoes but size 41',expectedRevision:session.revision},event=>{if(event.kind==='session.completed')terminal=event.session;if(event.kind==='session.failed')throw new Error(event.message);});
assert.equal(terminal.revision,2);assert.deepEqual(terminal.last.results.hits.map(h=>h.id),['shoe-size']);assert(terminal.last.queryIR.constraints.some(c=>c.field==='price'&&c.value==='100'));
const reconnected=await client.session(session.id);assert.equal(reconnected.revision,2);
let zero=await client.createSession();
zero=await client.turn(zero.id,{query:'black Nike shoes size 42 under 90.50 EUR, not refurbished, in stock',expectedRevision:0});
assert.equal(zero.last.status,'OK');assert.equal(zero.last.results.totalHits,0);
const proposals=await client.proposals(zero.id);const budget=proposals.find(p=>p.changedField==='price');assert(budget);assert.equal((await client.session(zero.id)).revision,1);
zero=await client.accept(zero.id,budget.id,zero.revision);assert.equal(zero.revision,2);assert.deepEqual(zero.last.results.hits.map(h=>h.id),['shoe-good']);assert(zero.last.queryIR.constraints.some(c=>c.field==='condition'&&c.operator==='NE'));
const editions=await request('/api/v2/platform/editions');assert(editions.COMMUNITY.includes('sessions'));assert(editions.PROFESSIONAL.includes('external-packs'));assert(!editions.ENTERPRISE.includes('postgresql'));
const license=await request('/api/v2/platform/license');assert(license.features.includes('search'));
const backends=await request('/api/v2/backends');assert.deepEqual(backends.map(b=>b.id).sort(),['memory','opensearch']);assert.equal(backends.find(b=>b.id==='memory').configured,true);assert.deepEqual(backends.find(b=>b.id==='memory').modes,['KEYWORD']);
const asset=await fetch(new URL('/playground/',process.env.BASE_URL));assert.equal(asset.status,200);assert((await asset.text()).includes('qwenbridge-search'));
console.log('PASS: persisted REST/SSE turns, retained conditions, explicit budget consent, edition/backend metadata and Playground assets');
NODE
}
