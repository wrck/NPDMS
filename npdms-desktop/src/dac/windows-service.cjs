'use strict';
// Attach to the existing machine service. SCM/WinSW retain process and lock
// ownership; disconnecting this UI never kills Java or releases its H2 lock.
const cp = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const http = require('node:http');
const { serverOrigin } = require('../security.cjs');
const { nonce, verifyStatus } = require('./protocol.cjs');
const { parseMetadata, requestStatus, validateStatus } = require('./probe.cjs');
const WINDOWS_ERROR_CODES = ['DAC_WINDOWS_SERVICE_UNTRUSTED', 'DAC_WINDOWS_SERVICE_REQUIRED', 'DAC_WINDOWS_ACCESS_DENIED'];
const fail = code => Object.assign(new Error(code), { code });
function createWindowsServiceHost({ helper, execFile = cp.execFile }) {
  if (!path.isAbsolute(helper)) throw fail('DAC_INVALID_CONFIGURATION');
  return async (action, signal) => {
    if (!['snapshot', 'start'].includes(action)) throw fail('DAC_INVALID_CONFIGURATION');
    try {
      const stat = fs.lstatSync(helper);
      if (!stat.isFile() || stat.isSymbolicLink() || stat.nlink !== 1) throw Error();
      const manifest = JSON.parse(fs.readFileSync(path.join(path.dirname(helper), 'bridge-manifest.json'), 'utf8'));
      const digest = crypto.createHash('sha256').update(fs.readFileSync(helper)).digest('hex');
      if (manifest.format !== 1 || manifest.sha256 !== digest || manifest.bytes !== stat.size ||
          manifest.ownershipBoundary !== 'scm-process-listener') throw Error();
    } catch { throw fail('DAC_WINDOWS_SERVICE_REQUIRED'); }
    const env = {};
    for (const name of ['SystemRoot', 'WINDIR', 'TEMP', 'TMP']) if (process.env[name]) env[name] = process.env[name];
    return new Promise((resolve, reject) => {
      execFile(helper, [action], { windowsHide: true, shell: false, timeout: 35000, maxBuffer: 16384,
        encoding: 'utf8', env, signal }, (error, stdout, stderr) => {
        if (error) {
          const code = signal?.aborted ? 'DAC_START_CANCELLED' :
            /^DAC_WINDOWS_ACCESS_DENIED\r?\n?$/.test(stderr || '') ? 'DAC_WINDOWS_ACCESS_DENIED' : 'DAC_WINDOWS_SERVICE_UNTRUSTED';
          reject(fail(code)); return;
        }
        try { resolve(JSON.parse(stdout)); } catch { reject(fail('DAC_WINDOWS_SERVICE_UNTRUSTED')); }
      });
    });
  };
}
function config(input) {
  try {
    if (!input || Object.keys(input).length !== 2 || input.serverUrl !== input.webOrigin ||
        serverOrigin(input.serverUrl) !== input.serverUrl) throw Error();
  } catch { throw fail('DAC_INVALID_CONFIGURATION'); }
  return Object.freeze({ serverUrl: input.serverUrl, webOrigin: input.webOrigin });
}
function snapshot(value, expected) {
  if (!value || Object.keys(value).sort().join(',') !== 'metadata,ownershipVerified,serverUrl,serviceState,webOrigin' ||
      !['Stopped', 'StartPending', 'Running', 'StopPending', 'Paused', 'PausePending', 'ContinuePending'].includes(value.serviceState))
    throw fail('DAC_WINDOWS_SERVICE_UNTRUSTED');
  if (value.serverUrl !== expected.serverUrl || value.webOrigin !== expected.webOrigin) throw fail('DAC_ENVIRONMENT_MISMATCH');
  if (value.metadata === null && value.ownershipVerified === false) return null;
  if (value.ownershipVerified !== true || typeof value.metadata !== 'string' || value.metadata.length > 4096 || value.serviceState !== 'Running') throw fail('DAC_WINDOWS_SERVICE_UNTRUSTED');
  try { return Object.freeze({ ...parseMetadata(value.metadata), ...expected }); }
  catch { throw fail('DAC_WINDOWS_SERVICE_UNTRUSTED'); }
}
const same = (a, b) => !!a && !!b && ['pid', 'port', 'publicKey', 'instanceId', 'serverUrl', 'webOrigin'].every(key => a[key] === b[key]);
function createWindowsServiceRuntime({ helper, host = createWindowsServiceHost({ helper }),
  request = http.request, timeoutMs = 60000, pollMs = 250 } = {}) {
  if (typeof host !== 'function' || !Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 60000 ||
      !Number.isInteger(pollMs) || pollMs < 1 || pollMs > 5000) throw fail('DAC_INVALID_CONFIGURATION');
  let operation = null, state = 'not-started', reason = 'DAC_EXPLICIT_START_REQUIRED';
  function status() {
    return Object.freeze({ state, reason, mode: 'windows-installed-service', canStart: !operation,
      canStop: !!operation, ...(state === 'ready' && operation?.context ? {
        port: operation.context.port,
        pairingCode: crypto.createHash('sha256').update(Buffer.from(operation.context.publicKey, 'hex')).digest('hex').slice(0, 32).toUpperCase()
      } : {}) });
  }
  function invalidate(op, code) {
    op.controller.abort();
    if (operation === op) { operation = null; state = 'blocked'; reason = code; }
  }
  async function start(input) {
    if (operation) throw fail('DAC_BUSY');
    const expected = config(input);
    const op = { controller: new AbortController(), context: null };
    operation = op; state = 'starting'; reason = 'DAC_STARTING';
    const signal = op.controller.signal;
    const check = () => { if (signal.aborted || operation !== op) throw fail('DAC_START_CANCELLED'); };
    const timer = setTimeout(() => op.controller.abort(), timeoutMs);
    try {
      // Validate configured environment before any service start. Never reconfigure
      // an installation merely to match the URL typed in the shell.
      snapshot(await host('snapshot', signal), expected); check();
      snapshot(await host('start', signal), expected); check();
      while (true) {
        const pinned = snapshot(await host('snapshot', signal), expected); check();
        if (pinned) {
          const challenge = nonce();
          let verified = false;
          try {
            const response = validateStatus(await requestStatus(request, pinned.port, challenge, signal, 2000, 8192), pinned);
            verifyStatus(response, { ...pinned, nonce: challenge }); verified = true;
          } catch { /* STARTING or unavailable/unverified response cannot establish readiness. */ }
          check();
          if (verified) {
            if (!same(pinned, snapshot(await host('snapshot', signal), expected))) throw fail('DAC_CONTEXT_INVALIDATED');
            check(); op.context = pinned; state = 'ready'; reason = 'DAC_READY'; return status();
          }
        }
        await new Promise(resolve => {
          const done = () => { clearTimeout(wait); signal.removeEventListener('abort', done); resolve(); };
          const wait = setTimeout(done, pollMs); signal.addEventListener('abort', done, { once: true });
        });
        check();
      }
    } catch (error) {
      const code = [...WINDOWS_ERROR_CODES, 'DAC_ENVIRONMENT_MISMATCH', 'DAC_CONTEXT_INVALIDATED', 'DAC_START_CANCELLED'].includes(error.code) ? error.code : 'DAC_START_FAILED';
      // A start may have reached SCM; cancellation only disconnects the UI.
      if (operation === op) invalidate(op, code);
      throw fail(code);
    } finally { clearTimeout(timer); }
  }
  async function stop() {
    const op = operation; operation = null; op?.controller.abort();
    state = 'stopped'; reason = 'DAC_WINDOWS_DISCONNECTED';
    return status();
  }
  async function withOwnedContext(input, use) {
    const expected = config(input), op = operation, pinned = op?.context;
    if (typeof use !== 'function') throw fail('DAC_INVALID_CONFIGURATION');
    const isCurrent = () => operation === op && state === 'ready' && !!pinned && !op.controller.signal.aborted;
    if (!isCurrent()) throw fail('DAC_NOT_READY');
    if (pinned.serverUrl !== expected.serverUrl || pinned.webOrigin !== expected.webOrigin) throw fail('DAC_ENVIRONMENT_MISMATCH');
    async function check() {
      try {
        if (!isCurrent() || !same(pinned, snapshot(await host('snapshot', op.controller.signal), expected))) throw Error();
        if (!isCurrent()) throw Error();
      } catch { invalidate(op, 'DAC_CONTEXT_INVALIDATED'); throw fail('DAC_CONTEXT_INVALIDATED'); }
    }
    await check();
    // Existing bridge verifies a fresh signed nonce on the exact TCP connection
    // carrying each operation; protected metadata is checked again after use.
    const result = await use(Object.freeze({ ...pinned, signal: op.controller.signal, isCurrent }));
    await check();
    return result;
  }
  return Object.freeze({ status, start, stop, withOwnedContext });
}
module.exports = { createWindowsServiceRuntime, createWindowsServiceHost, WINDOWS_ERROR_CODES };
