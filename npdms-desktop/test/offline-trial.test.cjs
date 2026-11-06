'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createTrialSession } = require('../src/offline/trial.cjs');
function fixture(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-trial-'));
  const key = crypto.randomBytes(32); let now = 100000; const sessions = [];
  t.after(() => { for (const session of sessions) session.close(); key.fill(0); fs.rmSync(directory, { recursive: true, force: true }); });
  const options = { directory, key, clock: () => now };
  return { directory, options, advance: ms => { now += ms; }, open: () => { const s = createTrialSession(options); sessions.push(s); return s; } };
}
test('synthetic fixtures are encrypted, scoped and explicitly never business authorized', t => {
  const f = fixture(t); const s = f.open(); const result = s.addDraft();
  assert.equal(result.synthetic, true); assert.equal(result.businessAuthorized, false); assert.equal(result.externalNetwork, false);
  assert.match(result.grantLabel, /TEST-ONLY 15-minute/); assert.match(result.notice, /never contacts NPDMS/);
  assert.equal(s.list().drafts[0].title, 'Synthetic draft Alpha');
  assert.equal(s.status().pendingCount, 1);
  const ciphertext = fs.readFileSync(path.join(f.directory, 'synthetic-trial-v1.vault'), 'utf8');
  assert.ok(!ciphertext.includes('Synthetic draft Alpha')); assert.ok(!ciphertext.includes(result.id));
  assert.ok(!JSON.stringify(s.status()).includes(f.options.key.toString('hex')));
  const copy = s.list(); copy.drafts[0].title = 'changed'; assert.equal(s.list().drafts[0].title, 'Synthetic draft Alpha');
});
test('retry survives restart with original operation ID and then simulates acceptance', async t => {
  const f = fixture(t); let s = f.open(); const { id } = s.addDraft({ fixtureId: 'draft-beta', scenario: 'retry' });
  assert.equal((await s.syncSimulated()).state, 'retry'); s.close(); s = f.open();
  assert.equal(s.list().queue[0].id, id); assert.equal(s.list().queue[0].attempts, 1);
  assert.equal((await s.syncSimulated()).state, 'waiting'); f.advance(1000);
  const accepted = await s.syncSimulated(); assert.equal(accepted.id, id); assert.equal(accepted.state, 'accepted');
  assert.equal(s.status().pendingCount, 0); assert.equal(s.list().drafts.length, 1);
  s.close(); s = f.open(); assert.equal(s.list().queue.length, 0); assert.equal(s.list().drafts.length, 1);
});
test('conflict persists across restart and blocks later drafts until explicit resolution', async t => {
  const f = fixture(t); let s = f.open(); const { id } = s.addDraft({ scenario: 'conflict' }); s.addDraft();
  assert.equal((await s.syncSimulated()).state, 'conflict'); s.close(); s = f.open();
  f.advance(5000); assert.equal((await s.syncSimulated()).state, 'conflict'); assert.equal(s.list().queue.length, 2);
  const resolved = s.resolveConflict({ id, action: 'retryAccepted' }); assert.notEqual(resolved.id, id);
  assert.equal((await s.syncSimulated()).state, 'accepted'); assert.equal((await s.syncSimulated()).state, 'accepted');
  assert.equal((await s.syncSimulated()).state, 'empty');
});
test('explicit conflict discard removes only the queued test operation', async t => {
  const f = fixture(t); const s = f.open(); const { id } = s.addDraft({ scenario: 'conflict' });
  await s.syncSimulated(); assert.equal(s.resolveConflict({ id, action: 'discard' }).id, null);
  assert.equal(s.list().queue.length, 0); assert.equal(s.list().drafts.length, 1);
});
test('15-minute expiry blocks data access and does not renew after restart', async t => {
  const f = fixture(t); let s = f.open(); s.addDraft(); const expiresAt = s.status().expiresAt;
  f.advance(15 * 60 * 1000);
  assert.equal(s.status().state, 'expired'); assert.equal(s.status().pendingCount, null);
  assert.throws(() => s.list(), { code: 'AUTH_EXPIRED' }); assert.throws(() => s.addDraft(), { code: 'AUTH_EXPIRED' });
  assert.throws(() => s.resolveConflict({ id: 'x', action: 'discard' }), { code: 'AUTH_EXPIRED' });
  await assert.rejects(s.syncSimulated(), { code: 'AUTH_EXPIRED' });
  s.close(); s = f.open(); assert.equal(s.status().expiresAt, expiresAt); assert.equal(s.status().state, 'expired');
  assert.throws(() => s.list(), { code: 'AUTH_EXPIRED' });
});
test('credential, endpoint, identity and arbitrary record input are rejected', t => {
  const f = fixture(t);
  for (const extra of [{ token: 'real-token' }, { endpoint: 'https://example.test' }, { tenantId: 'real-tenant' }, { transport: () => {} }]) {
    assert.throws(() => createTrialSession({ ...f.options, ...extra }), { code: 'TRIAL_INPUT_REJECTED' });
  }
  const s = f.open();
  for (const input of [{ title: 'real case' }, { fixtureId: 'real-record' }, { scenario: 'real-send' }, { password: 'secret' }, { fixtureId: '__proto__' }]) {
    assert.throws(() => s.addDraft(input), { code: 'TRIAL_INPUT_REJECTED' });
  }
  assert.equal(s.list().drafts.length, 0);
});
test('tampered test lease, wrong key, missing lease and corrupt vault fail closed', t => {
  const f = fixture(t); const s = f.open(); s.addDraft(); s.close();
  assert.throws(() => createTrialSession({ ...f.options, key: crypto.randomBytes(32) }), { code: 'TRIAL_LEASE_INVALID' });
  const leasePath = path.join(f.directory, 'synthetic-trial-v1.lease'); const original = fs.readFileSync(leasePath);
  const changed = JSON.parse(original); changed.value.expiresAt += 1000; fs.writeFileSync(leasePath, JSON.stringify(changed));
  assert.throws(() => f.open(), { code: 'TRIAL_LEASE_INVALID' }); fs.unlinkSync(leasePath);
  assert.throws(() => f.open(), { code: 'TRIAL_LEASE_MISSING' }); fs.writeFileSync(leasePath, original);
  fs.writeFileSync(path.join(f.directory, 'synthetic-trial-v1.vault'), 'bad ciphertext');
  assert.throws(() => f.open(), { code: 'STORE_CORRUPT' });
});
test('clock rollback and overlapping mutation fail closed', async t => {
  const f = fixture(t); const s = f.open(); s.addDraft();
  const syncing = s.syncSimulated(); assert.throws(() => s.addDraft(), { code: 'SYNC_BUSY' }); await syncing;
  f.advance(-1); assert.equal(s.status().code, 'CLOCK_UNTRUSTED'); assert.throws(() => s.list(), { code: 'CLOCK_UNTRUSTED' });
});
