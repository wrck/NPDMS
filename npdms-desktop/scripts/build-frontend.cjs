'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const { BASELINE, FRONTEND, stageFrontend, verifyBaseline } = require('./frontend-overlays.cjs');
const ROOT = path.resolve(__dirname, '..');
function run(command, args, options) {
  const result = spawnSync(command, args, { ...options, stdio: 'inherit' });
  if (result.status !== 0) throw new Error(`${command} failed (${result.status ?? result.error?.code})`);
}
function build({ source, skipInstall = false }) {
  const repo = path.resolve(source);
  const { cwd, provenance } = stageFrontend({ source: repo, stage: path.join(ROOT, 'build/frontend-source') });
  const output = path.join(ROOT, 'build/frontend');
  fs.rmSync(output, { recursive: true, force: true });
  const env = Object.fromEntries(Object.entries(process.env).filter(([name]) => !name.startsWith('VITE_')));
  Object.assign(env, {
    COREPACK_HOME: process.env.COREPACK_HOME || path.join(ROOT, 'build/corepack'),
    VITE_APP_TITLE: 'NPDMS', VITE_BASE_URL: '', VITE_API_URL: '/admin-api', VITE_BASE_PATH: '/',
    VITE_OUT_DIR: output, VITE_SOURCEMAP: 'false', VITE_DROP_DEBUGGER: 'true', VITE_DROP_CONSOLE: 'true',
    VITE_APP_TENANT_ENABLE: 'true', VITE_APP_CAPTCHA_ENABLE: 'true', VITE_UPLOAD_TYPE: 'server',
    VITE_COMPRESS: ''
  });
  if (skipInstall) {
    const dependencies = path.join(repo, FRONTEND, 'node_modules');
    if (!fs.existsSync(path.join(dependencies, 'vite/bin/vite.js'))) throw new Error('BASELINE_NODE_MODULES_REQUIRED_FOR_SKIP_INSTALL');
    // Explicit local dependency reuse only; source, lockfile and generated declarations stay staged.
    fs.symlinkSync(dependencies, path.join(cwd, 'node_modules'), 'dir');
  } else {
    run('corepack', ['pnpm', 'install', '--frozen-lockfile', '--ignore-scripts', '--store-dir', path.join(ROOT, 'build/pnpm-store')], { cwd, env });
  }
  run(process.execPath, ['scripts/generate-iconify-collections.cjs', '--check'], { cwd, env });
  run(process.execPath, ['--max-old-space-size=6144', 'node_modules/vite/bin/vite.js', 'build', '--mode', 'desktop'], { cwd, env });
  verifyBaseline(repo);
  const files = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name, 'en'))) {
      const file = path.join(dir, entry.name);
      if (entry.isSymbolicLink()) throw new Error('SYMLINK_NOT_ALLOWED_IN_FRONTEND');
      if (entry.isDirectory()) walk(file);
      else { const bytes = fs.readFileSync(file); files.push({ path: path.relative(output, file).split(path.sep).join('/'), size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') }); }
    }
  }
  walk(output);
  if (!files.some(file => file.path === 'index.html')) throw new Error('FRONTEND_INDEX_MISSING');
  const destination = path.join(ROOT, 'resources/frontend');
  fs.rmSync(destination, { recursive: true, force: true }); fs.cpSync(output, destination, { recursive: true });
  fs.writeFileSync(path.join(destination, 'build-manifest.json'), JSON.stringify({ format: 1, baseline: BASELINE, frontend: FRONTEND, buildMode: 'desktop', files, provenance }, null, 2) + '\n');
  return { baseline: BASELINE, provenance, destination, files: files.length, bytes: files.reduce((sum, file) => sum + file.size, 0) };
}
if (require.main === module) {
  try {
    const args = process.argv.slice(2); const i = args.indexOf('--source');
    if (i < 0 || !args[i + 1] || args.some((x, n) => n !== i + 1 && !['--source', '--skip-install'].includes(x))) throw new Error('Usage: node scripts/build-frontend.cjs --source <exact-baseline-repo> [--skip-install]');
    console.log(JSON.stringify(build({ source: args[i + 1], skipInstall: args.includes('--skip-install') }), null, 2));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { BASELINE, build };
