'use strict';
// Main-process only. Construction/status are read-only; start must be an explicit,
// trusted-shell action. Dependencies and roots must never come from IPC input.
const fs = require('node:fs');
const fsp = fs.promises;
const path = require('node:path');
const crypto = require('node:crypto');
const { environment } = require('./protocol.cjs');
const { ensurePrivateDirectory, provisionIdentity } = require('./identity.cjs');
const { loadMasterKey } = require('./key-vault.cjs');
const { createLinuxLockHost } = require('./linux-lock.cjs');
const { createReadinessProbe } = require('./probe.cjs');
const { DacSupervisor } = require('./supervisor.cjs');
const BASELINE = 'd4f6918d67c3f5b32559d7eba6deb6e9a847858a';
const PARSER = 'parser-releases/device-command-output-1.4.0';
const REQUIRED = ['device-ops-server.jar', 'jre/bin/java', 'jre/release', 'jre/lib/modules',
  'jre/lib/libjli.so', 'jre/lib/server/libjvm.so', ...['manifest.json', 'model-profiles.json',
    'projections.json', 'verification-cases.json', 'rules/base.json', 'rules/model-vpn1000-ga.json',
    'input-real-sanitized.json', 'input-tech-only-sanitized.json', 'expected-real.json',
    'expected-tech-only.json'].map(name => `${PARSER}/${name}`)];
const DAC_ERROR_CODES = Object.freeze(['DAC_INVALID_CONFIGURATION', 'DAC_BUSY', 'DAC_UNSUPPORTED_PLATFORM',
  'DAC_RESOURCES_MISSING', 'DAC_RESOURCES_INVALID', 'DAC_RESOURCES_UNVERIFIED', 'DAC_VAULT_UNAVAILABLE',
  'DAC_RECOVERY_REQUIRED', 'DAC_ENVIRONMENT_MISMATCH', 'DAC_DATA_UNTRUSTED', 'DAC_LOCK_UNAVAILABLE',
  'DAC_START_FAILED', 'DAC_START_CANCELLED']);
