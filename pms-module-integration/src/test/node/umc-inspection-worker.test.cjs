'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const https = require('node:https');
const { EventEmitter } = require('node:events');
const { PassThrough } = require('node:stream');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const worker = require('../../main/resources/umc/inspection-worker.cjs');
const word = '<?xml version="1.0"?><w:wordDocument xmlns:w="http://schemas.microsoft.com/office/word/2003/wordml"><w:body/></w:wordDocument>';

test('UMC IDs retain precision while booleans, levels and empty metrics retain meaning', () => {
  const data = worker.parse('{"id":9007199254740993,"taskid":9007199254740995,"tenantId":9007199254740997,"level":1,"result":false,"valuelist":[]}');
  assert.equal(data.id, '9007199254740993'); assert.equal(data.taskid, '9007199254740995');
  assert.equal(data.level, 1); assert.equal(data.result, false); assert.deepEqual(data.valuelist, []);
  assert.equal(data.tenantId, '9007199254740997');
});
test('only legal IP filenames cross the upload boundary', () => {
  assert.equal(worker.fileIp('Device(192.0.2.1)'), '192.0.2.1');
  assert.equal(worker.fileIp('192.0.2.1'), '192.0.2.1');
  for (const value of ['192.0.2.1.txt', '../Device(192.0.2.1)', 'x\r\n(192.0.2.1)', 'Device(192.0.2.999)'])
    assert.throws(() => worker.fileIp(value));
});
test('DOC must be WordML and cannot carry external entity declarations', () => {
  worker.validateWordXml(Buffer.from(word));
  for (const value of ['<html>session expired</html>', '<?xml?><report/>', word.replace('<w:wordDocument', '<!DOCTYPE x><w:wordDocument')])
    assert.throws(() => worker.validateWordXml(Buffer.from(value)));
});

