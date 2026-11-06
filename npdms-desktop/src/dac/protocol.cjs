'use strict';
const crypto = require('node:crypto');
function normalizeUrl(value, originOnly = false) {
  if (typeof value !== 'string' || value !== value.trim() || /[\s\\?#]/u.test(value) || !/^https?:\/\//.test(value) || value.split('/')[2].includes('@')) throw new Error('Invalid DAC environment URL');
  let u; try { u = new URL(value); } catch { throw new Error('Invalid DAC environment URL'); }
  if (!/^https?:$/.test(u.protocol) || !u.hostname || u.username || u.password || u.search || u.hash || u.port === '0' || (originOnly && u.pathname !== '/')) throw new Error('Invalid DAC environment URL');
  // Preserve exact configured spelling: Java only removes trailing slashes.
  return value.replace(/\/+$/, '');
}
function environment(serverUrl, webOrigin) { return Object.freeze({ serverUrl: normalizeUrl(serverUrl), webOrigin: normalizeUrl(webOrigin, true) }); }
function parseConnectUri(value) {
  const m = typeof value === 'string' && /^sdp-dac:\/\/connect\/([a-f0-9]{32})\?challenge=([a-f0-9]{64})&tenant=([1-9][0-9]{0,17})$/.exec(value);
  if (!m || m[0] !== value) throw new Error('Invalid DAC connect URI');
  return Object.freeze({ connectionId: m[1], nonce: m[2], tenantId: m[3] });
}
function nonce() { return crypto.randomBytes(32).toString('hex'); }
function validPort(port) { return Number.isInteger(port) && port >= 8280 && port <= 8289; }
function verifyStatus(status, expected) {
  if (!status || !expected || !(typeof expected.nonce === 'string' && expected.nonce.length === 64 && /^[a-f0-9]{64}$/.test(expected.nonce)) || !(typeof expected.publicKey === 'string' && expected.publicKey.length === 64 && /^[a-f0-9]{64}$/.test(expected.publicKey)) || typeof expected.instanceId !== 'string' || !expected.instanceId || /[\r\n]/.test(expected.instanceId) || !validPort(expected.port)) throw new Error('Invalid DAC verification expectations');
  environment(expected.serverUrl, expected.webOrigin);
  if (status.product !== 'sdp-dac' || status.protocolVersion !== 3 || status.status !== 'READY' || status.port !== expected.port || status.publicKey !== expected.publicKey || status.instanceId !== expected.instanceId || status.serverUrl !== expected.serverUrl || status.webOrigin !== expected.webOrigin || typeof status.signature !== 'string') throw new Error('DAC identity or environment mismatch');
  const signature = Buffer.from(status.signature, 'base64');
  if (signature.length !== 64 || signature.toString('base64') !== status.signature) throw new Error('Invalid DAC signature');
  const key = crypto.createPublicKey({ key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), Buffer.from(expected.publicKey, 'hex')]), format: 'der', type: 'spki' });
  const message = ['sdp-dac-discovery-v1', expected.nonce, status.port, status.instanceId, '3', status.status, status.serverUrl, status.webOrigin].join('\n');
  if (!crypto.verify(null, Buffer.from(message, 'utf8'), key, signature)) throw new Error('Invalid DAC signature');
  return Object.freeze({ ...status });
}
module.exports = { environment, normalizeUrl, parseConnectUri, nonce, validPort, verifyStatus };
