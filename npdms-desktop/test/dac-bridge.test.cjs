'use strict';
// Node-only tests: the real native HTTP parser/Agent run over an injected Duplex.
// No TCP listener, connect(), Electron, GUI, JVM or external service is used.
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const { Duplex, PassThrough } = require('node:stream');
const { EventEmitter } = require('node:events');
const { createDacBridge } = require('../src/dac/bridge.cjs');
const origin = 'https://pms.example:8443', prefix = '/__npdms_desktop/dac';
const challenge = 'a'.repeat(64), connectionId = 'b'.repeat(32);
const uri = `sdp-dac://connect/${connectionId}?challenge=${challenge}&tenant=999999999999999999`;
const pair = crypto.generateKeyPairSync('ed25519');
const publicKey = pair.publicKey.export({ format: 'der', type: 'spki' }).subarray(-32).toString('hex');
const instanceId = '12345678-1234-4123-8123-123456789abc';
const ticket = 'PRIVATE-TICKET', password = 'PRIVATE-PASSWORD';
function signed(nonce, patch = {}, signingKey = pair.privateKey) {
  const status = { product: 'sdp-dac', protocolVersion: 3, status: 'READY', serverUrl: origin, webOrigin: origin,
    port: 8280, publicKey, instanceId, ...patch };
  status.signature = crypto.sign(null, Buffer.from(['sdp-dac-discovery-v1', nonce, status.port, status.instanceId, '3', status.status, status.serverUrl, status.webOrigin].join('\n')), signingKey).toString('base64');
  return status;
}
class SyntheticSocket extends Duplex {
  constructor(onRequest, port) {
    super(); this.remoteAddress = '127.0.0.1'; this.localAddress = '127.0.0.1'; this.remotePort = port;
    this.connecting = false; this.pending = Buffer.alloc(0); this.onRequest = onRequest;
  }
  _read() {}
  _write(chunk, _encoding, callback) {
    this.pending = Buffer.concat([this.pending, chunk]);
    for (;;) {
      const boundary = this.pending.indexOf('\r\n\r\n');
      if (boundary < 0) break;
      const head = this.pending.subarray(0, boundary).toString(), lines = head.split('\r\n');
      const [method, path] = lines.shift().split(' '), headers = {};
      for (const line of lines) { const i = line.indexOf(':'); headers[line.slice(0, i).toLowerCase()] = line.slice(i + 1).trim(); }
      const size = Number(headers['content-length'] || 0);
      if (this.pending.length < boundary + 4 + size) break;
      const body = this.pending.subarray(boundary + 4, boundary + 4 + size).toString();
      this.pending = this.pending.subarray(boundary + 4 + size);
      queueMicrotask(() => { if (!this.destroyed) this.onRequest({ method, path, headers, body, socket: this }); });
    }
    callback();
  }
  setTimeout() { return this; }
  setNoDelay() { return this; }
  setKeepAlive() { return this; }
  ref() { return this; }
  unref() { return this; }
  reply({ code = 200, body = '{}', headers = {}, truncated = false } = {}) {
    const bytes = Buffer.from(body);
    const values = { 'Content-Type': 'application/json', 'Content-Length': String(bytes.length), Connection: 'keep-alive', ...headers };
    this.push(Buffer.concat([Buffer.from(`HTTP/1.1 ${code} Fixture\r\n${Object.entries(values).map(([k, v]) => `${k}: ${v}`).join('\r\n')}\r\n\r\n`), bytes]));
    if (truncated) this.push(null);
  }
}
function fixture(options = {}) {
  const records = [], sockets = [], proofRecords = [], abort = new AbortController();
  let current = true, ownedCalls = 0;
  const ctx = { pid: 1234, publicKey, instanceId, port: 8280, serverUrl: origin, webOrigin: origin,
    signal: abort.signal, isCurrent: () => current };
  const connect = value => {
    assert.deepEqual(value, { host: '127.0.0.1', port: 8280, family: 4 });
    const socket = new SyntheticSocket(entry => {
      records.push(entry);
      if (entry.path.startsWith('/api/v1/desktop/status?nonce=')) {
        const nonce = entry.path.split('=')[1];
        if (options.statusHandler) options.statusHandler(entry, nonce);
        else socket.reply({ body: JSON.stringify(signed(nonce, options.statusPatch)) });
      } else if (options.operationHandler) options.operationHandler(entry);
      else socket.reply({ code: options.operationCode || 200, body: options.operationBody || '{"items":[]}', headers: options.operationHeaders });
    }, value.port);
    if (options.socketPatch) Object.assign(socket, options.socketPatch);
    sockets.push(socket); return socket;
  };
  const proofRequest = (value, callback) => {
    const req = new EventEmitter(); req.socket = {}; req.headers = { ...value.headers };
    req.setHeader = (key, val) => { req.headers[key.toLowerCase()] = val; };
    req.destroy = () => { req.destroyed = true; };
    req.end = body => {
      proofRecords.push({ options: value, headers: { ...req.headers }, body: body.toString(), request: req });
      options.onProof?.(req);
      if (options.proofHang) return;
      queueMicrotask(() => {
        if (req.destroyed) return;
        const res = new PassThrough();
        res.statusCode = options.proofCode || 200;
        res.headers = options.proofHeaders || { 'content-type': 'application/json' }; res.rawHeaders = [];
        res.complete = options.proofComplete !== false;
        callback(res); res.end(options.proofBody || '{"code":0,"data":true}');
      });
    };
    queueMicrotask(() => req.emit('socket', req.socket)); return req;
  };
  const handle = createDacBridge({ origin, connect, proofRequest, timeoutMs: 1000,
    withOwnedContext: async (env, work) => { ownedCalls++; assert.deepEqual(env, { serverUrl: origin, webOrigin: origin }); return work(ctx); },
    ...options.bridge });
  return { handle, records, sockets, proofRecords, ctx, abort, setCurrent: v => { current = v; }, ownedCalls: () => ownedCalls };
}
function input(path, { method = 'GET', body, headers = {}, ...rest } = {}) {
  return new Request(origin + prefix + path, { method, body, headers, ...rest });
}
function operation(path = '/saved-connections', method = 'GET', extra = {}) {
  return input('/operations?port=8280&path=' + encodeURIComponent(path), { method,
    ...(method === 'POST' ? { body: JSON.stringify({ password }), headers: {} } : {}), ...extra,
    headers: { 'X-NPDMS-DAC-Ticket': ticket, 'tenant-id': '999999999999999999', ...(method === 'POST' ? { 'Content-Type': 'application/json' } : {}), ...extra.headers } });
}
function pairing(value = uri) { return input('/connect', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ uri: value }) }); }

