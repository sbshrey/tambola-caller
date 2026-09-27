// Explicit public-PC smoke test. Creates and deletes only its own two QA profiles.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { writeFile } from 'node:fs/promises';
const directory = 'https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json';
const directoryReply = await fetch(directory, {redirect:'error',signal:AbortSignal.timeout(15000)});
assert.equal(directoryReply.status,200);
const entry = await directoryReply.json();
assert.equal(entry.service,'tambola-together-public-beta-v1');
assert.ok(entry.expiresAt > Date.now());
assert.match(entry.origin,/^https:\/\/[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com$/);
const guests=[];
const checks=[];
async function api(path,method='GET',body,guest) {
  const response=await fetch(entry.origin+path, {method,redirect:'error',signal:AbortSignal.timeout(15000),
    headers:{...(body?{'content-type':'application/json'}:{}),...(guest?{authorization:`Bearer ${guest.token}`}:{})},
    ...(body?{body:JSON.stringify(body)}:{})});
  assert.ok(response.ok,`Public API returned ${response.status}`);
  return response.json();
}
try {
  assert.equal((await api('/health/ready')).protocolVersion,4);
  for(const displayName of ['Admission friends host QA','Admission friends guest QA'])
    guests.push(await api('/v1/guests','POST',{displayName}));
  for(const guest of guests) assert.equal((await api('/v1/wallet','GET',undefined,guest)).balance,1500);
  const create={id:randomUUID(),tickets:3,friendTable:true};
  const first=await api('/v1/matches','POST',create,guests[0]);
  assert.equal(first.snapshot.wallet.balance,1200);
  assert.equal(first.snapshot.coins.friendTable,true);
  assert.deepEqual(await api('/v1/matches','POST',create,guests[0]),first);
  const join={id:randomUUID(),tickets:2,friendTable:true,friendCode:first.snapshot.code};
  const second=await api('/v1/matches','POST',join,guests[1]);
  assert.equal(second.snapshot.roomId,first.snapshot.roomId);
  assert.equal(second.snapshot.coins.pool,500);
  assert.equal(second.snapshot.wallet.balance,1300);
  assert.equal(second.snapshot.options.computerPlayers,0);
  assert.deepEqual(await api('/v1/matches','POST',join,guests[1]),second);
  checks.push('normal published directory; trusted public HTTPS; two people; 3 and 2 tickets; 500-coin pool; exact purchase receipts');
  const page=await fetch(`${entry.origin}/friends/${first.snapshot.code}`,{redirect:'error',signal:AbortSignal.timeout(15000)});
  assert.equal(page.status,200);
  assert.ok((await page.text()).includes(`intent://friends/${first.snapshot.code}#Intent;scheme=tambola-beta;package=io.github.sbshrey.tambola.game.beta;`));
  checks.push('friend invitation landing targets the beta package and purchased table code');
  const path=`/v1/rooms/${first.snapshot.code}`;
  for(const guest of [...guests].reverse()) {
    const current=await api(path,'GET',undefined,guest);
    const leave={id:randomUUID(),expectedRevision:current.snapshot.revision,action:{type:'leave'}};
    const refund=await api(path+'/commands','POST',leave,guest);
    assert.equal(refund.snapshot.wallet.balance,1500);
    assert.deepEqual(await api(path+'/commands','POST',leave,guest),refund);
    assert.equal((await api('/v1/wallet','GET',undefined,guest)).balance,1500);
  }
  checks.push('both purchases fully refunded once; exact leave receipts; no game started');
} finally {
  const cleanup=await Promise.allSettled(guests.map(guest=>api('/v1/guests/me/delete','POST',{id:randomUUID()},guest)));
  assert.ok(cleanup.every(result=>result.status==='fulfilled'),'A QA profile deletion needs investigation');
}
checks.push('both temporary QA profiles deleted');
const evidence={passed:true,observedAt:new Date().toISOString(),origin:entry.origin,checks,
  scope:'Public HTTPS from this PC; no physical Android, mobile data, active calls or load acceptance'};
await writeFile(new URL('./public-friends.json',import.meta.url),JSON.stringify(evidence,null,2)+'\n');
console.log(JSON.stringify(evidence));
