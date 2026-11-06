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
async function fixture() {
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
  vm.runInNewContext(fs.readFileSync(main, 'utf8'), { require: name => name === 'electron' ? electron : name === 'node:fs' ? { ...fs, existsSync: file => file.endsWith('frontend/index.html') || fs.existsSync(file) } : realRequire(name), __dirname: path.dirname(main), console });
  await tick();
  const event = { sender: windows[0].webContents, senderFrame: windows[0].webContents.mainFrame };
  return { handlers, windows, sessions, event };
}
test('main exposes narrow sender-checked IPC and refuses insecure keyring trial start', async () => {
  const f = await fixture();
  assert.deepEqual([...f.handlers.keys()].sort(), ['desktop:open-platform', 'desktop:status', 'desktop:trial-add', 'desktop:trial-list', 'desktop:trial-resolve', 'desktop:trial-start', 'desktop:trial-sync']);
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
