import { beforeEach, expect, it, vi } from 'vitest'

const values = vi.hoisted(() => new Map<string, unknown>())
vi.mock('@/hooks/web/useCache', () => ({
  CACHE_KEY: { TenantId: 'tenant', VisitTenantId: 'visit' },
  useCache: () => ({
    wsCache: {
      get: (key: string) => values.get(key),
      set: (key: string, value: unknown) => values.set(key, value),
      delete: (key: string) => values.delete(key)
    }
  })
}))
vi.mock('@/utils/jsencrypt', () => ({ encrypt: vi.fn(), decrypt: vi.fn() }))

import { getTenantId, getVisitTenantId, setTenantId, setVisitTenantId } from './auth'

beforeEach(() => values.clear())

it('selects the directory default without inheriting a stale visit tenant', () => {
  setVisitTenantId(0)
  setTenantId(1)
  expect(getTenantId()).toBe(1)
  expect(getVisitTenantId()).toBeUndefined()
})

it('keeps normal explicit tenant selection without hardcoding tenant one', () => {
  setTenantId(9)
  expect(getTenantId()).toBe(9)
  setVisitTenantId(2)
  expect(getVisitTenantId()).toBe(2)
})
