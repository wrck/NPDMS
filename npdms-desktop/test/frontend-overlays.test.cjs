'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const { spawnSync } = require('node:child_process');
const { webcrypto } = require('node:crypto');
const api = require('../scripts/frontend-overlays.cjs');
const root = path.resolve(__dirname, '..');
const source = process.env.NPDMS_FRONTEND_SOURCE || path.resolve(root, '../../npdms-source-baseline');
const overrideRoot = path.join(root, 'frontend-overrides');
const hasBaseline = fs.existsSync(path.join(source, api.FRONTEND, 'package.json'));
const optional = hasBaseline ? {} : { skip: 'Pinned source checkout unavailable; set NPDMS_FRONTEND_SOURCE to run source/integration checks.' };
const temp = () => fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-overlay-'));
function runGit(repo, args) {
  const result = spawnSync('git', args, { cwd: repo, env: { ...process.env, GIT_NO_LAZY_FETCH: '1' }, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout;
}
function filesUnder(directory, prefix = '') {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const relative = prefix + entry.name;
    return entry.isDirectory() ? filesUnder(path.join(directory, entry.name), relative + '/') : [relative];
  }).sort();
}

test('declared overlays are mandatory and bound to exact full-source hashes', () => {
  const overrides = api.readOverrides(overrideRoot);
  assert.equal(overrides.length, 2);
  assert.deepEqual(filesUnder(overrideRoot), api.OVERLAYS.map(item => item.path).sort());
  assert.equal(new Set(overrides.map(item => item.path)).size, 2);
  const directory = temp();
  try {
    assert.throws(() => api.readOverrides(directory), /FRONTEND_OVERRIDE_MISSING/);
    fs.cpSync(overrideRoot, directory, { recursive: true });
    fs.appendFileSync(path.join(directory, api.OVERLAYS[0].path), '\n// mutation\n');
    assert.throws(() => api.readOverrides(directory), /FRONTEND_OVERRIDE_HASH_MISMATCH/);
  } finally { fs.rmSync(directory, { recursive: true, force: true }); }
});

test('all source and unchanged-contract hashes fail closed on omission or mutation', () => {
  const expected = [...api.OVERLAYS, ...api.UNCHANGED_CONTRACTS];
  api.verifySourceHashes(expected);
  for (const item of expected) {
    assert.throws(() => api.verifySourceHashes(expected.filter(candidate => candidate !== item)), /FRONTEND_SOURCE_HASH_MISMATCH/);
    assert.throws(() => api.verifySourceHashes(expected.map(candidate => candidate === item ? { ...candidate, sha256: '0'.repeat(64) } : candidate)), /FRONTEND_SOURCE_HASH_MISMATCH/);
  }
});

test('staging uses immutable tracked blobs, exactly two overrides, and leaves the baseline unchanged', optional, () => {
  const directory = temp();
  const before = runGit(source, ['status', '--porcelain=v1', '--untracked-files=all']);
  const original = api.readBaselineFiles(source);
  try {
    const stage = path.join(directory, 'source');
    api.stageFrontend({ source, stage });
    fs.writeFileSync(path.join(stage, 'stale-private.txt'), 'do not copy');
    const result = api.stageFrontend({ source, stage });
    assert.deepEqual(filesUnder(stage), original.map(item => item.path).sort());
    assert.equal(fs.existsSync(path.join(stage, 'node_modules')), false);
    assert.equal(fs.existsSync(path.join(stage, '.npmrc')), false);
    for (const file of original) {
      const override = api.OVERLAYS.find(item => item.path === file.path);
      assert.equal(api.sha256(fs.readFileSync(path.join(stage, file.path))), override ? override.overrideSha256 : file.sha256, file.path);
    }
    assert.equal(result.provenance.overlayVersion, api.OVERLAY_VERSION);
    assert.deepEqual(result.provenance.sources, api.OVERLAYS.map(item => ({ path: item.path, sha256: item.sha256, overridePath: `frontend-overrides/${item.path}`, overrideSha256: item.overrideSha256 })));
    assert.deepEqual(result.provenance.unchangedContracts, api.UNCHANGED_CONTRACTS);
    assert.equal(result.provenance.sourceTreeSha256, api.makeProvenance(original).sourceTreeSha256);
    assert.notEqual(result.provenance.sourceTreeSha256, api.makeProvenance(original.slice(1)).sourceTreeSha256);
    assert.throws(() => api.stageFrontend({ source, stage: path.join(source, 'must-not-write') }), /STAGING_INSIDE_BASELINE_NOT_ALLOWED/);
    assert.equal(fs.existsSync(path.join(source, 'must-not-write')), false);
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
    assert.equal(runGit(source, ['status', '--porcelain=v1', '--untracked-files=all']), before);
    for (const expected of [...api.OVERLAYS, ...api.UNCHANGED_CONTRACTS]) assert.equal(api.sha256(fs.readFileSync(path.join(source, api.FRONTEND, expected.path))), expected.sha256);
  }
});

