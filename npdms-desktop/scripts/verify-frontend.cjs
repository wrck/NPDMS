#!/usr/bin/env node
'use strict';

const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { createPlatformHandler } = require('../src/platform.cjs');

const BASELINE = 'd4f6918d67c3f5b32559d7eba6deb6e9a847858a';
const MAX_FILES = 10000;
const MAX_FILE_BYTES = 512 * 1024 * 1024;
const MAX_TOTAL_BYTES = 2 * 1024 * 1024 * 1024;
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
function fail(reason) { throw new Error('FRONTEND_VERIFICATION_FAILED: ' + reason); }
function safeRelative(value) {
  return typeof value === 'string' && value.length > 0 && value.length <= 1024 &&
    !/[\\:%\x00-\x1f\x7f]/.test(value) && !value.startsWith('/') &&
    value.split('/').every(part => part && part !== '.' && part !== '..');
}

function entryReferences(html) {
  const references = new Set();
  const scripts = new Set();
  for (const match of html.matchAll(/<(script|link)\b([^>]*)>/gi)) {
    const attributes = {};
    for (const attr of match[2].matchAll(/([^\s=\/]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g)) {
      attributes[attr[1].toLowerCase()] = attr[2] ?? attr[3] ?? attr[4];
    }
    const script = match[1].toLowerCase() === 'script';
    if (!script && !/(?:^|\s)(?:stylesheet|modulepreload)(?:\s|$)/i.test(attributes.rel || '')) continue;
    const source = script ? attributes.src : attributes.href;
    if (!source) { if (script) fail('index has an inline or missing-source script'); continue; }
    if (/[&#?]/.test(source) || source.startsWith('//') || /^[a-z][a-z0-9+.-]*:/i.test(source)) fail('nonlocal entry reference');
    const relative = source.startsWith('/') ? source.slice(1) : source.replace(/^\.\//, '');
    if (!safeRelative(relative)) fail('unsafe entry reference');
    references.add(relative);
    if (script) scripts.add(relative);
  }
  if (!scripts.size) fail('index has no entry scripts');
  return { references, scripts };
}

async function verifyFrontend(directory = path.resolve(__dirname, '../resources/frontend')) {
  const root = path.resolve(directory);
  if (!(await fs.lstat(root)).isDirectory() || (await fs.lstat(root)).isSymbolicLink()) fail('bundle root must be a real directory');
  const manifestPath = path.join(root, 'build-manifest.json');
  const manifestStat = await fs.lstat(manifestPath);
  if (!manifestStat.isFile() || manifestStat.isSymbolicLink() || manifestStat.size > 8 * 1024 * 1024) fail('invalid manifest file');
  const manifestBytes = await fs.readFile(manifestPath);
  let manifest;
  try { manifest = JSON.parse(manifestBytes.toString('utf8')); } catch { fail('invalid manifest JSON'); }
  if (!manifest || manifest.format !== 1 || manifest.baseline !== BASELINE || manifest.frontend !== 'yudao-ui/yudao-ui-admin-vue3' || manifest.buildMode !== 'desktop') fail('manifest provenance mismatch');
  if (!Array.isArray(manifest.files) || !manifest.files.length || manifest.files.length > MAX_FILES) fail('invalid manifest file count');
  const expected = new Map();
  let totalBytes = 0;
  for (const item of manifest.files) {
    if (!item || !safeRelative(item.path) || item.path === 'build-manifest.json' || expected.has(item.path)) fail('unsafe or duplicate manifest path');
    if (!Number.isSafeInteger(item.size) || item.size < 0 || item.size > MAX_FILE_BYTES || typeof item.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(item.sha256)) fail('invalid manifest file metadata');
    totalBytes += item.size;
    if (totalBytes > MAX_TOTAL_BYTES) fail('bundle exceeds size limit');
    expected.set(item.path, item);
  }
  if (!expected.has('index.html')) fail('manifest omits index.html');
  const actual = new Set();
  let entries = 0;
  async function walk(relative = '') {
    for (const entry of await fs.readdir(path.join(root, relative), { withFileTypes: true })) {
      if (++entries > MAX_FILES * 2) fail('too many filesystem entries');
      const name = relative ? relative + '/' + entry.name : entry.name;
      if (!safeRelative(name)) fail('unsafe filesystem path');
      if (entry.isSymbolicLink()) fail('symlink in bundle: ' + name);
      if (entry.isDirectory()) { await walk(name); continue; }
      if (!entry.isFile()) fail('nonregular bundle entry: ' + name);
      if (name === 'build-manifest.json') continue;
      if (!expected.has(name)) fail('unmanifested file: ' + name);
      actual.add(name);
      const item = expected.get(name);
      const stat = await fs.lstat(path.join(root, name));
      if (!stat.isFile() || stat.isSymbolicLink() || stat.size !== item.size) fail('size or type mismatch: ' + name);
      // Hash by chunks to keep memory bounded even for a large valid entry.
      const hash = createHash('sha256');
      const handle = await fs.open(path.join(root, name), require('node:fs').constants.O_RDONLY | require('node:fs').constants.O_NOFOLLOW);
      try { for await (const chunk of handle.createReadStream({ autoClose: false })) hash.update(chunk); }
      finally { await handle.close(); }
      if (hash.digest('hex') !== item.sha256) fail('SHA256 mismatch: ' + name);
    }
  }
  await walk();
  if (actual.size !== expected.size) fail('manifest file missing from bundle');
  const html = await fs.readFile(path.join(root, 'index.html'), 'utf8');
  const { references, scripts } = entryReferences(html);
  for (const reference of references) if (!actual.has(reference)) fail('missing entry asset: ' + reference);
  const origin = 'https://frontend-verification.invalid';
  const handler = createPlatformHandler({ origin, frontendDirectory: root, fetch: async () => { fail('network attempted'); } });
  let checked = 0;
  for (const route of ['/', '/verification/deep/history', ...references].map(value => value.startsWith('/') ? value : '/' + value)) {
    const response = await handler(new Request(origin + route, { headers: { accept: 'text/html' } }));
    const expectedPath = ['/', '/verification/deep/history'].includes(route) ? 'index.html' : route.slice(1);
    if (response.status !== 200) fail('handler rejected ' + route);
    const hash = createHash('sha256');
    for await (const chunk of response.body) hash.update(chunk);
    if (hash.digest('hex') !== expected.get(expectedPath).sha256) fail('handler bytes mismatch: ' + route);
    checked++;
  }
  return { ok: true, baseline: manifest.baseline, fileCount: actual.size, totalBytes,
    manifestSha256: sha256(manifestBytes), indexSha256: expected.get('index.html').sha256,
    entryScriptCount: scripts.size, entryAssetCount: references.size, handlerChecks: checked, networkRequests: 0 };
}

if (require.main === module) verifyFrontend(process.argv[2]).then(result => process.stdout.write(JSON.stringify(result) + '\n')).catch(error => {
  process.stderr.write(JSON.stringify({ ok: false, error: error.message }) + '\n'); process.exitCode = 1;
});
module.exports = { verifyFrontend, BASELINE };
