'use strict';
// Static archive smoke checks only. Does not install the package or start Electron/Java.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
function run(args) {
  const result = spawnSync('dpkg-deb', args, { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  if (result.status !== 0) throw new Error(result.stderr || result.error?.message || 'dpkg-deb failed');
  return result.stdout;
}
function verifyBuildInfo(app, info) {
  const { payloadInventory, frontendMetadata, dacMetadata, sourceCommit } = require('./package-linux.cjs');
  assert.equal(info.format, 1, 'Unsupported build metadata format');
  assert.equal(info.appId, 'org.npdms.desktop');
  assert.ok(Number.isSafeInteger(info.sourceDateEpoch) && info.sourceDateEpoch >= 0);
  assert.equal(info.runtimeAcceptance, 'not-performed');
  assert.deepEqual(info.windowClass, { configured: null, verification: 'not-performed' });
  assert.equal(info.sourceCommit, info.sourceCommit === null ? null : sourceCommit(info.sourceCommit));
  assert.ok(['development', 'production-payload'].includes(info.mode));
  assert.match(info.electronVersion, /^\d+\.\d+\.\d+$/);
  assert.deepEqual(info.payloadFiles, payloadInventory(app, ['build-info.json']), 'Installed payload hashes/sizes or file list differ from build-info');
  assert.deepEqual(info.frontend, frontendMetadata(app), 'Frontend metadata mismatch');
  assert.deepEqual(info.dac, dacMetadata(app), 'DAC completeness metadata mismatch');
  if (info.mode === 'production-payload') {
    assert.equal(info.dac.complete, true, 'Production DAC is incomplete');
    assert.ok(info.frontend.manifestSHA256, 'Production frontend manifest missing');
  }
}
function inspect(file) {
  file = path.resolve(file);
  const contents = run(['--contents', file]);
  const entries = contents.trim().split('\n');
  for (const entry of entries) {
    assert.match(entry, /^[-d][rwx-]{9}\s+root\/root\s/, 'Every archive entry must have normal modes and root ownership; no symlinks/setuid');
    assert.doesNotMatch(entry, /(?:^|\/)(?:\.env(?:\.|\/|$)|\.git\/|node_modules\/|userData\/|keys\/|runtime-data\/|test\/)|\.(?:pem|key|enc)(?:$|\s)/, 'Unexpected private/development files');
  }
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-inspect-'));
  try {
    run(['--extract', file, path.join(temp, 'payload')]);
    run(['--control', file, path.join(temp, 'control')]);
    assert.deepEqual(fs.readdirSync(path.join(temp, 'control')), ['control'], 'Maintainer hooks must not exist');
    const metadata = run(['--field', file]);
    assert.match(metadata, /^Package: npdms-desktop$/m);
    assert.match(metadata, /^Architecture: amd64$/m);
    assert.match(metadata, /^Depends: .+libnss3/m);
    assert.match(metadata, /^Depends: .*\butil-linux\b/m);
    assert.match(metadata, /^Depends: .*\blibsecret-1-0\b/m);
    const base = path.join(temp, 'payload');
    const install = path.join(base, 'opt/npdms-desktop');
    const app = path.join(install, 'resources/app');
    const pkg = JSON.parse(fs.readFileSync(path.join(app, 'package.json')));
    assert.equal(pkg.main, 'src/main.cjs');
    for (const name of ['src/main.cjs', 'src/preload.cjs', 'resources/index.html', 'build-info.json']) assert.ok(fs.statSync(path.join(app, name)).isFile(), name);
    for (const file of ['usr/bin/npdms-desktop', 'opt/npdms-desktop/electron']) assert.equal(fs.statSync(path.join(base, file)).mode & 0o777, 0o755);
    const launcher = fs.readFileSync(path.join(base, 'usr/bin/npdms-desktop'), 'utf8');
    assert.equal(launcher, '#!/bin/sh\nexec /opt/npdms-desktop/electron /opt/npdms-desktop/resources/app "$@"\n');
    const desktop = fs.readFileSync(path.join(base, 'usr/share/applications/org.npdms.desktop.desktop'), 'utf8');
    assert.match(desktop, /^Exec=\/usr\/bin\/npdms-desktop$/m);
    assert.match(desktop, /^Terminal=false$/m);
    assert.ok(fs.existsSync(path.join(base, 'usr/share/icons/hicolor/scalable/apps/org.npdms.desktop.svg')));
    const info = JSON.parse(fs.readFileSync(path.join(app, 'build-info.json')));
    verifyBuildInfo(app, info);
    assert.equal(info.electronVersion, fs.readFileSync(path.join(install, 'version'), 'utf8').trim());
    assert.doesNotMatch(desktop, /^StartupWMClass=/m, 'Window class remains unverified');
    if (info.mode === 'development') { assert.match(metadata, /^Version: .*~development\.1$/m); assert.match(desktop, /\(Development\)/); }
    else { assert.equal(info.mode, 'production-payload'); require('./package-linux.cjs').validatePayload({}, app); }
    assert.equal(info.runtimeAcceptance, 'not-performed');
    const sha256 = createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    return { file, sha256, mode: info.mode, sourceCommit: info.sourceCommit, frontend: info.frontend, dac: info.dac, verifiedPayloadFiles: info.payloadFiles.length, archiveEntries: entries.length, staticInspection: 'passed', installation: 'not-performed', electronLaunch: 'not-performed' };
  } finally { fs.rmSync(temp, { recursive: true, force: true }); }
}
if (require.main === module) {
  try { if (process.argv.length !== 3) throw new Error('Usage: node scripts/inspect-linux-package.cjs artifacts/package.deb'); console.log(JSON.stringify(inspect(process.argv[2]), null, 2)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { inspect, verifyBuildInfo };