test('staging refuses root, protected ancestors, unrelated directories, and symlinked destinations', optional, () => {
  const directory = temp();
  try {
    const unrelated = path.join(directory, 'important'); fs.mkdirSync(unrelated);
    const sentinel = path.join(unrelated, 'keep.txt'); fs.writeFileSync(sentinel, 'keep');
    for (const stage of ['/', root, path.dirname(source), overrideRoot, unrelated]) {
      assert.throws(() => api.stageFrontend({ source, stage }), /UNSAFE_FRONTEND_STAGING_PATH|UNMARKED_FRONTEND_STAGING_DIRECTORY/);
      assert.equal(fs.readFileSync(sentinel, 'utf8'), 'keep');
    }
    const linked = path.join(directory, 'linked'); fs.symlinkSync(source, linked, 'dir');
    assert.throws(() => api.stageFrontend({ source, stage: linked }), /UNSAFE_FRONTEND_STAGING_PATH/);
    assert.throws(() => api.stageFrontend({ source, stage: path.join(linked, 'new-source') }), /STAGING_INSIDE_BASELINE_NOT_ALLOWED/);
    assert.equal(fs.existsSync(path.join(source, 'new-source')), false);
    const destination = path.join(directory, 'marked');
    const marker = path.join(directory, '.marked.npdms-frontend-stage.json');
    const outside = path.join(directory, 'must-not-create');
    fs.symlinkSync(outside, marker);
    assert.throws(() => api.stageFrontend({ source, stage: destination }), /INVALID_FRONTEND_STAGING_MARKER/);
    assert.equal(fs.existsSync(outside), false);
  } finally { fs.rmSync(directory, { recursive: true, force: true }); }
});

test('wrong HEAD, tracked dirt, and local env files are rejected; other untracked inputs are never staged', optional, () => {
  const directory = temp();
  const fixture = path.join(directory, 'baseline');
  try {
    fs.mkdirSync(fixture);
    runGit(fixture, ['init', '--quiet']);
    const objects = path.resolve(source, runGit(source, ['rev-parse', '--git-path', 'objects']).trim());
    fs.writeFileSync(path.join(fixture, '.git/objects/info/alternates'), objects + '\n');
    runGit(fixture, ['update-ref', 'HEAD', api.BASELINE]);
    runGit(fixture, ['sparse-checkout', 'set', '--no-cone', `/${api.FRONTEND}/`]);
    runGit(fixture, ['read-tree', '--reset', '-u', api.BASELINE]);
    api.verifyBaseline(fixture);
    const file = path.join(fixture, api.FRONTEND, api.OVERLAYS[0].path);
    const bytes = fs.readFileSync(file);
    fs.appendFileSync(file, '\n// dirty\n');
    assert.throws(() => api.verifyBaseline(fixture), /CLEAN_BASELINE_REQUIRED/);
    fs.writeFileSync(file, bytes);
    const wrongHead = runGit(fixture, ['-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit-tree', `${api.BASELINE}^{tree}`, '-m', 'Fixture incorrect baseline']).trim();
    runGit(fixture, ['update-ref', 'HEAD', wrongHead]);
    assert.throws(() => api.verifyBaseline(fixture), /EXACT_BASELINE_REQUIRED/);
    runGit(fixture, ['update-ref', 'HEAD', api.BASELINE]);
    for (const parent of [fixture, path.join(fixture, api.FRONTEND)]) {
      const env = path.join(parent, '.env.desktop');
      fs.writeFileSync(env, 'VITE_SECRET=should-not-enter-the-build\n');
      assert.throws(() => api.verifyBaseline(fixture), /ENV_FILES_NOT_ALLOWED/);
      fs.unlinkSync(env);
    }
    fs.writeFileSync(path.join(fixture, api.FRONTEND, 'private-untracked.txt'), 'must not enter build');
    const result = api.stageFrontend({ source: fixture, stage: path.join(directory, 'stage') });
    assert.equal(fs.existsSync(path.join(result.cwd, 'private-untracked.txt')), false);
  } finally { fs.rmSync(directory, { recursive: true, force: true }); }
});

