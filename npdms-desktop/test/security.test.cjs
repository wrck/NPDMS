'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { serverOrigin, isSameOrigin, secureWebPreferences, requireTrustedSender } = require('../src/security.cjs');

test('only a credential-free HTTPS origin is accepted', () => {
  assert.equal(serverOrigin('https://npdms.example:443'), 'https://npdms.example');
  for (const url of ['http://npdms.example', 'file:///tmp/app', 'javascript:alert(1)', 'https://user:secret@npdms.example', 'https://npdms.example/path', 'https://npdms.example/?token=x', 'https://npdms.example/#x', null]) {
    assert.throws(() => serverOrigin(url));
  }
});
test('navigation rejects lookalikes, credentials, other ports and protocols', () => {
  const origin = 'https://npdms.example';
  assert.equal(isSameOrigin(origin + '/projects?id=1', origin), true);
  for (const url of ['https://npdms.example.evil/path', 'https://npdms.example:444', 'https://user@npdms.example', 'http://npdms.example', 'javascript:alert(1)', 'file:///tmp/app']) {
    assert.equal(isSameOrigin(url, origin), false);
  }
});
test('remote renderer has no Node or preload capability and remains sandboxed', () => {
  const prefs = secureWebPreferences();
  assert.equal(prefs.preload, undefined);
  assert.equal(prefs.contextIsolation, true); assert.equal(prefs.sandbox, true);
  assert.equal(prefs.webSecurity, true); assert.equal(prefs.nodeIntegration, false);
  assert.equal(prefs.nodeIntegrationInSubFrames, false); assert.equal(prefs.webviewTag, false);
});
test('IPC requires exact shell webContents, main frame and URL', () => {
  const frame = { url: 'file:///app/index.html' };
  const webContents = { mainFrame: frame };
  const window = { webContents, isDestroyed: () => false };
  const event = { sender: webContents, senderFrame: frame };
  requireTrustedSender(event, window, frame.url);
  assert.throws(() => requireTrustedSender({ ...event, sender: {} }, window, frame.url));
  assert.throws(() => requireTrustedSender({ ...event, senderFrame: { url: frame.url } }, window, frame.url));
  assert.throws(() => requireTrustedSender(event, window, 'https://npdms.example'));
});
