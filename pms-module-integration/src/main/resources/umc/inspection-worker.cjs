'use strict';

// Protocol adapter derived from the supplied umc-inspection-api.js. No business state is written.
const https = require('node:https');
const tls = require('node:tls');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const net = require('node:net');

const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const idKeys = new Set(['id', 'taskid', 'reportid', 'deviceid', 'ruleid', 'targetid', 'tenantId']);
function parse(text) {
  return JSON.parse(text, (key, value, context) => {
    if (!idKeys.has(key) || typeof value !== 'number') return value;
    if (!context || !context.source) throw new Error('NODE_24_REQUIRED');
    return context.source;
  });
}
function fileIp(name) {
  if (typeof name !== 'string' || name.length > 255 || /[\\/:*?"<>|\x00-\x1f]/.test(name)) throw new Error('INVALID_FILENAME');
  const m = /^([^()（）]*?)[(（]([^()（）]+)[)）]$/.exec(name);
  const ip = m ? m[2] : name;
  if (net.isIP(ip) !== 4) throw new Error('INVALID_FILENAME');
  return ip;
}
function validateWordXml(bytes) {
  const prefix = bytes.subarray(0, 8192).toString('utf8');
  if (!prefix.trimStart().startsWith('<?xml') ||
      !/http:\/\/schemas\.microsoft\.com\/office\/word\/2003\/wordml/.test(prefix) ||
      /<!DOCTYPE|<!ENTITY/i.test(bytes.toString('utf8'))) throw new Error('INVALID_DOC');
}

async function run(input, config, emit = () => {}) {
  if (Number(process.versions.node.split('.')[0]) < 24) throw new Error('NODE_24_REQUIRED');
  crypto.createHash('sm3');
  const base = new URL(config.baseUrl);
  if (base.protocol !== 'https:' || base.username || base.password || base.pathname !== '/' ||
      base.search || base.hash) throw new Error('INVALID_CONFIGURATION');
  if (!config.username || !config.password || !Number.isInteger(config.timeoutSeconds) ||
      config.timeoutSeconds < 1 || config.timeoutSeconds > 1800) throw new Error('INVALID_CONFIGURATION');
  if (!input.scope || !input.requestId || !Array.isArray(input.files) || !input.files.length ||
      input.files.length > 64) throw new Error('INVALID_INPUT');
  const deadline = Date.now() + config.timeoutSeconds * 1000;
  const jars = new Map();
  let checkpoint;
  const output = input.outputDirectory;
  let total = 0;
  const files = input.files.map(f => {
    const ip = fileIp(f.name);
    const content = fs.readFileSync(f.path);
    total += content.length;
    if (!content.length || total > 50 * 1024 * 1024) throw new Error('INPUT_SIZE_LIMIT');
    return { name: f.name, ip, content, sha256: sha(content) };
  });
  if (new Set(files.map(f => f.ip)).size !== files.length) throw new Error('DUPLICATE_DEVICE_IP');
  const scope = { tenantId: String(input.scope.tenantId), taskKey: input.scope.taskKey, attemptKey: input.scope.attemptKey };
  const digest = sha(JSON.stringify({ scope, requestId: input.requestId,
    files: files.map(f => ({ name: f.name, sha256: f.sha256 })) }));
  const name = 'NPDMS_' + sha(JSON.stringify([scope, input.requestId])).slice(0, 28);
  const warnings = [];
  checkpoint = input.checkpoint || { inputDigest: digest, taskId: null, reportId: null, uploadState: 'NOT_SENT' };
  if (checkpoint.inputDigest !== digest || !['NOT_SENT', 'UNKNOWN', 'ACCEPTED'].includes(checkpoint.uploadState))
    throw new Error('CHECKPOINT_MISMATCH');
  if (input.queryOnly) checkpoint = { ...checkpoint, uploadState: 'UNKNOWN' };
  const publish = update => { checkpoint = { ...checkpoint, ...update }; emit({ kind: 'checkpoint', checkpoint }); };
  const remaining = () => {
    const ms = deadline - Date.now();
    if (ms <= 0) throw new Error(checkpoint.uploadState === 'UNKNOWN' ? 'UPLOAD_OUTCOME_UNKNOWN' : 'TIMEOUT');
    return ms;
  };
  const sleep = async () => new Promise(resolve => setTimeout(resolve, Math.min(1000, remaining())));
  const request = async (route, init = {}) => {
    const budget = init.cleanup ? 3000 : Math.min(15000, remaining());
    const body = init.body == null ? null : Buffer.from(init.body);
    const headers = { accept: '*/*', apiversion: '2', ...init.headers };
    if (jars.size) headers.cookie = [...jars].map(([k, v]) => `${k}=${v}`).join('; ');
    if (body) headers['content-length'] = body.length;
    const pin = config.certificateSha256?.replace(/:/g, '').toUpperCase();
    return await new Promise((resolve, reject) => {
      const q = https.request(new URL(route, base), {
        method: init.method || 'GET', headers, timeout: budget,
        checkServerIdentity: (host, cert) => {
          if (!pin) return tls.checkServerIdentity(host, cert);
          if (cert.fingerprint256?.replace(/:/g, '').toUpperCase() !== pin) return new Error('TLS_PIN_MISMATCH');
        },
      }, r => {
        for (const c of r.headers['set-cookie'] || []) {
          const kv = c.split(';')[0], i = kv.indexOf('=');
          if (i > 0) jars.set(kv.slice(0, i), kv.slice(i + 1));
        }
        const chunks = []; let bytes = 0;
        r.on('data', b => {
          bytes += b.length;
          if (bytes > 50 * 1024 * 1024) r.destroy(new Error('RESPONSE_SIZE_LIMIT'));
          else chunks.push(b);
        });
        r.on('error', () => { clearTimeout(timer); reject(new Error('RESPONSE_READ_FAILED')); });
        r.on('end', () => { clearTimeout(timer); resolve({ status: r.statusCode, body: Buffer.concat(chunks), headers: r.headers }); });
      });
      const timer = setTimeout(() => q.destroy(new Error('REQUEST_TIMEOUT')), budget);
      q.on('timeout', () => q.destroy(new Error('REQUEST_TIMEOUT')));
      q.on('error', () => { clearTimeout(timer); reject(new Error('REQUEST_FAILED')); });
      if (body) q.write(body);
      q.end();
    });
  };
  const json = async route => {
    const r = await request(route);
    if (r.status !== 200) throw new Error('QUERY_FAILED');
    let data;
    try { data = parse(r.body.toString('utf8')); } catch { throw new Error('INVALID_JSON'); }
    if (data.code != null && String(data.code) !== '200') throw new Error('QUERY_REJECTED');
    return data.code != null ? data.data : data;
  };
  const post = async (route, body, headers = {}) => {
    const r = await request(route, { method: 'POST', body, headers });
    if (r.status !== 200) throw new Error('SUBMISSION_REJECTED');
    const text = r.body.toString('utf8').trim();
    if (text) {
      let data;
      try { data = parse(text); } catch { throw new Error('UNEXPECTED_SUBMISSION_RESPONSE'); }
      if (String(data.code) !== '200') throw new Error('SUBMISSION_REJECTED');
    }
  };
  const all = async route => {
    const rows = [];
    for (let page = 1; ; page++) {
      const data = await json(`${route}?pagesize=200&current=${page}`);
      if (!Array.isArray(data.list) || !Number.isInteger(data.total) || data.total < 0) throw new Error('INVALID_PAGE');
      rows.push(...data.list);
      if (rows.length >= data.total) return rows;
      if (!data.list.length || page >= 500) throw new Error('INCOMPLETE_PAGE_SCAN');
    }
  };
  const login = async () => {
    await request('/UMC/Login!start.action?c=-1486678149');
    const challenge = await request('/UMC/code.jsp');
    let code;
    try { code = JSON.parse(challenge.body.toString()).c; } catch { throw new Error('LOGIN_PROTOCOL_CHANGED'); }
    const page = (await request(`/UMC/Login!start.action?c=${encodeURIComponent(code)}&errmsg=`)).body.toString();
    const c = page.match(/getElementById\("c"\)\.value="(-?\d+)"/)?.[1];
    const cn = page.match(/\bcn="(-?\d+)"/)?.[1], cp = page.match(/\bcp="(-?\d+)"/)?.[1];
    if (!c || !cn || !cp) throw new Error('LOGIN_PROTOCOL_CHANGED');
    const ejs = await request('/UMC/js/e.js');
    if (ejs.status !== 200 || ejs.body.length > 128 * 1024) throw new Error('LOGIN_SCRIPT_INVALID');
    const context = vm.createContext(Object.create(null), { codeGeneration: { strings: false, wasm: false } });
    vm.runInContext(ejs.body.toString(), context, { timeout: 1500 });
    Object.assign(context, { u: config.username, p: config.password, c, cn, cp });
    const encrypted = vm.runInContext('({q:strEnc(u,cn,cp,c),w:strEnc(p,cp,cn,c)})', context, { timeout: 1500 });
    context.p = null;
    const body = new URLSearchParams({ c, ...encrypted, p: '', e: '',
      smvalidate: crypto.createHash('sm3').update(config.username + config.password + cp + cn).digest('hex') });
    loggedIn = true;
    await request('/UMC/Login.action', { method: 'POST', body: body.toString(), headers: {
      'content-type': 'application/x-www-form-urlencoded', origin: base.origin,
      referer: `${base.origin}/UMC/Login!start.action?c=${code}`,
    } });
    try { await json('/umc-api/dev/inspection/tasklist?pagesize=1&current=1'); }
    catch { throw new Error('LOGIN_FAILED'); }
  };
  let loggedIn = false;
  try {
    await login(); loggedIn = true;
    const findTask = async () => {
      const hits = (await all('/umc-api/dev/inspection/tasklist')).filter(t => t.name === name);
      if (hits.length > 1) throw new Error('AMBIGUOUS_TASK');
      if (hits[0] && (hits[0].descr !== 'NPDMS_' + digest || hits[0].type !== 1)) throw new Error('TASK_INPUT_CONFLICT');
      return hits[0];
    };
    let task = await findTask();
    if (!task && input.queryOnly) throw new Error('RECOVERY_TASK_NOT_FOUND');
    if (!task && checkpoint.taskId) throw new Error('CHECKPOINT_TASK_MISSING');
    if (!task) {
      try {
        await post('/umc-api/dev/inspection/taskadd', JSON.stringify({
          type: 1, edit: false, id: null, name, descr: 'NPDMS_' + digest, enabled: true, custom: '',
          lxr: '', lxrPhone: '', xjr: '', xjrPhone: '', whr: '', whrPhone: '', xmjl: '', xmjlPhone: '', fwjl: '', fwjlPhone: '',
          dev: 1, deviceid: null, devtype: null, devmodel: null, deviceids: [], exdeviceids: [],
          rule: 1, rulelevel: 1, ruletypes: [], ruleids: [], time: 1, timedate: null, timeday: 1, timehour: 14400,
          interval: 1, doc: true, pdf: false, send: false, recv: '',
        }), { 'content-type': 'application/json;charset=UTF-8' });
        while (!(task = await findTask())) await sleep();
      } catch { throw new Error('TASK_CREATION_OUTCOME_UNKNOWN'); }
    }
    if (checkpoint.taskId && checkpoint.taskId !== task.id) throw new Error('CHECKPOINT_TASK_MISMATCH');
    publish({ taskId: task.id });
    const reports = async () => {
      const hits = (await all('/umc-api/dev/inspection/reportlist')).filter(r => r.taskid === task.id);
      if (hits.length > 1) throw new Error('AMBIGUOUS_REPORT');
      return hits[0];
    };
    let report = await reports();
    if (!report && checkpoint.uploadState === 'NOT_SENT') {
      const boundary = '----npdms' + crypto.randomBytes(12).toString('hex');
      const parts = [];
      for (const f of files) {
        if (/["\r\n]/.test(f.name)) throw new Error('INVALID_FILENAME');
        parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="${f.name}"\r\nContent-Type: application/octet-stream\r\n\r\n`), f.content, Buffer.from('\r\n'));
      }
      parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="id"\r\n\r\n${task.id}\r\n--${boundary}--\r\n`));
      publish({ uploadState: 'UNKNOWN' });
      await post('/umc-api/dev/inspection/offlinetaskstart', Buffer.concat(parts), { 'content-type': 'multipart/form-data; boundary=' + boundary });
      publish({ uploadState: 'ACCEPTED' });
    }
    while (!report || report.doc !== true) { await sleep(); report = await reports(); }
    if (checkpoint.reportId && checkpoint.reportId !== report.id) throw new Error('CHECKPOINT_REPORT_MISMATCH');
    publish({ reportId: report.id, uploadState: 'ACCEPTED' });
    const detailResponse = await request('/umc-api/dev/inspection/report?id=' + encodeURIComponent(report.id));
    if (detailResponse.status !== 200) throw new Error('DETAIL_FAILED');
    const detail = parse(detailResponse.body.toString());
    if (detail.id !== report.id || detail.taskid !== task.id || !Array.isArray(detail.devlist)) throw new Error('DETAIL_IDENTITY_MISMATCH');
    const expectedIps = new Set(files.map(f => f.ip));
    if (detail.devlist.length !== expectedIps.size || detail.devlist.some(d => !expectedIps.delete(d.deviceip) || !Array.isArray(d.rulelist)))
      throw new Error('DETAIL_DEVICE_MISMATCH');
    const downloaded = await request('/umc-api/dev/inspection/report_download', { method: 'POST',
      headers: { 'content-type': 'application/json;charset=UTF-8' }, body: JSON.stringify({ id: report.id, type: 'doc' }) });
    if (downloaded.status !== 200) throw new Error('DOC_DOWNLOAD_FAILED');
    validateWordXml(downloaded.body);
    fs.writeFileSync(path.join(output, 'report.doc'), downloaded.body, { flag: 'wx' });
    fs.writeFileSync(path.join(output, 'detail.json'), detailResponse.body, { flag: 'wx' });
    return { checkpoint, fileName: name + '.doc', docSha256: sha(downloaded.body), detailSha256: sha(detailResponse.body), warnings };
  } catch (error) {
    error.checkpoint = checkpoint;
    throw error;
  } finally {
    if (loggedIn) {
      try {
        const exited = await request('/UMC/Logout.action', { cleanup: true });
        if (![200, 302].includes(exited.status)) throw new Error('LOGOUT_UNCONFIRMED');
      } catch { warnings.push('LOGOUT_UNCONFIRMED'); emit({ kind: 'warning', code: 'LOGOUT_UNCONFIRMED' }); }
    }
    jars.clear();
  }
}

module.exports = { run, parse, fileIp, validateWordXml };
if (require.main === module) {
  let input = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', chunk => { input += chunk; if (input.length > 256 * 1024) process.exit(2); });
  process.stdin.on('end', async () => {
    const emit = event => process.stdout.write(JSON.stringify(event) + '\n');
    try {
      const result = await run(parse(input), { baseUrl: process.env.UMC_URL, username: process.env.UMC_USER,
        password: process.env.UMC_PASS, certificateSha256: process.env.UMC_CERT_SHA256,
        timeoutSeconds: Number(process.env.UMC_TIMEOUT_SECONDS || 180) }, emit);
      emit({ kind: 'result', ...result });
    } catch (error) {
      const known = /^[A-Z][A-Z0-9_]{2,80}$/.test(error.message) ? error.message : 'PROTOCOL_FAILURE';
      emit({ kind: 'error', code: known, checkpoint: error.checkpoint || null });
      process.exitCode = 1;
    }
  });
}
