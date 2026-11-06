'use strict';
// Production read-only adapter for baseline d4f6918d67c3f5b32559d7eba6deb6e9a847858a.
// Live Linux DAC integration remains untested. Supervisor must still verify the
// signature with its pinned installation key and this protected instance ID.
// Caller provisions an owner-private runtime directory; no chmod or writes occur.
const fs = require('node:fs');
const fsp = fs.promises;
const path = require('node:path');
const http = require('node:http');
const { TextDecoder } = require('node:util');
const { validPort, normalizeUrl } = require('./protocol.cjs');
const HEX = /^[a-f0-9]{64}$/;
const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const abortError = () => Object.assign(new Error('DAC probe aborted'), { name: 'AbortError' });
function checkAbort(signal) { if (signal?.aborted) throw abortError(); }
function parseMetadata(text) {
  const match = /^port=(828[0-9])\npid=([1-9][0-9]*)\npublicKey=([a-f0-9]{64})\ninstanceId=([a-f0-9-]{36})\n$/.exec(text);
  if (!match || match[0] !== text || !UUID.test(match[4])) throw new Error('Invalid DAC runtime metadata');
  const pid = Number(match[2]);
  if (!Number.isSafeInteger(pid)) throw new Error('Invalid DAC runtime PID');
  return { port: Number(match[1]), pid, publicKey: match[3], instanceId: match[4] };
}
async function readMetadata(runtimeFile, protectedDirectory, ownerUid, signal) {
  checkAbort(signal);
  // Walk every ancestor without following symlinks. A sticky system temp directory
  // is permitted, but the configured protected directory itself must be private.
  const root = path.parse(protectedDirectory).root;
  let current = root;
  for (const part of protectedDirectory.slice(root.length).split(path.sep).filter(Boolean)) {
    current = path.join(current, part);
    const st = await fsp.lstat(current);
    if (!st.isDirectory() || st.isSymbolicLink() || ![0, ownerUid].includes(st.uid)) throw new Error('Untrusted DAC runtime directory');
    if (current === protectedDirectory) {
      if (st.uid !== ownerUid || (st.mode & 0o077)) throw new Error('DAC runtime directory must be private');
    } else if ((st.mode & 0o022) && !(st.mode & 0o1000)) throw new Error('Writable DAC runtime ancestor');
  }
  checkAbort(signal);
  if (!Number.isInteger(fs.constants.O_NOFOLLOW)) throw new Error('No-follow runtime reads unavailable');
  const handle = await fsp.open(runtimeFile, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
  try {
    const stat = await handle.stat();
    if (!stat.isFile() || stat.uid !== ownerUid || (stat.mode & 0o022) || stat.nlink !== 1 || stat.size > 4096 || stat.size < 1) throw new Error('Untrusted DAC runtime file');
    const bytes = Buffer.alloc(4097);
    let used = 0;
    while (used < bytes.length) {
      checkAbort(signal);
      const { bytesRead } = await handle.read(bytes, used, bytes.length - used, used);
      if (!bytesRead) break;
      used += bytesRead;
    }
    if (used > 4096) throw new Error('DAC runtime metadata too large');
    const after = await handle.stat();
    if (after.size !== stat.size || after.mtimeMs !== stat.mtimeMs || after.ctimeMs !== stat.ctimeMs || used !== stat.size) throw new Error('DAC runtime metadata changed');
    return parseMetadata(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, used)));
  } finally { await handle.close(); }
}
function validateStatus(status, metadata) {
  const fields = ['product','protocolVersion','status','serverUrl','webOrigin','port','publicKey','instanceId','signature'];
  if (!status || typeof status !== 'object' || Array.isArray(status) || Object.keys(status).length !== fields.length || !fields.every(f => Object.hasOwn(status,f))) throw new Error('Invalid DAC status structure');
  if (status.product !== 'sdp-dac' || status.protocolVersion !== 3 || !['READY','STARTING'].includes(status.status) || status.port !== metadata.port || status.publicKey !== metadata.publicKey || status.instanceId !== metadata.instanceId) throw new Error('DAC status metadata mismatch');
  if (typeof status.signature !== 'string' || status.signature.length !== 88 || Buffer.from(status.signature,'base64').length !== 64 || Buffer.from(status.signature,'base64').toString('base64') !== status.signature) throw new Error('Invalid DAC status signature format');
  for (const [name,originOnly] of [['serverUrl',false],['webOrigin',true]]) if (typeof status[name] !== 'string' || status[name].length > 2048 || normalizeUrl(status[name],originOnly) !== status[name]) throw new Error('Invalid DAC status environment');
  return status;
}
function requestStatus(request, port, nonce, signal, timeoutMs, maxBytes) {
  return new Promise((resolve,reject) => {
    let req, response, timer, settled = false;
    const finish = (error,value) => {
      if (settled) return; settled=true; clearTimeout(timer); signal?.removeEventListener('abort',abort);
      if (error) { response?.destroy(); req?.destroy(); reject(error); } else resolve(value);
    };
    const abort = () => finish(abortError());
    if (signal?.aborted) { abort(); return; }
    signal?.addEventListener('abort',abort,{once:true});
    timer=setTimeout(()=>finish(new Error('DAC status request timed out')),timeoutMs);
    try {
      req=request({protocol:'http:',hostname:'127.0.0.1',port,method:'GET',path:`/api/v1/desktop/status?nonce=${nonce}`,agent:false,headers:{Accept:'application/json','Accept-Encoding':'identity'}}, res=>{
        response=res;
        if (settled) { res.destroy(); return; }
        res.on('error',()=>finish(new Error('DAC status response failed')));
        res.on('aborted',()=>finish(new Error('DAC status response aborted')));
        if (res.statusCode !== 200 || !/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(res.headers['content-type'] || '') || (res.headers['content-encoding'] && res.headers['content-encoding'] !== 'identity')) { finish(new Error('Unexpected DAC status response')); return; }
        const length=res.headers['content-length'];
        if (length !== undefined && (!/^(0|[1-9][0-9]*)$/.test(String(length)) || Number(length)>maxBytes)) { finish(new Error('DAC status response too large')); return; }
        const chunks=[];let size=0;
        res.on('data',chunk=>{if(settled)return;size+=chunk.length;if(size>maxBytes){finish(new Error('DAC status response too large'));return;}chunks.push(Buffer.from(chunk));});
        res.on('end',()=>{
          if(settled)return;
          try { if(length !== undefined && Number(length)!==size)throw new Error();finish(null,JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(chunks)))); }
          catch {finish(new Error('Invalid DAC status JSON'));}
        });
        res.on('close',()=>{if(!settled)finish(new Error('DAC status response closed'));});
      });
      req.on('error',()=>finish(new Error('DAC status request failed')));
      if(settled)req.destroy();else req.end();
    } catch { finish(new Error('DAC status request failed')); }
  });
}
function createReadinessProbe({runtimeFile,protectedDirectory,ownerUid=process.getuid?.(),request=http.request,timeoutMs=2000,maxResponseBytes=8192}) {
  if (!Number.isInteger(ownerUid) || !path.isAbsolute(runtimeFile || '') || !path.isAbsolute(protectedDirectory || '') || path.resolve(runtimeFile)!==runtimeFile || path.resolve(protectedDirectory)!==protectedDirectory || path.dirname(runtimeFile)!==protectedDirectory || protectedDirectory===path.parse(protectedDirectory).root || typeof request!=='function' || !Number.isInteger(timeoutMs) || timeoutMs<1 || timeoutMs>30000 || !Number.isInteger(maxResponseBytes) || maxResponseBytes<1 || maxResponseBytes>65536) throw new Error('Invalid DAC readiness probe configuration');
  return async ({child,port,nonce,signal})=>{
    checkAbort(signal);
    if (!Number.isSafeInteger(child?.pid) || child.pid<1 || !validPort(port) || typeof nonce!=='string' || nonce.length!==64 || !HEX.test(nonce)) throw new Error('Invalid DAC readiness probe input');
    const metadata=await readMetadata(runtimeFile,protectedDirectory,ownerUid,signal);
    if(metadata.pid!==child.pid || metadata.port!==port)throw new Error('Stale DAC runtime metadata');
    checkAbort(signal);
    const status=validateStatus(await requestStatus(request,port,nonce,signal,timeoutMs,maxResponseBytes),metadata);
    checkAbort(signal);
    return {pid:metadata.pid,instanceId:metadata.instanceId,status};
  };
}
module.exports={createReadinessProbe,parseMetadata};
