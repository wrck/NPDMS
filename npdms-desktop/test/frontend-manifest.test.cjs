'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { createHash } = require('node:crypto');
const { expectedOverlayProvenance } = require('../scripts/frontend-overlays.cjs');
const { verifyFrontend, BASELINE } = require('../scripts/verify-frontend.cjs');
async function fixture(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'npdms-manifest-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(path.join(root, 'assets'));
  const files = { 'index.html': '<html><script type="module" src="/assets/main.js"></script><link rel="stylesheet" href="/assets/style.css"></html>', 'assets/main.js': 'export default 1;', 'assets/style.css': 'body {color: black}' };
  const manifest = { format: 1, baseline: BASELINE, frontend: 'yudao-ui/yudao-ui-admin-vue3', buildMode: 'desktop', provenance: { ...expectedOverlayProvenance(), sourceTreeSha256: 'a'.repeat(64) }, files: [] };
  for (const [name, contents] of Object.entries(files)) {
    await fs.writeFile(path.join(root, name), contents);
    manifest.files.push({ path: name, size: Buffer.byteLength(contents), sha256: createHash('sha256').update(contents).digest('hex') });
  }
  const save = () => fs.writeFile(path.join(root, 'build-manifest.json'), JSON.stringify(manifest));
  await save();
  return { root, manifest, save };
}
test('verifies every hash and exercises root, history, scripts and CSS locally', async t => {
  const { root } = await fixture(t);
  const result = await verifyFrontend(root);
  assert.equal(result.fileCount, 3);
  assert.equal(result.handlerChecks, 4);
  assert.equal(result.networkRequests, 0);
  assert.equal(result.entryScriptCount, 1);
  assert.match(result.manifestSha256, /^[a-f0-9]{64}$/);
});
test('rejects missing, corrupt, extra and symlink payloads', async t => {
  for (const mutation of [
    root => fs.rm(path.join(root, 'assets/main.js')),
    root => fs.writeFile(path.join(root, 'assets/main.js'), 'export default 2;'),
    root => fs.writeFile(path.join(root, 'extra.txt'), 'extra'),
    async root => { await fs.rm(path.join(root, 'assets/main.js')); await fs.symlink(path.join(root, 'assets/style.css'), path.join(root, 'assets/main.js')); },
    root => fs.symlink(os.tmpdir(), path.join(root, 'escape'))
  ]) { const { root } = await fixture(t); await mutation(root); await assert.rejects(verifyFrontend(root)); }
});
test('rejects unsafe, duplicate, unbounded and provenance-invalid manifests', async t => {
  for (const mutate of [
    m => { m.baseline = '0'.repeat(40); }, m => { m.buildMode = 'production'; },
    m => { m.files[0].path = '../index.html'; }, m => { m.files[0].path = '/index.html'; },
    m => { m.files[0].path = 'assets\\index.html'; }, m => { m.files[0].path = 'assets/%2e%2e/index.html'; },
    m => { m.files.push(m.files[0]); }, m => { m.files[0].size = -1; },
    m => { m.files[0].size = 1024 ** 3; }, m => { m.files[0].sha256 = 'invalid'; },
    m => { m.files = Array(10001).fill(m.files[0]); }
  ]) { const f = await fixture(t); mutate(f.manifest); await f.save(); await assert.rejects(verifyFrontend(f.root)); }
});
test('rejects missing or remote index references even with matching hashes', async t => {
  for (const src of ['/assets/missing.js', 'https://evil.example/entry.js', '//evil.example/entry.js', '/assets/../entry.js']) {
    const f = await fixture(t);
    const html = `<script src="${src}"></script>`;
    await fs.writeFile(path.join(f.root, 'index.html'), html);
    Object.assign(f.manifest.files[0], { size: Buffer.byteLength(html), sha256: createHash('sha256').update(html).digest('hex') });
    await f.save(); await assert.rejects(verifyFrontend(f.root));
  }
});

test('rejects missing, stale and extra desktop-overlay provenance despite matching asset hashes', async t => {
  for (const mutate of [
    m => { delete m.provenance; },
    m => { m.provenance.overlayVersion = 'web-only'; },
    m => { m.provenance.sources[0].overrideSha256 = '0'.repeat(64); },
    m => { m.provenance.unchangedContracts[0].sha256 = '0'.repeat(64); },
    m => { m.provenance.sourceTreeSha256 = '../invalid'; },
    m => { m.provenance.unexpected = true; }
  ]) { const f = await fixture(t); mutate(f.manifest); await f.save(); await assert.rejects(verifyFrontend(f.root)); }
});