test('local DAC overlay changes only the two fetch addresses and retains the complete original protocol', optional, () => {
  const baseline = fs.readFileSync(path.join(source, api.FRONTEND, api.OVERLAYS[0].path), 'utf8');
  const overlay = fs.readFileSync(path.join(overrideRoot, api.OVERLAYS[0].path), 'utf8');
  const expected = baseline
    .replace('`${discovery.origin}/api/v1/desktop${options.path}`', '`/__npdms_desktop/dac/operations?port=${new URL(discovery.origin).port}&path=${encodeURIComponent(options.path)}`')
    .replace('`${origin}/api/v1/desktop/status?nonce=${nonce}`', '`/__npdms_desktop/dac/status?port=${new URL(origin).port}&nonce=${nonce}`');
  assert.equal(overlay, expected);
  assert.equal((overlay.match(/await fetch\(/g) || []).length, 2);
  const component = fs.readFileSync(path.join(overrideRoot, api.OVERLAYS[1].path), 'utf8');
  assert.doesNotMatch(component, /installer|\.msi|JSZip|downloadInstaller|downloads\/dac|ElMessage|window\.open|location\.href\s*=/i);
  assert.match(component, /桌面客户端的服务控制/);
  assert.match(component, /event\.preventDefault\(\)/);
  assert.match(component, /await connection\.finish\(attempt\.signal\)/);
  assert.match(component, /response\.json\(\)\)\?\.ok !== true/);
  assert.doesNotMatch(component, /confirmLocalDac|localStorage|node:|electron|preload|Authorization/);
  const owner = fs.readFileSync(path.join(source, api.FRONTEND, api.UNCHANGED_CONTRACTS[0].path), 'utf8');
  assert.match(owner, /created\.expiresInSeconds !== 90/);
  assert.match(owner, /return confirmLocalDac/);
});

// Execute the actual source using the pinned checkout's already installed compiler/crypto.
// These are in-process module/lifecycle tests, not Electron, network, or real-app acceptance tests.
let dependencyRequire;
let ts;
let ed25519;
let vueCompiler;
try {
  dependencyRequire = createRequire(path.join(source, api.FRONTEND, 'package.json'));
  ts = dependencyRequire('typescript');
  ({ ed25519 } = dependencyRequire('@noble/curves/ed25519'));
  vueCompiler = dependencyRequire('vue/compiler-sfc');
} catch { /* Explicit skip below when local dependencies have not been installed. */ }
const modules = ts && ed25519 && vueCompiler ? {} : { skip: 'Pinned local TypeScript/Vue/noble dependencies unavailable; no dependency fetch is performed.' };
function executeTypeScript(text, requireModule, globals = {}) {
  const output = ts.transpileModule(text, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  const context = vm.createContext({ exports, require: requireModule, Uint8Array, TextEncoder, TextDecoder, URL, AbortController, Map, Set, Error, Date, crypto: webcrypto, atob, btoa, setTimeout, clearTimeout, ...globals });
  new vm.Script(output).runInContext(context);
  return exports;
}
const environment = { serverUrl: 'http://npdms.example:8443', webOrigin: 'http://127.0.0.1:19000' };
function dacHarness() {
  const privateKey = new Uint8Array(32).fill(7);
  const publicKey = Buffer.from(ed25519.getPublicKey(privateKey)).toString('hex');
  const storage = new Map();
  const calls = [];
  let modify = data => data;
  let operation = async () => ({ ok: true, json: async () => ({ collectionId: 'collection-1' }) });
  function proof(nonce, port = 8280) {
    const data = modify({ product: 'sdp-dac', protocolVersion: 3, status: 'READY', ...environment, port, publicKey, instanceId: '11111111-1111-4111-8111-111111111111' });
    const message = `sdp-dac-discovery-v1\n${nonce}\n${data.port}\n${data.instanceId}\n3\n${data.status}\n${data.serverUrl}\n${data.webOrigin}`;
    return { ...data, signature: Buffer.from(ed25519.sign(new TextEncoder().encode(message), privateKey)).toString('base64') };
  }
  const fetch = async (address, init) => {
    calls.push({ address, init });
    assert.equal(address.startsWith('/__npdms_desktop/dac/'), true);
    const url = new URL(address, environment.webOrigin);
    assert.equal(init.credentials, 'omit'); assert.equal(init.cache, 'no-store'); assert.equal(init.redirect, 'error');
    if (url.pathname.endsWith('/status')) {
      assert.match(url.searchParams.get('nonce'), /^[a-f0-9]{64}$/);
      assert.equal(init.headers, undefined);
      const port = Number(url.searchParams.get('port'));
      assert.equal(port >= 8280 && port <= 8289, true);
      if (port !== 8280) throw new Error('refused');
      return { ok: true, json: async () => proof(url.searchParams.get('nonce'), port) };
    }
    return operation(address, init);
  };
  const dac = executeTypeScript(fs.readFileSync(path.join(overrideRoot, api.OVERLAYS[0].path), 'utf8'), name => {
    assert.equal(name, '@noble/curves/ed25519'); return { ed25519 };
  }, { fetch, window: { localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) } } });
  return { dac, storage, calls, proof, setModify: fn => { modify = fn; }, setOperation: fn => { operation = fn; } };
}

