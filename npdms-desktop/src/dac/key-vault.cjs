'use strict';
const crypto = require('node:crypto');
// Adapter must provide atomic create-if-absent, backed by an OS vault. No filesystem fallback.
async function loadMasterKey(vault, installationId, { create = false } = {}) {
  if (!vault || vault.backend !== 'linux-os-key-vault' || typeof vault.isAvailable !== 'function' || !await vault.isAvailable() || typeof vault.read !== 'function') throw new Error('Linux OS key vault unavailable; DAC cannot start');
  if (typeof installationId !== 'string' || !/^[a-zA-Z0-9._-]{1,128}$/.test(installationId) || /[\r\n]/.test(installationId)) throw new Error('Invalid DAC installation identifier');
  let key = await vault.read(installationId);
  if (key == null && create) {
    if (typeof vault.createIfAbsent !== 'function') throw new Error('Vault lacks atomic key creation');
    const generated = crypto.randomBytes(32);
    try { await vault.createIfAbsent(installationId, generated); } finally { generated.fill(0); }
    key = await vault.read(installationId);
  }
  if (!Buffer.isBuffer(key) || key.length !== 32) throw new Error('DAC master key missing or invalid; recovery required');
  return Buffer.from(key);
}
module.exports = { loadMasterKey };
