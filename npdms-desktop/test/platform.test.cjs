'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { createPlatformHandler, isAllowedPlatformRequest } = require('../src/platform.cjs');
const origin = 'https://pms.example:8443';
async function fixture(t, options = {}) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'npdms-platform-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const frontendDirectory = path.join(root, 'frontend');
  await fs.mkdir(path.join(frontendDirectory, 'assets'), { recursive: true });
  await fs.writeFile(path.join(frontendDirectory, 'index.html'), '<html><script src="/assets/app.js"></script></html>');
  await fs.writeFile(path.join(frontendDirectory, 'assets/app.js'), 'console.log("fixture")');
  return { root, frontendDirectory, handler: createPlatformHandler({ origin, frontendDirectory, fetch: () => { throw new Error('Unexpected network'); }, ...options }) };
}
const req = (pathname, init) => new Request(origin + pathname, init);

test('request policy pins HTTPS and WSS; image-only data and origin-bound blobs', () => {
  assert.equal(isAllowedPlatformRequest(origin + '/x', origin), true);
  assert.equal(isAllowedPlatformRequest('wss://pms.example:8443/ws', origin), true);
  for (const url of ['http://pms.example:8443/', 'https://elsewhere.test/', 'wss://elsewhere.test/', 'file:///tmp/a', 'https://user@pms.example:8443/']) assert.equal(isAllowedPlatformRequest(url, origin), false);
  assert.equal(isAllowedPlatformRequest('data:image/png;base64,eA==', origin, 'image'), true);
  assert.equal(isAllowedPlatformRequest('data:text/html,<script>', origin, 'image'), false);
  assert.equal(isAllowedPlatformRequest('data:image/png;base64,eA==', origin, 'script'), false);
  assert.equal(isAllowedPlatformRequest('blob:' + origin + '/abc', origin, 'image'), true);
  assert.equal(isAllowedPlatformRequest('blob:https://evil.test/abc', origin, 'image'), false);
});

test('serves packaged HTML/assets/HEAD and SPA history without network', async t => {
  const { handler } = await fixture(t);
  for (const url of ['/', '/patients/123']) {
    const response = await handler(req(url, { headers: { accept: 'text/html' } }));
    assert.equal(response.status, 200);
    assert.match(await response.text(), /assets\/app.js/);
    assert.match(response.headers.get('content-security-policy'), /script-src 'self';/);
    assert.match(response.headers.get('content-security-policy'), /wss:\/\/pms.example:8443/);
    assert.doesNotMatch(response.headers.get('content-security-policy'), /unsafe-eval/);
  }
  const js = await handler(req('/assets/app.js'));
  assert.match(js.headers.get('content-type'), /javascript/);
  assert.equal(await js.text(), 'console.log("fixture")');
  const head = await handler(req('/assets/app.js', { method: 'HEAD' }));
  assert.equal(await head.text(), '');
  assert.ok(Number(head.headers.get('content-length')) > 0);
  for (const url of ['/assets/missing.js', '/missing.css', '/assets/missing', '/not-a-navigation', '/admin-api-wrong']) assert.equal((await handler(req(url))).status, 404);
});

test('rejects off-origin requests, unsafe encoding, symlink escapes and unsupported files', async t => {
  const { handler, root, frontendDirectory } = await fixture(t);
  await fs.writeFile(path.join(root, 'secret.txt'), 'secret');
  await fs.symlink(path.join(root, 'secret.txt'), path.join(frontendDirectory, 'escape.txt'));
  await fs.writeFile(path.join(frontendDirectory, 'secret.env'), 'secret');
  assert.equal((await handler(new Request('https://elsewhere.test/'))).status, 403);
  for (const url of ['/a%2fb', '/%5csecret', '/%00', '/%252e%252e/secret', '/%zz', '/escape.txt']) assert.equal((await handler(req(url))).status, 403, url);
  assert.equal((await handler(req('/secret.env'))).status, 404);
  assert.equal((await handler(req('/', { method: 'POST', body: 'x' }))).status, 405);
});

test('missing bundle returns explicit 503, never downloads frontend', async t => {
  const { handler, frontendDirectory } = await fixture(t);
  await fs.rm(path.join(frontendDirectory, 'index.html'));
  assert.equal((await handler(req('/'))).status, 503);
  assert.equal((await handler(req('/route', { headers: { accept: 'text/html' } }))).status, 503);
  await fs.rm(frontendDirectory, { recursive: true });
  assert.equal((await handler(req('/'))).status, 503);
});