test('actual transport module keeps logical origins and requires owner proof before sending tickets', modules, async () => {
  const h = dacHarness();
  const discovered = await h.dac.discoverLocalDac(environment);
  assert.equal(discovered.state, 'connection-required');
  assert.equal(discovered.origin, 'http://127.0.0.1:8280');
  assert.equal(h.storage.size, 0);
  await assert.rejects(h.dac.localDacRequest({ environment, path: '/saved-connections', ticket: { value: 'ticket', tenantId: 1 } }));
  assert.equal(h.calls.some(call => call.init.headers), false);
  const nonce = 'a'.repeat(64);
  assert.equal((await h.dac.confirmLocalDac({ ...environment, nonce, status: h.proof(nonce) })).state, 'ready');
  assert.equal(h.storage.size, 1);
  h.calls.length = 0;
  assert.equal((await h.dac.localDacRequest({ environment, path: '/tasks/task-1/executions', ticket: { value: 'ticket', tenantId: 1 }, method: 'POST', body: { password: 'test-only' } })).collectionId, 'collection-1');
  assert.equal(h.calls.length, 2);
  const operation = h.calls[1];
  assert.equal(operation.address, '/__npdms_desktop/dac/operations?port=8280&path=%2Ftasks%2Ftask-1%2Fexecutions');
  assert.equal(operation.init.headers['X-NPDMS-DAC-Ticket'], 'ticket');
  assert.equal(operation.init.headers['tenant-id'], '1');
  assert.equal(operation.init.headers.Authorization, undefined);
});

test('actual transport module rejects tampered owner proofs and environment/port confusion', modules, async () => {
  for (const change of ['nonce', 'signature', 'environment', 'port', 'aborted']) {
    const h = dacHarness(); const nonce = 'a'.repeat(64); const status = h.proof(nonce); const controller = new AbortController();
    if (change === 'signature') status.signature = 'AA==';
    if (change === 'environment') status.webOrigin = 'http://elsewhere';
    if (change === 'port') status.port = 9000;
    if (change === 'aborted') controller.abort();
    await assert.rejects(h.dac.confirmLocalDac({ ...environment, nonce: change === 'nonce' ? 'b'.repeat(64) : nonce, status, signal: controller.signal }));
    assert.equal(h.storage.size, 0);
  }
  for (const change of ['environment', 'port']) {
    const h = dacHarness(); const nonce = 'a'.repeat(64);
    await h.dac.confirmLocalDac({ ...environment, nonce, status: h.proof(nonce) }); h.calls.length = 0;
    h.setModify(data => ({ ...data, ...(change === 'environment' ? { serverUrl: 'http://elsewhere' } : { port: 8281 }) }));
    await assert.rejects(h.dac.localDacRequest({ environment, path: '/saved-connections', ticket: { value: 'must-not-send', tenantId: 1 } }));
    assert.equal(h.calls.some(call => call.init.headers), false);
  }
});