test('strict construction refuses unsafe origins, missing context and unbounded options', () => {
  const base = { origin, withOwnedContext: () => {} };
  for (const patch of [{ origin: 'http://pms.example' }, { origin: origin + '/path' }, { origin: 'https://user:pass@pms.example' }, { origin: origin + '/' }, { timeoutMs: 10001 }, { maxRequestBytes: 1024 * 1024 + 1 }, { maxResponseBytes: 8 * 1024 * 1024 + 1 }, { withOwnedContext: null }]) assert.throws(() => createDacBridge({ ...base, ...patch }), /INVALID_CONFIGURATION/);
});
test('unknown routes, methods, credentials, bad query and paths reject before context/network', async () => {
  const f = fixture();
  const cases = [
    [input(''), 404], [input('/evil'), 404], [input('/status/'), 404], [input('/status?port=8280&nonce=' + challenge, { method: 'POST' }), 405],
    [input('/connect'), 405], [operation('/saved-connections', 'DELETE'), 405],
    [input('/status?port=8280&port=8280&nonce=' + challenge), 400], [input('/status?port=8280&nonce=' + challenge + '&extra=1'), 400],
    [input('/status?port=8279&nonce=' + challenge), 400], [input('/status?port=8280&nonce=' + 'A'.repeat(64)), 400],
    [input('/status?port=8280&nonce=%zz'), 400], [input('/status?port=8280&nonce=' + challenge + '%0a'), 400], [input('/status?port=8280%0a&nonce=' + challenge), 400], [input('/operations?port=8280&path=%ff'), 400],
    [input('/operations?port=8280&path=%252fsaved-connections'), 400], [input('/operations?port=8280&path=/saved-connections+'), 400],
    [operation('/saved-connections?namespace=other'), 400], [operation('/saved-connections\n'), 400], [operation('/tasks/a/../b'), 400], [operation('/tasks/a%2fb'), 400],
    [operation('/tasks/a/cancellations', 'POST'), 400], [operation('/desktop-pairing'), 400], [operation('http://evil.test'), 400],
    [operation('/saved-connections', 'GET', { headers: { Authorization: 'Bearer SECRET' } }), 403],
    [operation('/saved-connections', 'GET', { headers: { Cookie: 'secret=1' } }), 403],
    [operation('/saved-connections', 'GET', { headers: { 'Proxy-Authorization': 'secret' } }), 403],
    [operation('/saved-connections', 'GET', { headers: { 'tenant-id': '01' } }), 400],
    [operation('/saved-connections', 'GET', { headers: { 'tenant-id': '1000000000000000000' } }), 400],
    [operation('/saved-connections', 'GET', { headers: { 'X-NPDMS-DAC-Ticket': 'a,b' } }), 400],
    [operation('/saved-connections', 'GET', { headers: { 'X-NPDMS-DAC-Ticket': 'x'.repeat(4097) } }), 400],
    [operation('/saved-connections', 'POST', { headers: { 'content-type': 'text/plain' } }), 415],
    [operation('/saved-connections', 'POST', { headers: { 'content-encoding': 'gzip' } }), 415]
  ];
  for (const [request, expected] of cases) assert.equal((await f.handle(request)).status, expected, request.url);
  assert.equal((await f.handle(new Request('https://evil.test' + prefix + '/status?port=8280&nonce=' + challenge))).status, 403);
  assert.equal(f.ownedCalls(), 0); assert.equal(f.sockets.length, 0); assert.equal(f.proofRecords.length, 0);
});
test('returns only genuine fresh signed status from the owned port', async () => {
  const f = fixture();
  const res = await f.handle(input('/status?port=8280&nonce=' + challenge));
  assert.equal(res.status, 200); assert.deepEqual(await res.json(), signed(challenge));
  assert.equal(f.sockets.length, 1); assert.equal(f.records.length, 1);
  assert.equal(f.records[0].headers.origin, origin); assert.equal(f.records[0].headers['x-npdms-dac-ticket'], undefined);
  assert.equal(f.records[0].body, ''); assert.equal(f.sockets[0].destroyed, true);
  const mismatch = fixture(); assert.equal((await mismatch.handle(input('/status?port=8281&nonce=' + challenge))).status, 503); assert.equal(mismatch.sockets.length, 0);
});
test('each exact operation uses fresh status and the same socket before ticket/password bytes', async () => {
  for (const [method, path] of [['GET', '/tasks/abc-123'], ['GET', '/tasks/abc-123/semantic-results'], ['POST', '/tasks/abc-123/executions'], ['GET', '/saved-connections'], ['POST', '/saved-connections']]) {
    const f = fixture(); const res = await f.handle(operation(path, method, { headers: { origin: 'https://evil.test', referer: 'https://evil.test', 'x-forwarded-for': 'other', 'x-extra': 'drop' } }));
    assert.equal(res.status, 200); assert.deepEqual(await res.json(), { items: [] });
    assert.equal(f.records.length, 2); assert.equal(f.sockets.length, 1); assert.equal(f.records[0].socket, f.records[1].socket);
    assert.match(f.records[0].path, /^\/api\/v1\/desktop\/status\?nonce=[a-f0-9]{64}$/); assert.notEqual(f.records[0].path.split('=')[1], challenge);
    const a = f.records[0], b = f.records[1];
    assert.equal(a.headers['x-npdms-dac-ticket'], undefined); assert.equal(a.body, '');
    assert.equal(b.path, '/api/v1/desktop' + path); assert.equal(b.method, method);
    assert.equal(b.headers['x-npdms-dac-ticket'], ticket); assert.equal(b.headers['tenant-id'], '999999999999999999'); assert.equal(b.headers.origin, origin);
    for (const name of ['authorization', 'cookie', 'referer', 'proxy-authorization', 'x-forwarded-for', 'x-extra']) assert.equal(b.headers[name], undefined);
    assert.equal(b.body, method === 'POST' ? JSON.stringify({ password }) : '');
  }
});
test('business error status/body pass through, never converted to success; redirects are never followed', async () => {
  for (const code of [202, 400, 401, 403, 404, 409, 500]) {
    const text = '{"message":"original business response"}', f = fixture({ operationCode: code, operationBody: text,
      operationHeaders: { Location: 'https://evil.test', 'Set-Cookie': 'SECRET=1' } });
    const result = await f.handle(operation('/tasks/a/executions', 'POST'));
    assert.equal(result.status, code); assert.equal(await result.text(), text); assert.equal(result.headers.get('location'), null); assert.equal(result.headers.get('set-cookie'), null);
    assert.equal(f.records.length, 2); assert.equal(f.sockets.length, 1);
  }
  for (const code of [301, 302, 303, 307, 308]) {
    const f = fixture({ operationCode: code, operationBody: '{"message":"redirect"}', operationHeaders: { Location: 'https://evil.test' } });
    const result = await f.handle(operation('/tasks/a/executions', 'POST'));
    assert.equal(result.status, 502); assert.deepEqual(await result.json(), { error: 'DAC_BRIDGE_UNAVAILABLE' });
    assert.equal(f.records.length, 2); assert.equal(f.sockets.length, 1); assert.equal(result.headers.get('location'), null);
  }
});
test('bad signatures, identity/environment/status/shape mismatch never transmit credentials', async () => {
  for (const patch of [{ status: 'STARTING' }, { publicKey: '0'.repeat(64) }, { port: 8281 }, { serverUrl: 'https://other.test' }, { webOrigin: 'https://other.test' }, { instanceId: '12345678-1234-4123-8123-111111111111' }, { protocolVersion: 2 }, { extra: true }]) {
    const f = fixture({ statusPatch: patch }); assert.equal((await f.handle(operation('/tasks/a/executions', 'POST'))).status, 502);
    assert.equal(f.records.length, 1); assert.equal(f.records[0].headers['x-npdms-dac-ticket'], undefined); assert.equal(f.records[0].body, '');
  }
  const f = fixture({ statusHandler: (entry, n) => entry.socket.reply({ body: JSON.stringify({ ...signed(n), signature: Buffer.alloc(64).toString('base64') }) }) });
  assert.equal((await f.handle(operation())).status, 502); assert.equal(f.records.length, 1);
  const replay = fixture({ statusHandler: entry => entry.socket.reply({ body: JSON.stringify(signed(challenge)) }) });
  assert.equal((await replay.handle(operation())).status, 502); assert.equal(replay.records.length, 1);
});
test('missing/stale ownership, aborted context and wrong endpoint block before sensitive send', async () => {
  for (const patch of [{ pid: 0 }, { pid: undefined }, { publicKey: null }, { serverUrl: 'https://other.test' }, { signal: null }, { instanceId: 'not-an-instance' }]) {
    const f = fixture(); Object.assign(f.ctx, patch); assert.equal((await f.handle(operation())).status, 503); assert.equal(f.sockets.length, 0);
  }
  const stale = fixture(); stale.setCurrent(false); assert.equal((await stale.handle(operation())).status, 503); assert.equal(stale.sockets.length, 0);
  const aborted = fixture(); aborted.abort.abort(); assert.equal((await aborted.handle(operation())).status, 503); assert.equal(aborted.sockets.length, 0);
  for (const patch of [{ remoteAddress: '127.0.0.2' }, { remotePort: 8281 }, { localAddress: '10.0.0.1' }]) {
    const f = fixture({ socketPatch: patch }); assert.equal((await f.handle(operation())).status, 502); assert.equal(f.records.length, 0);
  }
});
test('close/reconnect races, replacement sockets and last-moment ownership loss never release secrets', async () => {
  for (const mode of ['close-header', 'end', 'destroy', 'error']) {
    const f = fixture({ statusHandler: (entry, n) => {
      if (mode === 'close-header') entry.socket.reply({ body: JSON.stringify(signed(n)), headers: { Connection: 'close' } });
      else if (mode === 'end') { entry.socket.reply({ body: JSON.stringify(signed(n)) }); entry.socket.push(null); }
      else if (mode === 'destroy') entry.socket.destroy(); else entry.socket.destroy(new Error(password));
    } });
    const result = await f.handle(operation('/tasks/a/executions', 'POST'));
    assert.equal(result.status, 502, mode); assert.doesNotMatch(await result.text(), /PRIVATE/); assert.equal(f.sockets.length, 1); assert.equal(f.records.length, 1);
  }
  let attempts = 0;
  const oneShot = fixture({ bridge: { request: (opts, cb) => {
    attempts++;
    if (attempts === 2) { for (const list of Object.values(opts.agent.freeSockets)) for (const socket of list) socket.destroy(); }
    return http.request(opts, cb);
  } } });
  assert.equal((await oneShot.handle(operation('/tasks/a/executions', 'POST'))).status, 502);
  assert.equal(oneShot.sockets.length, 1); assert.equal(oneShot.records.length, 1);
  let beforeAssignCalls = 0;
  const beforeAssign = fixture({ bridge: { request: (opts, cb) => {
    const req = http.request(opts, cb);
    // Native ClientRequest emits socket asynchronously. Destroy the verified
    // socket after request construction, before the operation can install headers.
    if (++beforeAssignCalls === 2) beforeAssign.sockets[0].destroy();
    return req;
  } } });
  assert.equal((await beforeAssign.handle(operation('/tasks/a/executions', 'POST'))).status, 502);
  assert.equal(beforeAssignCalls, 2); assert.equal(beforeAssign.records.length, 1); assert.equal(beforeAssign.sockets.length, 1);
  assert.equal(beforeAssign.records[0].headers['x-npdms-dac-ticket'], undefined); assert.equal(beforeAssign.records[0].body, '');
  let calls = 0, replacementEnded = false;
  const wrong = fixture({ bridge: { request: (opts, cb) => {
    if (++calls === 1) return http.request(opts, cb);
    const req = new EventEmitter(); req.setHeader = () => assert.fail('No sensitive header before pin check'); req.destroy = () => {};
    req.end = () => { replacementEnded = true; };
    queueMicrotask(() => req.emit('socket', { ...wrong.sockets[0] })); return req;
  } } });
  assert.equal((await wrong.handle(operation())).status, 502); assert.equal(replacementEnded, false); assert.equal(wrong.records.length, 1);
  let count = 0;
  const lost = fixture({ bridge: { request: (opts, cb) => { const req = http.request(opts, cb); if (++count === 2) req.prependListener('socket', () => lost.setCurrent(false)); return req; } } });
  assert.equal((await lost.handle(operation('/tasks/a/executions', 'POST'))).status, 502); assert.equal(lost.records.length, 1);
});
test('JSON uploads and response bytes are bounded and malformed/truncated streams fail closed', async () => {
  const f = fixture({ bridge: { maxRequestBytes: 8 } });
  assert.equal((await f.handle(operation('/saved-connections', 'POST'))).status, 413); assert.equal(f.sockets.length, 0);
  const bad = fixture(); assert.equal((await bad.handle(operation('/saved-connections', 'POST', { body: '{invalid}' }))).status, 400); assert.equal(bad.sockets.length, 0);
  for (const options of [
    { operationBody: 'x'.repeat(65) },
    { operationHeaders: { 'Content-Length': '999' } },
    { operationHeaders: { 'Content-Encoding': 'gzip' } },
    { operationHandler: entry => entry.socket.reply({ body: 'abc', headers: { 'Content-Length': '9' }, truncated: true }) }
  ]) {
    const f = fixture({ ...options, bridge: { maxResponseBytes: 64 } }); assert.equal((await f.handle(operation())).status, 502);
  }
  const statusBig = fixture({ statusHandler: entry => entry.socket.reply({ body: 'x'.repeat(8193) }) });
  assert.equal((await statusBig.handle(operation())).status, 502); assert.equal(statusBig.records.length, 1);
});
test('runtime stop, request abort and hard timeout destroy dedicated socket', async () => {
  const controller = new AbortController();
  const early = fixture(); controller.abort(); assert.equal((await early.handle(operation('/saved-connections', 'GET', { signal: controller.signal }))).status, 503); assert.equal(early.sockets.length, 0);
  let f = fixture({ statusHandler: () => f.abort.abort() });
  assert.equal((await f.handle(operation())).status, 503); assert.equal(f.sockets[0].destroyed, true); assert.equal(f.records.length, 1);
  const timeout = fixture({ statusHandler: () => {}, bridge: { timeoutMs: 10 } });
  assert.equal((await timeout.handle(operation())).status, 503); await new Promise(resolve => setImmediate(resolve)); assert.equal(timeout.sockets[0].destroyed, true);
  let running = fixture({ operationHandler: entry => { entry.socket.push('HTTP/1.1 200 Fixture\r\nContent-Type: application/json\r\nContent-Length: 10\r\n\r\n{'); running.abort.abort(); } });
  assert.equal((await running.handle(operation())).status, 503); assert.equal(running.sockets[0].destroyed, true);
});
test('connect uses URI nonce, exact proof path and tenant, TLS verification, then ACK only', async () => {
  const f = fixture(); const result = await f.handle(pairing());
  assert.equal(result.status, 200); assert.deepEqual(await result.json(), { ok: true }); assert.equal(f.records.length, 1); assert.equal(f.proofRecords.length, 1);
  assert.equal(f.records[0].path, '/api/v1/desktop/status?nonce=' + challenge);
  const proof = f.proofRecords[0];
  assert.equal(proof.options.hostname, 'pms.example'); assert.equal(proof.options.port, '8443'); assert.equal(proof.options.protocol, 'https:');
  assert.equal(proof.options.path, `/api/v1/pms/integration/device-ops/desktop/connections/${connectionId}/proof`);
  assert.equal(proof.options.method, 'POST'); assert.equal(proof.options.agent, false); assert.equal(proof.options.rejectUnauthorized, true);
  assert.deepEqual(Object.keys(proof.headers).sort(), ['content-length', 'content-type', 'tenant-id']); assert.equal(proof.headers['tenant-id'], '999999999999999999');
  assert.deepEqual(JSON.parse(proof.body), { nonce: challenge, status: signed(challenge) });
});
test('connect rejects extra/duplicate keys, malformed URIs and forged status before proof', async () => {
  const f = fixture();
  for (const value of [uri + '&extra=1', uri.replace('tenant=999999999999999999', 'tenant=0'), uri.replace('sdp-dac:', 'https:'), uri.replace(connectionId, 'B'.repeat(32)), uri.replace(challenge, 'a'.repeat(63)), uri.replace('challenge=', 'tenant=1&challenge=')]) assert.equal((await f.handle(pairing(value))).status, 400);
  for (const body of [JSON.stringify({ uri, extra: true }), `{"uri":"${uri}","uri":"${uri}"}`, '{"uri":1}', '[]']) assert.equal((await f.handle(input('/connect', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body }))).status, 400);
  assert.equal(f.sockets.length, 0); assert.equal(f.proofRecords.length, 0);
  const forged = fixture({ statusPatch: { publicKey: '0'.repeat(64) } }); assert.equal((await forged.handle(pairing())).status, 502); assert.equal(forged.proofRecords.length, 0);
});
test('proof failure/redirect/malformed success never creates accepted or retries', async () => {
  for (const options of [{ proofCode: 302, proofHeaders: { 'content-type': 'application/json', location: 'https://evil.test' } }, { proofCode: 403 }, { proofCode: 201 }, { proofBody: '{"code":1,"data":true}' }, { proofBody: '{"code":0,"data":false}' }, { proofBody: '{"code":"0","data":true}' }, { proofBody: 'not json' }, { proofHeaders: { 'content-type': 'text/html' } }, { proofComplete: false }]) {
    const f = fixture(options), result = await f.handle(pairing()); assert.equal(result.status, 502); assert.deepEqual(await result.json(), { error: 'DAC_BRIDGE_UNAVAILABLE' }); assert.equal(f.proofRecords.length, 1);
  }
  const f = fixture({ proofHang: true, bridge: { timeoutMs: 10 } }); assert.equal((await f.handle(pairing())).status, 503); assert.equal(f.proofRecords.length, 1); assert.equal(f.proofRecords[0].request.destroyed, true);
});