test('only exact backend namespaces proxy, preserving auth/tenant while sanitizing context', async t => {
  const calls = [];
  const { handler } = await fixture(t, { fetch: async (url, options) => {
    calls.push({ url, options });
    return new Response('plain decoded result', { headers: { 'content-encoding': 'gzip', 'content-length': '3', 'set-cookie': 'secret=1', connection: 'x-hop', 'x-hop': 'drop', 'content-type': 'application/json' } });
  } });
  for (const route of ['/admin-api', '/admin-api/test?q=x', '/api/v1/pms', '/api/v1/pms/test']) {
    const response = await handler(req(route, { method: 'POST', body: 'payload', headers: { authorization: 'Bearer token', 'tenant-id': '42', cookie: 'session=x', origin: 'https://evil.test', referer: 'https://evil.test', 'sec-fetch-site': 'cross-site', 'x-forwarded-for': 'bad', connection: 'x-hop', 'x-hop': 'bad' } }));
    assert.equal(await response.text(), 'plain decoded result');
    assert.equal(response.headers.get('cache-control'), 'no-store');
    for (const header of ['content-encoding', 'content-length', 'set-cookie', 'connection', 'x-hop']) assert.equal(response.headers.get(header), null);
    const { url, options } = calls.at(-1);
    assert.equal(url, origin + route);
    assert.equal(options.redirect, 'manual');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.headers.get('origin'), origin);
    assert.equal(options.headers.get('authorization'), 'Bearer token');
    assert.equal(options.headers.get('tenant-id'), '42');
    assert.equal(options.body.toString(), 'payload');
    for (const header of ['cookie', 'referer', 'sec-fetch-site', 'x-forwarded-for', 'connection', 'x-hop']) assert.equal(options.headers.get(header), null);
  }
  for (const route of ['/api/v1/pms-evil', '/admin-api-evil', '/api/other']) assert.equal((await handler(req(route))).status, 404);
  assert.equal(calls.length, 4);
});

test('redirects and unreachable backends fail closed', async t => {
  for (const fetch of [async () => new Response(null, { status: 302, headers: { location: 'https://evil.test' } }), async () => { throw new Error('TLS failed'); }]) {
    const { handler } = await fixture(t, { fetch });
    assert.equal((await handler(req('/admin-api/test'))).status, 502);
  }
});

test('upload limits, encoding rejection, and fetch timeouts are bounded', async t => {
  let calls = 0;
  const { handler } = await fixture(t, { maxUploadBytes: 4, fetch: async () => { calls++; return new Response('ok'); } });
  assert.equal((await handler(req('/admin-api/test', { method: 'POST', body: '12345' }))).status, 413);
  assert.equal((await handler(req('/admin-api/test', { method: 'POST', body: '1', headers: { 'content-encoding': 'gzip' } }))).status, 415);
  assert.equal(calls, 0);
  const fixture2 = await fixture(t, { timeoutMs: 10, fetch: async (url, { signal }) => new Promise((resolve, reject) => { signal.addEventListener('abort', () => reject(signal.reason), { once: true }); }) });
  assert.equal((await fixture2.handler(req('/admin-api/test'))).status, 502);
});

test('desktop DAC namespace is handled locally before API/static routing and fails closed without bridge', async t => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'npdms-bridge-route-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  await fs.writeFile(path.join(directory, 'index.html'), '<html>not a bridge</html>');
  let calls = 0;
  const opts = { origin: 'https://npdms.example', frontendDirectory: directory, fetch: async () => assert.fail('bridge namespace must not proxy to remote backend') };
  const disabled = createPlatformHandler(opts);
  assert.equal((await disabled(new Request('https://npdms.example/__npdms_desktop/dac/status'))).status, 503);
  const active = createPlatformHandler({ ...opts, dacHandler: async request => { calls++; assert.equal(request.method, 'POST'); return new Response('bounded bridge response'); } });
  assert.equal(await (await active(new Request('https://npdms.example/__npdms_desktop/dac/connect', { method: 'POST', body: '{}' }))).text(), 'bounded bridge response');
  assert.equal(calls, 1);
  assert.equal((await active(new Request('https://evil.example/__npdms_desktop/dac/connect', { method: 'POST' }))).status, 403);
  assert.equal(calls, 1);
});
