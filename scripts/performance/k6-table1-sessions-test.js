import http from 'k6/http';
import { check, fail } from 'k6';
const base=__ENV.BASE_URL||'http://localhost:8080';
const headers={'Content-Type':'application/json'};
if(__ENV.QWENBRIDGE_TEST_API_TOKEN)headers.Authorization=`Bearer ${__ENV.QWENBRIDGE_TEST_API_TOKEN}`;
export const options={scenarios:{conversations:{executor:'shared-iterations',vus:Number(__ENV.PERF_VUS||2),iterations:Number(__ENV.PERF_TOTAL_REQUESTS||20),maxDuration:'5m'}},thresholds:{http_req_failed:['rate==0'],checks:['rate==1'],http_req_duration:['p(95)<5000']}};
function request(path,body){const response=body===undefined?http.get(base+path,{headers,timeout:'120s'}):http.post(base+path,JSON.stringify(body),{headers,timeout:'120s'});if(!check(response,{'Table 1 HTTP 200':r=>r.status===200}))fail(`Table 1 HTTP ${response.status}`);return response;}
function result(session,id){if(!check(session,{'owned conversation result':s=>s.last?.status==='OK'&&s.last.results.hits.length===1&&s.last.results.hits[0].id===id}))fail('Conversation violated expected product constraints');}
export default function(){
  let session=request('/api/v2/sessions',{lockedFields:['price']}).json();
  session=request(`/api/v2/sessions/${session.id}/turns`,{query:'black Nike shoes size 42 under 100 EUR, not refurbished, in stock',expectedRevision:0}).json();result(session,'shoe-good');
  const sse=request(`/api/v2/sessions/${session.id}/turns/stream`,{query:'same shoes but size 41',expectedRevision:1});
  const events=sse.body.split('\n').filter(line=>line.startsWith('data:')).map(line=>JSON.parse(line.slice(5).trim()));
  check(events,{'finite session SSE completed':es=>es[0]?.kind==='session.started'&&es[es.length-1]?.kind==='session.completed'});
  session=events[events.length-1].session;result(session,'shoe-size');
  let zero=request('/api/v2/sessions',{}).json();
  zero=request(`/api/v2/sessions/${zero.id}/turns`,{query:'black Nike shoes size 42 under 90.50 EUR, not refurbished, in stock',expectedRevision:0}).json();
  if(!check(zero,{'zero result kept explicit conditions':s=>s.last?.status==='OK'&&s.last.results.totalHits===0}))fail('Unexpected zero-result fixture');
  const offers=request(`/api/v2/sessions/${zero.id}/relaxations`).json();const budget=offers.find(p=>p.changedField==='price');if(!budget)fail('Missing explicit budget proposal');
  zero=request(`/api/v2/sessions/${zero.id}/relaxations/${budget.id}/accept`,{expectedRevision:1}).json();result(zero,'shoe-good');
  const saved=request(`/api/v2/sessions/${zero.id}`).json();check(saved,{'accepted consent persisted':s=>s.revision===2&&s.last.queryIR.constraints.some(c=>c.field==='condition'&&c.operator==='NE')});
}
