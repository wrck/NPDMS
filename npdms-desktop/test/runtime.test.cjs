'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const fsp = fs.promises;
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { EventEmitter } = require('node:events');
const { createDacRuntime, DAC_ERROR_CODES } = require('../src/dac/runtime.cjs');
const { createBuildInfo, payloadInventory } = require('../scripts/package-linux.cjs');
const CONFIG = { serverUrl: 'https://server.example/api', webOrigin: 'https://web.example' };
const PARSER = 'parser-releases/device-command-output-1.4.0';
const tick = () => new Promise(setImmediate);
function fixture(t, options = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'dac-runtime-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const resourcesDirectory = path.join(root, 'app/resources/dac');
  const dataRoot = path.join(root, 'private');
  const files = ['device-ops-server.jar', 'jre/bin/java', 'jre/release', 'jre/lib/modules',
    'jre/lib/libjli.so', 'jre/lib/server/libjvm.so', ...['manifest.json', 'model-profiles.json',
      'projections.json', 'verification-cases.json', 'rules/base.json', 'rules/model-vpn1000-ga.json',
      'input-real-sanitized.json', 'input-tech-only-sanitized.json', 'expected-real.json',
      'expected-tech-only.json'].map(n => `${PARSER}/${n}`)];
  for (const name of files) {
    const file = path.join(resourcesDirectory, name);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, `SYNTHETIC TEST FIXTURE, NEVER EXECUTE: ${name}\n`, { mode: name === 'jre/bin/java' ? 0o755 : 0o644 });
  }
  fs.writeFileSync(path.join(resourcesDirectory, 'jre/release'), 'JAVA_VERSION="25.0.1"\nOS_NAME="Linux"\nOS_ARCH="x86_64"\n');
  fs.writeFileSync(path.join(resourcesDirectory, PARSER, 'manifest.json'), JSON.stringify({ logType: 'device-command-output', releaseVersion: '1.4.0' }));
  const buildInfo = path.join(root, 'app/build-info.json');
  const frontend = path.join(root, 'app/resources/frontend');
  fs.mkdirSync(frontend, { recursive: true });
  fs.writeFileSync(path.join(frontend, 'index.html'), '<!doctype html><title>Synthetic fixture</title>');
  fs.writeFileSync(path.join(frontend, 'build-manifest.json'), JSON.stringify({ format: 1,
    baseline: 'd4f6918d67c3f5b32559d7eba6deb6e9a847858a', buildMode: 'desktop', files: payloadInventory(frontend) }));
  function inventory() {
    // Exercise the actual package producer schema. The reconstructed application
    // commit differs from the upstream frontend/DAC source baseline.
    const info = createBuildInfo(path.join(root, 'app'), { mode: 'production-payload', appId: 'org.npdms.desktop',
      electronVersion: '45.2.3', sourceDateEpoch: 1735689600, sourceCommit: 'a'.repeat(40),
      runtimeAcceptance: 'not-performed', windowClass: { configured: null, verification: 'not-performed' } });
    fs.writeFileSync(buildInfo, JSON.stringify(info));
  }
  inventory();
  const keys = options.keys || new Map(), readBuffers = [];
  let vaultReads = 0, creates = 0, available = true, acquireCount = 0, releaseCount = 0;
  const vault = { backend: 'linux-os-key-vault', isAvailable: () => available,
    read: async id => { vaultReads++; if (!keys.has(id)) return null; const b = Buffer.from(keys.get(id)); readBuffers.push(b); return b; },
    createIfAbsent: async (id, key) => { creates++; if (!keys.has(id)) keys.set(id, Buffer.from(key)); } };
  const leases = options.leases || new Set();
  const children = [], launches = [];
  const lockHost = { acquireLock: async directory => {
    acquireCount++;
    if (leases.has(directory)) throw new Error('Secret private path /must/not/leak');
    leases.add(directory);
    fs.writeFileSync(path.join(directory, 'service.lock'), '', { mode: 0o600 });
    return () => { releaseCount++; leases.delete(directory); };
  }, spawn: (command, args, launch) => {
    assert.ok(leases.has(launch.cwd), 'mock host retains lifetime lock during spawn');
    assert.equal(launch.stdio.length, 3, 'real createLinuxLockHost appends its inherited FD, not the coordinator');
    const child = new EventEmitter(); child.pid = 100 + children.length; child.kills = [];
    child.kill = signal => { child.kills.push(signal); if (options.signalThrows) throw Error('private kill error');
      if (!options.hung) setImmediate(() => child.emit('exit', 0)); return !options.hung; };
    children.push(child); launches.push({ command, args, options: launch, envCopy: { ...launch.env } });
    options.onSpawn?.(child, launch);
    return child;
  } };
  const createProbe = ({ protectedDirectory }) => async ({ child, port, nonce }) => {
    if (options.probe) return options.probe({ child, port, nonce });
    const props = Object.fromEntries(fs.readFileSync(path.join(protectedDirectory, 'desktop-identity.properties'), 'utf8').split('\n').filter(l => l && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
    const id = `dac-v1-${path.basename(protectedDirectory)}`;
    const encrypted = Buffer.from(props.private, 'base64'), dec = crypto.createDecipheriv('aes-256-gcm', keys.get(id), Buffer.from(props.nonce, 'base64'));
    dec.setAuthTag(encrypted.subarray(-16));
    const plain = Buffer.concat([dec.update(encrypted.subarray(0, -16)), dec.final()]);
    const priv = crypto.createPrivateKey({ key: Buffer.from(plain.toString(), 'base64'), format: 'der', type: 'pkcs8' });
    plain.fill(0);
    const config = JSON.parse(fs.readFileSync(path.join(protectedDirectory, 'environment.json')));
    const instanceId = '9c84c376-21cd-43e5-915a-25ace20d7167';
    const status = { product: 'sdp-dac', protocolVersion: 3, status: 'READY', serverUrl: config.serverUrl,
      webOrigin: config.webOrigin, port, publicKey: Buffer.from(props.public, 'base64').subarray(-32).toString('hex'), instanceId };
    if (options.wrongEnvironment) status.webOrigin = 'https://wrong.example';
    status.signature = crypto.sign(null, Buffer.from(['sdp-dac-discovery-v1', nonce, port, instanceId, 3, 'READY', status.serverUrl, status.webOrigin].join('\n')), priv).toString('base64');
    return { pid: child.pid, instanceId, status };
  };
  const args = { resourcesDirectory, dataRoot, vault, lockHost, createProbe, timeoutMs: 80, pollMs: 1, stopTimeoutMs: 20 };
  const runtime = createDacRuntime(args);
  t.after(async () => { for (const child of children) child.emit('exit', 0); await tick(); });
  return { root, resourcesDirectory, dataRoot, buildInfo, files, inventory, keys, readBuffers, vault, lockHost, leases, children, launches, runtime, args,
    directory: config => path.join(dataRoot, 'environments', crypto.createHash('sha256').update(config.serverUrl).digest('hex')),
    unavailable: () => { available = false; }, counts: () => ({ vaultReads, creates, acquireCount, releaseCount }) };
}
async function rejectCode(promise, code) {
  await assert.rejects(promise, error => { assert.equal(error.message, code); assert.equal(error.code, code); assert.equal(error.cause, undefined); return true; });
}

test('construction/status do not create data, use vault, acquire lock or launch; missing JAR blocks first', async t => {
  const f = fixture(t);
  fs.unlinkSync(path.join(f.resourcesDirectory, 'device-ops-server.jar'));
  assert.deepEqual(f.runtime.status(), { state: 'blocked', reason: 'DAC_RESOURCES_MISSING', canStart: false, canStop: false });
  await rejectCode(f.runtime.start(CONFIG), 'DAC_RESOURCES_MISSING');
  assert.equal(fs.existsSync(f.dataRoot), false);
  assert.deepEqual(f.counts(), { vaultReads: 0, creates: 0, acquireCount: 0, releaseCount: 0 });
  assert.equal(f.children.length, 0);
});
test('resources reject missing parser/JRE, symlinks, unverified inventory, added files and modified hashes', async t => {
  for (const kind of ['parser', 'jre', 'symlink', 'manifest', 'hash', 'added', 'source', 'baseline', 'app-id', 'development']) await t.test(kind, async t => {
    const f = fixture(t);
    let code = 'DAC_RESOURCES_UNVERIFIED';
    if (kind === 'parser' || kind === 'jre') { fs.unlinkSync(path.join(f.resourcesDirectory, kind === 'parser' ? `${PARSER}/rules/base.json` : 'jre/lib/modules')); code = 'DAC_RESOURCES_MISSING'; }
    if (kind === 'symlink') { const file = path.join(f.resourcesDirectory, 'device-ops-server.jar'); fs.unlinkSync(file); fs.symlinkSync('/etc/passwd', file); code = 'DAC_RESOURCES_INVALID'; }
    if (kind === 'manifest') fs.unlinkSync(f.buildInfo);
    if (kind === 'hash') fs.appendFileSync(path.join(f.resourcesDirectory, 'device-ops-server.jar'), 'changed');
    if (kind === 'added') fs.writeFileSync(path.join(f.resourcesDirectory, 'extra.jar'), 'extra');
    if (['source', 'baseline', 'app-id', 'development'].includes(kind)) {
      const info = JSON.parse(fs.readFileSync(f.buildInfo));
      if (kind === 'source') info.sourceCommit = '0'.repeat(40);
      if (kind === 'baseline') info.frontend.baseline = 'b'.repeat(40);
      if (kind === 'app-id') info.appId = 'another.app';
      if (kind === 'development') info.mode = 'development';
      fs.writeFileSync(f.buildInfo, JSON.stringify(info));
    }
    await rejectCode(f.runtime.start(CONFIG), code);
    assert.equal(f.counts().vaultReads, 0); assert.equal(f.counts().acquireCount, 0); assert.equal(f.children.length, 0);
  });
});
test('only exact HTTPS config fields accepted; no native path/command/env overrides', async t => {
  const f = fixture(t);
  for (const input of [null, {}, { ...CONFIG, javaPath: '/tmp/evil' }, { ...CONFIG, inheritedEnv: {} },
    { ...CONFIG, serverUrl: 'http://server.example' }, { ...CONFIG, serverUrl: 'https://user:secret@server.example' },
    { ...CONFIG, serverUrl: 'https://server.example?secret=x' }, { ...CONFIG, webOrigin: 'https://web.example/path' },
    { ...CONFIG, serverUrl: ' https://server.example' }, { ...CONFIG, webOrigin: 'https://web.example\n' },
    { ...CONFIG, serverUrl: 'https://server.example:0' }, Object.assign(Object.create({ inheritedEnv: {} }), CONFIG),
    Object.defineProperty({ serverUrl: CONFIG.serverUrl }, 'webOrigin', { get() { assert.fail('must not invoke IPC getter'); }, enumerable: true })]) {
    await rejectCode(f.runtime.start(input), 'DAC_INVALID_CONFIGURATION');
  }
  assert.equal(f.children.length, 0); assert.equal(f.counts().vaultReads, 0);
});
test('unavailable OS vault blocks without provisioning, locking, process or plaintext fallback', async t => {
  const f = fixture(t); f.unavailable();
  await rejectCode(f.runtime.start(CONFIG), 'DAC_VAULT_UNAVAILABLE');
  assert.equal(fs.existsSync(f.dataRoot), false); assert.equal(f.counts().acquireCount, 0); assert.equal(f.children.length, 0);
});
test('explicit fresh launch binds exact environment and independent identity, zeros keys, returns minimal status', async t => {
  const f = fixture(t);
  const status = await f.runtime.start(CONFIG);
  assert.deepEqual(Object.keys(status).sort(), ['canStart', 'canStop', 'pairingCode', 'port', 'reason', 'state']);
  assert.equal(status.state, 'ready'); assert.match(status.pairingCode, /^[A-F0-9]{32}$/); assert.equal(status.port, 8280);
  assert.equal(status.canStart, false); assert.equal(status.canStop, true);
  assert.ok(f.readBuffers.every(b => b.equals(Buffer.alloc(32))));
  const launch = f.launches[0];
  assert.equal(launch.command, path.join(f.resourcesDirectory, 'jre/bin/java'));
  assert.equal(launch.options.shell, false); assert.equal(launch.options.env.DEVICE_OPS_CREDENTIAL_MASTER_KEY, undefined);
  assert.deepEqual(Object.keys(launch.envCopy).sort(), ['DEVICE_OPS_CREDENTIAL_MASTER_KEY', 'LANG', 'LC_ALL', 'PATH', 'SERVER_URL', 'WEB_ORIGIN']);
  assert.equal(launch.envCopy.SERVER_URL, CONFIG.serverUrl); assert.equal(launch.envCopy.WEB_ORIGIN, CONFIG.webOrigin);
  assert.equal(launch.options.cwd, f.directory(CONFIG));
  assert.ok(launch.args.includes('--spring.profiles.active=desktop'));
  for (const dir of [f.dataRoot, path.join(f.dataRoot, 'environments'), f.directory(CONFIG), path.join(f.directory(CONFIG), 'data')]) assert.equal(fs.statSync(dir).mode & 0o777, 0o700);
  assert.equal(f.counts().releaseCount, 0);
  const before = fs.readFileSync(path.join(f.directory(CONFIG), 'desktop-identity.properties'));
  assert.equal((await f.runtime.stop()).state, 'stopped'); assert.equal(f.counts().releaseCount, 1);
  const again = await f.runtime.start(CONFIG); assert.equal(again.pairingCode, status.pairingCode);
  assert.deepEqual(fs.readFileSync(path.join(f.directory(CONFIG), 'desktop-identity.properties')), before); assert.equal(f.counts().creates, 1);
});
test('concurrent and double starts reject instead of duplicate children, locks or keys', async t => {
  const f = fixture(t);
  const first = f.runtime.start(CONFIG);
  await rejectCode(f.runtime.start({ ...CONFIG, webOrigin: 'https://other.example' }), 'DAC_BUSY');
  assert.equal((await first).state, 'ready');
  await rejectCode(f.runtime.start(CONFIG), 'DAC_BUSY');
  assert.equal(f.children.length, 1); assert.equal(f.counts().creates, 1); assert.equal(f.counts().acquireCount, 1);
});
test('lifetime lock acquired before keys/identity; occupied lock never reads or kills unknown owner', async t => {
  const f = fixture(t); f.leases.add(f.directory(CONFIG));
  await rejectCode(f.runtime.start(CONFIG), 'DAC_LOCK_UNAVAILABLE');
  assert.equal(f.counts().vaultReads, 0); assert.equal(f.counts().creates, 0); assert.equal(f.children.length, 0);
  assert.ok(f.leases.has(f.directory(CONFIG))); assert.equal(f.counts().releaseCount, 0);
});
test('same server cannot change web origin; different server gets independent storage/key/identity', async t => {
  const f = fixture(t); const first = await f.runtime.start(CONFIG); await f.runtime.stop();
  const before = fs.readFileSync(path.join(f.directory(CONFIG), 'environment.json'));
  await rejectCode(f.runtime.start({ ...CONFIG, webOrigin: 'https://other.example' }), 'DAC_ENVIRONMENT_MISMATCH');
  assert.deepEqual(fs.readFileSync(path.join(f.directory(CONFIG), 'environment.json')), before);
  const other = { ...CONFIG, serverUrl: 'https://other.example/api' };
  const second = await f.runtime.start(other);
  assert.notEqual(first.pairingCode, second.pairingCode); assert.equal(f.keys.size, 2);
  assert.notEqual(f.launches[0].options.cwd, f.launches[1].options.cwd);
});
test('recovery never generates or overwrites key/identity after missing key, decrypt failure or incomplete data', async t => {
  for (const scenario of ['missing-key', 'wrong-key', 'missing-identity', 'corrupt-identity', 'data-without-binding', 'key-without-data']) await t.test(scenario, async t => {
    const f = fixture(t); await f.runtime.start(CONFIG); await f.runtime.stop();
    const dir = f.directory(CONFIG), identity = path.join(dir, 'desktop-identity.properties');
    if (scenario === 'missing-key') f.keys.clear();
    if (scenario === 'wrong-key') for (const id of f.keys.keys()) f.keys.set(id, crypto.randomBytes(32));
    if (scenario === 'missing-identity') fs.unlinkSync(identity);
    if (scenario === 'corrupt-identity') fs.writeFileSync(identity, 'not an encrypted identity');
    if (scenario === 'data-without-binding') { fs.unlinkSync(path.join(dir, 'environment.json')); fs.writeFileSync(path.join(dir, 'data/device-ops.mv.db'), 'durable'); }
    if (scenario === 'key-without-data') fs.rmSync(dir, { recursive: true });
    const before = fs.existsSync(identity) ? fs.readFileSync(identity) : null;
    await rejectCode(f.runtime.start(CONFIG), 'DAC_RECOVERY_REQUIRED');
    assert.equal(f.counts().creates, 1); assert.equal(f.children.length, 1);
    if (before) assert.deepEqual(fs.readFileSync(identity), before); else assert.equal(fs.existsSync(identity), false);
    assert.ok(f.readBuffers.every(b => b.equals(Buffer.alloc(32))));
  });
});
test('stop during delayed vault/preflight cancels before any key creation or child launch', async t => {
  const f = fixture(t); let continueRead, entered;
  const atRead = new Promise(r => { entered = r; });
  f.vault.read = async () => { entered(); await new Promise(r => { continueRead = r; }); return null; };
  const start = rejectCode(f.runtime.start(CONFIG), 'DAC_START_CANCELLED');
  await atRead;
  assert.equal((await f.runtime.stop()).state, 'stopping');
  await rejectCode(f.runtime.start(CONFIG), 'DAC_BUSY');
  continueRead(); await start;
  assert.equal(f.runtime.status().state, 'stopped'); assert.equal(f.children.length, 0); assert.equal(f.counts().creates, 0);
  assert.equal(f.counts().releaseCount, 1);
});
test('stop waits for actual owned exit, preserves inherited lease on timeout/error, then allows restart', async t => {
  for (const signalThrows of [false, true]) await t.test(String(signalThrows), async t => {
    const f = fixture(t, { hung: true, signalThrows }); await f.runtime.start(CONFIG);
    const stopped = await f.runtime.stop(); assert.equal(stopped.state, 'stopping'); assert.equal(stopped.canStart, false);
    assert.equal(stopped.pairingCode, undefined); assert.equal(f.counts().releaseCount, 0);
    assert.deepEqual(f.children[0].kills, ['SIGTERM']); await rejectCode(f.runtime.start(CONFIG), 'DAC_BUSY');
    f.children[0].emit('exit', 0); await tick();
    assert.equal(f.runtime.status().state, 'stopped'); assert.equal(f.counts().releaseCount, 1);
  });
});
test('readiness failure and unexpected exit never expose false READY or release a living child lock', async t => {
  const f = fixture(t, { wrongEnvironment: true, hung: true });
  await rejectCode(f.runtime.start(CONFIG), 'DAC_START_FAILED');
  assert.equal(f.runtime.status().state, 'stopping'); assert.equal(f.counts().releaseCount, 0);
  assert.equal(f.runtime.status().pairingCode, undefined); assert.ok(f.readBuffers.every(b => b.equals(Buffer.alloc(32))));
  f.children[0].emit('exit', 0); await tick(); assert.equal(f.counts().releaseCount, 1);
  const other = fixture(t); await other.runtime.start(CONFIG);
  other.children[0].emit('exit', 7); await tick();
  assert.equal(other.runtime.status().state, 'stopped'); assert.equal(other.runtime.status().reason, 'DAC_PROCESS_EXITED');
});
test('stale metadata cleared only under lock; durable H2 bytes and identity persist through stop/restart', async t => {
  const f = fixture(t); await f.runtime.start(CONFIG); await f.runtime.stop();
  const dir = f.directory(CONFIG), runtime = path.join(dir, 'desktop-runtime.properties'), db = path.join(dir, 'data/device-ops.mv.db');
  fs.writeFileSync(runtime, 'stale metadata', { mode: 0o600 }); fs.writeFileSync(`${runtime}.new`, 'stale pending', { mode: 0o600 }); fs.writeFileSync(db, 'immutable offline/outbox fixture', { mode: 0o600 });
  await f.runtime.start(CONFIG);
  assert.equal(fs.existsSync(runtime), false); assert.equal(fs.existsSync(`${runtime}.new`), false);
  assert.equal(fs.readFileSync(db, 'utf8'), 'immutable offline/outbox fixture');
  await f.runtime.stop(); assert.equal(fs.readFileSync(db, 'utf8'), 'immutable offline/outbox fixture');
});
test('untrusted data symlinks and permissions block before launching without repairing or removing data', async t => {
  for (const scenario of ['root-mode', 'db-symlink', 'binding-mode']) await t.test(scenario, async t => {
    const f = fixture(t); await f.runtime.start(CONFIG); await f.runtime.stop();
    const dir = f.directory(CONFIG);
    if (scenario === 'root-mode') fs.chmodSync(f.dataRoot, 0o755);
    if (scenario === 'db-symlink') fs.symlinkSync('/etc/passwd', path.join(dir, 'data/device-ops.mv.db'));
    if (scenario === 'binding-mode') fs.chmodSync(path.join(dir, 'environment.json'), 0o644);
    await rejectCode(f.runtime.start(CONFIG), scenario === 'binding-mode' ? 'DAC_RECOVERY_REQUIRED' : 'DAC_DATA_UNTRUSTED');
    assert.equal(f.children.length, 1);
    if (scenario === 'root-mode') assert.equal(fs.statSync(f.dataRoot).mode & 0o777, 0o755);
    if (scenario === 'db-symlink') assert.equal(fs.readlinkSync(path.join(dir, 'data/device-ops.mv.db')), '/etc/passwd');
  });
});
test('all exported errors are fixed safe codes, not runtime data', () => {
  assert.ok(Object.isFrozen(DAC_ERROR_CODES)); assert.ok(DAC_ERROR_CODES.every(code => /^DAC_[A-Z_]+$/.test(code)));
});

test('separate coordinators compete for same lock before key use and cannot kill each other', async t => {
  const f = fixture(t); await f.runtime.start(CONFIG);
  const contender = createDacRuntime(f.args), reads = f.counts().vaultReads;
  await rejectCode(contender.start(CONFIG), 'DAC_LOCK_UNAVAILABLE');
  assert.equal(f.counts().vaultReads, reads); await contender.stop();
  assert.deepEqual(f.children[0].kills, []); assert.equal(f.counts().releaseCount, 0);
  await f.runtime.stop(); assert.equal((await contender.start(CONFIG)).state, 'ready');
  await contender.stop();
});
test('spawn throw is sanitized, wipes environment, releases lock and preserves provisioned identity', async t => {
  const f = fixture(t); let captured;
  f.lockHost.spawn = (_command, _args, options) => { captured = options.env; throw Error('secret /private/path'); };
  await rejectCode(f.runtime.start(CONFIG), 'DAC_START_FAILED');
  assert.equal(captured.DEVICE_OPS_CREDENTIAL_MASTER_KEY, undefined);
  assert.equal(f.counts().releaseCount, 1); assert.equal(f.leases.size, 0);
  assert.ok(fs.existsSync(path.join(f.directory(CONFIG), 'desktop-identity.properties')));
  assert.ok(f.readBuffers.every(b => b.equals(Buffer.alloc(32))));
  assert.equal(JSON.stringify(f.runtime.status()).includes('secret'), false);
});
test('stop during pending lock acquisition waits for acquire then releases without key or child', async t => {
  const f = fixture(t); const acquire = f.lockHost.acquireLock;
  let resume, entered;
  const waiting = new Promise(resolve => { entered = resolve; });
  f.lockHost.acquireLock = async directory => { entered(); await new Promise(resolve => { resume = resolve; }); return acquire(directory); };
  const starting = rejectCode(f.runtime.start(CONFIG), 'DAC_START_CANCELLED');
  await waiting; assert.equal((await f.runtime.stop()).state, 'stopping');
  resume(); await starting;
  assert.equal(f.runtime.status().state, 'stopped'); assert.equal(f.counts().releaseCount, 1);
  assert.equal(f.counts().vaultReads, 0); assert.equal(f.children.length, 0);
});
test('uncertain lifetime close remains blocked and never leaks raw errors or falsely reports stopped', async t => {
  const f = fixture(t); const acquire = f.lockHost.acquireLock;
  f.lockHost.acquireLock = async directory => { await acquire(directory); return () => { throw Error('private close error'); }; };
  await f.runtime.start(CONFIG);
  f.children[0].emit('exit', 0); await tick();
  assert.equal(f.runtime.status().state, 'stopping'); assert.equal(f.runtime.status().reason, 'DAC_LOCK_UNAVAILABLE');
  assert.equal(f.runtime.status().canStart, false);
  await rejectCode(f.runtime.start(CONFIG), 'DAC_BUSY');
});

test('an uncertain release is not retried by overlapping supervisor/runtime error cleanup', async t => {
  const f = fixture(t); let closes = 0;
  const acquire = f.lockHost.acquireLock;
  f.lockHost.acquireLock = async directory => { await acquire(directory); return () => { closes++; throw Error('uncertain close'); }; };
  f.lockHost.spawn = () => { throw Error('synthetic spawn refused'); };
  await rejectCode(f.runtime.start(CONFIG), 'DAC_LOCK_UNAVAILABLE');
  assert.equal(closes, 1); assert.equal(f.runtime.status().state, 'stopping');
  assert.equal(f.runtime.status().canStart, false);
});
