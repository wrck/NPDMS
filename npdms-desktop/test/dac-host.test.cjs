'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const cp = require('node:child_process');
const { once } = require('node:events');
const { provisionIdentity } = require('../src/dac/identity.cjs');
const { createLinuxLockHost } = require('../src/dac/linux-lock.cjs');
async function directory(t) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'dac-host-'));
  await fs.chmod(dir, 0o700);
  t.after(() => fs.rm(dir, { recursive: true, force: true }));
  return dir;
}
test('identity create/read preserves bytes and decrypts Java-compatible AESGCM PKCS8', async (t) => {
  const dir = await directory(t),
    key = crypto.randomBytes(32);
  const identity = await provisionIdentity({
    directory: dir,
    masterKey: key,
    create: true,
  });
  const file = path.join(dir, 'desktop-identity.properties'),
    before = await fs.readFile(file, 'utf8');
  assert.equal((await fs.stat(file)).mode & 0o777, 0o600);
  assert.deepEqual(
    await provisionIdentity({ directory: dir, masterKey: key, create: true }),
    identity,
  );
  assert.equal(await fs.readFile(file, 'utf8'), before);
  const props = Object.fromEntries(
    before
      .split('\n')
      .filter((l) => l && !l.startsWith('#'))
      .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
  );
  const enc = Buffer.from(props.private, 'base64'),
    dec = crypto.createDecipheriv(
      'aes-256-gcm',
      key,
      Buffer.from(props.nonce, 'base64'),
    );
  dec.setAuthTag(enc.subarray(-16));
  const plain = Buffer.concat([dec.update(enc.subarray(0, -16)), dec.final()]);
  const priv = crypto.createPrivateKey({
    key: Buffer.from(plain.toString(), 'base64'),
    format: 'der',
    type: 'pkcs8',
  });
  assert.equal(
    crypto
      .createPublicKey(priv)
      .export({ format: 'der', type: 'spki' })
      .subarray(-32)
      .toString('hex'),
    identity.publicKey,
  );
  // Java Properties.store escapes padding equals signs and writes CRLF on Windows.
  await fs.writeFile(
    file,
    before
      .split('\n')
      .map((l) =>
        l.startsWith('#')
          ? l
          : l.replace(/=/g, (m, i) => (i === l.indexOf('=') ? '=' : '\\=')),
      )
      .join('\r\n'),
  );
  assert.deepEqual(
    await provisionIdentity({ directory: dir, masterKey: key }),
    identity,
  );
});
test('identity rejects wrong key, corruption, mismatched public key and missing identity without opt-in', async (t) => {
  const dir = await directory(t),
    key = crypto.randomBytes(32);
  await assert.rejects(provisionIdentity({ directory: dir, masterKey: key }));
  await provisionIdentity({ directory: dir, masterKey: key, create: true });
  const file = path.join(dir, 'desktop-identity.properties'),
    text = await fs.readFile(file, 'utf8');
  await assert.rejects(
    provisionIdentity({
      directory: dir,
      masterKey: crypto.randomBytes(32),
      create: true,
    }),
  );
  assert.equal(await fs.readFile(file, 'utf8'), text);
  const other = crypto
    .generateKeyPairSync('ed25519')
    .publicKey.export({ format: 'der', type: 'spki' })
    .toString('base64');
  await fs.writeFile(file, text.replace(/^public=.*$/m, 'public=' + other));
  await assert.rejects(
    provisionIdentity({ directory: dir, masterKey: key, create: true }),
    /mismatch/,
  );
  await fs.writeFile(file, 'corrupt');
  await assert.rejects(
    provisionIdentity({ directory: dir, masterKey: key, create: true }),
  );
  assert.equal(await fs.readFile(file, 'utf8'), 'corrupt');
});
test('concurrent identity provisioning converges without overwrite', async (t) => {
  const dir = await directory(t),
    key = crypto.randomBytes(32);
  const results = await Promise.all(
    Array.from({ length: 8 }, () =>
      provisionIdentity({ directory: dir, masterKey: key, create: true }),
    ),
  );
  assert.equal(new Set(results.map((x) => x.publicKey)).size, 1);
});
test('flock is cross-process exclusive and inherited child retains lock after parent FD release', async (t) => {
  const dir = await directory(t),
    first = createLinuxLockHost(),
    second = createLinuxLockHost();
  const release = await first.acquireLock(dir);
  t.after(() => release());
  await assert.rejects(second.acquireLock(dir), /occupied/);
  const child = first.spawn(
    process.execPath,
    ['-e', 'setTimeout(()=>{},30000)'],
    { cwd: dir, shell: false, stdio: ['ignore', 'ignore', 'ignore'] },
  );
  t.after(() => child.kill());
  await once(child, 'spawn');
  release();
  await assert.rejects(second.acquireLock(dir), /occupied/);
  const exit = once(child, 'exit');
  child.kill();
  await exit;
  const unlock = await second.acquireLock(dir);
  unlock();
  assert.ok(await fs.stat(path.join(dir, 'service.lock')));
});
test('owned child retains inherited flock across actual parent SIGKILL', async (t) => {
  const dir = await directory(t),
    modulePath = require.resolve('../src/dac/linux-lock.cjs');
  const script = `const {createLinuxLockHost}=require(${JSON.stringify(modulePath)});(async()=>{const host=createLinuxLockHost();await host.acquireLock(${JSON.stringify(dir)});const c=host.spawn(process.execPath,['-e','setTimeout(()=>{},30000)'],{cwd:${JSON.stringify(dir)},shell:false,stdio:['ignore','ignore','ignore']});c.once('spawn',()=>process.send({pid:c.pid}));setTimeout(()=>{},30000);})();`;
  const parent = cp.spawn(process.execPath, ['-e', script], {
    stdio: ['ignore', 'ignore', 'ignore', 'ipc'],
  });
  let childPid;
  t.after(() => {
    try {
      parent.kill('SIGKILL');
    } catch {}
    if (childPid)
      try {
        process.kill(childPid, 'SIGKILL');
      } catch {}
  });
  const [message] = await once(parent, 'message');
  childPid = message.pid;
  const exit = once(parent, 'exit');
  parent.kill('SIGKILL');
  await exit;
  const contender = createLinuxLockHost();
  await assert.rejects(contender.acquireLock(dir), /occupied/);
  process.kill(childPid, 'SIGTERM');
  let unlock;
  for (let i = 0; i < 100; i++) {
    try {
      unlock = await contender.acquireLock(dir);
      break;
    } catch {
      await new Promise((r) => setTimeout(r, 10));
    }
  }
  assert.equal(typeof unlock, 'function');
  unlock();
  childPid = null;
});
test('identity and lifetime lock reject symlink and insecure existing files without repair', async (t) => {
  const dir = await directory(t),
    key = crypto.randomBytes(32),
    outside = path.join(dir, 'other');
  await fs.writeFile(outside, 'unchanged', { mode: 0o600 });
  const identity = path.join(dir, 'desktop-identity.properties');
  await fs.symlink(outside, identity);
  await assert.rejects(
    provisionIdentity({ directory: dir, masterKey: key, create: true }),
  );
  assert.equal(await fs.readFile(outside, 'utf8'), 'unchanged');
  await fs.unlink(identity);
  await provisionIdentity({ directory: dir, masterKey: key, create: true });
  await fs.chmod(identity, 0o644);
  await assert.rejects(
    provisionIdentity({ directory: dir, masterKey: key }),
    /Untrusted/,
  );
  assert.equal((await fs.stat(identity)).mode & 0o777, 0o644);
  await fs.symlink(outside, path.join(dir, 'service.lock'));
  await assert.rejects(createLinuxLockHost().acquireLock(dir));
  assert.equal(await fs.readFile(outside, 'utf8'), 'unchanged');
});
