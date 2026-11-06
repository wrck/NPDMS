'use strict';
// Offline source staging only: no installation, Java, service startup, or device access.
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const BASELINE = 'd4f6918d67c3f5b32559d7eba6deb6e9a847858a';
const WEB = 'device-ops-platform/device-ops-web';
const OVERRIDE_ROOT = path.resolve(__dirname, '../dac-web-overrides');
const OVERLAYS = Object.freeze([
  Object.freeze({ path: 'src/components/ScriptArtifactEditor.vue', sourceSha256: '8e65a9eee20ed8048b9ddc074d5a4a33e52761d65e449047e859413ff6a0161f', overrideSha256: '954f8e9bd6d0983a0e0bc21b1294b151b0b0d30df3040c06fca2b4565610ad90' }),
  Object.freeze({ path: 'src/data/common-commands.ts', sourceSha256: null, overrideSha256: 'aa14ae9d578324263194edb7e344f419f5535f3a126f0eeb419f3eee259e2e72' }),
  Object.freeze({ path: 'src/components/common-commands-unavailable.spec.ts', sourceSha256: null, overrideSha256: '42ec319a8552c4e94d961d977be05164dd7be70b3754f176fcc63c44580a15e7' })
]);
const sha256 = value => createHash('sha256').update(value).digest('hex');
function git(repo, args, options = {}) {
  const result = spawnSync('git', ['-c', 'core.fsmonitor=false', ...args], {
    cwd: repo, env: { ...process.env, GIT_NO_LAZY_FETCH: '1' }, maxBuffer: 64 * 1024 * 1024, ...options
  });
  if (result.status !== 0) throw new Error(`DAC_WEB_GIT_FAILED: ${args[0]}`);
  return result.stdout;
}
function verifyBaseline(repo) {
  if (git(repo, ['rev-parse', 'HEAD']).toString().trim() !== BASELINE) throw new Error('EXACT_DAC_WEB_BASELINE_REQUIRED');
  if (git(repo, ['status', '--porcelain=v1', '--untracked-files=no']).length) throw new Error('CLEAN_DAC_WEB_BASELINE_REQUIRED');
}
function excluded(file) {
  return file.split('/').some(part => part.startsWith('.env') || ['node_modules', '.git', '.npmrc', '.yarnrc', '.yarnrc.yml', '.pnpmfile.cjs'].includes(part))
    || /\.(?:pem|key|p12|pfx)$/i.test(file);
}
function readSource(repo) {
  verifyBaseline(repo);
  const prefix = `${WEB}/`;
  const entries = git(repo, ['ls-tree', '-r', '-z', BASELINE, '--', WEB]).toString().split('\0').filter(Boolean).map(line => {
    const match = /^(100644|100755) blob ([a-f0-9]{40})\t(.+)$/.exec(line);
    if (!match || !match[3].startsWith(prefix)) throw new Error('UNSUPPORTED_DAC_WEB_SOURCE_ENTRY');
    const file = match[3].slice(prefix.length);
    if (!file || file.includes('\\') || file.split('/').some(part => !part || part === '.' || part === '..')) throw new Error('INVALID_DAC_WEB_SOURCE_PATH');
    return { path: file, mode: match[1], object: match[2] };
  }).filter(entry => !excluded(entry.path));
  if (!entries.some(entry => entry.path === 'pnpm-lock.yaml') || !entries.some(entry => entry.path === 'vite.config.ts')) throw new Error('DAC_WEB_SOURCE_INPUT_MISSING');
  const objects = git(repo, ['cat-file', '--batch'], { input: entries.map(entry => entry.object).join('\n') + '\n' });
  let cursor = 0;
  const files = entries.map(entry => {
    const newline = objects.indexOf(10, cursor);
    const match = /^([a-f0-9]{40}) blob (\d+)$/.exec(objects.subarray(cursor, newline).toString('ascii'));
    if (!match || match[1] !== entry.object) throw new Error('INVALID_DAC_WEB_GIT_OBJECT');
    const size = Number(match[2]); cursor = newline + 1;
    const bytes = Buffer.from(objects.subarray(cursor, cursor + size)); cursor += size;
    if (bytes.length !== size || objects[cursor++] !== 10) throw new Error('TRUNCATED_DAC_WEB_GIT_OBJECT');
    return { path: entry.path, mode: entry.mode, size, sha256: sha256(bytes), bytes };
  });
  if (cursor !== objects.length) throw new Error('UNEXPECTED_DAC_WEB_GIT_OBJECT');
  return files;
}
function validateOverlays(files, overrideRoot = OVERRIDE_ROOT) {
  const sources = new Map(files.map(file => [file.path, file]));
  return OVERLAYS.map(entry => {
    const original = sources.get(entry.path);
    if ((original?.sha256 ?? null) !== entry.sourceSha256) throw new Error(`DAC_WEB_SOURCE_HASH_MISMATCH: ${entry.path}`);
    const file = path.join(overrideRoot, entry.path);
    const real = fs.realpathSync(file);
    if (!within(fs.realpathSync(overrideRoot), real) || !fs.lstatSync(file).isFile()) throw new Error(`UNSAFE_DAC_WEB_OVERLAY: ${entry.path}`);
    const bytes = fs.readFileSync(file);
    if (sha256(bytes) !== entry.overrideSha256) throw new Error(`DAC_WEB_OVERLAY_HASH_MISMATCH: ${entry.path}`);
    return { ...entry, bytes };
  });
}
function within(parent, child) {
  const relative = path.relative(parent, child);
  return !relative || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}