test('actual protected request is not retried after uncertain execution and retains HTTP error meaning', modules, async () => {
  const h = dacHarness(); const nonce = 'a'.repeat(64);
  await h.dac.confirmLocalDac({ ...environment, nonce, status: h.proof(nonce) }); h.calls.length = 0;
  h.setOperation(async () => { throw new Error('uncertain'); });
  await assert.rejects(h.dac.localDacRequest({ environment, path: '/tasks/task-1/executions', ticket: { value: 'ticket', tenantId: 1 }, method: 'POST', body: { password: 'must-not-log' } }), error => {
    assert.equal(error.outcomeUnknown, true); assert.equal(JSON.stringify(error).includes('must-not-log'), false); return true;
  });
  assert.equal(h.calls.filter(call => call.address.includes('/operations?')).length, 1);
  for (const status of [400, 401, 403, 404, 409, 503]) {
    h.setOperation(async () => ({ ok: false, status }));
    await assert.rejects(h.dac.localDacRequest({ environment, path: '/saved-connections', ticket: { value: 'ticket', tenantId: 1 } }), error => error.status === status && error.outcomeUnknown === (status >= 500));
  }
});

function componentHarness({ fetch: fetcher = async () => ({ ok: true, json: async () => ({ ok: true }) }), finish = async () => ({ state: 'ready' }) } = {}) {
  const component = fs.readFileSync(path.join(overrideRoot, api.OVERLAYS[1].path), 'utf8');
  const parsed = vueCompiler.parse(component, { filename: 'LocalDacStatus.vue' });
  assert.deepEqual(parsed.errors, []);
  const compiled = vueCompiler.compileScript(parsed.descriptor, { id: 'desktop-dac-test' });
  const timers = new Map(); let nextTimer = 1; const mounted = []; const unmounted = []; const events = []; const calls = []; let cancellations = 0;
  const connection = { href: `sdp-dac://connect/${'1'.repeat(32)}?challenge=${'a'.repeat(64)}&tenant=1`, expiresAt: Date.now() + 90000, cancel: async () => { cancellations++; }, finish };
  const vue = { defineComponent: options => options, ref: value => ({ value }), computed: callback => ({ get value() { return callback(); } }), onMounted: callback => mounted.push(callback), onBeforeUnmount: callback => unmounted.push(callback) };
  const exports = executeTypeScript(compiled.content, name => {
    if (name === 'vue') return vue;
    if (name === './localDac') return { discoverLocalDac: async () => ({ state: 'connection-required' }) };
    if (name === './localDacConnection') return { prepareLocalDacConnection: async () => connection };
    throw new Error(`unexpected import ${name}`);
  }, { window: { location: { origin: environment.webOrigin } }, fetch: (...args) => { calls.push(args); return fetcher(...args); }, setTimeout: (callback, delay) => { const id = nextTimer++; timers.set(id, { callback, delay }); return id; }, clearTimeout: id => timers.delete(id) });
  const state = exports.default.setup({ serverUrl: environment.serverUrl }, { expose() {}, emit: (...args) => events.push(args) });
  return { state, calls, timers, events, connection, get cancellations() { return cancellations; }, mount: async () => { mounted.forEach(callback => callback()); for (let i = 0; i < 8; i++) await Promise.resolve(); }, unmount: () => unmounted.forEach(callback => callback()) };
}

