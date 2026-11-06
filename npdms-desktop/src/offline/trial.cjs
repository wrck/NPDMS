'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { OfflineStore, OfflineError } = require('./index.cjs');
const TTL = 15 * 60 * 1000;
const LABEL = 'TEST-ONLY 15-minute synthetic grant. No production authorization.';
const NOTICE = 'Synthetic offline trial only. Simulated synchronization never contacts NPDMS or writes business records.';
const BINDING = Object.freeze({ tenantId: 'npdms-synthetic-trial-v1', userId: 'synthetic-test-user', datasetId: 'synthetic-drafts-v1' });
const FIXTURES = Object.freeze({ 'draft-alpha': 'Synthetic draft Alpha', 'draft-beta': 'Synthetic draft Beta' });
const flags = () => ({ synthetic: true, trialOnly: true, businessAuthorized: false, externalNetwork: false, notice: NOTICE, grantLabel: LABEL });
const fail = code => { throw new OfflineError(code); };
function exact(value, allowed) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Object.getPrototypeOf(value) !== Object.prototype ||
      Object.keys(value).some(k => !allowed.includes(k))) fail('TRIAL_INPUT_REJECTED');
}
function validOperation(op) {
  return op && Object.keys(op).sort().join(',') === 'draftId,fixtureId,kind,scenario' && op.kind === 'synthetic-draft' &&
    /^[0-9a-f-]{36}$/.test(op.draftId) && Object.hasOwn(FIXTURES, op.fixtureId) && ['accepted', 'retry', 'conflict'].includes(op.scenario);
}
function createTrialSession(options) {
  exact(options, ['directory', 'key', 'clock']);
  const { directory, key, clock = Date.now } = options;
  if (typeof directory !== 'string' || !directory || !Buffer.isBuffer(key) || key.length !== 32 || typeof clock !== 'function') fail('TRIAL_INPUT_REJECTED');
  const now = clock();
  if (!Number.isSafeInteger(now) || now < 0 || now > Number.MAX_SAFE_INTEGER - TTL) fail('CLOCK_UNTRUSTED');
  // The HMAC key is domain-separated from the AES key. Only public test lease
  // timestamps and an authentication tag are persisted outside the encrypted store.
  const leaseKey = crypto.createHmac('sha256', key).update('npdms:synthetic-trial:lease:v1').digest();
  const mac = value => crypto.createHmac('sha256', leaseKey).update(JSON.stringify(value)).digest('hex');
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const file = path.join(directory, 'synthetic-trial-v1.vault');
  const leaseFile = path.join(directory, 'synthetic-trial-v1.lease');
  let lease;
  try {
    if (!fs.existsSync(leaseFile)) {
      if (fs.existsSync(file)) fail('TRIAL_LEASE_MISSING');
      const value = { version: 1, issuedAt: now, expiresAt: now + TTL, label: LABEL };
      let fd;
      try {
        fd = fs.openSync(leaseFile, 'wx', 0o600);
        fs.writeFileSync(fd, JSON.stringify({ value, mac: mac(value) })); fs.fsyncSync(fd);
      } finally { if (fd !== undefined) fs.closeSync(fd); }
    }
    if (fs.statSync(leaseFile).size > 4096) fail('TRIAL_LEASE_INVALID');
    const saved = JSON.parse(fs.readFileSync(leaseFile, 'utf8'));
    if (!saved.value || typeof saved.mac !== 'string' || !/^[a-f0-9]{64}$/.test(saved.mac) ||
        !crypto.timingSafeEqual(Buffer.from(saved.mac, 'hex'), Buffer.from(mac(saved.value), 'hex'))) fail('TRIAL_LEASE_INVALID');
    lease = saved.value;
    if (lease.version !== 1 || lease.label !== LABEL || !Number.isSafeInteger(lease.issuedAt) ||
        !Number.isSafeInteger(lease.expiresAt) || lease.expiresAt - lease.issuedAt !== TTL || now < lease.issuedAt) fail('TRIAL_LEASE_INVALID');
  } catch (error) {
    if (error instanceof OfflineError) throw error;
    fail('TRIAL_LEASE_INVALID');
  } finally { leaseKey.fill(0); }
  let closed = false; let store; let lastNow = now; let busy = false;
  function check() {
    if (closed) fail('STORE_CLOSED');
    const time = clock();
    if (!Number.isSafeInteger(time) || time < lastNow) fail('CLOCK_UNTRUSTED');
    lastNow = time;
    if (time >= lease.expiresAt) fail('AUTH_EXPIRED');
  }
  const grant = { ...BINDING, keyId: 'synthetic-trial-key-v1', expiresAt: lease.expiresAt, label: LABEL };
  if (now < lease.expiresAt) {
    store = new OfflineStore({ file, key, clock, binding: BINDING, grant,
      verifyGrant: candidate => JSON.stringify(candidate) === JSON.stringify(grant),
      authorize: ({ action, operation }) => {
        if (['enqueue', 'send'].includes(action)) return validOperation(operation);
        if (action === 'resolveConflict') return operation === undefined || validOperation(operation);
        return ['open', 'read', 'readQueue', 'writeWorkingSet', 'persist', 'sync'].includes(action);
      } });
    try { if (store.read() === null) store.setWorkingSet({ synthetic: true, drafts: [] }); }
    catch (error) { store.close(); throw error; }
  }
  function mutationCheck() { check(); if (busy) fail('SYNC_BUSY'); }
  function status() {
    let state = 'active'; let code = null;
    try { check(); } catch (error) { state = error.code === 'AUTH_EXPIRED' ? 'expired' : 'blocked'; code = error.code; }
    return { ...flags(), state, code, expiresAt: lease.expiresAt, pendingCount: state === 'active' ? store.pending().length : null };
  }
  return Object.freeze({
    status,
    list() {
      check();
      return { ...flags(), drafts: store.read().drafts, queue: store.pending().map(op => ({ id: op.id, draftId: op.operation.draftId,
        scenario: op.operation.scenario, state: op.state, attempts: op.attempts, nextAttemptAt: op.nextAttemptAt })) };
    },
    addDraft(input = {}) {
      mutationCheck(); exact(input, ['fixtureId', 'scenario']);
      const { fixtureId = 'draft-alpha', scenario = 'accepted' } = input;
      if (!Object.hasOwn(FIXTURES, fixtureId) || !['accepted', 'retry', 'conflict'].includes(scenario)) fail('TRIAL_INPUT_REJECTED');
      const data = store.read(); if (data.drafts.length >= 100) fail('TRIAL_CAPACITY_EXCEEDED');
      const draft = { id: crypto.randomUUID(), fixtureId, title: FIXTURES[fixtureId], synthetic: true, kind: 'test-draft' };
      data.drafts.push(draft);
      const id = store.enqueue({ kind: 'synthetic-draft', draftId: draft.id, fixtureId, scenario }, data);
      return { ...flags(), id, draft };
    },
    async syncSimulated() {
      mutationCheck(); busy = true;
      try {
        const first = store.pending()[0];
        const result = await store.syncOne(async request => {
          if (!validOperation(request.operation)) fail('TRIAL_INPUT_REJECTED');
          const scenario = request.operation.scenario;
          return { state: scenario === 'retry' ? (first.attempts === 0 ? 'retry' : 'accepted') : scenario };
        });
        return { ...flags(), ...result };
      } finally { busy = false; }
    },
    resolveConflict(input) {
      mutationCheck(); exact(input, ['id', 'action']);
      if (typeof input.id !== 'string' || !['retryAccepted', 'discard'].includes(input.action)) fail('TRIAL_INPUT_REJECTED');
      const op = store.pending().find(item => item.id === input.id);
      if (!op || op.state !== 'conflict') fail('NOT_BLOCKED');
      const id = store.resolve(input.id, input.action === 'discard' ? undefined : { ...op.operation, scenario: 'accepted' });
      return { ...flags(), id, action: input.action };
    },
    close() { if (busy) fail('SYNC_BUSY'); if (store) store.close(); closed = true; }
  });
}
module.exports = { createTrialSession };
