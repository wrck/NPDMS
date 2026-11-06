'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const html = fs.readFileSync(path.join(__dirname, '../resources/index.html'), 'utf8');
const script = fs.readFileSync(path.join(__dirname, '../resources/renderer.js'), 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup(t, overrides = {}) {
  const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'file:///app/resources/index.html' });
  t.after(() => dom.window.close());
  const api = {
    status: async () => ({ ok: true, data: { version: '0.1.0', baseline: 'd4f6918', frontend: { bundled: true }, offline: { enabled: false, reason: '真实离线业务未启用' }, dac: { state: 'not-configured' }, trial: { state: 'not-started', keyVaultAvailable: false } } }),
    openPlatform: async () => ({ ok: true }), dacStart: async () => ({ ok: true }), dacStop: async () => ({ ok: true }), trialStart: async () => ({ ok: true, data: { state: 'active' } }),
    trialList: async () => ({ ok: true, data: { drafts: [], queue: [] } }),
    trialAdd: async () => ({ ok: true }), trialSync: async () => ({ ok: true, data: { state: 'accepted' } }),
    trialResolve: async () => ({ ok: true }), ...overrides
  };
  dom.window.npdmsDesktop = api; dom.window.eval(script);
  return { window: dom.window, get: id => dom.window.document.getElementById(id), api };
}
test('shell labels synthetic scope and leaves real offline access disabled', async t => {
  const f = setup(t); await tick();
  assert.match(f.get('offline').textContent, /未启用/);
  assert.match(f.get('trial-state').textContent, /不可用/);
  assert.equal(f.get('trial-controls').hidden, true);
  assert.match(f.window.document.body.textContent, /不修改真实记录/);
  assert.match(f.get('version').textContent, /内置 Vue 已装配/);
});
test('repeated platform submits share one pending attempt and recover after failure', async t => {
  let finish, calls = 0;
  const f = setup(t, { openPlatform: () => { calls++; return new Promise(resolve => { finish = resolve; }); } });
  f.get('origin').value = 'https://npdms.example';
  const submit = () => f.get('connect').dispatchEvent(new f.window.Event('submit', { bubbles: true, cancelable: true }));
  submit(); submit(); assert.equal(calls, 1); assert.equal(f.get('open').disabled, true);
  finish({ ok: false, code: 'FRONTEND_NOT_BUILT' }); await tick();
  assert.equal(f.get('open').disabled, false); assert.match(f.get('connection').textContent, /尚未装配/);
  submit(); assert.equal(calls, 2); finish({ ok: true }); await tick(); assert.match(f.get('connection').textContent, /已打开/);
});
test('trial is explicit, fixture-only and conflict resolution requires a click', async t => {
  let added, resolved;
  const f = setup(t, {
    trialAdd: async input => { added = input; return { ok: true }; },
    trialList: async () => ({ ok: true, data: { drafts: [{}], queue: [{ id: '01234567-test', state: 'conflict', attempts: 1 }] } }),
    trialResolve: async input => { resolved = input; return { ok: true, data: { action: input.action } }; }
  });
  f.get('trial-start').click(); await tick(); assert.equal(f.get('trial-controls').hidden, false);
  assert.equal(resolved, undefined);
  f.get('scenario').value = 'retry'; f.get('trial-add').click(); await tick();
  assert.deepEqual(JSON.parse(JSON.stringify(added)), { fixtureId: 'draft-alpha', scenario: 'retry' });
  f.get('trial-queue').querySelector('button').click(); await tick();
  assert.deepEqual(JSON.parse(JSON.stringify(resolved)), { id: '01234567-test', action: 'retryAccepted' });
  assert.match(f.get('trial-result').textContent, /未发起任何业务网络请求/);
});
test('expired trial and rejected start retain an honest failure state', async t => {
  const f = setup(t, { trialStart: async () => { throw Error('private internal detail'); }, trialList: async () => ({ ok: false, code: 'AUTH_EXPIRED' }) });
  f.get('trial-start').click(); await tick(); assert.equal(f.get('trial-start').disabled, false);
  assert.match(f.get('trial-state').textContent, /保持关闭/); assert.equal(f.get('trial-controls').hidden, true);
  f.get('trial-refresh').click(); await tick(); assert.match(f.get('trial-result').textContent, /已过期/);
  assert.equal(f.window.document.body.textContent.includes('private internal detail'), false);
});

test('DAC controls remain closed for missing payload and start only on an explicit click', async t => {
  let calls = 0;
  const f = setup(t, { dacStart: async () => { calls++; return { ok: false }; } });
  await tick(); assert.equal(calls, 0); assert.equal(f.get('dac-start').disabled, true); assert.equal(f.get('dac-stop').disabled, true);
  f.get('dac-start').click(); await tick(); assert.equal(calls, 0);
});
test('DAC pending clicks are serialized and show only the safe service state', async t => {
  let finish, calls = 0, stopped = 0;
  const data = { version: '0.1.0', baseline: 'd4f6918', frontend: { bundled: true }, offline: { reason: '未启用' }, trial: { state: 'not-started' }, dac: { state: 'not-started', canStart: true, canStop: false } };
  const f = setup(t, { status: async () => ({ ok: true, data }), dacStart: input => { assert.equal(input, 'https://npdms.example'); calls++; return new Promise(resolve => { finish = resolve; }); }, dacStop: async () => { stopped++; data.dac = { state: 'stopped', canStart: true, canStop: false }; return { ok: true }; } });
  await tick(); assert.equal(calls, 0); f.get('origin').value = 'https://npdms.example';
  f.get('dac-start').click(); f.get('dac-start').click(); assert.equal(calls, 1); assert.equal(f.get('dac-start').disabled, true);
  data.dac = { state: 'ready', canStart: false, canStop: true, pairingCode: 'ABCD' }; finish({ ok: true }); await tick();
  assert.match(f.get('dac-action').textContent, /未发起配对/); assert.match(f.get('dac').textContent, /业务链路尚待验证/); assert.match(f.get('dac').textContent, /ABCD/);
  f.get('dac-stop').click(); await tick(); assert.equal(stopped, 1); assert.equal(f.get('dac-stop').disabled, true);
});

test('Windows controls state native-service prerequisite and disconnect semantics', async t => {
  const f = setup(t, { status: async () => ({ ok: true, data: { version: '0.1.0', baseline: 'd4f6918', frontend: { bundled: true }, offline: { reason: '未启用' }, dac: { mode: 'windows-installed-service', state: 'stopped', canStart: true, canStop: false, reason: 'DAC_WINDOWS_DISCONNECTED' }, trial: { state: 'not-started', keyVaultAvailable: false } } }) });
  await tick();
  assert.equal(f.get('dac-stop').textContent, '断开桌面连接');
  assert.match(f.get('dac-help').textContent, /须先安装原生 Windows DAC/);
  assert.match(f.get('dac-help').textContent, /不会停止该服务/);
  assert.match(f.get('dac').textContent, /桌面已断开/);
});
