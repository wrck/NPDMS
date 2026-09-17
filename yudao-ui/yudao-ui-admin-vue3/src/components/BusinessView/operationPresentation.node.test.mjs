import { test } from 'node:test'
import assert from 'node:assert/strict'
import { operationPresentation } from './operationPresentation.ts'

for (const status of ['READ_ONLY', 'UNAVAILABLE', 'UNKNOWN', undefined]) {
  test(`controlled view closes write UI for ${status}`, () => {
    const state = operationPresentation('CONTROLLED', { status })
    assert.equal(state.readonly, true)
    assert.equal(state.reason, status === 'READ_ONLY' ? 'VIEW_READ_ONLY' : 'VIEW_UNAVAILABLE')
  })
}
test('available presentation does not add a write restriction', () => {
  assert.deepEqual(operationPresentation('CONTROLLED', { status: 'AVAILABLE' }), { readonly: false })
})
test('missing presentation does not grant write UI', () => {
  assert.equal(operationPresentation('CONTROLLED').readonly, true)
  assert.equal(operationPresentation('CONTROLLED', null).readonly, true)
})
for (const mode of ['INDEPENDENT', 'LEGACY']) {
  test(`${mode} retains its existing view contract`, () => {
    assert.deepEqual(operationPresentation(mode, { status: 'READ_ONLY' }), { readonly: false })
  })
}
test('checking cannot expose a writable controlled form', () => {
  assert.equal(operationPresentation('CHECKING', { status: 'AVAILABLE' }).readonly, true)
})
test('preserves the server diagnostic without changing observation or action privileges', () => {
  const observation = Object.freeze({ status: 'UNAVAILABLE', reason: 'VIEW_IDENTITY_MISMATCH' })
  assert.deepEqual(operationPresentation('CONTROLLED', observation), {
    readonly: true, reason: 'VIEW_IDENTITY_MISMATCH'
  })
  assert.equal(observation.status, 'UNAVAILABLE')
})
test('available to readonly to available is reversible and has no execution-client side effects', () => {
  const statuses = ['AVAILABLE', 'READ_ONLY', 'AVAILABLE']
  assert.deepEqual(statuses.map(status => operationPresentation('CONTROLLED', { status }).readonly), [false, true, false])
})