function fixture(t, options = {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'umc-worker-test-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const inputPath = path.join(directory, 'input'); fs.writeFileSync(inputPath, '<synthetic>show version\nredacted');
  const input = { scope: { tenantId: 1, taskKey: 'test-task', attemptKey: 'test-attempt' }, requestId: 'request-1',
    files: [{ name: 'Test(192.0.2.1)', path: inputPath }], outputDirectory: directory };
  const config = { baseUrl: 'https://umc.test/', username: 'test', password: 'synthetic-test-only', timeoutSeconds: 5 };
  const state = { task: null, created: 0, uploaded: 0, reportQueries: 0, logout: 0, detail: null };
  t.mock.method(https, 'request', (url, init, callback) => {
    const q = new EventEmitter(); const parts = [];
    q.write = part => parts.push(Buffer.from(part));
    q.destroy = error => { if (error) q.emit('error', error); };
    q.end = () => queueMicrotask(() => {
      const p = new URL(url).pathname; let body = ''; let status = 200;
      if (p === '/UMC/code.jsp') body = '{"c":1}';
      else if (p === '/UMC/Login!start.action') body = 'getElementById("c").value="1";cn="2";cp="3";';
      else if (p === '/UMC/js/e.js') body = 'function strEnc(){return "cipher";}';
      else if (p === '/UMC/Logout.action') state.logout++;
      else if (p.endsWith('/tasklist')) {
        if (options.authFail) { status = 401; body = 'sensitive-server-material'; }
        else body = JSON.stringify({ code: 200, data: { total: state.task ? 1 : 0, list: state.task ? [state.task] : [] } });
      } else if (p.endsWith('/taskadd')) {
        state.created++; state.task = { ...JSON.parse(Buffer.concat(parts)), id: '9007199254740993' };
        if (options.taskCreateFail) { status = 503; body = 'task-creation-response-lost'; }
      } else if (p.endsWith('/offlinetaskstart')) {
        state.uploaded++; if (options.uploadFail) { status = 500; body = 'sensitive-server-material'; }
      } else if (p.endsWith('/reportlist')) {
        state.reportQueries++;
        if (options.failBeforeUpload && state.reportQueries === 1) { status = 503; body = 'transient-query-failure'; }
        else body = JSON.stringify({ code: 200, data: { total: state.uploaded && !options.noReport ? 1 : 0, list: state.uploaded && !options.noReport
          ? [{ id: '9007199254740995', taskid: state.task.id, doc: options.notReady ? false : state.reportQueries > 2 }] : [] } });
      } else if (p.endsWith('/report')) {
        state.detail = { id: '9007199254740995', taskid: state.task.id, doc: true,
          devlist: [{ id: '1', deviceip: options.wrongDevice ? '192.0.2.2' : '192.0.2.1',
            rulelist: [{ ruleid: '85', support: true, error: '', result: false, targetlist: [{ id: '5', valuelist: [] }] }] }] };
        body = JSON.stringify(state.detail);
      } else if (p.endsWith('/report_download')) body = options.htmlDownload ? '<html>session expired</html>' : word;
      else if (p !== '/UMC/Login.action') throw new Error('Unexpected route ' + p);
      const response = new PassThrough(); response.statusCode = status;
      response.headers = { 'set-cookie': ['JSESSIONID=synthetic-session; HttpOnly'] };
      callback(response); response.end(body);
    });
    return q;
  });
  return { input, config, state };
}

test('uploads once, waits for DOC, obtains structured hierarchy and recovers without duplicate creation', async t => {
  const { input, config, state } = fixture(t); const events = [];
  const first = await worker.run(input, config, e => events.push(e));
  assert.equal(first.checkpoint.taskId, '9007199254740993');
  assert.equal(first.checkpoint.reportId, '9007199254740995');
  assert.equal(state.created, 1); assert.equal(state.uploaded, 1); assert.ok(state.reportQueries >= 3);
  assert.equal(state.logout, 1); assert.ok(events.some(e => e.checkpoint?.uploadState === 'UNKNOWN'));
  const detail = JSON.parse(fs.readFileSync(path.join(input.outputDirectory, 'detail.json')));
  assert.equal(detail.devlist[0].rulelist[0].result, false);
  assert.deepEqual(detail.devlist[0].rulelist[0].targetlist[0].valuelist, []);
  const secondDir = path.join(input.outputDirectory, 'retry'); fs.mkdirSync(secondDir);
  const second = await worker.run({ ...input, scope: { attemptKey: 'test-attempt', taskKey: 'test-task', tenantId: '1' },
    outputDirectory: secondDir, checkpoint: first.checkpoint }, config);
  assert.equal(second.checkpoint.reportId, first.checkpoint.reportId);
  assert.equal(state.created, 1); assert.equal(state.uploaded, 1); assert.equal(state.logout, 2);
});
test('unconfirmed upload returns its recovery checkpoint and never echoes remote error content', async t => {
  const { input, config, state } = fixture(t, { uploadFail: true });
  let checkpoint;
  await assert.rejects(worker.run(input, config), e => {
    assert.equal(e.message, 'SUBMISSION_REJECTED'); assert.equal(e.checkpoint.uploadState, 'UNKNOWN');
    assert.equal(e.checkpoint.taskId, '9007199254740993'); checkpoint = e.checkpoint; return true;
  });
  assert.equal(state.uploaded, 1); assert.equal(state.logout, 1);
  state.reportQueries = 3;
  const recovered = await worker.run({ ...input, checkpoint }, config);
  assert.equal(recovered.checkpoint.reportId, '9007199254740995');
  assert.equal(state.created, 1); assert.equal(state.uploaded, 1); assert.equal(state.logout, 2);
});

test('confirmed failure before upload resumes the existing task and uploads exactly once', async t => {
  const { input, config, state } = fixture(t, { failBeforeUpload: true });
  let checkpoint;
  await assert.rejects(worker.run(input, config), error => {
    assert.equal(error.message, 'QUERY_FAILED');
    assert.equal(error.checkpoint.uploadState, 'NOT_SENT');
    checkpoint = error.checkpoint; return true;
  });
  assert.equal(state.created, 1); assert.equal(state.uploaded, 0);
  const recovered = await worker.run({ ...input, checkpoint, queryOnly: false }, config);
  assert.equal(recovered.checkpoint.reportId, '9007199254740995');
  assert.equal(state.created, 1); assert.equal(state.uploaded, 1);
});

test('unknown upload with no report remains unresolved without any second upload', async t => {
  const { input, config, state } = fixture(t, { uploadFail: true, noReport: true });
  let checkpoint;
  await assert.rejects(worker.run(input, config), error => {
    checkpoint = error.checkpoint; assert.equal(checkpoint.uploadState, 'UNKNOWN'); return true;
  });
  config.timeoutSeconds = 1;
  await assert.rejects(worker.run({ ...input, checkpoint, queryOnly: true }, config),
    { message: 'UPLOAD_OUTCOME_UNKNOWN' });
  assert.equal(state.created, 1); assert.equal(state.uploaded, 1);
});

test('unknown task creation stays read-only even though log upload has not started', async t => {
  const { input, config, state } = fixture(t, { taskCreateFail: true });
  let checkpoint;
  await assert.rejects(worker.run(input, config), error => {
    assert.equal(error.message, 'TASK_CREATION_OUTCOME_UNKNOWN');
    checkpoint = error.checkpoint; assert.equal(checkpoint.uploadState, 'NOT_SENT'); return true;
  });
  assert.equal(state.created, 1); assert.equal(state.uploaded, 0);
  config.timeoutSeconds = 1;
  await assert.rejects(worker.run({ ...input, checkpoint, queryOnly: true }, config),
    { message: 'UPLOAD_OUTCOME_UNKNOWN' });
  assert.equal(state.created, 1); assert.equal(state.uploaded, 0);
});
test('recovery after process loss queries existing intent and never creates or uploads a new task', async t => {
  const { input, config, state } = fixture(t);
  await assert.rejects(worker.run({ ...input, queryOnly: true }, config), { message: 'RECOVERY_TASK_NOT_FOUND' });
  assert.equal(state.created, 0); assert.equal(state.uploaded, 0);
  const first = await worker.run(input, config);
  const outputDirectory = path.join(input.outputDirectory, 'recovery'); fs.mkdirSync(outputDirectory);
  const recovered = await worker.run({ ...input, outputDirectory, queryOnly: true }, config);
  assert.equal(recovered.checkpoint.reportId, first.checkpoint.reportId);
  assert.equal(state.created, 1); assert.equal(state.uploaded, 1);
});
test('login failure releases the owned session and does not leak response text', async t => {
  const { input, config, state } = fixture(t, { authFail: true });
  await assert.rejects(worker.run(input, config), { message: 'LOGIN_FAILED' });
  assert.equal(state.created, 0); assert.equal(state.logout, 1);
});
test('cross-device details cannot be associated with the submitted logs', async t => {
  const { input, config, state } = fixture(t, { wrongDevice: true }); state.reportQueries = 3;
  await assert.rejects(worker.run(input, config), { message: 'DETAIL_DEVICE_MISMATCH' });
});
test('HTML download is not returned as DOC', async t => {
  const { input, config, state } = fixture(t, { htmlDownload: true }); state.reportQueries = 3;
  await assert.rejects(worker.run(input, config), { message: 'INVALID_DOC' });
});
test('expired report wait still logs out and retains accepted-upload state', async t => {
  const { input, config, state } = fixture(t, { notReady: true }); config.timeoutSeconds = 1;
  await assert.rejects(worker.run(input, config), e => {
    assert.equal(e.message, 'TIMEOUT'); assert.equal(e.checkpoint.uploadState, 'ACCEPTED'); return true;
  });
  assert.equal(state.logout, 1);
});
