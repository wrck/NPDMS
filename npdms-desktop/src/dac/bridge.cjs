'use strict';
// Main-process adapter for the baseline DesktopStatus/CollectionController and
// DacDesktopConnectProofController contracts. No service launch or browser auth.
// The runtime callback and transport injections are trusted main-process code,
// never renderer input. All requests use dedicated, non-reconnecting transports.
const http = require('node:http');
const https = require('node:https');
const net = require('node:net');
const { TextDecoder } = require('node:util');
const { normalizeUrl, parseConnectUri, nonce, validPort, verifyStatus } = require('./protocol.cjs');
const PREFIX = '/__npdms_desktop/dac';
const STATUS_FIELDS = ['product', 'protocolVersion', 'status', 'serverUrl', 'webOrigin', 'port', 'publicKey', 'instanceId', 'signature'];
const JSON_TYPE = /^application\/json(?:\s*;\s*charset=utf-8)?$/i;
const fail = (status = 502, code = 'DAC_BRIDGE_UNAVAILABLE') => Object.assign(new Error(code), { bridgeStatus: status, bridgeCode: code });
function checkSignal(signal) { if (signal.aborted) throw fail(503, 'DAC_BRIDGE_CANCELLED'); }
function json(bytes) { return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)); }
function response(body, status = 200, type = 'application/json') {
  return new Response([204, 205, 304].includes(status) ? null : body, { status, headers: {
    'content-type': type, 'cache-control': 'no-store', 'x-content-type-options': 'nosniff',
    'content-security-policy': "default-src 'none'; frame-ancestors 'none'", 'referrer-policy': 'no-referrer'
  } });
}
function query(url, keys) {
  const raw = url.search.slice(1), result = Object.create(null);
  for (const item of raw ? raw.split('&') : []) {
    const split = item.indexOf('=');
    if (split < 1) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
    const key = item.slice(0, split), value = item.slice(split + 1);
    if (!keys.includes(key) || Object.hasOwn(result, key) || /\+|%(?![0-9a-f]{2})/i.test(value)) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
    try { result[key] = decodeURIComponent(value); } catch { throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST'); }
  }
  if (Object.keys(result).length !== keys.length) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
  return result;
}
function route(request, origin) {
  let url;
  try { url = new URL(request.url); } catch { throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST'); }
  if (url.protocol !== 'https:' || url.origin !== new URL(origin).origin || url.username || url.password || url.hash) throw fail(403, 'DAC_BRIDGE_FORBIDDEN');
  const name = url.pathname.slice(PREFIX.length);
  if (!url.pathname.startsWith(PREFIX + '/') || !['/status', '/operations', '/connect'].includes(name)) throw fail(404, 'DAC_BRIDGE_NOT_FOUND');
  const method = request.method;
  if ((name === '/status' && method !== 'GET') || (name === '/connect' && method !== 'POST') || (name === '/operations' && !['GET', 'POST'].includes(method))) throw fail(405, 'DAC_BRIDGE_METHOD_NOT_ALLOWED');
  for (const header of ['authorization', 'cookie', 'cookie2', 'proxy-authorization']) if (request.headers.has(header)) throw fail(403, 'DAC_BRIDGE_FORBIDDEN');
  if (request.headers.has('content-encoding') || request.headers.has('transfer-encoding')) throw fail(415, 'DAC_BRIDGE_INVALID_CONTENT_TYPE');
  if (method === 'POST' && !JSON_TYPE.test(request.headers.get('content-type') || '')) throw fail(415, 'DAC_BRIDGE_INVALID_CONTENT_TYPE');
  if (name === '/connect') { query(url, []); return { name, method }; }
  const params = query(url, name === '/status' ? ['port', 'nonce'] : ['port', 'path']);
  if (params.port.length !== 4 || !/^828[0-9]$/.test(params.port)) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
  if (name === '/status') {
    if (params.nonce.length !== 64 || !/^[a-f0-9]{64}$/.test(params.nonce)) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
    return { name, method, port: Number(params.port), nonce: params.nonce };
  }
  const allowed = method === 'GET' ? /^\/(?:tasks\/[a-zA-Z0-9-]+(?:\/semantic-results)?|saved-connections)$/ : /^\/(?:tasks\/[a-zA-Z0-9-]+\/executions|saved-connections)$/;
  if (params.path.length > 512 || /[\x00-\x20\x7f]/.test(params.path) || !allowed.test(params.path)) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
  const ticket = request.headers.get('x-npdms-dac-ticket'), tenant = request.headers.get('tenant-id');
  // Headers joins duplicate values with commas; no ticket or tenant accepts one.
  if (!ticket || ticket.length > 4096 || !/^[\x21-\x2b\x2d-\x7e]+$/.test(ticket) || !/^[1-9][0-9]{0,17}$/.test(tenant || '')) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
  return { name, method, port: Number(params.port), path: '/api/v1/desktop' + params.path, ticket, tenant };
}
async function readBody(request, signal, maxBytes) {
  const length = request.headers.get('content-length');
  if (length !== null && (!/^(0|[1-9][0-9]*)$/.test(length) || Number(length) > maxBytes)) throw fail(413, 'DAC_BRIDGE_BODY_TOO_LARGE');
  if (!request.body) throw fail(400, 'DAC_BRIDGE_INVALID_JSON');
  const reader = request.body.getReader(), chunks = [];
  const cancel = () => { reader.cancel().catch(() => {}); };
  signal.addEventListener('abort', cancel, { once: true });
  let size = 0;
  try {
    for (;;) {
      checkSignal(signal);
      const { done, value } = await reader.read();
      checkSignal(signal);
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) { cancel(); throw fail(413, 'DAC_BRIDGE_BODY_TOO_LARGE'); }
      chunks.push(Buffer.from(value));
    }
    if (length !== null && Number(length) !== size) throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST');
    const result = Buffer.concat(chunks, size);
    try { json(result); } catch { result.fill(0); throw fail(400, 'DAC_BRIDGE_INVALID_JSON'); }
    return result;
  } finally {
    signal.removeEventListener('abort', cancel); reader.releaseLock();
    for (const chunk of chunks) chunk.fill(0);
  }
}
function ownedContext(ctx, origin, signal) {
  if (!ctx || !Number.isSafeInteger(ctx.pid) || ctx.pid <= 0 || !validPort(ctx.port) ||
      !/^[a-f0-9]{64}$/.test(ctx.publicKey || '') || !/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(ctx.instanceId || '') ||
      ctx.serverUrl !== origin || ctx.webOrigin !== origin || typeof ctx.isCurrent !== 'function' || !(ctx.signal instanceof AbortSignal)) throw fail(503);
  const snapshot = Object.freeze({ pid: ctx.pid, port: ctx.port, publicKey: ctx.publicKey, instanceId: ctx.instanceId, serverUrl: ctx.serverUrl, webOrigin: ctx.webOrigin });
  const combined = AbortSignal.any([signal, ctx.signal]);
  const assertCurrent = () => {
    checkSignal(combined);
    if (ctx.isCurrent() !== true || Object.keys(snapshot).some(key => ctx[key] !== snapshot[key])) throw fail(503);
  };
  assertCurrent();
  return { ...snapshot, signal: combined, assertCurrent };
}
// The Agent is private to one bridge call and can create exactly one TCP socket.
// A closed/preflight-rejected connection is never replaced, including by Node's
// Agent free-socket race handling. Sensitive headers/body are not installed until
// ClientRequest emits the exact pinned socket and all guards have passed.
function loopbackSession(ctx, connect, request) {
  let socket, created = false, broken = false;
  const agent = new http.Agent({ keepAlive: true, maxSockets: 1, maxFreeSockets: 1, maxTotalSockets: 1 });
  agent.createConnection = (_options, callback) => {
    if (created) { callback(fail()); return undefined; }
    created = true;
    try {
      ctx.assertCurrent();
      socket = connect({ host: '127.0.0.1', port: ctx.port, family: 4 });
      for (const event of ['close', 'error', 'end']) socket.on(event, () => { broken = true; });
      return socket;
    } catch { callback(fail()); return undefined; }
  };
  function guard(candidate) {
    ctx.assertCurrent();
    if (broken || !socket || candidate !== socket || socket.destroyed || socket.connecting || !socket.readable || !socket.writable ||
        socket.remoteAddress !== '127.0.0.1' || socket.remotePort !== ctx.port || socket.localAddress !== '127.0.0.1') throw fail();
  }
  return {
    async exchange(method, path, headers, body, maxBytes) {
      ctx.assertCurrent();
      if (broken || (created && (!socket || socket.destroyed))) throw fail();
      return exchange(request, { protocol: 'http:', hostname: '127.0.0.1', port: ctx.port, method, path, agent,
        maxHeaderSize: 16384, insecureHTTPParser: false, headers: {} }, {
        signal: ctx.signal, guard, headers, body, maxBytes, local: true
      });
    },
    assert: () => guard(socket),
    close: () => { broken = true; agent.destroy(); socket?.destroy(); }
  };
}
function exchange(request, options, { signal, guard, headers, body, maxBytes, local = false }) {
  return new Promise((resolve, reject) => {
    let req, res, settled = false, sent = false;
    const chunks = [];
    const finish = (error, result) => {
      if (settled) return;
      settled = true; signal.removeEventListener('abort', abort);
      if (error) { res?.destroy(); req?.destroy(); reject(error); } else resolve(result);
    };
    const abort = () => finish(fail(503, 'DAC_BRIDGE_CANCELLED'));
    if (signal.aborted) { abort(); return; }
    signal.addEventListener('abort', abort, { once: true });
    try {
      req = request(options, incoming => {
        res = incoming;
        if (settled || !sent) { res.destroy(); finish(fail()); return; }
        res.on('error', () => finish(fail()));
        res.on('aborted', () => finish(fail()));
        const length = res.headers['content-length'];
        const type = res.headers['content-type'];
        const duplicates = ['content-type', 'content-length', 'content-encoding'].some(name =>
          (res.rawHeaders || []).filter((value, i) => i % 2 === 0 && value.toLowerCase() === name).length > 1);
        if (!Number.isInteger(res.statusCode) || res.statusCode < 200 || res.statusCode > 599 || duplicates ||
            (type !== undefined && (typeof type !== 'string' || type.length > 256 || /[\r\n]/.test(type))) ||
            (res.headers['content-encoding'] && res.headers['content-encoding'] !== 'identity') ||
            (length !== undefined && (!/^(0|[1-9][0-9]*)$/.test(String(length)) || Number(length) > maxBytes))) { finish(fail()); return; }
        let size = 0;
        res.on('data', chunk => {
          if (settled) return;
          size += chunk.length;
          if (size > maxBytes) { finish(fail()); return; }
          chunks.push(Buffer.from(chunk));
        });
        res.on('end', () => {
          if (settled) return;
          try {
            checkSignal(signal); guard(req.socket);
            if (!res.complete || (length !== undefined && Number(length) !== size) || ([204, 205, 304].includes(res.statusCode) && size)) throw fail();
            finish(null, { status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks, size) });
          } catch { finish(fail()); }
        });
        res.on('close', () => { if (!settled) finish(fail()); });
      });
      req.on('error', () => finish(fail()));
      req.on('upgrade', (_res, other) => { other.destroy(); finish(fail()); });
      req.on('connect', (_res, other) => { other.destroy(); finish(fail()); });
      req.on('socket', assigned => {
        const send = () => {
          if (settled || sent) return;
          try {
            checkSignal(signal); guard(assigned);
            for (const [name, value] of Object.entries(headers)) req.setHeader(name, value);
            // Length framing is controlled here, never copied from the renderer.
            if (body) req.setHeader('Content-Length', String(body.length));
            sent = true; req.end(body);
          } catch { finish(fail()); }
        };
        if (local && assigned.connecting) assigned.once('connect', send); else send();
      });
      if (settled) req.destroy();
    } catch { finish(fail()); }
  });
}
async function signedStatus(session, ctx, challenge) {
  const result = await session.exchange('GET', `/api/v1/desktop/status?nonce=${challenge}`, { Origin: ctx.webOrigin }, undefined, 8192);
  if (result.status !== 200 || !JSON_TYPE.test(result.headers['content-type'] || '') || /(?:^|,)\s*close\s*(?:,|$)/i.test(result.headers.connection || '')) throw fail();
  let status;
  try {
    status = json(result.body);
    if (!status || Array.isArray(status) || Object.keys(status).length !== STATUS_FIELDS.length || !STATUS_FIELDS.every(key => Object.hasOwn(status, key))) throw Error();
    verifyStatus(status, { ...ctx, nonce: challenge });
  } catch { throw fail(); }
  session.assert();
  return { result, status };
}
function createDacBridge({ origin, withOwnedContext, connect = net.createConnection, request = http.request,
  proofRequest = https.request, timeoutMs = 10000, maxRequestBytes = 1024 * 1024, maxResponseBytes = 8 * 1024 * 1024, maxInFlight = 16 } = {}) {
  try {
    if (normalizeUrl(origin, true) !== origin || !origin.startsWith('https://') || origin.length > 2048 ||
        ![withOwnedContext, connect, request, proofRequest].every(fn => typeof fn === 'function') ||
        !Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 10000 ||
        !Number.isInteger(maxRequestBytes) || maxRequestBytes < 1 || maxRequestBytes > 1024 * 1024 ||
        !Number.isInteger(maxResponseBytes) || maxResponseBytes < 1 || maxResponseBytes > 8 * 1024 * 1024 ||
        !Number.isInteger(maxInFlight) || maxInFlight < 1 || maxInFlight > 32) throw Error();
  } catch { throw new Error('DAC_BRIDGE_INVALID_CONFIGURATION'); }
  let active = 0;
  return async function handle(requestInput) {
    let body, timer, admitted = false;
    try {
      const target = route(requestInput, origin);
      // No credential queue or unbounded loopback fan-out from the renderer.
      if (active >= maxInFlight) throw fail(503, 'DAC_BRIDGE_BUSY');
      active++; admitted = true;
      const controller = new AbortController();
      timer = setTimeout(() => controller.abort(), timeoutMs);
      const signal = AbortSignal.any([controller.signal, requestInput.signal]);
      checkSignal(signal);
      if (target.method === 'POST') body = await readBody(requestInput, signal, maxRequestBytes);
      let pairing;
      if (target.name === '/connect') {
        try {
          const text = new TextDecoder('utf-8', { fatal: true }).decode(body);
          // One literal property/string only: reject duplicate or escaped keys,
          // extra fields, alternate URI spellings, and coercible tenant numbers.
          if (!/^\s*\{\s*"uri"\s*:\s*"[^"\\]*"\s*\}\s*$/.test(text)) throw Error();
          pairing = parseConnectUri(json(body).uri);
        } catch { throw fail(400, 'DAC_BRIDGE_INVALID_REQUEST'); }
      }
      const work = withOwnedContext({ serverUrl: origin, webOrigin: origin }, async value => {
        const ctx = ownedContext(value, origin, signal);
        if (target.port !== undefined && target.port !== ctx.port) throw fail(503);
        const session = loopbackSession(ctx, connect, request);
        try {
          const challenge = target.name === '/status' ? target.nonce : target.name === '/connect' ? pairing.nonce : nonce();
          const verified = await signedStatus(session, ctx, challenge);
          if (target.name === '/status') return response(verified.result.body);
          if (target.name === '/operations') {
            const headers = { Origin: ctx.webOrigin, 'X-NPDMS-DAC-Ticket': target.ticket, 'tenant-id': target.tenant };
            if (target.method === 'POST') headers['Content-Type'] = 'application/json';
            const result = await session.exchange(target.method, target.path, headers, body, maxResponseBytes);
            ctx.assertCurrent();
            // A redirect is an unknown outcome, including after POST. Never
            // retry it, follow it, or expose its Location/cookies.
            if (result.status >= 300 && result.status < 400) throw fail();
            // Preserve genuine business response status/body without inventing success.
            return response(result.body, result.status, result.headers['content-type'] || 'application/octet-stream');
          }
          ctx.assertCurrent();
          const server = new URL(origin);
          const proof = Buffer.from(JSON.stringify({ nonce: pairing.nonce, status: verified.status }));
          const result = await exchange(proofRequest, { protocol: 'https:', hostname: server.hostname, port: server.port || 443,
            path: `/api/v1/pms/integration/device-ops/desktop/connections/${pairing.connectionId}/proof`, method: 'POST',
            agent: false, rejectUnauthorized: true, maxHeaderSize: 16384, insecureHTTPParser: false, headers: {} }, {
            signal: ctx.signal, guard: () => ctx.assertCurrent(), headers: { 'Content-Type': 'application/json', 'tenant-id': pairing.tenantId },
            body: proof, maxBytes: 65536
          });
          ctx.assertCurrent();
          let ack;
          try { ack = json(result.body); } catch { throw fail(); }
          if (result.status !== 200 || !JSON_TYPE.test(result.headers['content-type'] || '') || !ack || ack.code !== 0 || ack.data !== true) throw fail();
          // Proof submission is an ACK, not pairing trust. The unchanged frontend
          // still owner-polls, confirms, verifies and pins the installation key.
          return response(JSON.stringify({ ok: true }));
        } finally { session.close(); }
      });
      let abort;
      try {
        return await Promise.race([work, new Promise((_, reject) => {
          abort = () => reject(fail(503, 'DAC_BRIDGE_CANCELLED'));
          signal.addEventListener('abort', abort, { once: true });
          if (signal.aborted) abort();
        })]);
      } finally { signal.removeEventListener('abort', abort); }
    } catch (error) {
      const known = error?.bridgeStatus && typeof error.bridgeCode === 'string';
      return response(JSON.stringify({ error: known ? error.bridgeCode : 'DAC_BRIDGE_UNAVAILABLE' }), known ? error.bridgeStatus : 503);
    } finally { clearTimeout(timer); body?.fill(0); if (admitted) active--; }
  };
}
module.exports = { createDacBridge };
