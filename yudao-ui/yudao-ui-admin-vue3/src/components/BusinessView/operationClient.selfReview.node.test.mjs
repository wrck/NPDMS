import test from 'node:test'
import assert from 'node:assert/strict'
import { OperationClient, OperationRejected, captureOperationResponse } from './operationClient.ts'
const selection = { stage: { projectId: '1', stageId: '2', executionContractId: '3', contractVersion: 1, planVersionId: '4', executionId: '5' } }
const result = { ownerContext: 'SOL', objectType: 'SITE_SURVEY', objectId: '8', objectVersion: 2,
  businessFactVersion: 'v2', resultCode: 'SAVED', response: { id: '8' }, replayed: false }
function fixture() {
  let serial = 0
  const sent = [], inspected = [], pending = []
  const transport = {
    newKey: () => `key-${++serial}`,
    inspect: async query => { inspected.push(query); return { node: { projectId: '1', kind: 'STAGE', id: '2', status: 'ACTIVE' },
      execution: selection, ownerFactVersion: 'v1', actions: [{ operationCode: 'SAVE', operationVersion: 1, allowed: true }] } },
    submit: async (...args) => { sent.push(args); return result }, pendingChanged: value => pending.push(value)
  }
  const client = new OperationClient({ projectId: '1', nodeKind: 'STAGE', nodeId: '2' }, selection, transport)
  const intent = { operationCode: 'SAVE', objectId: '8', expectedBusinessVersion: 1, input: { value: 'a' } }
  return { client, transport, intent, sent, inspected, pending }
}
test('retired client recovers an uncertain command with the exact previous envelope', async () => {
  const f = fixture(); let attempt = 0
  f.transport.submit = async (...args) => { f.sent.push(args); if (!attempt++) throw new Error('lost'); return { ...result, replayed: true } }
  await assert.rejects(f.client.execute(f.intent), /lost/)
  f.client.invalidate()
  assert.equal(f.client.hasUncertain(), true)
  const recovered = await f.client.retryPending()
  assert.equal(recovered[0].replayed, true)
  assert.deepEqual(f.sent[0], f.sent[1])
  assert.equal(f.inspected.length, 1)
  assert.equal(f.client.hasUncertain(), false)
  assert.deepEqual(f.pending, [true, false])
  await assert.rejects(f.client.execute({ ...f.intent, input: { value: 'b' } }), /CONTEXT_CHANGED/)
})
test('uncertain intent can also be retried through its old form after invalidation', async () => {
  const f = fixture(); let attempt = 0
  f.transport.submit = async (...args) => { f.sent.push(args); if (!attempt++) throw new Error('lost'); return result }
  await assert.rejects(f.client.execute(f.intent)); f.client.invalidate()
  assert.deepEqual(await f.client.execute(f.intent), result)
  assert.deepEqual(f.sent[0], f.sent[1])
})
test('a new intent cannot overtake an unresolved earlier command', async () => {
  const f = fixture(); f.transport.submit = async () => { throw new Error('lost') }
  await assert.rejects(f.client.execute(f.intent))
  await assert.rejects(f.client.execute({ ...f.intent, input: { value: 'b' } }), /UNCERTAIN_RETRY_REQUIRED/)
  assert.equal(f.inspected.length, 1)
})
test('a later identical implicit command gets a new key and a fresh capability check', async () => {
  const f = fixture(); await f.client.execute(f.intent); await f.client.execute(f.intent)
  assert.deepEqual(f.sent.map(row => row[2]), ['key-1', 'key-2'])
  assert.equal(f.inspected.length, 2)
})
test('an explicit key still replays the original command and checks current server authorization', async () => {
  const f = fixture(); const intent = { ...f.intent, key: 'explicit' }
  await f.client.execute(intent); f.client.invalidate(); await f.client.execute(intent)
  assert.deepEqual(f.sent[0], f.sent[1]); assert.equal(f.inspected.length, 1)
  await assert.rejects(f.client.execute({ ...intent, input: { value: 'other' } }), /KEY_CONFLICT/)
})
test('first definite server rejection permits a corrected new intent', async () => {
  const f = fixture(); let attempt = 0
  f.transport.submit = async (...args) => { f.sent.push(args); if (!attempt++) throw new OperationRejected(400, 'OPERATION_PRE_NOT_MATCHED'); return result }
  await assert.rejects(f.client.execute(f.intent), /PRE_NOT_MATCHED/)
  assert.equal(f.client.hasUncertain(), false)
  await f.client.execute({ ...f.intent, input: { value: 'corrected' } })
  assert.deepEqual(f.sent.map(row => row[2]), ['key-1', 'key-2'])
})
test('a refusal while recovering does not claim the earlier attempt rolled back', async () => {
  const f = fixture(); let attempt = 0
  f.transport.submit = async () => { if (!attempt++) throw new Error('lost'); throw new OperationRejected(403, 'FORBIDDEN') }
  await assert.rejects(f.client.execute(f.intent)); await assert.rejects(f.client.retryPending(), /FORBIDDEN/)
  assert.equal(f.client.hasUncertain(), true)
  await assert.rejects(f.client.execute({ ...f.intent, input: { value: 'new' } }), /UNCERTAIN_RETRY_REQUIRED/)
})
test('unknown string failures are never treated as proof of rejection', async () => {
  const f = fixture(); f.transport.submit = async () => { throw 'error' }
  await assert.rejects(f.client.execute(f.intent)); assert.equal(f.client.hasUncertain(), true)
})
test('recovery does not duplicate an in-flight submission', async () => {
  const f = fixture(); let done
  f.transport.submit = (...args) => { f.sent.push(args); return new Promise(resolve => { done = resolve }) }
  const original = f.client.execute(f.intent)
  await new Promise(resolve => setImmediate(resolve))
  const recovery = f.client.retryPending(); assert.equal(f.sent.length, 1)
  done(result); await Promise.all([original,recovery]); assert.equal(f.client.isBusy(), false)
})
test('decoded success keeps exact IDs and does not replace the existing JSON decoder', () => {
  const data = { code: 0, data: { id: '9007199254740999' } }
  assert.equal(captureOperationResponse(data), data)
  assert.equal(captureOperationResponse('encrypted-or-undecoded'), 'encrypted-or-undecoded')
})
test('decoded definite rejection retains its safe message and code', () => {
  assert.throws(() => captureOperationResponse({ code: 400, msg: 'OPERATION_PRE_NOT_MATCHED' }),
    error => error instanceof OperationRejected && error.code === 400 && error.message === 'OPERATION_PRE_NOT_MATCHED')
})
test('authentication refresh, busy, rate limits and server failures remain uncertain', () => {
  for (const code of [401,408,429,500,501,502,503,504,901]) {
    const data = { code, msg: 'error' }; assert.equal(captureOperationResponse(data),data)
  }
  const data = { code: 400, msg: 'OPERATION_IN_PROGRESS' }
  assert.equal(captureOperationResponse(data),data)
})
test('a failed preflight never leaves a phantom uncertain request', async () => {
  const f = fixture(); f.transport.inspect = async () => { throw new Error('unavailable') }
  await assert.rejects(f.client.execute(f.intent)); assert.equal(f.client.hasUncertain(), false)
  assert.equal(f.client.isBusy(), false)
})
