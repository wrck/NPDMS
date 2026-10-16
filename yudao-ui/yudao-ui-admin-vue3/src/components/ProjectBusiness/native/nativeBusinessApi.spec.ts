import { expect, it, vi } from 'vitest'
import { shallowRef } from 'vue'
import type { NativeBusinessBridge } from './nativeBusinessBridge'
import { nativeProcurementApi } from './nativeProcurementApi'
import { nativeRequirementApi } from './nativeRequirementApi'
import { nativeAcceptanceApi } from './nativeAcceptanceApi'
import { nativeStagePlanApi, nativeDurationApi } from './nativePlanApi'
import { nativeContactApi } from './nativeContactApi'

vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => 42, getTenantId: () => 7, getVisitTenantId: () => undefined }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))

const receipt = { entityRef: { tenantId: 7, ownerModule: 'SOL', entityType: 'siteSurvey', entityId: 11 },
  newConcurrencyBasis: 4, outcome: 'SAVED' }
const bridgeFor = (context: Record<string, any> = {}) => ({
  read: vi.fn(async (_id: any) => ({ row: { concurrencyBasis: 3, fieldValues: { projectId: 99 } },
    form: { context, extensions: { fields: { note: 'old' }, version: 2 },
      layout: { binding: { formRevisionId: 8, version: 1 } } }, value: { id: 11, projectId: 99, version: 3 } })),
  update: vi.fn(async (_data: any, _key?: string) => receipt), create: vi.fn(async (_data: any, _key?: string) => 11),
  command: vi.fn(async (..._args: any[]) => ({ receipt })), remove: vi.fn(), restore: vi.fn(),
  state: { receipt: shallowRef(receipt) }, records: new Map()
})
const asBridge = (value: ReturnType<typeof bridgeFor>) => value as unknown as NativeBusinessBridge

it('keeps exchange serial snapshots and extensions together and excludes a user-selected audit identity', async () => {
  const bridge = bridgeFor(), api = nativeProcurementApi('exchange', asBridge(bridge))! as {
    updateMaterialExchange: (data: Record<string, unknown>) => Promise<unknown>
    approveMaterialExchange: (data: Record<string, unknown>) => Promise<unknown>
  }
  await api.updateMaterialExchange({ id: 11, version: 3, serials: [{ deviceId: '2102228462045130754', quantity: 2 }],
    $extensions: { expectedVersion: 2, values: { note: 'new' } } })
  expect(bridge.update.mock.calls[0][0]).toMatchObject({ version: 3,
    $business: { serials: [{ deviceId: '2102228462045130754', quantity: 2 }] },
    $extensions: { expectedVersion: 2, values: { note: 'new' } } })
  await api.approveMaterialExchange({ id: 11, version: 3, approveAction: 'PASS', approveOpinion: 'ok', approverUserId: 123 })
  expect(bridge.command).toHaveBeenLastCalledWith('approve', 11, { action: 'PASS', opinion: 'ok' }, 3)
})

it('rejects a mismatched native requirement revision after a successful command instead of showing another revision', async () => {
  const bridge = bridgeFor(), read = vi.fn(async () => ({ revision: { ref: { revisionId: 12 }, version: 4 } }))
  const api = nativeRequirementApi(asBridge(bridge), { read } as any)
  await expect(api.complete({ ref: { revisionId: 11 }, version: 3 } as any, 'finish')).rejects.toThrow('版本已变化')
  expect(read).toHaveBeenCalledWith(11)
  expect(bridge.command).toHaveBeenCalledWith('complete', 11, {}, 3, 'finish')
})

it('checks plan snapshots against the concurrency basis instead of accepting a mixed-version history view', async () => {
  const bridge = bridgeFor({ plan: { id: 11, version: 2 }, duration: { planId: 11, planVersion: 2 } })
  await expect(nativeStagePlanApi(asBridge(bridge)).getStagePlanBatch(11)).rejects.toThrow('计划版本已变化')
  await expect(nativeDurationApi(asBridge(bridge), {} as any).getPlan(11)).rejects.toThrow('工期版本已变化')
})

it('publishes the selected acceptance report using the activity version and the previous report identity', async () => {
  const bridge = bridgeFor(), getReportVersions = vi.fn(async () => [{ id: 71, reportVersionNo: 2, reportStatus: 'PUBLISHED' },
    { id: 70, reportVersionNo: 1, reportStatus: 'PUBLISHED' }])
  const api = nativeAcceptanceApi(asBridge(bridge), { getReportVersions } as any)
  const result = await api.publishVersion({ id: 11, version: 3, currentReportVersionId: 70 } as any, { id: 71, reportVersionNo: 2 }, 'publish')
  expect(bridge.command).toHaveBeenCalledWith('report-publish', 11, { reportId: 71, reportNo: 2, expectedCurrent: 70 }, 3, 'publish')
  expect(result).toMatchObject({ reportVersionId: 71, reportVersionNo: 2, activityVersion: 4 })
})

it('keeps the project version and source-contact identity when adding an existing customer contact', async () => {
  const bridge = bridgeFor(), api = nativeContactApi(asBridge(bridge))
  await api.createProjectContact(99, { sourceContactId: 25, expectedProjectVersion: 6, name: '联系人' }, 'contact-create')
  expect(bridge.create).toHaveBeenCalledWith(expect.objectContaining({ projectId: 99,
    $business: { sourceContactId: 25, expectedProjectVersion: 6 } }), 'contact-create')
  await api.deleteProjectContact(99, { id: 11, version: 3 }, 6, true)
  expect(bridge.remove).toHaveBeenCalledWith(11, 3, undefined, { confirmNoPrimary: true })
})
