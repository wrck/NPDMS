'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { LinuxKeyVault } = require('../src/key-store.cjs');
function fixture(t, backend = 'gnome_libsecret') {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-key-test-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const testKey = crypto.randomBytes(32);
  const safeStorage = {
    isEncryptionAvailable: () => true, getSelectedStorageBackend: () => backend,
    encryptString: text => {
      const iv = crypto.randomBytes(12), cipher = crypto.createCipheriv('aes-256-gcm', testKey, iv);
      const encrypted = Buffer.concat([cipher.update(text), cipher.final()]);
      return Buffer.concat([iv, cipher.getAuthTag(), encrypted]);
    },
    decryptString: bytes => {
      const cipher = crypto.createDecipheriv('aes-256-gcm', testKey, bytes.subarray(0, 12));
      cipher.setAuthTag(bytes.subarray(12, 28));
      return Buffer.concat([cipher.update(bytes.subarray(28)), cipher.final()]).toString();
    }
  };
  return { directory, safeStorage, vault: new LinuxKeyVault({ directory, safeStorage, platform: 'linux' }) };
}
test('OS vault refuses basic_text, unavailable service and unsupported platforms', async t => {
  const f = fixture(t, 'basic_text');
  assert.equal(f.vault.isAvailable(), false);
  await assert.rejects(f.vault.read('dac'), /OS_KEY_VAULT_UNAVAILABLE/);
  f.safeStorage.getSelectedStorageBackend = () => 'gnome_libsecret';
  f.safeStorage.isEncryptionAvailable = () => false;
  await assert.rejects(f.vault.createIfAbsent('dac', crypto.randomBytes(32)), /UNAVAILABLE/);
  const other = new LinuxKeyVault({ directory: f.directory, safeStorage: f.safeStorage, platform: 'win32' });
  assert.equal(other.isAvailable(), false);
});
test('OS vault writes only encrypted bytes and cannot replace an existing key', async t => {
  const f = fixture(t), key = crypto.randomBytes(32);
  assert.equal(await f.vault.read('dac'), null);
  await f.vault.createIfAbsent('dac', key);
  const files = fs.readdirSync(f.directory);
  assert.equal(files.length, 1);
  const bytes = fs.readFileSync(path.join(f.directory, files[0]));
  assert.equal(bytes.includes(key), false); assert.equal(bytes.includes(key.toString('base64')), false);
  assert.deepEqual(await f.vault.read('dac'), key);
  await f.vault.createIfAbsent('dac', crypto.randomBytes(32));
  assert.deepEqual(await f.vault.read('dac'), key);
  assert.equal(fs.statSync(path.join(f.directory, files[0])).mode & 0o077, 0);
});
test('corrupt, substituted, public or symlinked key files remain blocked', async t => {
  const f = fixture(t); await f.vault.createIfAbsent('dac', crypto.randomBytes(32));
  const file = path.join(f.directory, fs.readdirSync(f.directory)[0]), saved = fs.readFileSync(file);
  fs.writeFileSync(file, Buffer.from('broken')); await assert.rejects(f.vault.read('dac'), /DECRYPTION/);
  fs.writeFileSync(file, f.safeStorage.encryptString(JSON.stringify({ version: 1, id: 'other', key: crypto.randomBytes(32).toString('base64') })));
  await assert.rejects(f.vault.read('dac'), /DECRYPTION/);
  fs.writeFileSync(file, saved); fs.chmodSync(file, 0o644); await assert.rejects(f.vault.read('dac'), /INVALID_KEY_FILE/);
  fs.unlinkSync(file); fs.symlinkSync('outside', file); await assert.rejects(f.vault.read('dac'), /INVALID_KEY_FILE/);
});
test('key vault rejects shared directories and path-like identifiers', async t => {
  const f = fixture(t);
  await assert.rejects(f.vault.read('../escape'), /INVALID_KEY_ID/);
  fs.chmodSync(f.directory, 0o755); await assert.rejects(f.vault.read('dac'), /PRIVATE_KEY_DIRECTORY/);
});
