'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const { EventEmitter } = require('node:events');
const { createRequire } = require('node:module');
const main = path.join(__dirname, '../src/main.cjs');
const realRequire = createRequire(main);
const tick = () => new Promise(resolve => setImmediate(resolve));
async function fixture(overrides = {}) {
  const dacCalls = [], dacOptions = [];
  const runtime = { status: () => ({ state: 'blocked', reason: 'DAC_PAYLOAD_INCOMPLETE', canStart: false, canStop: false }), start: async config => { dacCalls.push(config); return { state: 'ready' }; }, stop: async () => ({ state: 'stopped' }), ...overrides };
  const handlers = new Map(), windows = [], sessions = [];
  const makeSession = () => { const s = new EventEmitter(); s.setPermissionRequestHandler = fn => { s.permissions = fn; }; s.setPermissionCheckHandler = fn => { s.check = fn; };
    s.webRequest = { onBeforeRequest: fn => { s.request = fn; } }; s.protocol = { handle: (scheme, fn) => { s.scheme = scheme; s.handle = fn; }, unhandle: () => {} }; s.clearStorageData = async () => {}; sessions.push(s); return s; };
  class Window extends EventEmitter {
    constructor(options) { super(); this.options = options; this.webContents = new EventEmitter(); this.webContents.mainFrame = { url: '' }; this.webContents.setWindowOpenHandler = fn => { this.popup = fn; }; windows.push(this); }
    isDestroyed() { return !!this.destroyed; } isMinimized() { return false; } restore() {} focus() {} show() {}
    async loadURL(url) { this.url = url; this.webContents.mainFrame.url = url; }
    close() { this.destroyed = true; this.emit('closed'); }
  }
  const app = new EventEmitter(); Object.assign(app, { requestSingleInstanceLock: () => true, whenReady: async () => {}, getVersion: () => '0.1.0', getPath: () => '/unused-test-userdata', quit() {}, exit() { throw Error('unexpected exit'); } });
  const electron = { app, BrowserWindow: Window, ipcMain: { handle: (name, fn) => handlers.set(name, fn) }, session: { defaultSession: makeSession(), fromPartition: name => { assert.equal(name.startsWith('persist:'), false); return makeSession(); } }, Menu: { setApplicationMenu() {} }, safeStorage: { isEncryptionAvailable: () => false } };
  vm.runInNewContext(fs.readFileSync(main, 'utf8'), { require: name => name === 'electron' ? electron : name === './dac/runtime.cjs' ? { DAC_ERROR_CODES: ['DAC_RESOURCES_MISSING', 'DAC_RECOVERY_REQUIRED'], createDacRuntime: options => { dacOptions.push(options); return runtime; } } : name === 'node:fs' ? { ...fs, existsSync: file => file.endsWith('frontend/index.html') || fs.existsSync(file) } : realRequire(name), __dirname: path.dirname(main), console });
  await tick();
  const event = { sender: windows[0].webContents, senderFrame: windows[0].webContents.mainFrame };
  return { handlers, windows, sessions, event, app, dacCalls, dacOptions };
}
test('main exposes narrow sender-checked IPC and refuses insecure keyring trial start', async () => {
  const f = await fixture();
  assert.deepEqual([...f.handlers.keys()].sort(), ['desktop:dac-start', 'desktop:dac-stop', 'desktop:open-platform', 'desktop:status', 'desktop:trial-add', 'desktop:trial-list', 'desktop:trial-resolve', 'desktop:trial-start', 'desktop:trial-sync']);
  const status = await f.handlers.get('desktop:status')(f.event); assert.equal(status.data.offline.enabled, false);
  await assert.rejects(f.handlers.get('desktop:status')({ sender: {}, senderFrame: f.event.senderFrame }), /UNTRUSTED/);
  const trial = await f.handlers.get('desktop:trial-start')(f.event); assert.equal(trial.ok, false); assert.equal(trial.code, 'OPERATION_UNAVAILABLE');
});
test('packaged Vue window is unprivileged, uses HTTPS handler and denies cross-origin requests', async () => {
  const f = await fixture(); const result = await f.handlers.get('desktop:open-platform')(f.event, 'https://npdms.example');
  assert.equal(result.ok, true); const window = f.windows[1], ses = f.sessions[1];
  assert.equal(window.options.webPreferences.preload, undefined); assert.equal(window.options.webPreferences.sandbox, true); assert.equal(window.options.webPreferences.nodeIntegration, false);
  assert.equal(ses.scheme, 'https'); assert.equal(window.url, 'https://npdms.example'); assert.equal(window.popup().action, 'deny');
  let verdict; ses.request({ url: 'https://evil.example', resourceType: 'script' }, value => { verdict = value; }); assert.equal(verdict.cancel, true);
  ses.request({ url: 'https://npdms.example/assets/index.js', resourceType: 'script' }, value => { verdict = value; }); assert.equal(verdict.cancel, false);
});

