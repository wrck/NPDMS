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
    if (info.mode === 'development') { assert.match(metadata, /^Version: .*~development\.1$/m); assert.match(desktop, /\(Development\)/); }
    else { assert.equal(info.mode, 'production-payload'); require('./package-linux.cjs').validatePayload({}, app); }
    assert.equal(info.runtimeAcceptance, 'not-performed');
    const sha256 = createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    return { file, sha256, mode: info.mode, archiveEntries: entries.length, staticInspection: 'passed', installation: 'not-performed', electronLaunch: 'not-performed' };
  } finally { fs.rmSync(temp, { recursive: true, force: true }); }
}
if (require.main === module) {
  try { if (process.argv.length !== 3) throw new Error('Usage: node scripts/inspect-linux-package.cjs artifacts/package.deb'); console.log(JSON.stringify(inspect(process.argv[2]), null, 2)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { inspect };
