'use strict';
// Builds an archive only. Never launches Electron or modifies the host installation.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const config = require('../packaging/linux.json');
const { verifyOverlayProvenance } = require('./frontend-provenance.cjs');
const { BASELINE } = require('./frontend-overlays.cjs');
const root = path.resolve(__dirname, '..');
const assert = require('node:assert/strict');
function parseArgs(args) {
  const out = { development: false };
  for (let i = 0; i < args.length; i++) {
    const flag = args[i];
    if (flag === '--development') out.development = true;
    else if (['--frontend-dir', '--dac-dir', '--electron-dir'].includes(flag)) {
      if (!args[i + 1] || args[i + 1].startsWith('--')) throw new Error(`Missing value: ${flag}`);
      out[{ '--frontend-dir': 'frontendDir', '--dac-dir': 'dacDir', '--electron-dir': 'electronDir' }[flag]] = path.resolve(args[++i]);
    } else throw new Error(`Unknown argument: ${flag}`);
  }
  return out;
}
function requireFile(file) { if (!fs.existsSync(file) || !fs.statSync(file).isFile()) throw new Error(`Required payload missing: ${file}`); }
function copyTree(source, destination) {
  const stat = fs.lstatSync(source);
  if (stat.isSymbolicLink() || (!stat.isFile() && !stat.isDirectory())) throw new Error(`Unsupported payload entry: ${source}`);
  if (stat.isDirectory()) {
    fs.mkdirSync(destination, { recursive: true, mode: 0o755 });
    for (const name of fs.readdirSync(source).sort()) copyTree(path.join(source, name), path.join(destination, name));
  } else {
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.copyFileSync(source, destination);
    fs.chmodSync(destination, stat.mode & 0o111 ? 0o755 : 0o644);
  }
}
function validatePayload(options, projectRoot = root) {
  const frontend = options.frontendDir || path.join(projectRoot, 'resources/frontend');
  const dac = options.dacDir || path.join(projectRoot, 'resources/dac');
  if (!options.development) {
    requireFile(path.join(frontend, 'index.html'));
    requireFile(path.join(dac, 'device-ops-server.jar'));
    requireFile(path.join(dac, 'jre/bin/java'));
    if (!(fs.statSync(path.join(dac, 'jre/bin/java')).mode & 0o111)) throw new Error('Bundled Java must be executable');
    if (!fs.existsSync(path.join(dac, 'parser-releases/device-command-output-1.4.0')) || !fs.statSync(path.join(dac, 'parser-releases/device-command-output-1.4.0')).isDirectory()) throw new Error('Required DAC parser release directory missing');
  }
  for (const [explicit, actual] of [[options.frontendDir, frontend], [options.dacDir, dac]]) if (explicit && !fs.existsSync(actual)) throw new Error(`Staged directory missing: ${actual}`);
  return { frontend, dac };
}
function payloadInventory(directory, excluded = []) {
  const files = [];
  function walk(relative) {
    for (const name of fs.readdirSync(path.join(directory, relative)).sort()) {
      const child = relative ? `${relative}/${name}` : name;
      if (excluded.includes(child)) continue;
      const full = path.join(directory, child);
      const stat = fs.lstatSync(full);
      if (stat.isSymbolicLink() || (!stat.isDirectory() && !stat.isFile())) throw new Error(`Unsupported payload entry: ${child}`);
      if (stat.isDirectory()) walk(child);
      else {
        if (/(?:^|\/)(?:\.env(?:\.|$)|\.git(?:\/|$)|node_modules(?:\/|$)|userData(?:\/|$)|keys(?:\/|$)|runtime-data(?:\/|$)|artifacts(?:\/|$)|test(?:\/|$))|\.(?:pem|key|enc|p12|pfx)$/.test(child)) throw new Error(`Private/development payload entry refused: ${child}`);
        files.push({ path: child, size: stat.size, sha256: createHash('sha256').update(fs.readFileSync(full)).digest('hex') });
      }
    }
  }
  walk('');
  return files.sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
}
function frontendMetadata(app) {
  const directory = path.join(app, 'resources/frontend');
  if (!fs.existsSync(directory)) return { present: false, baseline: null, manifestSHA256: null, fileCount: 0 };
  const manifestFile = path.join(directory, 'build-manifest.json');
  const files = payloadInventory(directory, ['build-manifest.json']);
  if (!fs.existsSync(manifestFile)) return { present: true, baseline: null, manifestSHA256: null, fileCount: files.length };
  const bytes = fs.readFileSync(manifestFile);
  const manifest = JSON.parse(bytes);
  assert.equal(manifest.format, 1, 'Unsupported frontend manifest format');
  assert.equal(manifest.baseline, BASELINE, 'Invalid frontend baseline');
  const desktopProvenance = verifyOverlayProvenance(manifest.provenance);
  assert.equal(manifest.buildMode, 'desktop', 'Expected desktop frontend build');
  assert.ok(Array.isArray(manifest.files), 'Frontend manifest files required');
  const declared = [...manifest.files].sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
  assert.deepEqual(declared, files, 'Frontend manifest file hashes/sizes differ from staged payload');
  return { present: true, baseline: manifest.baseline, desktopProvenance, manifestSHA256: createHash('sha256').update(bytes).digest('hex'), fileCount: files.length };
}
function dacMetadata(app) {
  const directory = path.join(app, 'resources/dac');
  const requirements = ['device-ops-server.jar', 'jre/bin/java', 'parser-releases/device-command-output-1.4.0'];
  const missing = requirements.filter(name => {
    const target = path.join(directory, name);
    if (!fs.existsSync(target)) return true;
    const stat = fs.lstatSync(target);
    if (name.startsWith('parser-releases/')) return !stat.isDirectory() || fs.readdirSync(target).length === 0;
    return !stat.isFile() || stat.size === 0 || (name === 'jre/bin/java' && !(stat.mode & 0o111));
  });
  return { complete: missing.length === 0, missing, readiness: 'not-tested' };
}
function sourceCommit(value) {
  if (value === undefined) return null;
  if (!/^[a-fA-F0-9]{40}$/.test(value)) throw new Error('NPDMS_SOURCE_COMMIT must be exactly 40 hexadecimal characters');
  return value.toLowerCase();
}
function createBuildInfo(app, metadata) {
  const frontend = frontendMetadata(app);
  const dac = dacMetadata(app);
  if (metadata.mode === 'production-payload' && (!frontend.manifestSHA256 || !dac.complete)) throw new Error('Production requires a verified frontend manifest and complete DAC payload');
  return { format: 1, ...metadata, frontend, dac, payloadFiles: payloadInventory(app, ['build-info.json']) };
}
function normalizeTimes(directory, epoch) {
  for (const name of fs.readdirSync(directory)) {
    const file = path.join(directory, name);
    const stat = fs.statSync(file);
    if (stat.isDirectory()) normalizeTimes(file, epoch);
    else fs.chmodSync(file, stat.mode & 0o111 ? 0o755 : 0o644);
    fs.utimesSync(file, epoch, epoch);
  }
  fs.chmodSync(directory, 0o755);
  fs.utimesSync(directory, epoch, epoch);
}
function build(options = {}, projectRoot = root) {
  const commit = sourceCommit(process.env.NPDMS_SOURCE_COMMIT);
  const payload = validatePayload(options, projectRoot);
  const pkg = JSON.parse(fs.readFileSync(path.join(projectRoot, 'package.json')));
  if (!/^\d+\.\d+\.\d+$/.test(pkg.version)) throw new Error('Package version must be numeric major.minor.patch');
  const runtime = options.electronDir || path.join(projectRoot, 'node_modules/electron/dist');
  requireFile(path.join(runtime, 'electron'));
  requireFile(path.join(runtime, 'version'));
  const electronVersion = fs.readFileSync(path.join(runtime, 'version'), 'utf8').trim();
  if (electronVersion !== pkg.devDependencies.electron) throw new Error('Electron runtime version differs from pinned dependency');
  const header = fs.readFileSync(path.join(runtime, 'electron')).subarray(0, 20);
  if (header.toString('hex', 0, 4) !== '7f454c46' || header[4] !== 2 || header[5] !== 1 || header.readUInt16LE(18) !== 62) throw new Error('Expected Linux x86-64 ELF Electron runtime');
  const epoch = Number(process.env.SOURCE_DATE_EPOCH || '1735689600');
  if (!Number.isSafeInteger(epoch) || epoch < 0) throw new Error('Invalid SOURCE_DATE_EPOCH');
  const version = pkg.version + (options.development ? '~development.1' : '');
  const outputDir = path.join(projectRoot, 'artifacts');
  fs.mkdirSync(outputDir, { recursive: true });
  const output = path.join(outputDir, `${config.packageName}_${version}_${config.architecture}.deb`);
  const stage = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-package-'));
  try {
    const install = path.join(stage, config.installDirectory);
    copyTree(runtime, install);
    fs.rmSync(path.join(install, 'resources/default_app.asar'), { force: true });
    const app = path.join(install, 'resources/app');
    for (const name of ['src', 'resources']) copyTree(path.join(projectRoot, name), path.join(app, name));
    for (const [name, source] of Object.entries(payload)) if (fs.existsSync(source)) {
      const target = path.join(app, 'resources', name);
      fs.rmSync(target, { recursive: true, force: true });
      copyTree(source, target);
    }
    fs.writeFileSync(path.join(app, 'package.json'), JSON.stringify({ name: pkg.name, version: pkg.version, main: pkg.main, description: pkg.description }, null, 2) + '\n');
    fs.writeFileSync(path.join(app, 'build-info.json'), JSON.stringify(createBuildInfo(app, { mode: options.development ? 'development' : 'production-payload', appId: config.appId, electronVersion, sourceDateEpoch: epoch, sourceCommit: commit, runtimeAcceptance: 'not-performed', windowClass: { configured: null, verification: 'not-performed' } }), null, 2) + '\n');
    function write(relative, content, mode = 0o644) { const file = path.join(stage, relative); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, content, { mode }); }
    write('usr/bin/npdms-desktop', '#!/bin/sh\nexec /opt/npdms-desktop/electron /opt/npdms-desktop/resources/app "$@"\n', 0o755);
    let desktop = fs.readFileSync(path.join(root, 'packaging/org.npdms.desktop.desktop'), 'utf8');
    if (options.development) desktop = desktop.replace('Name=NPDMS Desktop', 'Name=NPDMS Desktop (Development)');
    write(`usr/share/applications/${config.appId}.desktop`, desktop);
    copyTree(path.join(root, 'packaging/org.npdms.desktop.svg'), path.join(stage, `usr/share/icons/hicolor/scalable/apps/${config.appId}.svg`));
    write('DEBIAN/control', `Package: ${config.packageName}\nVersion: ${version}\nArchitecture: ${config.architecture}\nMaintainer: NPDMS Desktop Maintainers\nSection: utils\nPriority: optional\nDepends: ${config.dependencies}\nDescription: NPDMS Electron desktop ${options.development ? 'development build' : 'payload build'}\n Archive validation only; runtime and business acceptance are separate.\n`);
    normalizeTimes(stage, epoch);
    const result = spawnSync('dpkg-deb', ['--root-owner-group', '-Zgzip', '-z6', '--build', stage, output], { encoding: 'utf8', env: { ...process.env, SOURCE_DATE_EPOCH: String(epoch) } });
    if (result.status !== 0) throw new Error(result.stderr || result.error?.message || 'dpkg-deb failed');
    const sha256 = createHash('sha256').update(fs.readFileSync(output)).digest('hex');
    fs.writeFileSync(`${output}.sha256`, `${sha256}  ${path.basename(output)}\n`);
    return { output, sha256, mode: options.development ? 'development' : 'production-payload' };
  } finally { fs.rmSync(stage, { recursive: true, force: true }); }
}
if (require.main === module) { try { console.log(JSON.stringify(build(parseArgs(process.argv.slice(2))), null, 2)); } catch (error) { console.error(error.message); process.exitCode = 1; } }
module.exports = { parseArgs, validatePayload, copyTree, normalizeTimes, build, payloadInventory, frontendMetadata, dacMetadata, sourceCommit, createBuildInfo };
