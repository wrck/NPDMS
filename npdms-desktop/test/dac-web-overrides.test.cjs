'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { OVERLAYS, excluded, validateOverlays, safeStage } = require('../scripts/prepare-dac-web.cjs');
const overrides = path.resolve(__dirname, '../dac-web-overrides');
const sourceEntries = () => OVERLAYS.filter(entry => entry.sourceSha256).map(entry => ({ path: entry.path, sha256: entry.sourceSha256 }));

test('DAC web overlays are pinned and missing catalogue is an explicit empty state', () => {
  const files = validateOverlays(sourceEntries());
  assert.equal(files.length, 3);
  const data = files.find(file => file.path === 'src/data/common-commands.ts').bytes.toString();
  assert.match(data, /readonly CommonCommand\[\] = Object\.freeze\(\[\]\)/);
  assert.match(data, /BLOCKED_BY_SPEC/);
});

test('DAC web overlay rejects changed source and unexpected catalogue additions', () => {
  assert.throws(() => validateOverlays([]), /SOURCE_HASH_MISMATCH/);
  assert.throws(() => validateOverlays([...sourceEntries(), { path: 'src/data/common-commands.ts', sha256: 'unexpected' }]), /SOURCE_HASH_MISMATCH/);
});

test('DAC web overlay rejects edited or symlinked override files', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-dac-overlay-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  fs.cpSync(overrides, root, { recursive: true });
  const editor = path.join(root, 'src/components/ScriptArtifactEditor.vue');
  fs.appendFileSync(editor, '\nchanged\n');
  assert.throws(() => validateOverlays(sourceEntries(), root), /OVERLAY_HASH_MISMATCH/);
  fs.unlinkSync(editor);
  fs.symlinkSync(path.join(overrides, 'src/components/ScriptArtifactEditor.vue'), editor);
  assert.throws(() => validateOverlays(sourceEntries(), root), /UNSAFE_DAC_WEB_OVERLAY/);
});

test('DAC web source filtering excludes environment, private-key, and dependency files', () => {
  for (const name of ['.env', '.env.local', '.env.example', '.npmrc', 'secrets/private.key', 'client.p12', 'node_modules/a.js']) assert.equal(excluded(name), true, name);
  for (const name of ['src/env.d.ts', 'src/config/runtime.ts', 'pnpm-lock.yaml', 'vite.config.ts']) assert.equal(excluded(name), false, name);
});

test('DAC web staging cannot overwrite an existing directory or write into baseline/overrides', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-dac-stage-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const source = path.join(root, 'source'); const overlay = path.join(root, 'overlay');
  fs.mkdirSync(source); fs.mkdirSync(overlay);
  assert.throws(() => safeStage(source, source, overlay), /FRESH_DAC_WEB_STAGE_REQUIRED/);
  assert.throws(() => safeStage(source, path.join(source, 'new'), overlay), /UNSAFE_DAC_WEB_STAGE/);
  assert.throws(() => safeStage(source, path.join(overlay, 'new'), overlay), /UNSAFE_DAC_WEB_STAGE/);
  const alias = path.join(root, 'source-alias');
  fs.symlinkSync(source, alias, 'dir');
  assert.throws(() => safeStage(source, path.join(alias, 'new'), overlay), /UNSAFE_DAC_WEB_STAGE/);
  const stage = path.join(root, 'fresh');
  assert.equal(safeStage(source, stage, overlay), stage);
  assert.equal(fs.existsSync(stage), false);
});
