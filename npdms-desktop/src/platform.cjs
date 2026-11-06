'use strict';

const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { Readable } = require('node:stream');
const { serverOrigin } = require('./security.cjs');

const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
  '.webp': 'image/webp', '.avif': 'image/avif', '.ico': 'image/x-icon',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.otf': 'font/otf',
  '.wasm': 'application/wasm', '.txt': 'text/plain; charset=utf-8', '.pdf': 'application/pdf',
  '.mp3': 'audio/mpeg', '.mp4': 'video/mp4', '.webmanifest': 'application/manifest+json'
};
const HOP = ['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization',
  'te', 'trailer', 'transfer-encoding', 'upgrade'];

function isAllowedPlatformRequest(value, configuredOrigin, resourceType) {
  try {
    const origin = serverOrigin(configuredOrigin);
    const url = new URL(value);
    if (url.username || url.password) return false;
    if (url.protocol === 'https:') return url.origin === origin;
    if (url.protocol === 'wss:') return url.host === new URL(origin).host;
    if (resourceType !== 'image') return false;
    if (url.protocol === 'blob:') return url.origin === origin;
    return url.protocol === 'data:' && /^data:image\/(?:png|jpe?g|gif|webp|avif|bmp|x-icon|svg\+xml)(?:;|,)/i.test(value);
  } catch { return false; }
}

function cleanHeaders(source, request = false) {
  const headers = new Headers(source);
  const connection = (headers.get('connection') || '').split(',').map(x => x.trim()).filter(Boolean);
  for (const name of [...HOP, ...connection, 'content-length', 'content-encoding', 'set-cookie', 'set-cookie2']) headers.delete(name);
  if (request) {
    for (const name of [...headers.keys()]) {
      if (/^(?:sec-|proxy-|x-forwarded-)/i.test(name) || ['cookie', 'cookie2', 'host', 'origin', 'referer', 'forwarded', 'accept-encoding'].includes(name)) headers.delete(name);
    }
  }
  return headers;
}

