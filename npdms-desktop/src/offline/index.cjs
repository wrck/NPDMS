'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const owners = new Set();
const MAX_BYTES = 8 * 1024 * 1024;
const MAX_OPERATIONS = 1000;
const clone = value => JSON.parse(JSON.stringify(value));
class OfflineError extends Error {
  constructor(code) { super(code); this.name = 'OfflineError'; this.code = code; }
}
const fail = code => { throw new OfflineError(code); };

// Main-process only. The caller must obtain a trusted, verified grant and key;
// no grant format, TTL, business permissions or transport endpoint is inferred.
class OfflineStore {
  #file; #lock; #binding; #grant; #key; #authorize; #clock; #lastNow = 0;
  #state; #closed = false; #busy = false; #poisoned = false;
  constructor({ file, binding, grant, key, verifyGrant, authorize, clock = Date.now } = {}) {
    if (!file || !binding || !grant || typeof verifyGrant !== 'function' ||
        typeof authorize !== 'function' || !Buffer.isBuffer(key) || key.length !== 32 ||
        typeof clock !== 'function') fail('POLICY_REQUIRED');
    for (const field of ['tenantId', 'userId', 'datasetId']) {
      if (typeof binding[field] !== 'string' || !binding[field] || binding[field] !== grant[field]) fail('BINDING_MISMATCH');
    }
    if (!Number.isSafeInteger(grant.expiresAt) || typeof grant.keyId !== 'string' || !grant.keyId) fail('POLICY_REQUIRED');
    if (verifyGrant(clone(grant), clone(binding)) !== true) fail('GRANT_UNVERIFIED');
    this.#binding = Object.fromEntries(['tenantId', 'userId', 'datasetId'].map(k => [k, binding[k]]));
    this.#grant = clone(grant); this.#key = Buffer.from(key); this.#authorize = authorize;
    this.#clock = clock; this.#file = path.resolve(file); this.#lock = `${this.#file}.lock`;
    this.#check('open');
    if (owners.has(this.#file)) fail('STORE_LOCKED');
    fs.mkdirSync(path.dirname(this.#file), { recursive: true, mode: 0o700 });
    let fd;
    try { fd = fs.openSync(this.#lock, 'wx', 0o600); }
    catch { this.#key.fill(0); fail('STORE_LOCKED'); }
    try {
      fs.writeFileSync(fd, JSON.stringify({ pid: process.pid })); fs.fsyncSync(fd);
      owners.add(this.#file);
      this.#state = this.#load();
      if (this.#state.revoked === true) fail('AUTH_REVOKED');
      this.#lastNow = Math.max(this.#lastNow, this.#state.observedAt);
      this.#check('open');
    } catch (error) {
      owners.delete(this.#file); fs.unlinkSync(this.#lock); this.#key.fill(0); throw error;
    } finally { fs.closeSync(fd); }
  }
  #check(action, operation) {
    if (this.#closed) fail('STORE_CLOSED');
    if (this.#poisoned) fail('RECOVERY_REQUIRED');
    const now = this.#clock();
    if (!Number.isSafeInteger(now) || now < this.#lastNow) fail('CLOCK_UNTRUSTED');
    this.#lastNow = now;
    if (now >= this.#grant.expiresAt) fail('AUTH_EXPIRED');
    if (this.#authorize({ binding: clone(this.#binding), grant: clone(this.#grant), action,
      operation: operation === undefined ? undefined : clone(operation), now }) !== true) fail('ACTION_DENIED');
    return now;
  }
  #aad() { return Buffer.from(JSON.stringify({ version: 1, binding: this.#binding, keyId: this.#grant.keyId })); }
  #load() {
    if (!fs.existsSync(this.#file)) return { version: 1, observedAt: this.#lastNow, workingSet: null, queue: [] };
    try {
      if (fs.statSync(this.#file).size > MAX_BYTES * 2) fail('STORE_CORRUPT');
      const envelope = JSON.parse(fs.readFileSync(this.#file, 'utf8'));
      if (envelope.version !== 1) fail('STORE_CORRUPT');
      const iv = Buffer.from(envelope.iv, 'base64'); const tag = Buffer.from(envelope.tag, 'base64');
      if (iv.length !== 12 || tag.length !== 16) fail('STORE_CORRUPT');
      const decipher = crypto.createDecipheriv('aes-256-gcm', this.#key, iv);
      decipher.setAAD(this.#aad()); decipher.setAuthTag(tag);
      const raw = Buffer.concat([decipher.update(Buffer.from(envelope.data, 'base64')), decipher.final()]);
      if (raw.length > MAX_BYTES) fail('STORE_CORRUPT');
      const state = JSON.parse(raw.toString('utf8')); raw.fill(0);
      if (state.version !== 1 || !Number.isSafeInteger(state.observedAt) || !Array.isArray(state.queue) || state.queue.length > MAX_OPERATIONS) fail('STORE_CORRUPT');
      const ids = new Set();
      for (const op of state.queue) {
        if (typeof op.id !== 'string' || ids.has(op.id) || !['pending', 'inflight', 'conflict', 'rejected'].includes(op.state) ||
            !Number.isSafeInteger(op.attempts) || op.attempts < 0 || !Number.isSafeInteger(op.nextAttemptAt)) fail('STORE_CORRUPT');
        ids.add(op.id);
      }
      return state;
    } catch { fail('STORE_CORRUPT'); }
  }
  #commit(next) {
    next.observedAt = this.#check('persist');
    const raw = Buffer.from(JSON.stringify(next));
    if (raw.length > MAX_BYTES) { raw.fill(0); fail('CAPACITY_EXCEEDED'); }
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', this.#key, iv); cipher.setAAD(this.#aad());
    const data = Buffer.concat([cipher.update(raw), cipher.final()]); raw.fill(0);
    const bytes = JSON.stringify({ version: 1, iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64'), data: data.toString('base64') });
    const temporary = `${this.#file}.${crypto.randomUUID()}.tmp`;
    let fd;
    try {
      fd = fs.openSync(temporary, 'wx', 0o600); fs.writeFileSync(fd, bytes); fs.fsyncSync(fd); fs.closeSync(fd); fd = undefined;
      this.#check('persist'); fs.renameSync(temporary, this.#file);
      // Directory fsync is unsupported on Windows; file fsync + rename remains used.
      if (process.platform !== 'win32') { const dir = fs.openSync(path.dirname(this.#file), 'r'); try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); } }
      this.#state = next;
    } catch (error) {
      this.#poisoned = true;
      // Outcome may be uncertain after rename: require close/reopen, never overwrite from stale memory.
      throw new OfflineError(error instanceof OfflineError ? error.code : 'PERSISTENCE_FAILED');
    } finally {
      if (fd !== undefined) fs.closeSync(fd);
      try { fs.unlinkSync(temporary); } catch (error) { if (error.code !== 'ENOENT') this.#poisoned = true; }
    }
  }
  #mutable() { if (this.#busy) fail('SYNC_BUSY'); }
  read() { this.#check('read'); return clone(this.#state.workingSet); }
  pending() { this.#check('readQueue'); return clone(this.#state.queue); }
  setWorkingSet(value) {
    this.#mutable(); this.#check('writeWorkingSet');
    const next = clone(this.#state); next.workingSet = clone(value); this.#commit(next);
  }
  enqueue(operation, workingSet) {
    this.#mutable(); this.#check('enqueue', operation);
    if (operation === undefined || this.#state.queue.length >= MAX_OPERATIONS) fail('CAPACITY_EXCEEDED');
    const next = clone(this.#state); const id = crypto.randomUUID();
    next.queue.push({ id, operation: clone(operation), state: 'pending', attempts: 0, nextAttemptAt: 0 });
    if (workingSet !== undefined) { this.#check('writeWorkingSet'); next.workingSet = clone(workingSet); }
    this.#commit(next); return id;
  }
  // Explicit discard or replacement only. A changed operation always gets a new idempotency key.
  resolve(id, replacement) {
    this.#mutable(); this.#check('resolveConflict', replacement);
    const next = clone(this.#state); const index = next.queue.findIndex(op => op.id === id);
    if (index < 0 || !['conflict', 'rejected'].includes(next.queue[index].state)) fail('NOT_BLOCKED');
    if (replacement === undefined) next.queue.splice(index, 1);
    else {
      this.#check('enqueue', replacement);
      next.queue[index] = { id: crypto.randomUUID(), operation: clone(replacement), state: 'pending', attempts: 0, nextAttemptAt: 0 };
    }
    this.#commit(next); return replacement === undefined ? null : next.queue[index].id;
  }
  async syncOne(transport) {
    this.#mutable(); const now = this.#check('sync');
    if (typeof transport !== 'function') fail('TRANSPORT_REQUIRED');
    const first = this.#state.queue[0];
    if (!first) return { state: 'empty' };
    if (['conflict', 'rejected'].includes(first.state)) return { state: first.state, id: first.id };
    if (first.nextAttemptAt > now) return { state: 'waiting', id: first.id };
    this.#check('send', first.operation); this.#busy = true;
    try {
      const next = clone(this.#state); const op = next.queue[0];
      op.state = 'inflight'; op.attempts += 1;
      op.nextAttemptAt = now + Math.min(60000, 1000 * 2 ** Math.min(op.attempts - 1, 6));
      this.#commit(next);
      this.#check('send', op.operation);
      let result;
      try { result = await transport({ binding: clone(this.#binding), idempotencyKey: op.id, operation: clone(op.operation) }); }
      catch { this.#check('sync'); return { state: 'retry', id: op.id }; }
      this.#check('sync'); // Never expose/commit returned data after authorization expiry.
      const updated = clone(this.#state); const current = updated.queue[0];
      if (!result || !['accepted', 'conflict', 'retry', 'rejected', 'unauthorized'].includes(result.state)) fail('TRANSPORT_CONTRACT');
      if (result.state === 'unauthorized') { updated.revoked = true; this.#commit(updated); this.#closed = true; this.#key.fill(0); fail('AUTH_REVOKED'); }
      if (result.state === 'accepted') updated.queue.shift();
      else current.state = result.state === 'retry' ? 'pending' : result.state;
      this.#commit(updated); return { state: result.state, id: op.id };
    } finally { this.#busy = false; }
  }
  close() {
    if (this.#busy) fail('SYNC_BUSY');
    this.#closed = true; this.#key.fill(0);
    if (owners.delete(this.#file)) { try { fs.unlinkSync(this.#lock); } catch (error) { if (error.code !== 'ENOENT') fail('LOCK_RELEASE_FAILED'); } }
  }
}

// Administrative startup recovery ONLY under an exclusive app-level startup lock.
// Never blindly delete a lock. PID reuse conservatively blocks recovery.
function recoverAbandonedLock(file) {
  const resolved = path.resolve(file);
  if (owners.has(resolved)) fail('STORE_LOCKED');
  const lock = `${resolved}.lock`;
  let owner;
  try { owner = JSON.parse(fs.readFileSync(lock, 'utf8')); } catch { fail('LOCK_RECOVERY_REQUIRED'); }
  if (!Number.isSafeInteger(owner.pid) || owner.pid <= 0) fail('LOCK_RECOVERY_REQUIRED');
  try { process.kill(owner.pid, 0); fail('STORE_LOCKED'); }
  catch (error) { if (error.code !== 'ESRCH') throw new OfflineError('STORE_LOCKED'); }
  fs.unlinkSync(lock);
}
module.exports = { OfflineStore, OfflineError, recoverAbandonedLock };
