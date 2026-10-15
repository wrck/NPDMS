import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { inspectOperationCapabilities, type CapabilityQuery } from '@/api/pms/project/execution-operations'
import { resolvedOperationCapabilities } from './operationCapabilityPrefetch'

const actor = vi.hoisted(() => ({ user: 42, tenant: 7, visit: undefined as number | undefined }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => actor.user, getTenantId: () => actor.tenant, getVisitTenantId: () => actor.visit }))
vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
const query: CapabilityQuery = { projectId: 1, nodeKind: 'TASK', nodeId: 2, objectId: 3 }
beforeEach(() => {
  vi.clearAllMocks()
  actor.user = 42; actor.tenant = 7; actor.visit = undefined
  vi.spyOn(Date, 'now').mockReturnValue(1000)
  vi.mocked(inspectOperationCapabilities).mockResolvedValue({ actions: [] } as never)
})
afterEach(() => vi.restoreAllMocks())

it('hands off resolved capabilities once with lossless string or numeric identities', async () => {
  const offered = resolvedOperationCapabilities(query, { actions: [] } as never)
  const response = offered.take({ projectId: '1', nodeKind: 'TASK', nodeId: '2', objectId: '3' })
  expect(await response).toEqual({ actions: [] })
  expect(offered.take(query)).toBeUndefined()
  expect(inspectOperationCapabilities).not.toHaveBeenCalled()
  await response
})
it.each<Partial<CapabilityQuery>>([
  { projectId: 9 }, { nodeKind: 'STAGE' }, { nodeId: 9 }, { objectId: 9 },
  { objectId: undefined }, { expected: { task: null, stage: null } }
])('discards an observation offered to a different execution query %j', patch => {
  const offered = resolvedOperationCapabilities(query, { actions: [] } as never)
  expect(offered.take({ ...query, ...patch })).toBeUndefined()
  expect(offered.take(query)).toBeUndefined()
})
it.each(['user', 'tenant', 'visit'] as const)('does not hand an observation to a changed %s', field => {
  const offered = resolvedOperationCapabilities(query, { actions: [] } as never)
  actor[field] = 9
  expect(offered.take(query)).toBeUndefined()
})
it('discards an observation when its opening has waited too long', () => {
  const offered = resolvedOperationCapabilities(query, { actions: [] } as never)
  vi.mocked(Date.now).mockReturnValue(5000)
  expect(offered.take(query)).toBeUndefined()
})
