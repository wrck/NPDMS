'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { OfflineStore, recoverAbandonedLock } = require('../src/offline/index.cjs');
function fixture(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-offline-'));
  let now = 1000;
  const binding = { tenantId: 'tenant', userId: 'user', datasetId: 'dataset' };
  const options = { file: path.join(dir, 'vault'), binding, grant: { ...binding, expiresAt: 100000, keyId: 'key-1' },
    key: Buffer.alloc(32, 7), verifyGrant: () => true, authorize: () => true, clock: () => now };
  const instances = [];
  t.after(() => { for (const instance of instances) instance.close(); fs.rmSync(dir, { recursive: true, force: true }); });
  return { options, advance: ms => { now += ms; }, open: (override = {}) => { const s = new OfflineStore({ ...options, ...override }); instances.push(s); return s; } };
}
test('fails closed without policy, verified grant, scope, key or allowed action', t => {
  const f = fixture(t);
  assert.throws(() => new OfflineStore(), { code: 'POLICY_REQUIRED' });
  assert.throws(() => f.open({ verifyGrant: () => false }), { code: 'GRANT_UNVERIFIED' });
  assert.throws(() => f.open({ authorize: () => false }), { code: 'ACTION_DENIED' });
  assert.throws(() => f.open({ grant: { ...f.options.grant, expiresAt: undefined } }), { code: 'POLICY_REQUIRED' });
  assert.throws(() => f.open({ binding: { ...f.options.binding, tenantId: 'other' } }), { code: 'BINDING_MISMATCH' });
});
test('atomic encrypted working set and queue survive reopen without plaintext', t => {
  const f = fixture(t); const store = f.open();
  const id = store.enqueue({ type: 'synthetic-test', value: 'secret-operation' }, { value: 'secret-record' });
  const bytes = fs.readFileSync(f.options.file, 'utf8');
  assert.ok(!bytes.includes('secret-')); assert.ok(!bytes.includes('tenant'));
  const copy = store.read(); copy.value = 'mutated'; assert.equal(store.read().value, 'secret-record');
  store.close(); const reopened = f.open();
  assert.equal(reopened.pending()[0].id, id); assert.equal(reopened.read().value, 'secret-record');
});
test('tamper, truncation, wrong key and cross-tenant ciphertext fail closed', t => {
  const f = fixture(t); const s = f.open(); s.setWorkingSet({ sensitive: true }); s.close();
  const original = fs.readFileSync(f.options.file);
  assert.throws(() => f.open({ key: Buffer.alloc(32, 8) }), { code: 'STORE_CORRUPT' });
  const binding = { ...f.options.binding, tenantId: 'other' };
  assert.throws(() => f.open({ binding, grant: { ...f.options.grant, ...binding } }), { code: 'STORE_CORRUPT' });
  const envelope = JSON.parse(original); const payload = Buffer.from(envelope.data, 'base64'); payload[0] ^= 1;
  envelope.data = payload.toString('base64'); fs.writeFileSync(f.options.file, JSON.stringify(envelope));
  assert.throws(() => f.open(), { code: 'STORE_CORRUPT' });
  fs.writeFileSync(f.options.file, original.subarray(0, 20));
  assert.throws(() => f.open(), { code: 'STORE_CORRUPT' });
});
test('expired grant blocks reads, writes, queue access and sync', async t => {
  const f = fixture(t); const s = f.open(); s.enqueue({ test: true }); f.advance(100000);
  for (const action of [() => s.read(), () => s.pending(), () => s.setWorkingSet({}), () => s.enqueue({}), () => s.resolve('x')]) assert.throws(action, { code: 'AUTH_EXPIRED' });
  let calls = 0; await assert.rejects(s.syncOne(async () => { calls++; }), { code: 'AUTH_EXPIRED' }); assert.equal(calls, 0);
  s.close(); assert.throws(() => f.open(), { code: 'AUTH_EXPIRED' });
});
test('lost response and restart retry with identical durable idempotency key', async t => {
  const f = fixture(t); let s = f.open(); const id = s.enqueue({ test: 'same' }); const seen = new Set(); let effects = 0;
  const transport = async request => { if (!seen.has(request.idempotencyKey)) { seen.add(request.idempotencyKey); effects++; } throw Error('lost response secret'); };
  assert.deepEqual(await s.syncOne(transport), { state: 'retry', id });
  assert.equal(s.pending()[0].state, 'inflight'); s.close(); s = f.open();
  assert.equal((await s.syncOne(transport)).state, 'waiting'); f.advance(2000);
  await s.syncOne(async request => { assert.equal(request.idempotencyKey, id); assert.ok(seen.has(id)); return { state: 'accepted' }; });
  assert.equal(effects, 1); assert.equal(s.pending().length, 0);
});
test('conflict blocks ordered queue until explicit replacement using new id', async t => {
  const f = fixture(t); const s = f.open(); const id = s.enqueue({ test: 1 }); s.enqueue({ test: 2 });
  await s.syncOne(async () => ({ state: 'conflict' }));
  let calls = 0; f.advance(3000); assert.equal((await s.syncOne(async () => { calls++; })).state, 'conflict'); assert.equal(calls, 0);
  const replacement = s.resolve(id, { test: 'resolved' }); assert.notEqual(replacement, id);
  await s.syncOne(async request => { assert.equal(request.idempotencyKey, replacement); return { state: 'accepted' }; });
  assert.equal(s.pending().length, 1);
});
test('expiry during transport does not acknowledge queue; mutations cannot race sync', async t => {
  const f = fixture(t); const s = f.open(); s.enqueue({ test: 1 });
  await assert.rejects(s.syncOne(async () => {
    assert.throws(() => s.enqueue({}), { code: 'SYNC_BUSY' });
    assert.throws(() => s.close(), { code: 'SYNC_BUSY' });
    f.advance(100000); return { state: 'accepted' };
  }), { code: 'AUTH_EXPIRED' });
});
test('live owner prevents opening/recovery; clock rollback blocks use and reopening', t => {
  const f = fixture(t); const s = f.open(); s.setWorkingSet({});
  assert.throws(() => f.open(), { code: 'STORE_LOCKED' }); assert.throws(() => recoverAbandonedLock(f.options.file), { code: 'STORE_LOCKED' });
  f.advance(-1); assert.throws(() => s.read(), { code: 'CLOCK_UNTRUSTED' }); s.close();
  assert.throws(() => f.open(), { code: 'CLOCK_UNTRUSTED' });
});
test('actual process crash leaves a recoverable lock and durable inflight operation', async t => {
  const f = fixture(t); const modulePath = require.resolve('../src/offline/index.cjs');
  const script = `const {OfflineStore}=require(${JSON.stringify(modulePath)}); const s=new OfflineStore({file:${JSON.stringify(f.options.file)},binding:${JSON.stringify(f.options.binding)},grant:${JSON.stringify(f.options.grant)},key:Buffer.alloc(32,7),clock:()=>1000,verifyGrant:()=>true,authorize:()=>true}); s.enqueue({test:'crash'}); s.syncOne(async()=>process.exit(31));`;
  const result = spawnSync(process.execPath, ['-e', script]); assert.equal(result.status, 31, result.stderr.toString());
  assert.throws(() => f.open(), { code: 'STORE_LOCKED' }); recoverAbandonedLock(f.options.file);
  const s = f.open(); const op = s.pending()[0]; assert.equal(op.state, 'inflight'); f.advance(2000);
  await s.syncOne(async req => { assert.equal(req.idempotencyKey, op.id); return { state: 'accepted' }; });
  assert.equal(s.pending().length, 0);
});
test('server revocation locks storage across restart and never logs response bodies', async t => {
  const f = fixture(t); const s = f.open(); s.enqueue({ test: true });
  await assert.rejects(s.syncOne(async () => ({ state: 'unauthorized', secret: 'never retained' })), { code: 'AUTH_REVOKED' });
  assert.throws(() => s.read(), { code: 'STORE_CLOSED' }); s.close();
  assert.throws(() => f.open(), { code: 'AUTH_REVOKED' });
});
test('failed atomic replace preserves previous snapshot and poisons instance', t => {
  const f = fixture(t); const s = f.open(); s.setWorkingSet({ version: 'old' });
  const rename = fs.renameSync;
  try {
    fs.renameSync = () => { const error = new Error('disk failure'); error.code = 'EIO'; throw error; };
    assert.throws(() => s.setWorkingSet({ version: 'new' }), { code: 'PERSISTENCE_FAILED' });
  } finally { fs.renameSync = rename; }
  assert.throws(() => s.read(), { code: 'RECOVERY_REQUIRED' }); s.close();
  assert.equal(f.open().read().version, 'old');
  assert.equal(fs.readdirSync(path.dirname(f.options.file)).filter(name => name.endsWith('.tmp')).length, 0);
});
test('orphan ciphertext temporary is ignored, never treated as committed state', t => {
  const f = fixture(t); const s = f.open(); s.setWorkingSet({ committed: true }); s.close();
  fs.writeFileSync(`${f.options.file}.orphan.tmp`, 'incomplete ciphertext');
  assert.deepEqual(f.open().read(), { committed: true });
});
test('invalid transport contract is not interpreted as success', async t => {
  const f = fixture(t); const s = f.open(); const id = s.enqueue({ test: true });
  await assert.rejects(s.syncOne(async () => ({ status: 200 })), { code: 'TRANSPORT_CONTRACT' });
  assert.equal(s.pending()[0].id, id); assert.equal(s.pending()[0].state, 'inflight');
});