test('DAC main wiring accepts only a trusted-shell HTTPS origin and fixed local resources', async () => {
  const f = await fixture();
  assert.equal(f.dacOptions.length, 1);
  assert.equal(f.dacOptions[0].resourcesDirectory, path.join(__dirname, '../resources/dac'));
  assert.equal(f.dacOptions[0].dataRoot, '/unused-test-userdata/dac-service-v1');
  const start = f.handlers.get('desktop:dac-start');
  await assert.rejects(start({ sender: {}, senderFrame: f.event.senderFrame }, 'https://npdms.example'), /UNTRUSTED/);
  for (const value of ['http://npdms.example', 'https://npdms.example/path', { javaPath: '/evil', serverUrl: 'https://npdms.example' }]) assert.equal((await start(f.event, value)).ok, false);
  assert.equal(f.dacCalls.length, 0);
  const result = await start(f.event, 'https://npdms.example:443');
  assert.equal(result.ok, true);
  assert.deepEqual(JSON.parse(JSON.stringify(f.dacCalls)), [{ serverUrl: 'https://npdms.example', webOrigin: 'https://npdms.example' }]);
  assert.equal((await f.handlers.get('desktop:status')(f.event)).data.dac.canStart, false);
});
test('shutdown waits for owned DAC exit and keeps the shell while service is stopping', async () => {
  let state = 'stopping', stops = 0, quits = 0, prevented = 0;
  const f = await fixture({ stop: async () => { stops++; return { state }; } });
  f.app.quit = () => { quits++; };
  const event = { preventDefault: () => { prevented++; } };
  f.app.emit('before-quit', event); f.app.emit('before-quit', event);
  await tick(); assert.equal(stops, 1); assert.equal(quits, 0); assert.equal(prevented, 2);
  f.windows[0].emit('close', event); assert.equal(prevented, 3); assert.equal(quits, 1);
  state = 'stopped'; f.app.emit('before-quit', event); await tick();
  assert.equal(stops, 2); assert.equal(quits, 2);
  f.app.emit('before-quit', event); f.windows[0].emit('close', event);
  assert.equal(prevented, 4); assert.equal(stops, 2);
});
test('DAC internal failures are sanitized at the shell IPC boundary', async () => {
  const f = await fixture({ start: async () => { throw Error('private filesystem or key detail'); } });
  const result = await f.handlers.get('desktop:dac-start')(f.event, 'https://npdms.example');
  assert.deepEqual(JSON.parse(JSON.stringify(result)), { ok: false, code: 'OPERATION_UNAVAILABLE' });
});

test('preload exposes only fixed DAC operations without raw IPC or Node objects', () => {
  const exposed = [], invocations = [];
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/preload.cjs'), 'utf8'), { require: name => {
    assert.equal(name, 'electron');
    return { contextBridge: { exposeInMainWorld: (name, api) => exposed.push({ name, api }) }, ipcRenderer: { invoke: (...args) => invocations.push(args) } };
  } });
  assert.equal(exposed.length, 1); assert.equal(exposed[0].name, 'npdmsDesktop');
  const api = exposed[0].api; assert.equal(Object.isFrozen(api), true);
  assert.deepEqual(Object.keys(api).sort(), ['dacStart', 'dacStop', 'openPlatform', 'status', 'trialAdd', 'trialList', 'trialResolve', 'trialStart', 'trialSync']);
  api.dacStart('https://npdms.example'); api.dacStop();
  assert.deepEqual(invocations, [['desktop:dac-start', 'https://npdms.example'], ['desktop:dac-stop']]);
});
