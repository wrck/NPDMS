import { beforeEach, expect, it, vi } from 'vitest'
import type { BusinessPageInitialData, ProjectBusinessApi } from '@/api/pms/platform/business'
import { initialBusinessPage } from './initialBusinessPage'

const actor = vi.hoisted(() => ({ user: 9, tenant: 1 }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => actor.user, getTenantId: () => actor.tenant, getVisitTenantId: () => undefined }))
beforeEach(() => { actor.user = 9; actor.tenant = 1; vi.useRealTimers() })
const row = (id: string, version = 1) => ({ ref: { entityId: id }, available: true, concurrencyBasis: version, fieldValues: { projectId: '9007199254740993' } })
const fixture = () => {
  const api = { model: vi.fn(), fieldConfiguration: vi.fn(), page: vi.fn(), form: vi.fn(), get: vi.fn(),
    action: vi.fn(), update: vi.fn(), create: vi.fn(), remove: vi.fn() } as unknown as ProjectBusinessApi
  const data = { request: { projectId: '9007199254740993', initialEntityId: '33' },
    model: { stableCode: 'NEW_BUSINESS', fields: [], operations: [] }, configuration: { version: 1, fields: [] },
    pageSize: 20, records: { list: [row('33'), row('32')], total: 2 },
    forms: [{ entity: row('33'), context: {}, definitions: [] }, { entity: row('32'), context: {}, definitions: [] }],
    relatedPages: [], context: { marker: 'initial' } } as unknown as BusinessPageInitialData
  return { raw: api, data, ...initialBusinessPage(api, data) }
}
it('serves the initial model, rows and selected/effective forms without starting reads', async () => {
  const original = fixture(), client = original.api
  expect(await client.fieldConfiguration()).toBe(original.data.configuration)
  expect(await client.model()).toBe(original.data.model)
  expect(await client.page(1, 10, [], undefined, original.data.request.projectId)).toEqual(original.data.records)
  expect((await client.get('33')).ref.entityId).toBe('33')
  expect((await client.form('33')).entity.ref.entityId).toBe('33')
  expect((await client.form('32')).entity.ref.entityId).toBe('32')
  expect(original.takeContext(original.data.request.projectId)).toEqual({ marker: 'initial' })
  expect(original.takeContext(original.data.request.projectId)).toBeUndefined()
  for (const read of ['model', 'fieldConfiguration', 'page', 'get', 'form'] as const) expect(original.raw[read]).not.toHaveBeenCalled()
})
it('refreshes once the first page is consumed and discards unconsumed old forms', async () => {
  const raw = { page: vi.fn(async () => ({ list: [row('33', 5)], total: 1 })), form: vi.fn(async () => ({ entity: row('33', 5) })) }
  const { data } = fixture(), client = initialBusinessPage(raw as unknown as ProjectBusinessApi, data).api
  await client.page(1, 20, [], undefined, data.request.projectId)
  expect((await client.page(1, 20, [], undefined, data.request.projectId)).list[0].concurrencyBasis).toBe(5)
  expect((await client.form('33')).entity.concurrencyBasis).toBe(5)
  expect(raw.page).toHaveBeenCalledTimes(1); expect(raw.form).toHaveBeenCalledTimes(1)
})
it.each(['project', 'filter', 'configuration', 'sort'] as const)('does not reuse rows for a different %s', async change => {
  const { data } = fixture(), raw = { page: vi.fn(async () => ({ list: [], total: 0 })) }
  const client = initialBusinessPage(raw as unknown as ProjectBusinessApi, data).api
  await client.page(1, 20, change === 'filter' ? [{ fieldCode: 'name', operator: 'EQ', values: ['different'] }] : [],
    change === 'sort' ? [{ fieldCode: 'id', direction: 'ASC' }] : undefined,
    change === 'project' ? '9007199254740992' : data.request.projectId, change === 'configuration' ? 'other' : undefined)
  expect(raw.page).toHaveBeenCalledTimes(1)
})
it.each(['user', 'tenant', 'expired'] as const)('does not expose initialization after %s changes', async change => {
  vi.useFakeTimers()
  const { data } = fixture(), raw = { form: vi.fn(async () => { throw new Error('403') }) }
  vi.useFakeTimers()
  const client = initialBusinessPage(raw as unknown as ProjectBusinessApi, data).api
  if (change === 'expired') vi.advanceTimersByTime(4000)
  else actor[change]++
  await expect(client.form('33')).rejects.toThrow('403')
  expect(raw.form).toHaveBeenCalledTimes(1)
  vi.useRealTimers()
})
it('discards unused initial forms after a write even when the write fails', async () => {
  const { data } = fixture(), raw = { action: vi.fn(async () => { throw new Error('stale') }), form: vi.fn(async () => ({ entity: row('33', 5) })) }
  const client = initialBusinessPage(raw as unknown as ProjectBusinessApi, data).api
  await expect(client.action('submit', '33', 1, 'attempt')).rejects.toThrow('stale')
  expect((await client.form('33')).entity.concurrencyBasis).toBe(5)
})
