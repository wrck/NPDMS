'use strict';
const fs = require('node:fs');
const fsp = fs.promises;
const path = require('node:path');
const crypto = require('node:crypto');
async function ensurePrivateDirectory(directory) {
  if (
    process.platform !== 'linux' ||
    !path.isAbsolute(directory) ||
    path.resolve(directory) !== directory ||
    directory === '/'
  )
    throw Error('Invalid private DAC directory');

  let current = '/';
  for (const part of directory.slice(1).split('/')) {
    current = path.join(current, part);
    let s;
    try {
      s = await fsp.lstat(current);
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
      try {
        await fsp.mkdir(current, { mode: 0o700 });
      } catch (created) {
        if (created.code !== 'EEXIST') throw created;
      }
      s = await fsp.lstat(current);
    }
    if (
      !s.isDirectory() ||
      s.isSymbolicLink() ||
      ![0, process.getuid()].includes(s.uid)
    )
      throw Error('Untrusted DAC directory');
    if (current === directory) {
      if (s.uid !== process.getuid() || s.mode & 0o077)
        throw Error('DAC directory must be owner private');
    } else if (s.mode & 0o022 && !(s.mode & 0o1000))
      throw Error('Writable DAC directory ancestor');
  }
}
function b64(value) {
  if (typeof value !== 'string' || !value.length)
    throw Error('Invalid DAC identity encoding');
  const b = Buffer.from(value, 'base64');
  if (b.toString('base64') !== value)
    throw Error('Invalid DAC identity encoding');
  return b;
}
function decodeIdentity(text, key) {
  const props = {};
  for (const line of text.split(/\r?\n/)) {
    if (!line || /^[#!]/.test(line)) continue;
    const m = /^(private|nonce|public)=(.*)$/.exec(line);
    if (!m || Object.hasOwn(props, m[1]))
      throw Error('Invalid DAC identity properties');
    props[m[1]] = m[2].replace(/\\=/g, '=');
  }
  if (Object.keys(props).length !== 3) throw Error('Incomplete DAC identity');
  const encrypted = b64(props.private),
    nonce = b64(props.nonce),
    pub = b64(props.public);
  if (nonce.length !== 12 || encrypted.length < 17)
    throw Error('Invalid DAC encrypted identity');
  let plain, privateDer;
  try {
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, nonce);
    decipher.setAuthTag(encrypted.subarray(-16));
    plain = Buffer.concat([
      decipher.update(encrypted.subarray(0, -16)),
      decipher.final(),
    ]);
    privateDer = b64(plain.toString('utf8'));
    const priv = crypto.createPrivateKey({
      key: privateDer,
      format: 'der',
      type: 'pkcs8',
    });
    const publicObject = crypto.createPublicKey({
      key: pub,
      format: 'der',
      type: 'spki',
    });
    if (
      priv.asymmetricKeyType !== 'ed25519' ||
      publicObject.asymmetricKeyType !== 'ed25519'
    )
      throw Error('Invalid DAC identity algorithm');
    const derived = crypto
      .createPublicKey(priv)
      .export({ format: 'der', type: 'spki' });
    if (!derived.equals(pub)) throw Error('DAC identity key mismatch');
    const publicKey = derived.subarray(-32).toString('hex');
    return Object.freeze({
      publicKey,
      pairingCode: crypto
        .createHash('sha256')
        .update(derived.subarray(-32))
        .digest('hex')
        .slice(0, 32)
        .toUpperCase(),
    });
  } finally {
    plain?.fill(0);
    privateDer?.fill(0);
  }
}
async function readIdentity(file, key) {
  const handle = await fsp.open(
    file,
    fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK,
  );
  try {
    const s = await handle.stat();
    if (
      !s.isFile() ||
      s.uid !== process.getuid() ||
      (s.mode & 0o777) !== 0o600 ||
      s.nlink !== 1 ||
      s.size < 1 ||
      s.size > 4096
    )
      throw Error('Untrusted DAC identity file');
    const bytes = Buffer.alloc(4097);
    let n = 0;
    while (n < bytes.length) {
      const r = await handle.read(bytes, n, bytes.length - n, n);
      if (!r.bytesRead) break;
      n += r.bytesRead;
    }
    const after = await handle.stat();
    if (
      n !== s.size ||
      after.size !== s.size ||
      after.mtimeMs !== s.mtimeMs ||
      after.ctimeMs !== s.ctimeMs
    )
      throw Error('DAC identity changed');
    return decodeIdentity(bytes.subarray(0, n).toString('utf8'), key);
  } finally {
    await handle.close();
  }
}
async function provisionIdentity({ directory, masterKey, create = false }) {
  if (!Buffer.isBuffer(masterKey) || masterKey.length !== 32)
    throw Error('DAC master key required');
  await ensurePrivateDirectory(directory);
  const file = path.join(directory, 'desktop-identity.properties');
  try {
    return await readIdentity(file, masterKey);
  } catch (e) {
    if (e.code !== 'ENOENT' || !create) throw e;
  }
  const pair = crypto.generateKeyPairSync('ed25519');
  const privateDer = pair.privateKey.export({ format: 'der', type: 'pkcs8' });
  let plain;
  let temp;
  try {
    plain = Buffer.from(privateDer.toString('base64'), 'utf8');
    const nonce = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', masterKey, nonce);
    const encrypted = Buffer.concat([
      cipher.update(plain),
      cipher.final(),
      cipher.getAuthTag(),
    ]);
    const text = `# DAC encrypted installation identity\nprivate=${encrypted.toString('base64')}\nnonce=${nonce.toString('base64')}\npublic=${pair.publicKey.export({ format: 'der', type: 'spki' }).toString('base64')}\n`;
    temp = path.join(
      directory,
      `.identity-${crypto.randomBytes(16).toString('hex')}.tmp`,
    );
    const h = await fsp.open(temp, 'wx', 0o600);
    try {
      await h.writeFile(text, 'utf8');
      await h.sync();
    } finally {
      await h.close();
    }
    try {
      await fsp.link(temp, file);
    } catch (e) {
      if (e.code !== 'EEXIST') throw e;
    }
    await fsp.unlink(temp);
    temp = null;
    const dir = await fsp.open(directory, 'r');
    try {
      await dir.sync();
    } finally {
      await dir.close();
    }
    return await readIdentity(file, masterKey);
  } finally {
    privateDer.fill(0);
    plain?.fill(0);
    if (temp) await fsp.unlink(temp).catch(() => {});
  }
}
module.exports = { provisionIdentity, ensurePrivateDirectory };