const failure = code => Object.assign(new Error(code), { code });
function absolute(value) {
  return typeof value === 'string' && path.isAbsolute(value) && path.resolve(value) === value &&
    value !== path.parse(value).root && !/[\0\r\n]/.test(value);
}
function configuration(input) {
  try {
    if (!input || ![Object.prototype, null].includes(Object.getPrototypeOf(input)) ||
        Reflect.ownKeys(input).length !== 2 || !Object.hasOwn(input, 'serverUrl') || !Object.hasOwn(input, 'webOrigin')) throw Error();
    for (const name of ['serverUrl', 'webOrigin']) {
      const d = Object.getOwnPropertyDescriptor(input, name);
      if (!d || !Object.hasOwn(d, 'value') || typeof d.value !== 'string' || d.value.length > 2048 ||
          !d.value.startsWith('https://') || /[^\x21-\x7e]|["<>*]/.test(d.value)) throw Error();
    }
    const result = environment(input.serverUrl, input.webOrigin);
    // No host-case/port/path rewriting: the exact configured bytes bind storage,
    // launch and signed readiness. Only baseline trailing-slash removal is allowed.
    return result;
  } catch { throw failure('DAC_INVALID_CONFIGURATION'); }
}
function trustedPath(target, kind) {
  let current = path.parse(target).root;
  for (const part of target.slice(current.length).split(path.sep).filter(Boolean)) {
    current = path.join(current, part);
    const st = fs.lstatSync(current);
    if (st.isSymbolicLink() || ![0, process.getuid()].includes(st.uid) ||
        ((st.mode & 0o022) && !(st.isDirectory() && (st.mode & 0o1000)))) throw Error();
    if (current !== target && !st.isDirectory()) throw Error();
    if (current === target && (kind === 'file' ? !st.isFile() || st.nlink !== 1 || st.size < 1 : !st.isDirectory())) throw Error();
  }
  return fs.lstatSync(target);
}
function resourceState(directory, buildInfo) {
  try {
    for (const name of REQUIRED) {
      const st = trustedPath(path.join(directory, name), 'file');
      if (name === 'jre/bin/java' && !(st.mode & 0o111)) throw Error();
    }
  } catch (error) { return error.code === 'ENOENT' ? 'DAC_RESOURCES_MISSING' : 'DAC_RESOURCES_INVALID'; }
  try { trustedPath(buildInfo, 'file'); }
  catch { return 'DAC_RESOURCES_UNVERIFIED'; }
  return null;
}
async function hashFile(file) {
  const handle = await fsp.open(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
  try {
    const before = await handle.stat();
    if (!before.isFile() || before.nlink !== 1) throw Error();
    const hash = crypto.createHash('sha256');
    const bytes = Buffer.alloc(65536);
    let size = 0;
    for (;;) {
      const { bytesRead } = await handle.read(bytes, 0, bytes.length, null);
      if (!bytesRead) break;
      size += bytesRead; hash.update(bytes.subarray(0, bytesRead));
    }
    const after = await handle.stat();
    if (size !== before.size || after.size !== before.size || after.ctimeMs !== before.ctimeMs || after.mtimeMs !== before.mtimeMs) throw Error();
    return { size, sha256: hash.digest('hex') };
  } finally { await handle.close(); }
}
async function verifyResources(directory, buildInfo, checkCancelled) {
  const problem = resourceState(directory, buildInfo);
  if (problem) throw failure(problem);
  try {
    if (trustedPath(buildInfo, 'file').size > 32 * 1024 * 1024) throw Error();
    const info = JSON.parse(await fsp.readFile(buildInfo, 'utf8'));
    if (info.format !== 1 || info.mode !== 'production-payload' || info.appId !== 'org.npdms.desktop' ||
        typeof info.sourceCommit !== 'string' || !/^[a-f0-9]{40}$/.test(info.sourceCommit) || info.sourceCommit === '0'.repeat(40) ||
        info.frontend?.baseline !== BASELINE ||
        info.dac?.complete !== true || !Array.isArray(info.payloadFiles)) throw Error();
    const declared = new Map();
    for (const entry of info.payloadFiles) {
      if (typeof entry.path !== 'string' || !entry.path.startsWith('resources/dac/')) continue;
      const relative = entry.path.slice('resources/dac/'.length);
      if (!relative || relative.split('/').some(p => !p || p === '.' || p === '..') || /[\\\0\r\n]/.test(relative) ||
          declared.has(relative) || !Number.isSafeInteger(entry.size) || entry.size < 1 || !/^[a-f0-9]{64}$/.test(entry.sha256)) throw Error();
      declared.set(relative, entry);
    }
    const actual = [];
    async function walk(relative = '', depth = 0) {
      if (depth > 16 || actual.length > 10000) throw Error();
      for (const name of await fsp.readdir(path.join(directory, relative))) {
        checkCancelled();
        const child = relative ? `${relative}/${name}` : name;
        const target = path.join(directory, child);
        const st = await fsp.lstat(target);
        if (st.isDirectory()) { trustedPath(target, 'directory'); await walk(child, depth + 1); }
        else { trustedPath(target, 'file'); actual.push(child); }
      }
    }
    await walk();
    if (actual.length !== declared.size || REQUIRED.some(name => !declared.has(name))) throw Error();
    for (const relative of actual) {
      checkCancelled();
      const entry = declared.get(relative);
      if (!entry) throw Error();
      const digest = await hashFile(path.join(directory, relative));
      if (entry.size !== digest.size || entry.sha256 !== digest.sha256) throw Error();
    }
    const release = await fsp.readFile(path.join(directory, 'jre/release'), 'utf8');
    if (!/^JAVA_VERSION="25(?:[.+][^"]*)?"$/m.test(release) || !/^OS_NAME="Linux"$/m.test(release) || !/^OS_ARCH="x86_64"$/m.test(release)) throw Error();
    const parser = JSON.parse(await fsp.readFile(path.join(directory, PARSER, 'manifest.json'), 'utf8'));
    if (parser.logType !== 'device-command-output' || parser.releaseVersion !== '1.4.0') throw Error();
  } catch (error) {
    if (error.code === 'DAC_START_CANCELLED') throw error;
    throw failure('DAC_RESOURCES_UNVERIFIED');
  }
}
async function inspectData(directory, depth = 0) {
  if (depth > 16) throw Error();
  const names = await fsp.readdir(directory);
  if (names.length > 10000) throw Error();
  for (const name of names) {
    const file = path.join(directory, name), st = await fsp.lstat(file);
    if (st.isSymbolicLink() || st.uid !== process.getuid() || (st.mode & 0o022) ||
        (!st.isDirectory() && (!st.isFile() || st.nlink !== 1))) throw Error();
    if (st.isDirectory()) await inspectData(file, depth + 1);
  }
  return names;
}
async function readBinding(file) {
  const handle = await fsp.open(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
  try {
    const st = await handle.stat();
    if (!st.isFile() || st.uid !== process.getuid() || (st.mode & 0o777) !== 0o600 || st.nlink !== 1 || st.size > 8192) throw Error();
    const bytes = Buffer.alloc(8193);
    let used = 0;
    while (used < bytes.length) {
      const { bytesRead } = await handle.read(bytes, used, bytes.length - used, used);
      if (!bytesRead) break;
      used += bytesRead;
    }
    const after = await handle.stat();
    if (used !== st.size || after.size !== st.size || after.ctimeMs !== st.ctimeMs || after.mtimeMs !== st.mtimeMs) throw Error();
    return JSON.parse(bytes.subarray(0, used).toString('utf8'));
  } finally { await handle.close(); }
}
async function writeBinding(file, value) {
  const temporary = path.join(path.dirname(file), `.environment-${crypto.randomUUID()}.tmp`);
  try {
    const handle = await fsp.open(temporary, 'wx', 0o600);
    try { await handle.writeFile(JSON.stringify(value) + '\n'); await handle.sync(); }
    finally { await handle.close(); }
    await fsp.link(temporary, file); // Never replace an existing binding.
    const dir = await fsp.open(path.dirname(file), 'r');
    try { await dir.sync(); } finally { await dir.close(); }
  } finally { await fsp.unlink(temporary).catch(() => {}); }
}
function createDacRuntime({ resourcesDirectory, dataRoot, vault, lockHost = createLinuxLockHost(),
  createProbe = createReadinessProbe, timeoutMs = 30000, pollMs = 100, stopTimeoutMs = 5000 } = {}) {
  if (!absolute(resourcesDirectory) || !absolute(dataRoot) || typeof lockHost?.acquireLock !== 'function' ||
      typeof lockHost?.spawn !== 'function' || typeof createProbe !== 'function' ||
      ![timeoutMs, pollMs, stopTimeoutMs].every(n => Number.isSafeInteger(n) && n > 0 && n <= 60000)) throw failure('DAC_INVALID_CONFIGURATION');
  const buildInfo = path.join(resourcesDirectory, '../../build-info.json');
  let state = 'not-started', reason = 'DAC_EXPLICIT_START_REQUIRED', operation = null, pairing = null;
  function status() {
    let currentState = state, currentReason = reason;
    if (!operation && (state === 'not-started' || reason.startsWith('DAC_RESOURCES_'))) {
      const problem = process.platform !== 'linux' || process.arch !== 'x64' ? 'DAC_UNSUPPORTED_PLATFORM' : resourceState(resourcesDirectory, buildInfo);
      if (problem) { currentState = 'blocked'; currentReason = problem; }
    }
    return Object.freeze({ state: currentState, reason: currentReason,
      canStart: !operation && !['DAC_RESOURCES_MISSING', 'DAC_RESOURCES_INVALID', 'DAC_RESOURCES_UNVERIFIED', 'DAC_UNSUPPORTED_PLATFORM'].includes(currentReason),
      canStop: !!operation,
      ...(currentState === 'ready' && pairing ? { pairingCode: pairing.pairingCode, port: pairing.port } : {}) });
  }
  function finish(op) {
    if (op.pending || op.lease || operation !== op) return;
    operation = null; pairing = null;
    if (op.cancelled) { state = 'stopped'; reason = 'DAC_STOPPED'; }
    else if (!op.failed) { state = 'stopped'; reason = 'DAC_PROCESS_EXITED'; }
    op.resolveDone();
  }
  async function start(input) {
    if (operation) throw failure('DAC_BUSY');
    const config = configuration(input);
    const op = { cancelled: false, pending: true, lease: false, supervisor: null, failed: false };
    op.done = new Promise(resolve => { op.resolveDone = resolve; });
    operation = op; state = 'starting'; reason = 'DAC_STARTING'; pairing = null;
    const check = () => { if (op.cancelled) throw failure('DAC_START_CANCELLED'); };
    let key, release;
    try {
      if (process.platform !== 'linux' || process.arch !== 'x64') throw failure('DAC_UNSUPPORTED_PLATFORM');
      await verifyResources(resourcesDirectory, buildInfo, check); check();
      try {
        if (vault?.backend !== 'linux-os-key-vault' || typeof vault.isAvailable !== 'function' || !await vault.isAvailable()) throw Error();
      } catch { throw failure('DAC_VAULT_UNAVAILABLE'); }
      check();
      const id = crypto.createHash('sha256').update(config.serverUrl).digest('hex');
      const directory = path.join(dataRoot, 'environments', id);
      try {
        await ensurePrivateDirectory(dataRoot); check();
        await ensurePrivateDirectory(path.join(dataRoot, 'environments')); check();
        await ensurePrivateDirectory(directory); check();
      } catch (error) { if (error.code === 'DAC_START_CANCELLED') throw error; throw failure('DAC_DATA_UNTRUSTED'); }
      try {
        const unlock = await lockHost.acquireLock(directory);
        if (typeof unlock !== 'function') throw Error();
        op.lease = true;
        release = () => {
          if (!op.lease || op.releaseAttempted) return;
          op.releaseAttempted = true;
          try { unlock(); }
          catch {
            // An uncertain close must not advertise a reusable lifetime lock or
            // forward a filesystem error through IPC. No forced release/retry.
            op.failed = true; state = 'stopping'; reason = 'DAC_LOCK_UNAVAILABLE';
            return;
          }
          op.lease = false; finish(op);
        };
      } catch { throw failure('DAC_LOCK_UNAVAILABLE'); }
      check();
      let names;
      try { names = await inspectData(directory); } catch { throw failure('DAC_DATA_UNTRUSTED'); }
      const fresh = names.every(name => name === 'service.lock');
      const bindingFile = path.join(directory, 'environment.json');
      const binding = { version: 1, ...config };
      if (!fresh) {
        let saved;
        try { saved = await readBinding(bindingFile); } catch { throw failure('DAC_RECOVERY_REQUIRED'); }
        if (saved.version !== 1 || Object.keys(saved).length !== 3 || saved.serverUrl !== config.serverUrl || saved.webOrigin !== config.webOrigin) throw failure('DAC_ENVIRONMENT_MISMATCH');
      }
      check();
      const installationId = `dac-v1-${id}`;
      // Vault adapters return caller-owned buffers. Wipe both adapter returns and
      // loadMasterKey's defensive copy, even on decrypt/provision/launch failure.
      const readBuffers = [];
      const keyVault = { backend: vault.backend, isAvailable: () => vault.isAvailable(),
        read: async name => { const value = await vault.read(name); if (Buffer.isBuffer(value)) readBuffers.push(value); return value; },
        createIfAbsent: (name, bytes) => vault.createIfAbsent(name, bytes) };
      try {
        if (fresh) {
          if (await keyVault.read(installationId) != null) throw Error();
          check();
          await writeBinding(bindingFile, binding); check();
        }
        key = await loadMasterKey(keyVault, installationId, { create: fresh });
      } catch (error) { if (error.code === 'DAC_START_CANCELLED') throw error; throw failure('DAC_RECOVERY_REQUIRED'); }
      finally { for (const bytes of readBuffers) bytes.fill(0); }
      check();
      let identity;
      try { identity = await provisionIdentity({ directory, masterKey: key, create: fresh }); }
      catch { throw failure('DAC_RECOVERY_REQUIRED'); }
      check();
      try { await ensurePrivateDirectory(path.join(directory, 'data')); }
      catch { throw failure('DAC_DATA_UNTRUSTED'); }
      check();
      const runtimeFile = path.join(directory, 'desktop-runtime.properties');
      // Clear only ephemeral metadata under the held lock; durable H2, identity,
      // key and outbox data are never removed, repaired or replaced.
      for (const file of [runtimeFile, `${runtimeFile}.new`]) {
        try {
          const st = await fsp.lstat(file);
          if (!st.isFile() || st.isSymbolicLink() || st.uid !== process.getuid() || st.nlink !== 1 || (st.mode & 0o022)) throw Error();
          await fsp.unlink(file);
        } catch (error) { if (error.code !== 'ENOENT') throw failure('DAC_DATA_UNTRUSTED'); }
      }
      check();
      op.supervisor = new DacSupervisor({ timeoutMs, pollMs,
        acquireLock: async target => { check(); if (target !== directory || !op.lease) throw Error(); return release; },
        spawn: (command, args, options) => { check(); return lockHost.spawn(command, args, options); },
        probe: createProbe({ runtimeFile, protectedDirectory: directory }) });
      const verified = await op.supervisor.start({ ...config, publicKey: identity.publicKey,
        javaPath: path.join(resourcesDirectory, 'jre/bin/java'), jarPath: path.join(resourcesDirectory, 'device-ops-server.jar'),
        parserDirectory: path.join(resourcesDirectory, PARSER), runtimeFile, dataDirectory: directory,
        inheritedEnv: { PATH: '/usr/bin:/bin', LANG: 'C.UTF-8', LC_ALL: 'C.UTF-8' } }, key);
      check();
      if (!op.supervisor.child || !op.lease) throw failure('DAC_START_FAILED');
      pairing = { pairingCode: identity.pairingCode, port: verified.port };
      state = 'ready'; reason = 'DAC_READY';
    } catch (error) {
      op.failed = true; pairing = null;
      const uncertainRelease = op.lease && op.releaseAttempted;
      const code = uncertainRelease ? 'DAC_LOCK_UNAVAILABLE' : DAC_ERROR_CODES.includes(error.code) ? error.code : 'DAC_START_FAILED';
      state = op.supervisor?.child || uncertainRelease ? 'stopping' : 'blocked'; reason = code;
      if (op.supervisor?.child) { try { op.supervisor.stop(); } catch { /* Retain child and lock. */ } }
      throw failure(code); // Never forward raw OS errors, paths or secrets.
    } finally {
      key?.fill(0); op.pending = false;
      if (!op.supervisor?.child) release?.();
      finish(op);
    }
    return status();
  }
  async function stop() {
    const op = operation;
    if (!op) return status();
    op.cancelled = true; pairing = null; state = 'stopping'; reason = 'DAC_STOP_PENDING';
    try { op.supervisor?.stop(); } catch { /* A signal error is not evidence of exit. */ }
    let timer;
    try { await Promise.race([op.done, new Promise(resolve => { timer = setTimeout(resolve, stopTimeoutMs); })]); }
    finally { clearTimeout(timer); }
    return status();
  }
  return Object.freeze({ status, start, stop });
}
module.exports = { createDacRuntime, DAC_ERROR_CODES };