function message(status, text) {
  return new Response(text, { status, headers: { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' } });
}

function createPlatformHandler({ origin: configuredOrigin, frontendDirectory, fetch: fetchImpl = globalThis.fetch,
  timeoutMs = 30000, maxUploadBytes = 16 * 1024 * 1024, dacHandler = null }) {
  const origin = serverOrigin(configuredOrigin);
  if (typeof frontendDirectory !== 'string' || !path.isAbsolute(frontendDirectory)) throw new Error('ABSOLUTE_FRONTEND_DIRECTORY_REQUIRED');
  if ((dacHandler !== null && typeof dacHandler !== 'function') || typeof fetchImpl !== 'function' || !Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || !Number.isSafeInteger(maxUploadBytes) || maxUploadBytes <= 0) throw new Error('INVALID_PLATFORM_OPTIONS');
  const csp = `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' ${origin.replace(/^https:/, 'wss:')}; object-src 'none'; frame-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`;

  async function proxy(request, url) {
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(new Error('BACKEND_TIMEOUT')), timeoutMs);
    const onAbort = () => abort.abort(request.signal.reason);
    request.signal?.addEventListener('abort', onAbort, { once: true });
    if (request.signal?.aborted) onAbort();
    const finish = () => { clearTimeout(timer); request.signal?.removeEventListener('abort', onAbort); };
    try {
      if (Number(request.headers.get('content-length')) > maxUploadBytes) { finish(); return message(413, 'Request body too large'); }
      // Reject encoded/compressed uploads rather than forwarding misleading length/encoding headers.
      if (request.headers.has('content-encoding')) { finish(); return message(415, 'Encoded request bodies are not supported'); }
      let body;
      if (request.body && !['GET', 'HEAD'].includes(request.method)) {
        const reader = request.body.getReader();
        let size = 0;
        const chunks = [];
        const cancel = () => { reader.cancel(abort.signal.reason).catch(() => {}); };
        abort.signal.addEventListener('abort', cancel, { once: true });
        try {
          while (true) {
            abort.signal.throwIfAborted();
            const { done, value } = await reader.read();
            if (done) break;
            size += value.byteLength;
            if (size > maxUploadBytes) { await reader.cancel(); finish(); return message(413, 'Request body too large'); }
            chunks.push(Buffer.from(value));
          }
          abort.signal.throwIfAborted();
          body = Buffer.concat(chunks, size);
        } finally { abort.signal.removeEventListener('abort', cancel); reader.releaseLock(); }
      }
      const headers = cleanHeaders(request.headers, true);
      headers.set('origin', origin);
      const response = await fetchImpl(url.href, { method: request.method, headers, body,
        redirect: 'manual', signal: abort.signal, credentials: 'omit' });
      if (response.redirected || (response.url && new URL(response.url).origin !== origin) || (response.status >= 300 && response.status < 400)) {
        await response.body?.cancel(); finish(); return message(502, 'Backend redirects are not permitted');
      }
      const resultHeaders = cleanHeaders(response.headers);
      resultHeaders.delete('location');
      resultHeaders.set('cache-control', 'no-store');
      resultHeaders.set('x-content-type-options', 'nosniff');
      if (!response.body || request.method === 'HEAD' || [204, 205, 304].includes(response.status)) {
        await response.body?.cancel(); finish();
        return new Response(null, { status: response.status, headers: resultHeaders });
      }
      const reader = response.body.getReader();
      const stream = new ReadableStream({
        async pull(controller) {
          try {
            abort.signal.throwIfAborted();
            const { done, value } = await reader.read();
            abort.signal.throwIfAborted();
            if (done) { finish(); reader.releaseLock(); controller.close(); }
            else controller.enqueue(value);
          } catch (error) { finish(); reader.cancel(error).catch(() => {}); controller.error(error); }
        },
        async cancel(reason) { abort.abort(reason); finish(); await reader.cancel(reason); }
      });
      return new Response(stream, { status: response.status, headers: resultHeaders });
    } catch { finish(); return message(502, 'Backend unavailable'); }
  }

  async function openAsset(root, relative) {
    const candidate = path.resolve(root, relative);
    if (!candidate.startsWith(root + path.sep)) throw new Error('UNSAFE_PATH');
    let actual;
    try { actual = await fsp.realpath(candidate); } catch (error) { if (error.code === 'ENOENT' || error.code === 'ENOTDIR') return null; throw error; }
    if (!actual.startsWith(root + path.sep)) throw new Error('UNSAFE_PATH');
    const file = await fsp.open(actual, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const stat = await file.stat();
    if (!stat.isFile()) { await file.close(); return null; }
    return { file, stat, actual };
  }

  return async function platformHandler(request) {
    let url;
    try { url = new URL(request.url); } catch { return message(403, 'Forbidden origin'); }
    if (url.protocol !== 'https:' || url.origin !== origin || url.username || url.password) return message(403, 'Forbidden origin');
    let segments;
    try {
      segments = url.pathname.split('/').slice(1).map(segment => decodeURIComponent(segment));
      if (segments.some(s => s === '.' || s === '..' || /[\\/\x00-\x1f\x7f]/.test(s) || /%[0-9a-f]{2}/i.test(s))) return message(403, 'Unsafe path');
    } catch { return message(403, 'Unsafe path'); }
    if (/^\/__npdms_desktop\/dac(?:\/|$)/.test(url.pathname)) return dacHandler ? dacHandler(request) : message(503, 'Desktop DAC bridge unavailable');
    if (/^\/(?:admin-api|api\/v1\/pms)(?:\/|$)/.test(url.pathname)) return proxy(request, url);
    if (!['GET', 'HEAD'].includes(request.method)) return message(405, 'Method not allowed');
    let root;
    try { root = await fsp.realpath(frontendDirectory); } catch { return message(503, 'Packaged frontend unavailable'); }
    let asset;
    try {
      asset = await openAsset(root, segments.join('/') || 'index.html');
      if (!asset) {
        if (!segments.join('/') || segments.join('/') === 'index.html') return message(503, 'Packaged frontend unavailable');
        const last = segments.at(-1) || '';
        const acceptsHtml = (request.headers.get('accept') || '').includes('text/html');
        if (path.extname(last) || !acceptsHtml || ['assets', 'static'].includes(segments[0])) return message(404, 'Asset not found');
        asset = await openAsset(root, 'index.html');
        if (!asset) return message(503, 'Packaged frontend unavailable');
      }
      const type = TYPES[path.extname(asset.actual).toLowerCase()];
      if (!type) { await asset.file.close(); return message(404, 'Asset type not supported'); }
      const headers = { 'content-type': type, 'content-length': String(asset.stat.size), 'cache-control': 'no-cache', 'x-content-type-options': 'nosniff', 'content-security-policy': csp, 'referrer-policy': 'no-referrer' };
      if (request.method === 'HEAD') { await asset.file.close(); return new Response(null, { headers }); }
      return new Response(Readable.toWeb(asset.file.createReadStream()), { headers });
    } catch { if (asset) await asset.file.close().catch(() => {}); return message(403, 'Unsafe or inaccessible asset'); }
  };
}

module.exports = { createPlatformHandler, isAllowedPlatformRequest };
