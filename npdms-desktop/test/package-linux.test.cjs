'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { parseArgs, validatePayload, copyTree, build } = require('../scripts/package-linux.cjs');
const { inspect } = require('../scripts/inspect-linux-package.cjs');
function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-package-test-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return root;
}
function write(root, name, data, mode = 0o644) { const file = path.join(root, name); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, data, { mode }); }
test('CLI is explicit and rejects unknown flags and missing values', () => {
  assert.deepEqual(parseArgs(['--development']), { development: true });
  assert.equal(parseArgs([]).development, false);
  assert.throws(() => parseArgs(['--no-sandbox']), /Unknown/);
  assert.throws(() => parseArgs(['--dac-dir']), /Missing/);
});
test('production fails closed without frontend and complete staged DAC', t => {
  const root = fixture(t);
  assert.throws(() => validatePayload({}, root), /frontend.*index.html/);
  write(root, 'resources/frontend/index.html', '<!doctype html>');
  assert.throws(() => validatePayload({}, root), /device-ops-server.jar/);
  write(root, 'resources/dac/device-ops-server.jar', 'fixture');
  write(root, 'resources/dac/jre/bin/java', 'fixture', 0o755);
  assert.throws(() => validatePayload({}, root), /parser release/);
  fs.mkdirSync(path.join(root, 'resources/dac/parser-releases/device-command-output-1.4.0'), { recursive: true });
  assert.doesNotThrow(() => validatePayload({}, root));
});
test('development is explicit; a mistyped explicit staging directory still fails', t => {
  const root = fixture(t);
  assert.doesNotThrow(() => validatePayload({ development: true }, root));
  assert.throws(() => validatePayload({ development: true, frontendDir: path.join(root, 'absent') }, root), /Staged directory missing/);
});
test('payload copying rejects symlinks and strips privileged executable bits', t => {
  const root = fixture(t);
  write(root, 'input/run', 'fixture', 0o4755);
  copyTree(path.join(root, 'input'), path.join(root, 'output'));
  assert.equal(fs.statSync(path.join(root, 'output/run')).mode & 0o7777, 0o755);
  fs.symlinkSync('/etc/passwd', path.join(root, 'input/escape'));
  assert.throws(() => copyTree(path.join(root, 'input'), path.join(root, 'rejected')), /Unsupported payload/);
});
test('archive builds deterministically, passes static smoke checks and omits project private files', t => {
  const root = fixture(t);
  write(root, 'package.json', JSON.stringify({ name: 'npdms-desktop', version: '0.1.0', main: 'src/main.cjs', description: 'fixture', devDependencies: { electron: '45.2.3' } }));
  for (const name of ['src/main.cjs', 'src/preload.cjs', 'resources/index.html']) write(root, name, 'fixture');
  write(root, '.env', 'must not be packaged');
  write(root, 'test/private.key', 'must not be packaged');
  const elf = Buffer.alloc(32); Buffer.from([0x7f, 0x45, 0x4c, 0x46, 2, 1]).copy(elf); elf.writeUInt16LE(62, 18);
  write(root, 'runtime/electron', elf, 0o755);
  write(root, 'runtime/version', '45.2.3');
  write(root, 'runtime/resources/default_app.asar', 'fixture');
  const options = { development: true, electronDir: path.join(root, 'runtime') };
  const first = build(options, root);
  assert.equal(inspect(first.output).staticInspection, 'passed');
  const second = build(options, root);
  assert.equal(first.sha256, second.sha256);
  assert.throws(() => build({ ...options, development: false }, root), /Required payload/);
});
