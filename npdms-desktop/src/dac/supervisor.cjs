'use strict';
const path = require('node:path');
const { environment, validPort, nonce, verifyStatus } = require('./protocol.cjs');
function candidatePorts(previous) { return [...new Set([...(validPort(previous) ? [previous] : []), ...Array.from({ length: 10 }, (_, i) => 8280 + i)])]; }
function launchSpec(config, key, port, inherited = {}) {
  const envConfig = environment(config.serverUrl, config.webOrigin);
  if (!validPort(port) || !Buffer.isBuffer(key) || key.length !== 32) throw new Error('Invalid DAC launch parameters');
  for (const field of ['javaPath', 'jarPath', 'parserDirectory', 'runtimeFile', 'dataDirectory']) if (typeof config[field] !== 'string' || !path.isAbsolute(config[field]) || /[\0\r\n]/.test(config[field])) throw new Error('DAC paths must be absolute');
  const env = {};
  for (const [name, value] of Object.entries(inherited)) {
    if (!/^(SPRING_|DEVICE_OPS_|DAC_|OIDC_|MANAGEMENT_|LOGGING_|SERVER_)/i.test(name) && !/^(SERVER_URL|WEB_ORIGIN|SERVER_PORT|SERVER_ADDRESS|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|JAVA_OPTIONS|CLASSPATH|LD_PRELOAD|LD_LIBRARY_PATH)$/i.test(name)) env[name] = value;
  }
  Object.assign(env, { SERVER_URL: envConfig.serverUrl, WEB_ORIGIN: envConfig.webOrigin, DEVICE_OPS_CREDENTIAL_MASTER_KEY: key.toString('base64') });
  return { command: config.javaPath, args: ['-Xms128m', '-Xmx768m', '-Dfile.encoding=UTF-8', '-jar', config.jarPath, '--spring.profiles.active=desktop', '--spring.config.location=classpath:/application.yml,classpath:/application-desktop.yml', '--server.address=127.0.0.1', `--server.port=${port}`, `--device-ops.desktop.parser-release-directory=${config.parserDirectory}`, `--device-ops.desktop.runtime-file=${config.runtimeFile}`], options: { cwd: config.dataDirectory, env, shell: false, stdio: ['ignore', 'ignore', 'ignore'] } };
}
class DacSupervisor {
  constructor({ spawn, acquireLock, probe, timeoutMs = 30000, pollMs = 100 }) {
    if (![spawn, acquireLock, probe].every(x => typeof x === 'function') || !(timeoutMs > 0) || !(pollMs > 0)) throw new Error('DAC supervision dependencies required');
    Object.assign(this, { spawn, acquireLock, probe, timeoutMs, pollMs });
    this.busy = false; this.child = null;
  }
  async start(config, key) {
    if (this.busy) throw new Error('DAC already owned or starting');
    if (typeof config.publicKey !== 'string' || config.publicKey.length !== 64 || !/^[a-f0-9]{64}$/.test(config.publicKey)) throw new Error('Trusted DAC installation identity required');
    this.busy = true;
    let release;
    try {
      release = await this.acquireLock(config.dataDirectory);
      if (typeof release !== 'function') throw new Error('Exclusive DAC lock unavailable');
      for (const port of candidatePorts(config.previousPort)) {
        const spec = launchSpec(config, key, port, config.inheritedEnv || {});
        let child;
        try { child = this.spawn(spec.command, spec.args, spec.options); }
        finally { delete spec.options.env.DEVICE_OPS_CREDENTIAL_MASTER_KEY; }
        this.child = child;
        let outcome = null;
        const exited = new Promise(resolve => {
          child.once('exit', (code, signal) => { outcome = { code, signal }; resolve(outcome); });
          // A kill/IPC error is not evidence that Java stopped. Keep a persistent
          // listener so repeated termination errors cannot become unhandled events.
          child.on('error', () => {
            if (!child.pid) { outcome = { code: null, error: true }; resolve(outcome); }
          });
        });
        const deadline = Date.now() + this.timeoutMs;
        while (!outcome && Date.now() < deadline) {
          const challenge = nonce();
          // Probe receives an AbortSignal and must read protected runtime metadata tied to child.pid.
          const controller = new AbortController();
          const timer = setTimeout(() => controller.abort(), Math.max(1, deadline - Date.now()));
          let response;
          try {
            response = await Promise.race([this.probe({ child, port, nonce: challenge, signal: controller.signal }), exited.then(() => null), new Promise(resolve => controller.signal.addEventListener('abort', () => resolve(null), { once: true }))]);
          } catch { response = null; } finally { clearTimeout(timer); controller.abort(); }
          if (response && !outcome) {
            try {
              if (response.pid !== child.pid) throw new Error('DAC process mismatch');
              const verified = verifyStatus(response.status, { ...environment(config.serverUrl, config.webOrigin), port, publicKey: config.publicKey, instanceId: response.instanceId, nonce: challenge });
              // Retain lock until owned Java exits, including unexpected exits after READY.
              exited.then(() => { if (this.child === child) { this.child = null; this.busy = false; release(); } });
              return verified;
            } catch { /* Unverified responses never establish readiness. */ }
          }
          if (!outcome) await Promise.race([exited, new Promise(resolve => setTimeout(resolve, this.pollMs))]);
        }
        if (outcome && outcome.code === 98) { this.child = null; continue; }
        if (!outcome) {
          // No force kill or lock release while process may still own H2.
          const unlock = release;
          exited.then(() => { this.child = null; this.busy = false; unlock(); });
          release = null;
          try { child.kill('SIGTERM'); } catch { /* Retain ownership until actual exit. */ }
          throw new Error('DAC readiness timed out; owned process stopping');
        }
        this.child = null;
        throw new Error('DAC failed to start');
      }
      throw new Error('DAC candidate ports unavailable');
    } finally {
      if (!this.child) { this.busy = false; if (release) release(); }
    }
  }
  stop() { if (this.child) this.child.kill('SIGTERM'); }
}
module.exports = { candidatePorts, launchSpec, DacSupervisor };