test('caps concurrent calls without queuing credentials and frees capacity after abort', async () => {
  const f = fixture({ statusHandler: () => {} });
  const requests = Array.from({ length: 16 }, () => new AbortController());
  const pending = requests.map(c => f.handle(operation('/saved-connections', 'GET', { signal: c.signal })));
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.sockets.length, 16);
  const busy = await f.handle(operation()); assert.equal(busy.status, 503); assert.deepEqual(await busy.json(), { error: 'DAC_BRIDGE_BUSY' });
  assert.equal(f.sockets.length, 16);
  for (const c of requests) c.abort();
  for (const result of await Promise.all(pending)) assert.equal(result.status, 503);
  assert.ok(f.sockets.every(s => s.destroyed));
  const next = new AbortController(), final = f.handle(operation('/saved-connections', 'GET', { signal: next.signal }));
  await new Promise(resolve => setImmediate(resolve)); assert.equal(f.sockets.length, 17); next.abort(); assert.equal((await final).status, 503);
});

test('connect cancels remote proof on runtime exit and never retries after cancellation', async () => {
  const f = fixture({ proofHang: true, onProof: () => f.abort.abort() });
  const result = await f.handle(pairing()); assert.equal(result.status, 503); assert.equal(f.proofRecords.length, 1);
  assert.equal(f.proofRecords[0].request.destroyed, true); assert.equal(f.sockets[0].destroyed, true);
});
test('UTF-8, content-length, duplicate headers and streamed body timeout reject before network', async () => {
  const f = fixture({ bridge: { timeoutMs: 10 } });
  for (const extra of [
    { body: Buffer.from([0xff]) }, { body: '{}', headers: { 'content-length': '3' } },
    { body: '{}', headers: { 'content-length': '01' } }, { body: '{}', headers: { 'content-type': 'application/json, application/json' } },
    { body: '{}', headers: { 'tenant-id': '1, 1' } }
  ]) assert.ok((await f.handle(operation('/saved-connections', 'POST', extra))).status >= 400);
  const stream = new ReadableStream({ start(controller) { controller.enqueue(Buffer.from('{')); } });
  assert.equal((await f.handle(operation('/saved-connections', 'POST', { body: stream, duplex: 'half' }))).status, 503);
  assert.equal(f.sockets.length, 0);
});
