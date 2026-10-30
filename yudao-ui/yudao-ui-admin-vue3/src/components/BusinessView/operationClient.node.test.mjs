import test from 'node:test'
import assert from 'node:assert/strict'
import { OperationClient, routeSelection, selectionClient, selectionIdentity, stableJson } from './operationClient.ts'
const selection = (kind = 'TASK', round = '17') => ({ [kind === 'TASK' ? 'task' : 'stage']: {
  projectId: '9007199254740999', [kind === 'TASK' ? 'taskId' : 'stageId']: '12', executionId: round,
  executionContractId: '13', planVersionId: '14', contractVersion: 1, executionVersion: 1 } })
const result = { ownerContext: 'SOL', objectType: 'SITE_SURVEY', objectId: '20', objectVersion: 2,
  businessFactVersion: 'f2', resultCode: 'SAVED', response: { id: '20' }, replayed: false }
function fixture(kind = 'TASK') {
  const selected = selection(kind)
  const target = { projectId: '9007199254740999', nodeKind: kind, nodeId: '12' }
  const capability = { node: { projectId: target.projectId, kind, id: '12', status: 'ACTIVE' }, execution: selected,
    ownerFactVersion: 'f1', actions: [{ operationCode: 'SAVE', operationVersion: 1, allowed: true }] }
  const requests = [], inspections = []
  const transport = { inspect: async q => { inspections.push(q); return capability }, submit: async (...args) => { requests.push(args); return result }, newKey: () => 'key-1' }
  const client = new OperationClient(target, selected, transport)
  const intent = { operationCode: 'SAVE', objectId: '20', expectedBusinessVersion: 1, input: { value: false } }
  return { client, selected, target, capability, transport, requests, inspections, intent }
}
for (const kind of ['TASK', 'STAGE']) test(`${kind} submits exact object and expected business version`, async () => {
  const f = fixture(kind)
  await f.client.execute(f.intent)
  assert.equal(f.requests.length, 1)
  assert.equal(f.requests[0][1].nodeKind, kind)
  assert.equal(f.requests[0][1].projectId, f.target.projectId)
  assert.equal(f.requests[0][1].expectedBusinessVersion, 1)
  assert.equal(f.requests[0][1].expectedObjectFactVersion, 'f1')
  assert.deepEqual(f.requests[0][1].input, { value: false })
})
test('spread retains local routing; JSON and structuredClone never transmit it', () => {
  const f = fixture(), routed = routeSelection(f.selected, f.client)
  assert.equal(selectionClient({ task: { ...routed.task } }), f.client)
  assert.deepEqual(JSON.parse(JSON.stringify(routed)), f.selected)
  assert.deepEqual(structuredClone(routed), f.selected)
  assert.equal(selectionClient(f.selected), undefined)
})
test('same identity survives numeric/string transport without losing Snowflake precision', () => {
  const f = fixture()
  assert.equal(selectionIdentity(f.selected), selectionIdentity({ task: { ...f.selected.task, taskId: 12 } }))
})
test('revoked permission blocks submit; no legacy fallback exists', async () => {
  const f = fixture(); f.capability.actions[0].allowed = false
  await assert.rejects(f.client.execute(f.intent), /OPERATION_NOT_ALLOWED/)
  assert.equal(f.requests.length, 0)
})
test('new execution round cannot retarget an existing editing buffer', async () => {
  const f = fixture(); f.capability.execution = selection('TASK', '18')
  await assert.rejects(f.client.execute(f.intent), /EXECUTION_CONTEXT_CHANGED/)
  assert.equal(f.requests.length, 0)
})
test('changed plan or contract is rejected', async () => {
  for (const field of ['planVersionId', 'executionContractId']) {
    const f = fixture(); f.capability.execution = { task: { ...f.selected.task, [field]: '99' } }
    await assert.rejects(f.client.execute(f.intent), /EXECUTION_CONTEXT_CHANGED/)
  }
})
test('response with different node cannot grant command rights', async () => {
  const f = fixture(); f.capability.node.id = '99'
  await assert.rejects(f.client.execute(f.intent), /EXECUTION_CONTEXT_MISMATCH/)
})
test('ambiguous execution and mismatched construction are rejected', () => {
  assert.throws(() => selectionIdentity({ ...selection(), ...selection('STAGE') }), /REQUIRED/)
  const f = fixture()
  assert.throws(() => new OperationClient({ ...f.target, nodeId: '99' }, f.selected, f.transport), /MISMATCH/)
})
test('unavailable capabilities and missing object versions fail closed', async () => {
  for (const problem of ['reason', 'version', 'fact']) {
    const f = fixture()
    if (problem === 'reason') f.capability.reason = 'UNKNOWN'
    if (problem === 'version') f.intent.expectedBusinessVersion = undefined
    if (problem === 'fact') f.capability.ownerFactVersion = null
    await assert.rejects(f.client.execute(f.intent))
    assert.equal(f.requests.length, 0)
  }
})
test('uncertain response retries exact envelope without re-resolving now completed node', async () => {
  const f = fixture(); let attempt = 0
  f.transport.submit = async (...args) => { f.requests.push(args); if (++attempt === 1) throw new Error('connection lost'); return { ...result, replayed: true } }
  await assert.rejects(f.client.execute(f.intent), /connection lost/)
  f.capability.reason = 'NODE_ALREADY_COMPLETED'
  const replay = await f.client.execute(f.intent)
  assert.equal(replay.replayed, true)
  assert.equal(f.inspections.length, 1)
  assert.deepEqual(f.requests[0], f.requests[1])
})
test('completed retry still reaches the server for current authorization', async () => {
  const f = fixture(); await f.client.execute(f.intent)
  f.transport.submit = async () => { throw new Error('FORBIDDEN') }
  await assert.rejects(f.client.execute(f.intent), /FORBIDDEN/)
})
test('same key different input is an explicit conflict', async () => {
  const f = fixture(); await f.client.execute({ ...f.intent, key: 'fixed' })
  await assert.rejects(f.client.execute({ ...f.intent, key: 'fixed', input: { value: true } }), /KEY_CONFLICT/)
})
test('double click coalesces in flight, without merging different business intents', async () => {
  const f = fixture(); let finish
  f.transport.submit = async (...args) => { f.requests.push(args); return new Promise(resolve => { finish = resolve }) }
  const first = f.client.execute(f.intent), second = f.client.execute(f.intent)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(f.requests.length, 1); assert.equal(f.client.isBusy(), true)
  finish(result); await Promise.all([first, second]); assert.equal(f.client.isBusy(), false)
})
test('context disposed during preflight does not submit', async () => {
  const f = fixture(); let finish
  f.transport.inspect = () => new Promise(resolve => { finish = resolve })
  const pending = f.client.execute(f.intent)
  f.client.invalidate(); finish(f.capability)
  await assert.rejects(pending, /CONTEXT_CHANGED/); assert.equal(f.requests.length, 0)
})
test('metadata callback failure does not report committed business as failed', async () => {
  const f = fixture(); f.transport.submitted = () => { throw new Error('projection unavailable') }
  assert.deepEqual(await f.client.execute(f.intent), result)
})
test('forged execution payload is never forwarded to Owner command', async () => {
  const f = fixture()
  await assert.rejects(f.client.execute({ ...f.intent, input: { execution: {} } }), /UNTRUSTED/)
  assert.equal(f.inspections.length, 0)
})
test('stable intent keeps false, zero, empty text and sorts keys', () => {
  assert.equal(stableJson({ z: 0, b: '', a: false }), stableJson({ a: false, b: '', z: 0 }))
  assert.notEqual(stableJson({ a: false }), stableJson({}))
})