test('actual compiled Vue setup submits only on click and waits for bridge ACK then original owner finish', modules, async () => {
  let ack; let owner; let finishes = 0;
  const h = componentHarness({ fetch: () => new Promise(resolve => { ack = resolve; }), finish: () => { finishes++; return new Promise(resolve => { owner = resolve; }); } });
  await h.mount();
  assert.equal(h.calls.length, 0); assert.equal(finishes, 0);
  let prevented = 0;
  const pending = h.state.connect({ preventDefault() { prevented++; } });
  assert.equal(prevented, 1); assert.equal(h.calls.length, 1); assert.equal(finishes, 0);
  const [url, init] = h.calls[0];
  assert.equal(url, '/__npdms_desktop/dac/connect'); assert.equal(init.method, 'POST');
  assert.equal(init.credentials, 'omit'); assert.equal(init.cache, 'no-store'); assert.equal(init.redirect, 'error');
  assert.deepEqual(JSON.parse(init.body), { uri: h.connection.href });
  assert.equal(init.headers.Authorization, undefined);
  assert.equal([...h.timers.values()].some(timer => timer.delay === 10000), true);
  ack({ ok: true, json: async () => ({ ok: true }) });
  for (let i = 0; i < 8; i++) await Promise.resolve();
  assert.equal(finishes, 1); assert.equal(h.events.some(event => event[1] === true), false);
  owner({ state: 'ready' }); await pending;
  assert.equal(h.events.at(-1)[1], true); assert.equal(h.cancellations, 1);
  h.unmount(); assert.equal(h.timers.size, 0);
});

test('actual compiled Vue setup rejects bad ACK and never starts stale polling after cancellation', modules, async () => {
  for (const response of [{ ok: false }, { ok: true, json: async () => ({ ok: false }) }, { ok: true, json: async () => ({ ready: true }) }]) {
    let finishes = 0;
    const h = componentHarness({ fetch: async () => response, finish: async () => { finishes++; return { state: 'ready' }; } });
    await h.mount(); await h.state.connect({ preventDefault() {} });
    assert.equal(finishes, 0); assert.equal(h.events.some(event => event[1] === true), false);
    assert.match(h.state.connectionError.value, /桌面客户端的服务控制/); h.unmount();
  }
  for (const cancel of ['cancel', 'unmount', 'repeat']) {
    let ack; let finishes = 0;
    const h = componentHarness({ fetch: () => new Promise(resolve => { ack = resolve; }), finish: async () => { finishes++; return { state: 'ready' }; } });
    await h.mount(); const pending = h.state.connect({ preventDefault() {} });
    if (cancel === 'unmount') h.unmount();
    else if (cancel === 'repeat') await h.state.connect({ preventDefault() {} });
    else h.state.cancelConnection();
    assert.equal(h.calls[0][1].signal.aborted, true);
    ack({ ok: true, json: async () => ({ ok: true }) }); await pending;
    assert.equal(h.calls.length, 1); assert.equal(finishes, 0); assert.equal(h.events.some(event => event[1] === true), false); h.unmount();
  }
});

test('actual compiled Vue setup bounds submission timeout and cancels late owner results', modules, async () => {
  const h = componentHarness({ fetch: (_url, init) => new Promise((_resolve, reject) => init.signal.addEventListener('abort', () => reject(new Error('aborted')))) });
  await h.mount(); const pending = h.state.connect({ preventDefault() {} });
  const timer = [...h.timers.values()].find(item => item.delay === 10000); assert.ok(timer); timer.callback(); await pending;
  assert.equal(h.events.some(event => event[1] === true), false); h.unmount();
  let owner; let ownerSignal;
  const late = componentHarness({ finish: signal => { ownerSignal = signal; return new Promise(resolve => { owner = resolve; }); } });
  await late.mount(); const finishing = late.state.connect({ preventDefault() {} });
  for (let i = 0; i < 8; i++) await Promise.resolve();
  late.unmount(); assert.equal(ownerSignal.aborted, true);
  owner({ state: 'ready' }); await finishing;
  assert.equal(late.events.some(event => event[1] === true), false);
});


test('actual compiled Vue setup preserves challenge expiry and idle replacement without auto-submission', modules, async () => {
  const h = componentHarness();
  await h.mount();
  assert.equal(h.state.prepared.value, h.connection);
  const refresh = [...h.timers.values()].find(item => item.delay > 80000 && item.delay <= 85000);
  assert.ok(refresh);
  refresh.callback();
  for (let i = 0; i < 8; i++) await Promise.resolve();
  assert.equal(h.cancellations, 1); assert.equal(h.calls.length, 0);
  h.connection.expiresAt = Date.now() - 1;
  let prevented = 0;
  await h.state.connect({ preventDefault() { prevented++; } });
  assert.equal(prevented, 1); assert.equal(h.calls.length, 0);
  assert.equal(h.events.some(event => event[1] === true), false);
  h.unmount();
});