function safeStage(source, stage, overrideRoot = OVERRIDE_ROOT) {
  const requested = path.resolve(stage);
  // Require a fresh target and an existing parent; never delete or replace anything.
  try { fs.lstatSync(requested); throw new Error('FRESH_DAC_WEB_STAGE_REQUIRED'); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const destination = path.join(fs.realpathSync(path.dirname(requested)), path.basename(requested));
  const protectedRoots = [fs.realpathSync(source), fs.realpathSync(overrideRoot)];
  if (protectedRoots.some(root => within(root, destination) || within(destination, root))) throw new Error('UNSAFE_DAC_WEB_STAGE');
  return destination;
}
function prepare({ source, stage, dependencies, overrideRoot = OVERRIDE_ROOT }) {
  const repo = fs.realpathSync(source);
  const destination = safeStage(repo, stage, overrideRoot);
  const files = readSource(repo);
  const overlays = validateOverlays(files, overrideRoot);
  let installed;
  if (dependencies) {
    installed = fs.realpathSync(dependencies);
    for (const binary of ['vitest/vitest.mjs', 'vue-tsc/bin/vue-tsc.js', 'vite/bin/vite.js']) {
      if (!fs.statSync(path.join(installed, binary)).isFile()) throw new Error(`EXISTING_DAC_WEB_DEPENDENCY_REQUIRED: ${binary}`);
    }
  }
  // All guards precede the first write. Only immutable tracked Git blobs are copied.
  fs.mkdirSync(destination);
  for (const file of files) {
    const output = path.join(destination, file.path);
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, file.bytes, { mode: file.mode === '100755' ? 0o755 : 0o644 });
  }
  for (const overlay of overlays) {
    const output = path.join(destination, overlay.path);
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, overlay.bytes);
  }
  if (installed) fs.symlinkSync(installed, path.join(destination, 'node_modules'), 'dir');
  const manifest = {
    format: 1, baseline: BASELINE, source: WEB, catalogueStatus: 'BLOCKED_BY_SPEC',
    overlayPurpose: 'Explicitly unavailable command presets; manual editing retained',
    sourceTreeSha256: sha256(JSON.stringify(files.map(({ bytes, ...file }) => file))),
    files: files.map(({ bytes, ...file }) => file),
    overlays: overlays.map(({ bytes, ...file }) => file)
  };
  fs.writeFileSync(path.join(destination, 'dac-web-source-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  verifyBaseline(repo);
  return { cwd: destination, baseline: BASELINE, catalogueStatus: manifest.catalogueStatus, sourceFiles: files.length, overlayFiles: overlays.length, dependenciesLinked: Boolean(installed) };
}
if (require.main === module) {
  try {
    const args = process.argv.slice(2); const options = {};
    if (args.length % 2) throw new Error('Expected --source <baseline-repo> --stage <fresh-path> [--dependencies <installed-node_modules>]');
    for (let i = 0; i < args.length; i += 2) {
      if (!['--source', '--stage', '--dependencies'].includes(args[i]) || Object.hasOwn(options, args[i].slice(2))) throw new Error('INVALID_DAC_WEB_PREPARE_ARGUMENT');
      options[args[i].slice(2)] = args[i + 1];
    }
    if (!options.source || !options.stage) throw new Error('SOURCE_AND_FRESH_STAGE_REQUIRED');
    console.log(JSON.stringify(prepare(options), null, 2));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { BASELINE, WEB, OVERLAYS, sha256, excluded, readSource, validateOverlays, safeStage, prepare };
