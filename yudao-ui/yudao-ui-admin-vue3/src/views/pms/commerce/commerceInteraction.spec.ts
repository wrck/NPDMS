import { describe, expect, it } from 'vitest'
import {
  commerceIntentOf,
  createCommerceIntentStore,
  parseProjectRouteContext,
  splitSerialNumbers
} from './commerceInteraction'

describe('F-COM-001 frontend interaction contracts', () => {
  it('accepts only a complete server-provided project context', () => {
    expect(
      parseProjectRouteContext({ projectId: '9', projectVersion: '0', projectScopeVersion: '2' })
    ).toEqual({ projectId: 9, projectVersion: 0, projectScopeVersion: 2 })
    expect(parseProjectRouteContext({ projectId: '9', projectVersion: '0' })).toBeUndefined()
    expect(
      parseProjectRouteContext({ projectId: '9', projectVersion: '-1', projectScopeVersion: '2' })
    ).toBeUndefined()
  })

  it('normalizes serial input without inventing device facts', () => {
    expect(splitSerialNumbers('SN-1, SN-2\nSN-1，SN-3')).toEqual(['SN-1', 'SN-2', 'SN-3'])
    expect(splitSerialNumbers('  ')).toEqual([])
  })

  it('reuses an idempotency key until one complete intent succeeds', () => {
    let sequence = 0
    const store = createCommerceIntentStore(() => `op-${++sequence}`)
    const intent = commerceIntentOf('release', { id: 1, version: 3, reason: '调整' })
    expect(store.key(intent)).toBe('op-1')
    expect(store.key(intent)).toBe('op-1')
    store.complete(intent)
    expect(store.key(intent)).toBe('op-2')
  })
})
