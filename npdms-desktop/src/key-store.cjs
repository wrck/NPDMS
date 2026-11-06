'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const ALLOWED_BACKENDS = new Set(['gnome_libsecret', 'kwallet', 'kwallet5', 'kwallet6']);

// Main-process Linux adapter. Electron must be ready before availability is checked.
class LinuxKeyVault {
  constructor({ directory, safeStorage, platform = process.platform }) {
    if (typeof directory !== 'string' || !path.isAbsolute(directory)) throw new Error('PRIVATE_KEY_DIRECTORY_REQUIRED');
    this.directory = path.resolve(directory); this.safeStorage = safeStorage; this.platform = platform;
    this.backend = 'linux-os-key-vault';
  }
  isAvailable() {
    try {
      return this.platform === 'linux' && this.safeStorage.isEncryptionAvailable() === true &&
        ALLOWED_BACKENDS.has(this.safeStorage.getSelectedStorageBackend());
    } catch { return false; }
  }
  #check() {
    if (!this.isAvailable()) throw new Error('OS_KEY_VAULT_UNAVAILABLE');
    fs.mkdirSync(this.directory, { recursive: true, mode: 0o700 });
    const stat = fs.lstatSync(this.directory);
    if (!stat.isDirectory() || stat.isSymbolicLink() || fs.realpathSync(this.directory) !== this.directory ||
        (stat.mode & 0o077) !== 0 || (typeof process.getuid === 'function' && stat.uid !== process.getuid())) {
      throw new Error('PRIVATE_KEY_DIRECTORY_REQUIRED');
    }
  }
  #file(id) {
    if (typeof id !== 'string' || !/^[a-zA-Z0-9._-]{1,128}$/.test(id)) throw new Error('INVALID_KEY_ID');
    return path.join(this.directory, crypto.createHash('sha256').update(id).digest('hex') + '.key');
  }
  async read(id) {
    this.#check(); const file = this.#file(id);
    let stat;
    try { stat = fs.lstatSync(file); } catch (error) { if (error.code === 'ENOENT') return null; throw error; }
    if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 65536 || (stat.mode & 0o077) !== 0 ||
        (typeof process.getuid === 'function' && stat.uid !== process.getuid())) throw new Error('INVALID_KEY_FILE');
    try {
      const clear = JSON.parse(this.safeStorage.decryptString(fs.readFileSync(file)));
      if (clear.version !== 1 || clear.id !== id || typeof clear.key !== 'string') throw new Error();
      const key = Buffer.from(clear.key, 'base64');
      if (key.length !== 32 || key.toString('base64') !== clear.key) { key.fill(0); throw new Error(); }
      return key;
    } catch { throw new Error('KEY_DECRYPTION_FAILED'); }
  }
  async createIfAbsent(id, key) {
    this.#check(); const file = this.#file(id);
    if (!Buffer.isBuffer(key) || key.length !== 32) throw new Error('INVALID_KEY');
    const encrypted = this.safeStorage.encryptString(JSON.stringify({ version: 1, id, key: key.toString('base64') }));
    const temporary = path.join(this.directory, crypto.randomUUID() + '.tmp');
    let descriptor;
    try {
      descriptor = fs.openSync(temporary, 'wx', 0o600);
      fs.writeFileSync(descriptor, encrypted); fs.fsyncSync(descriptor); fs.closeSync(descriptor); descriptor = undefined;
      try { fs.linkSync(temporary, file); }
      catch (error) { if (error.code !== 'EEXIST') throw error; }
      // A hard link publishes only the complete encrypted file, and never replaces a prior key.
      const dir = fs.openSync(this.directory, 'r');
      try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
    } finally {
      if (descriptor !== undefined) fs.closeSync(descriptor);
      try { fs.unlinkSync(temporary); } catch (error) { if (error.code !== 'ENOENT') throw error; }
    }
  }
}
module.exports = { LinuxKeyVault };
