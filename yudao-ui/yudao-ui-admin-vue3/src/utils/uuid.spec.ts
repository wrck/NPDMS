import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { generateUUID } from './index'
import { createCustomerIntentStore } from '@/views/pms/customer/customerInteraction'

const randomValues = crypto.getRandomValues.bind(crypto)
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
afterEach(() => vi.unstubAllGlobals())

describe('browser UUID compatibility', () => {
  it('uses native UUID generation when available', () => {
    const native = vi.fn(() => 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee')
    vi.stubGlobal('crypto', { randomUUID: native })
    expect(generateUUID()).toBe('aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee')
    expect(native).toHaveBeenCalledOnce()
  })

  it.each(['http', 'legacy'])('generates distinct UUIDs in %s environments', (environment) => {
    vi.stubGlobal('crypto', environment === 'http' ? { getRandomValues: randomValues } : undefined)
    const keys = Array.from({ length: 1000 }, () => generateUUID())
    expect(keys.every(key => uuidPattern.test(key))).toBe(true)
    expect(new Set(keys).size).toBe(keys.length)
  })

  it('preserves default business intent retry keys on HTTP', () => {
    vi.stubGlobal('crypto', { getRandomValues: randomValues })
    const store = createCustomerIntentStore()
    const key = store.key('create')
    expect(key).toMatch(uuidPattern)
    expect(store.key('create')).toBe(key)
    store.complete('create')
    expect(store.key('create')).not.toBe(key)
  })

  it('keeps native randomUUID access inside the compatibility utility', () => {
    const root = resolve(process.cwd(), 'src')
    const offenders: string[] = []
    const scan = (directory: string) => {
      for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const path = resolve(directory, entry.name)
        if (entry.isDirectory()) scan(path)
        else if (/\.(?:[cm]?[jt]sx?|vue)$/.test(entry.name) && !/\.(?:spec|test)\./.test(entry.name)
          && path !== resolve(root, 'utils/index.ts')
          && /\brandomUUID\b/.test(readFileSync(path, 'utf8'))) offenders.push(path)
      }
    }
    scan(root)
    expect(offenders).toEqual([])
  })
})
