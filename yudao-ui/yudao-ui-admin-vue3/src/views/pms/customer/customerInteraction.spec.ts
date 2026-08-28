import { describe, expect, it } from 'vitest'
import { createCustomerIntentStore, customerIntentOf } from './customerInteraction'

describe('customer command intent store', () => {
  it('reuses the same key until the intent completes', () => {
    let sequence = 0
    const store = createCustomerIntentStore(() => `key-${++sequence}`)
    const intent = customerIntentOf('create', { code: 'C-001', name: '客户一' })

    expect(store.key(intent)).toBe('key-1')
    expect(store.key(intent)).toBe('key-1')
    store.complete(intent)
    expect(store.key(intent)).toBe('key-2')
  })

  it('uses a new key when the complete user intent changes', () => {
    let sequence = 0
    const store = createCustomerIntentStore(() => `key-${++sequence}`)

    expect(store.key(customerIntentOf('delete', { id: 1, version: 2, reason: '重复' }))).toBe(
      'key-1'
    )
    expect(store.key(customerIntentOf('delete', { id: 1, version: 2, reason: '修正' }))).toBe(
      'key-2'
    )
  })
})
