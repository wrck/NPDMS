'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { EventEmitter } = require('node:events');
const { createWindowsServiceRuntime, createWindowsServiceHost } = require('../src/dac/windows-service.cjs');
const origin = 'https://npdms.example';
const config = { serverUrl: origin, webOrigin: origin };
function fixture(options = {}) {
  const pair = crypto.generateKeyPairSync('ed25519');
  const key = pair.publicKey.export({ format: 'der', type: 'spki' }).subarray(-32).toString('hex');
  let instanceId = crypto.randomUUID(), pid = 1234;
  const calls = [];
  const host = async (action, signal) => {
    calls.push(action);
    if (options.host) return options.host(action, signal);
    return { serviceState: 'Running', ownershipVerified: true, ...config, ...options.environment,
      metadata: `port=8280\npid=${pid}\npublicKey=${key}\ninstanceId=${instanceId}\n` };
  };
  const request = (params, callback) => {
    const req = new EventEmitter(); req.destroy = () => {};
    req.end = () => setImmediate(() => {
      assert.equal(params.hostname, '127.0.0.1'); assert.equal(params.agent, false);
      const nonce = new URL('http://localhost' + params.path).searchParams.get('nonce');
      const body = { product: 'sdp-dac', protocolVersion: 3, status: 'READY', ...config, port: 8280, publicKey: key, instanceId };
      body.signature = crypto.sign(null, Buffer.from(['sdp-dac-discovery-v1', nonce, 8280, instanceId, '3', 'READY', origin, origin].join('\n')), pair.privateKey).toString('base64');
      if (options.forged) body.signature = Buffer.alloc(64).toString('base64');
      const res = new EventEmitter(); res.destroy = () => {}; res.statusCode = 200; res.headers = { 'content-type': 'application/json' };
      callback(res); res.emit('data', Buffer.from(JSON.stringify(body))); res.emit('end');
    });
    return req;
  };
  const runtime = createWindowsServiceRuntime({ host, request, timeoutMs: 150, pollMs: 2 });
  return { runtime, calls, replace: () => { instanceId = crypto.randomUUID(); pid++; } };
}
test('existing Windows service is started explicitly and pinned by signed fresh status', async () => {
  const f = fixture(); assert.equal(f.calls.length, 0); assert.equal(f.runtime.status().mode, 'windows-installed-service');
  assert.equal((await f.runtime.start(config)).state, 'ready');
  assert.equal(f.calls[0], 'snapshot'); assert.equal(f.calls[1], 'start');
  const value = await f.runtime.withOwnedContext(config, async ctx => {
    assert.equal(ctx.pid, 1234); assert.equal(ctx.isCurrent(), true); assert.equal(ctx.publicKey.length, 64); return 'verified';
  });
  assert.equal(value, 'verified'); await f.runtime.stop();
  assert.equal(f.runtime.status().reason, 'DAC_WINDOWS_DISCONNECTED');
  assert.equal(f.calls.some(x => /stop|install|configure|key/.test(x)), false);
});
test('wrong environment never starts or reconfigures the installed service', async () => {
  const f = fixture({ environment: { webOrigin: 'https://other.example' } });
  await assert.rejects(f.runtime.start(config), { code: 'DAC_ENVIRONMENT_MISMATCH' });
  assert.deepEqual(f.calls, ['snapshot']);
});
test('untrusted service and raw helper errors fail closed without secret details', async () => {
  const f = fixture({ host: async () => { throw Error('secret local path'); } });
  await assert.rejects(f.runtime.start(config), { code: 'DAC_START_FAILED', message: 'DAC_START_FAILED' });
  assert.equal(f.runtime.status().state, 'blocked');
});
test('forged status cannot establish readiness or permit bridge operations', async () => {
  const f = fixture({ forged: true });
  await assert.rejects(f.runtime.start(config));
  await assert.rejects(f.runtime.withOwnedContext(config, async () => { assert.fail('must not use'); }), { code: 'DAC_NOT_READY' });
});
test('restart invalidates the pinned context instead of silently adopting a new instance', async () => {
  const f = fixture(); await f.runtime.start(config); f.replace();
  await assert.rejects(f.runtime.withOwnedContext(config, async () => { assert.fail('must not use'); }), { code: 'DAC_CONTEXT_INVALIDATED' });
  assert.equal(f.runtime.status().state, 'blocked');
});
test('disconnect aborts in-flight bridge context and does not stop shared service', async () => {
  const f = fixture(); await f.runtime.start(config);
  await assert.rejects(f.runtime.withOwnedContext(config, async ctx => {
    await f.runtime.stop(); assert.equal(ctx.signal.aborted, true); assert.equal(ctx.isCurrent(), false);
  }), { code: 'DAC_CONTEXT_INVALIDATED' });
  assert.equal(f.runtime.status().reason, 'DAC_WINDOWS_DISCONNECTED');
});
test('metadata changed during operation is rejected after the operation too', async () => {
  const f = fixture(); await f.runtime.start(config);
  await assert.rejects(f.runtime.withOwnedContext(config, async () => f.replace()), { code: 'DAC_CONTEXT_INVALIDATED' });
});
test('invalid origins and extra properties never reach native helper', async () => {
  const f = fixture();
  for (const bad of [{ ...config, serverUrl: 'http://npdms.example' }, { ...config, command: 'anything' }, null])
    await assert.rejects(f.runtime.start(bad), { code: 'DAC_INVALID_CONFIGURATION' });
  assert.deepEqual(f.calls, []);
});
test('cancellation while native start is pending never restores a stale ready context', async () => {
  let unblock;
  const f = fixture({ host: async (action) => {
    if (action === 'start') await new Promise(resolve => { unblock = resolve; });
    return { serviceState: 'Stopped', ...config, metadata: null, ownershipVerified: false };
  } });
  const started = f.runtime.start(config);
  while (!unblock) await new Promise(resolve => setImmediate(resolve));
  await assert.rejects(f.runtime.start(config), { code: 'DAC_BUSY' });
  await f.runtime.stop(); unblock();
  await assert.rejects(started, { code: 'DAC_START_CANCELLED' });
  assert.equal(f.runtime.status().state, 'stopped');
});
test('helper boundary rejects tampering, filters environment and accepts no arbitrary actions', async t => {
  const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'windows-helper-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const helper = path.join(directory, 'NpdmsServiceBridge.exe');
  const bytes = Buffer.from('test helper bytes; never executed'); fs.writeFileSync(helper, bytes);
  fs.writeFileSync(path.join(directory, 'bridge-manifest.json'), JSON.stringify({ format: 1, ownershipBoundary: 'scm-process-listener', bytes: bytes.length, sha256: crypto.createHash('sha256').update(bytes).digest('hex') }));
  let calls = 0, nativeError;
  const host = createWindowsServiceHost({ helper, execFile: (file, args, options, done) => {
    calls++; assert.equal(file, helper); assert.deepEqual(args, ['snapshot']); assert.equal(options.shell, false);
    assert.equal(options.windowsHide, true); assert.equal(options.maxBuffer, 16384);
    assert.ok(Object.keys(options.env).every(name => ['SystemRoot', 'WINDIR', 'TEMP', 'TMP'].includes(name)));
    if (nativeError) done(Error('native failure'), '', nativeError);
    else done(null, JSON.stringify({ result: 'test' }));
  } });
  await assert.rejects(host('stop'), { code: 'DAC_INVALID_CONFIGURATION' });
  assert.deepEqual(await host('snapshot'), { result: 'test' });
  fs.appendFileSync(helper, 'tampered');
  await assert.rejects(host('snapshot'), { code: 'DAC_WINDOWS_SERVICE_REQUIRED' });
  assert.equal(calls, 1);
  fs.writeFileSync(helper, bytes);
  nativeError = 'DAC_WINDOWS_ACCESS_DENIED\n';
  await assert.rejects(host('snapshot'), { code: 'DAC_WINDOWS_ACCESS_DENIED' });
  nativeError = 'private local detail: DAC_WINDOWS_ACCESS_DENIED';
  await assert.rejects(host('snapshot'), { code: 'DAC_WINDOWS_SERVICE_UNTRUSTED', message: 'DAC_WINDOWS_SERVICE_UNTRUSTED' });
});

test('a signed response cannot compensate for missing native process ownership verification', async () => {
  const f = fixture({ environment: { ownershipVerified: false } });
  await assert.rejects(f.runtime.start(config), { code: 'DAC_WINDOWS_SERVICE_UNTRUSTED' });
  assert.deepEqual(f.calls, ['snapshot']);
});
