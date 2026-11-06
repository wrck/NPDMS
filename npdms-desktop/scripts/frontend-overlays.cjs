'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');

const BASELINE = 'd4f6918d67c3f5b32559d7eba6deb6e9a847858a';
const FRONTEND = 'yudao-ui/yudao-ui-admin-vue3';
const OVERLAY_VERSION = 'npdms-desktop-dac-v1';
const OVERLAYS = Object.freeze([
  Object.freeze({ path: 'src/components/DeviceCollection/localDac.ts', sha256: '0578f62141bda77a8841a08f931b344d991d47046958acf1cb5fcffb8b79464a', overrideSha256: 'c936a553bdc94043fe8c559d2f4f397aaa9edf6fe0e5288f149909178c970dc5' }),
  Object.freeze({ path: 'src/components/DeviceCollection/LocalDacStatus.vue', sha256: 'a716e5c1c1e1ad616c53faf07c46e37f0d292a66d5d406cb92f12e665e333846', overrideSha256: 'ceb13b107467432c25da0d5e94ffd486e7f64ca3a0dbee7936e5b13d6e69b163' })
]);
const UNCHANGED_CONTRACTS = Object.freeze([
  Object.freeze({ path: 'src/components/DeviceCollection/localDacConnection.ts', sha256: 'f67cb916acf4ecfda3fa00fde7b1ae1d0640bf38a4da446fc888eb37c35aeef0' })
]);
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const sortFiles = (a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0;
function git(repo, args, options = {}) {
  const result = spawnSync('git', ['-c', 'core.fsmonitor=false', ...args], { cwd: repo, env: { ...process.env, GIT_NO_LAZY_FETCH: '1' }, maxBuffer: 128 * 1024 * 1024, ...options });
  if (result.status !== 0) throw new Error(`BASELINE_GIT_CHECK_FAILED: ${args[0]}`);
  return result.stdout;
}
function isExcluded(file) {
  return file.split('/').some(part => part.startsWith('.env') || ['node_modules', '.git', '.npmrc', '.pnpmfile.cjs', '.yarnrc', '.yarnrc.yml'].includes(part))
    || /\.(?:pem|key|p12|pfx)$/i.test(file);
}
function verifyBaseline(repo) {
  if (git(repo, ['rev-parse', 'HEAD']).toString().trim() !== BASELINE) throw new Error('EXACT_BASELINE_REQUIRED');
  // Both index and worktree must match HEAD, including files outside the frontend.
  if (git(repo, ['status', '--porcelain=v1', '--untracked-files=no']).length) throw new Error('CLEAN_BASELINE_REQUIRED');
  for (const directory of [repo, path.join(repo, FRONTEND)]) {
    if (fs.readdirSync(directory).some(name => name.startsWith('.env'))) throw new Error('ENV_FILES_NOT_ALLOWED_IN_REPRODUCIBLE_DESKTOP_BUILD');
  }
}
function readBaselineFiles(repo) {
  verifyBaseline(repo);
  const prefix = `${FRONTEND}/`;
  const entries = git(repo, ['ls-tree', '-r', '-z', BASELINE, '--', FRONTEND]).toString('utf8').split('\0').filter(Boolean).map(line => {
    const match = /^(100644|100755) blob ([a-f0-9]{40})\t(.+)$/.exec(line);
    if (!match || !match[3].startsWith(prefix)) throw new Error('UNSUPPORTED_TRACKED_FRONTEND_ENTRY');
    const file = match[3].slice(prefix.length);
    if (!file || file.split('/').some(part => !part || part === '.' || part === '..') || file.includes('\\')) throw new Error('INVALID_TRACKED_FRONTEND_PATH');
    if (file.split('/').some(part => part.startsWith('.env'))) throw new Error('ENV_FILES_NOT_ALLOWED_IN_REPRODUCIBLE_DESKTOP_BUILD');
    return { path: file, mode: match[1], object: match[2] };
  }).filter(entry => !isExcluded(entry.path)).sort(sortFiles);
  if (!entries.some(entry => entry.path === 'vite.config.ts') || !entries.some(entry => entry.path === 'pnpm-lock.yaml')) throw new Error('FRONTEND_BUILD_INPUT_MISSING');
  // Read immutable Git objects, never copy the local checkout or untracked content.
  const objects = git(repo, ['cat-file', '--batch'], { input: entries.map(entry => entry.object).join('\n') + '\n' });
  let cursor = 0;
  const files = entries.map(entry => {
    const newline = objects.indexOf(10, cursor);
    const header = objects.subarray(cursor, newline).toString('ascii');
    const match = /^([a-f0-9]{40}) blob (\d+)$/.exec(header);
    if (!match || match[1] !== entry.object) throw new Error('INVALID_BASELINE_GIT_OBJECT');
    const size = Number(match[2]); cursor = newline + 1;
    const bytes = Buffer.from(objects.subarray(cursor, cursor + size)); cursor += size;
    if (bytes.length !== size || objects[cursor++] !== 10) throw new Error('TRUNCATED_BASELINE_GIT_OBJECT');
    return { path: entry.path, mode: entry.mode, size, sha256: sha256(bytes), bytes };
  });
  if (cursor !== objects.length) throw new Error('UNEXPECTED_BASELINE_GIT_OBJECT');
  return files;
}
function verifySourceHashes(files) {
  const byPath = new Map(files.map(file => [file.path, file]));
  for (const expected of [...OVERLAYS, ...UNCHANGED_CONTRACTS]) {
    if (byPath.get(expected.path)?.sha256 !== expected.sha256) throw new Error(`FRONTEND_SOURCE_HASH_MISMATCH: ${expected.path}`);
  }
}
function readOverrides(overrideRoot) {
  return OVERLAYS.map(expected => {
    const file = path.join(overrideRoot, expected.path);
    let bytes;
    try {
      // Full source overrides are deliberately tracked deliverables, never optional patches.
      if (!fs.lstatSync(file).isFile() || fs.lstatSync(file).isSymbolicLink()) throw new Error();
      bytes = fs.readFileSync(file);
    } catch { throw new Error(`FRONTEND_OVERRIDE_MISSING: ${expected.path}`); }
    if (sha256(bytes) !== expected.overrideSha256) throw new Error(`FRONTEND_OVERRIDE_HASH_MISMATCH: ${expected.path}`);
    return { ...expected, bytes };
  });
}
function expectedOverlayProvenance(overrideRoot = path.resolve(__dirname, '../frontend-overrides')) {
  const overrides = readOverrides(overrideRoot);
  return {
    overlayVersion: OVERLAY_VERSION,
    sources: overrides.map(item => ({ path: item.path, sha256: item.sha256, overridePath: `frontend-overrides/${item.path}`, overrideSha256: sha256(item.bytes) })),
    unchangedContracts: UNCHANGED_CONTRACTS.map(item => ({ ...item }))
  };
}
function makeProvenance(files, overrideRoot) {
  return {
    ...expectedOverlayProvenance(overrideRoot),
    sourceTreeSha256: sha256(JSON.stringify(files.map(({ path: file, mode, size, sha256: hash }) => ({ path: file, mode, size, sha256: hash })).sort(sortFiles)))
  };
}
function statOrMissing(file) {
  try { return fs.lstatSync(file); } catch (error) { if (error.code === 'ENOENT') return undefined; throw error; }
}
function resolvedDestination(destination) {
  let existing = path.resolve(destination);
  const suffix = [];
  while (!fs.existsSync(existing)) {
    const parent = path.dirname(existing);
    if (parent === existing) throw new Error('INVALID_FRONTEND_STAGING_PATH');
    suffix.unshift(path.basename(existing));
    existing = parent;
  }
  return path.join(fs.realpathSync(existing), ...suffix);
}
function isWithin(parent, child) {
  const relative = path.relative(parent, child);
  return !relative || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}
function stagingGuard(source, stage, overrideRoot) {
  const requested = path.resolve(stage);
  if (statOrMissing(requested)?.isSymbolicLink()) throw new Error('UNSAFE_FRONTEND_STAGING_PATH');
  const destination = resolvedDestination(requested);
  const project = fs.realpathSync(path.resolve(__dirname, '..'));
  const overrides = fs.realpathSync(overrideRoot);
  if (isWithin(source, destination)) throw new Error('STAGING_INSIDE_BASELINE_NOT_ALLOWED');
  if (destination === path.parse(destination).root || [source, project, overrides].some(protectedPath => isWithin(destination, protectedPath)) || isWithin(overrides, destination)) throw new Error('UNSAFE_FRONTEND_STAGING_PATH');
  const marker = path.join(path.dirname(destination), `.${path.basename(destination)}.npdms-frontend-stage.json`);
  const markerBytes = JSON.stringify({ format: 1, kind: 'npdms-desktop-frontend-source', baseline: BASELINE, destination }) + '\n';
  const markerStat = statOrMissing(marker);
  if (markerStat && (!markerStat.isFile() || markerStat.isSymbolicLink() || fs.readFileSync(marker, 'utf8') !== markerBytes)) throw new Error('INVALID_FRONTEND_STAGING_MARKER');
  if (fs.existsSync(destination) && (!fs.lstatSync(destination).isDirectory() || !fs.existsSync(marker))) throw new Error('UNMARKED_FRONTEND_STAGING_DIRECTORY');
  return { destination, marker, markerBytes };
}
function stageFrontend({ source, stage, overrideRoot = path.resolve(__dirname, '../frontend-overrides') }) {
  const repo = fs.realpathSync(source);
  const { destination, marker, markerBytes } = stagingGuard(repo, stage, overrideRoot);
  // Resolve all guards before replacing a previous staging directory.
  const files = readBaselineFiles(repo);
  verifySourceHashes(files);
  const overrides = readOverrides(overrideRoot);
  fs.rmSync(destination, { recursive: true, force: true });
  fs.mkdirSync(destination, { recursive: true });
  fs.writeFileSync(marker, markerBytes);
  for (const file of files) {
    const output = path.join(destination, file.path);
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, file.bytes, { mode: file.mode === '100755' ? 0o755 : 0o644 });
  }
  for (const override of overrides) fs.writeFileSync(path.join(destination, override.path), override.bytes);
  return { cwd: destination, provenance: makeProvenance(files, overrideRoot), files: files.map(({ bytes, ...file }) => file) };
}
module.exports = { BASELINE, FRONTEND, OVERLAY_VERSION, OVERLAYS, UNCHANGED_CONTRACTS, sha256, isExcluded, verifyBaseline, readBaselineFiles, verifySourceHashes, readOverrides, expectedOverlayProvenance, makeProvenance, stagingGuard, stageFrontend };
