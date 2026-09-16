import test from 'node:test'
import assert from 'node:assert/strict'
import {
  OperationContractAuthoringError,
  readOperationContract,
  withOperationContract,
  replaceOperationCheck
} from './operationContract.ts'

const operation = () => ({
  operationCode: 'SOL.SITE_SURVEY.CONFIRM', operationVersion: 1,
  pre: { mode: 'RULE', ruleKey: 'ready' }, post: { mode: 'NONE' }
})
const contract = () => ({ version: 1, operations: [operation()] })
const fails = (value, code) => assert.throws(() => readOperationContract(value), (error) =>
  error instanceof OperationContractAuthoringError && error.code === code)

test('absent contract remains absent after JSON save/reopen', () => {
  const binding = { type: 'BUSINESS_OBJECT', targetContextCode: 'SOL', parameters: { x: 0 } }
  assert.equal(readOperationContract(undefined), undefined)
  const saved = withOperationContract(binding, undefined)
  assert.deepEqual(saved, binding)
  assert.equal(Object.hasOwn(saved, 'operationContract'), false)
  assert.equal(Object.hasOwn(JSON.parse(JSON.stringify(saved)), 'operationContract'), false)
})
test('explicit null does not become legacy absence', () => fails(null, 'OBJECT_REQUIRED'))
test('unknown version rejected', () => fails({ ...contract(), version: 2 }, 'CONTRACT_VERSION_UNSUPPORTED'))
test('string version rejected', () => fails({ ...contract(), version: '1' }, 'CONTRACT_VERSION_UNSUPPORTED'))
test('empty contract operations rejected', () => fails({ version: 1, operations: [] }, 'OPERATIONS_REQUIRED'))
test('missing operations rejected', () => fails({ version: 1 }, 'OPERATIONS_REQUIRED'))
test('null operation rejected', () => fails({ version: 1, operations: [null] }, 'OBJECT_REQUIRED'))
test('duplicate operation rejected', () => fails({ version: 1, operations: [operation(), operation()] }, 'OPERATION_DUPLICATE'))
test('missing PRE not turned into NONE', () => {
  const value = contract(); delete value.operations[0].pre; fails(value, 'OBJECT_REQUIRED')
})
test('missing POST not turned into NONE', () => {
  const value = contract(); delete value.operations[0].post; fails(value, 'OBJECT_REQUIRED')
})
test('NONE cannot carry a nonempty ruleKey', () => {
  const value = contract(); value.operations[0].post.ruleKey = 'later'; fails(value, 'UNKNOWN_FIELD')
})
test('NONE cannot carry an empty ruleKey', () => {
  const value = contract(); value.operations[0].post.ruleKey = ''; fails(value, 'UNKNOWN_FIELD')
})
test('NONE cannot carry a null ruleKey', () => {
  const value = contract(); value.operations[0].post.ruleKey = null; fails(value, 'UNKNOWN_FIELD')
})
test('RULE must carry a nonempty reference', () => {
  const value = contract(); value.operations[0].pre.ruleKey = ' '; fails(value, 'NONEMPTY_STRING_REQUIRED')
})
test('unknown check mode rejected', () => {
  const value = contract(); value.operations[0].pre.mode = 'ALLOW'; fails(value, 'CHECK_MODE_INVALID')
})
test('cannot inject a script or authorization field', () => {
  const value = contract(); value.operations[0].pre.script = 'return true'; fails(value, 'UNKNOWN_FIELD')
  const grant = contract(); grant.allowedActions = ['CONFIRM']; fails(grant, 'UNKNOWN_FIELD')
})
test('operation declaration cannot carry arbitrary command URL', () => {
  const value = contract(); value.operations[0].url = 'https://example.invalid'; fails(value, 'UNKNOWN_FIELD')
})
test('requires exact positive integer operation version', () => {
  for (const bad of [0, -1, 1.5, Number.NaN, Number.MAX_SAFE_INTEGER + 1, '1', null]) {
    const value = contract(); value.operations[0].operationVersion = bad; fails(value, 'EXACT_VERSION_REQUIRED')
  }
})
test('empty operation identity rejected', () => {
  const value = contract(); value.operations[0].operationCode = ''; fails(value, 'NONEMPTY_STRING_REQUIRED')
})
test('identifiers are not silently trimmed', () => {
  const value = contract(); value.operations[0].pre.ruleKey = ' ready '
  assert.equal(readOperationContract(value).operations[0].pre.ruleKey, ' ready ')
})
test('JSON save/reopen retains exact operation contract', () => {
  const value = contract()
  assert.deepEqual(readOperationContract(JSON.parse(JSON.stringify(value))), value)
})
test('editing new contract leaves view and other binding fields unchanged', () => {
  const binding = { type: 'BUSINESS_OBJECT', businessViewSnapshot: { id: '99999999999999999', version: 1 }, parameters: { value: false }, permission: { key: 'original' } }
  const before = structuredClone(binding)
  const next = withOperationContract(binding, contract())
  assert.deepEqual(binding, before)
  assert.deepEqual(next.businessViewSnapshot, before.businessViewSnapshot)
  assert.deepEqual(next.parameters, before.parameters)
  assert.deepEqual(next.permission, before.permission)
})
test('independent deep copy of operations and rules', () => {
  const source = contract(); const parsed = readOperationContract(source)
  parsed.operations[0].pre.ruleKey = 'edited'
  assert.equal(source.operations[0].pre.ruleKey, 'ready')
})
test('removing opt-in does not add null to old binding', () => {
  const source = { type: 'BUSINESS_OBJECT', operationContract: contract() }
  const next = withOperationContract(source, undefined)
  assert.deepEqual(next, { type: 'BUSINESS_OBJECT' })
  assert.ok(Object.hasOwn(source, 'operationContract'))
})
test('readonly authoring path rejects edits', () => {
  assert.throws(() => withOperationContract({}, contract(), true), (error) => error.code === 'READ_ONLY')
})
test('replacing PRE retains POST and does not mutate original', () => {
  const source = contract()
  const next = replaceOperationCheck(source, operation().operationCode, 'pre', { mode: 'NONE' })
  assert.deepEqual(next.operations[0].pre, { mode: 'NONE' })
  assert.deepEqual(next.operations[0].post, source.operations[0].post)
  assert.equal(source.operations[0].pre.ruleKey, 'ready')
})
test('replacing an absent operation does not create one', () => {
  assert.throws(() => replaceOperationCheck(contract(), 'missing', 'pre', { mode: 'NONE' }), (error) => error.code === 'OPERATION_NOT_FOUND')
})
test('invalid runtime checkpoint is rejected', () => {
  assert.throws(() => replaceOperationCheck(contract(), operation().operationCode, 'exit', { mode: 'NONE' }), (error) => error.code === 'CHECKPOINT_INVALID')
})
test('operation order is canonical without mutating input', () => {
  const a = operation(); const b = { ...operation(), operationCode: 'SOL.SITE_SURVEY.UPDATE' }
  const value = { version: 1, operations: [b, a] }
  const parsed = readOperationContract(value)
  assert.deepEqual(parsed.operations.map((item) => item.operationCode), [a.operationCode, b.operationCode])
  assert.equal(value.operations[0].operationCode, b.operationCode)
})
test('malformed input reports the exact field', () => {
  const value = contract(); value.operations[0].operationVersion = 0
  assert.throws(() => readOperationContract(value), (error) => error.path === 'operationContract.operations[0].operationVersion')
})
test('non JSON objects and inherited declarations rejected', () => {
  fails(new Date(), 'PLAIN_OBJECT_REQUIRED')
  fails(Object.create(contract()), 'PLAIN_OBJECT_REQUIRED')
})
