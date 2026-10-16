import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { ProjectBusinessApi } from '@/api/pms/platform/business'
import type { BusinessEntityData } from '@/api/pms/platform/businessmodel'
import { createNativeBusinessBridge } from './nativeBusinessBridge'

vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => 42, getTenantId: () => 7, getVisitTenantId: () => undefined }))
const storage = new Map<string, string>()
let api: ProjectBusinessApi
const row = { ref: { tenantId: 7, ownerModule: 'IMP', entityType: 'arrival', entityId: 11 }, available: true,
  concurrencyBasis: 3, fieldValues: { projectId: 99, equipmentId: '2102228462045130754', quantity: 2, status: 0, arrivalTime: '2026-10-12 10:30:00' } }
const receipt = { outcome: 'SAVED', entityRef: row.ref, newConcurrencyBasis: 4, operationCode: 'save', operationVersion: 1 }
const formValue = (entity: BusinessEntityData = row) => ({ entity, initialValues: {}, extensions: { fields: {}, version: 0 }, definitions: [] })
beforeEach(() => {
  storage.clear(); vi.stubGlobal('sessionStorage', { getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key) })
  api = { base: '/api/v1/pms/arrival-business', model: vi.fn(async () => ({
    fields: ['projectId', 'equipmentId', 'quantity', 'arrivalTime', 'status'].map(code => ({ code, name: code, readable: true,
      writable: code !== 'status', searchable: true, type: code === 'arrivalTime' ? 'DATETIME' : 'NUMBER' })),
    operations: [{ code: 'create', kind: 'CREATE', executable: true }, { code: 'save', kind: 'UPDATE', executable: true },
      { code: 'sign', kind: 'DOMAIN_COMMAND', executable: true }]
  })), page: vi.fn(async () => ({ list: [row], total: 1 })), get: vi.fn(async () => row),
    form: vi.fn(async () => formValue()),
    update: vi.fn(async () => receipt), create: vi.fn(async () => receipt), action: vi.fn(async () => receipt)
  } as unknown as ProjectBusinessApi
})
afterEach(() => vi.unstubAllGlobals())
it('starts an unfiltered scoped page with its model and waits for both before exposing rows', async () => {
  const model = await api.model()
  let release!: (value: typeof model) => void
  vi.mocked(api.model).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const bridge = createNativeBusinessBridge(api, () => 99, () => true,
    () => ({ configurationName: 'project-default', businessViewRevisionId: 17 }))
  let settled = false
  const pending = bridge.page({ projectId: 100, pageSize: 10, status: undefined }).then(value => { settled = true; return value })
  try {
    expect(api.page).toHaveBeenCalledWith(1, 10, [], undefined, 99, 'project-default')
    await Promise.resolve()
    expect(settled).toBe(false)
    expect(bridge.records.size).toBe(0)
  } finally { release(model); await pending }
  expect(bridge.records.size).toBe(1)
})
it.each(['search', 'binding'])('waits for model validation before issuing a %s filtered page', async kind => {
  const model = await api.model()
  let release!: (value: typeof model) => void
  vi.mocked(api.model).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const bridge = createNativeBusinessBridge(api, () => 99, () => true,
    () => kind === 'binding' ? { status: 0 } : {})
  const pending = bridge.page(kind === 'search' ? { status: 0 } : {})
  try { expect(api.page).not.toHaveBeenCalled() }
  finally { release(model); await pending }
  expect(api.page).toHaveBeenCalledWith(1, 10, [{ fieldCode: 'status', operator: 'EQ', values: [0] }], undefined, 99)
})
it('never queries an unscoped page when a frozen selector is unavailable', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => true, () => ({ missingField: 'FINAL' }))
  await expect(bridge.page()).rejects.toThrow('绑定条件不可用')
  expect(api.page).not.toHaveBeenCalled()
})
it.each(['page', 'read'] as const)('does not expose %s results when the concurrent model read is denied', async method => {
  vi.mocked(api.model).mockRejectedValueOnce(new Error('MODEL_FORBIDDEN'))
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  await expect(method === 'page' ? bridge.page() : bridge.read(11)).rejects.toThrow('MODEL_FORBIDDEN')
  expect(bridge.records.size).toBe(0)
  expect(bridge.forms.size).toBe(0)
})
it('starts the combined form read with model loading without exposing an unchecked record or rereading detail', async () => {
  const model = await api.model()
  let release!: (value: typeof model) => void
  vi.mocked(api.model).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  const pending = bridge.read(11)
  try {
    expect(api.get).not.toHaveBeenCalled()
    expect(api.form).toHaveBeenCalledWith(11)
    expect(bridge.records.size).toBe(0)
  } finally { release(model); await pending }
  expect(bridge.records.size).toBe(1)
})
it('uses the frozen selector and named configuration for lists, reads and creation', async () => {
  const model = await api.model()
  vi.mocked(api.model).mockResolvedValue({ ...model, fields: [...model.fields,
    { code: 'acceptanceType', type: 'TEXT', readable: true, writable: true, searchable: true } as any] })
  const selected = { ...row, fieldValues: { ...row.fieldValues, acceptanceType: 'FINAL' } }
  vi.mocked(api.page).mockResolvedValue({ list: [selected], total: 1 })
  vi.mocked(api.get).mockResolvedValue(selected)
  vi.mocked(api.form).mockResolvedValue(formValue(selected))
  const bridge = createNativeBusinessBridge(api, () => 99, () => true,
    () => ({ acceptanceType: 'FINAL', configurationName: 'final-review' }))
  await bridge.page({ pageSize: 100 })
  expect(api.model).toHaveBeenCalledWith('final-review')
  expect(api.page).toHaveBeenLastCalledWith(1, 100,
    [{ fieldCode: 'acceptanceType', operator: 'EQ', values: ['FINAL'] }], undefined, 99, 'final-review')
  await expect(bridge.create({ acceptanceType: 'PRELIMINARY' })).rejects.toThrow('办理范围')
  expect(api.create).not.toHaveBeenCalled()
  await bridge.create({ quantity: 3 })
  expect(api.create).toHaveBeenCalledWith({ quantity: 3, projectId: 99, acceptanceType: 'FINAL' }, expect.any(String))
  vi.mocked(api.get).mockResolvedValue({ ...selected, fieldValues: { ...selected.fieldValues, acceptanceType: 'PRELIMINARY' } })
  vi.mocked(api.form).mockResolvedValue(formValue({ ...selected, fieldValues: { ...selected.fieldValues, acceptanceType: 'PRELIMINARY' } }))
  await expect(bridge.read(11)).rejects.toThrow('办理范围')
})
it('binds the configured published form during inherited creation', async () => {
  const model = await api.model()
  vi.mocked(api.model).mockResolvedValue({ ...model, formRevisionId: '9001' })
  api.formOptions = vi.fn(async () => [{ name: 'Review', layout: { binding: { formRevisionId: '9001', fieldBindings: { amount: 'quantity' }, version: 0 } } }] as any)
  const bridge = createNativeBusinessBridge(api, () => 99, () => true, () => ({ configurationName: 'review' }))
  await bridge.create({ quantity: 3 })
  expect(vi.mocked(api.create).mock.calls[0][0]).toMatchObject({ projectId: 99, quantity: 3,
    $binding: { formRevisionId: '9001', fieldBindings: { amount: 'quantity' }, expectedVersion: 0, bindRemainingFields: true } })
  vi.mocked(api.formOptions).mockResolvedValue([])
  await expect(bridge.create({ quantity: 4 })).rejects.toThrow('表单版本不可用')
  expect(api.create).toHaveBeenCalledTimes(1)
})
it('rejects creation into a server-owned state filter before saving any business row', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => true, () => ({ status: 1 }))
  await expect(bridge.create({ quantity: 3 })).rejects.toThrow('只读条件')
  expect(api.create).not.toHaveBeenCalled()
  expect(storage.size).toBe(0)
})
it('reuses presentation only for the same record version and rereads a newer version', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  const original = await bridge.read(11)
  const cached = await bridge.readPresentation(11, 3)
  expect(cached.form).toBe(original.form)
  expect(api.get).not.toHaveBeenCalled(); expect(api.form).toHaveBeenCalledTimes(1)
  vi.mocked(api.get).mockResolvedValue({ ...row, concurrencyBasis: 4 })
  vi.mocked(api.form).mockResolvedValue({ entity: { ...row, concurrencyBasis: 4 }, initialValues: { quantity: 5 }, extensions: { fields: {}, version: 1 }, definitions: [] } as any)
  const refreshed = await bridge.readPresentation(11, 4)
  expect(api.get).not.toHaveBeenCalled(); expect(api.form).toHaveBeenCalledTimes(2)
  expect(refreshed.form).not.toBe(original.form)
  expect(refreshed.row.concurrencyBasis).toBe(4)
})
it('keeps project scope, dedicated pagination and lossless device IDs while adapting timestamps', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  const result = await bridge.page({ projectId: 100, pageNo: 2, pageSize: 10, status: 0 })
  expect(api.page).toHaveBeenLastCalledWith(2, 10, [{ fieldCode: 'status', operator: 'EQ', values: [0] }], undefined, 99)
  expect(result.list[0].equipmentId).toBe('2102228462045130754')
  expect(Number.isFinite(result.list[0].arrivalTime)).toBe(true)
})
it('preserves the form version and extension patch and excludes read-only status from saves', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  const { value } = await bridge.read(11)
  await bridge.update({ ...value, version: 2, quantity: 5, status: 2, $extensions: { expectedVersion: 1, values: { note: 'draft' } } })
  const call = vi.mocked(api.update).mock.calls[0]
  expect(call[0]).toBe(11); expect(call[2]).toBe(2)
  expect(call[1]).toEqual({ quantity: 5, $extensions: { expectedVersion: 1, values: { note: 'draft' } } })
})
it('retries an unknown save with the same receipt key and does not store business content', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  const { value } = await bridge.read(11)
  vi.mocked(api.update).mockRejectedValueOnce(new Error('Network Error'))
  await expect(bridge.update({ ...value, quantity: 5 })).rejects.toThrow('Network Error')
  const key = vi.mocked(api.update).mock.calls[0][3]
  await bridge.update({ ...value, quantity: 5 })
  expect(vi.mocked(api.update).mock.calls[1][3]).toBe(key)
  expect(storage.size).toBe(0)
})
it('rejects foreign records and read-only context writes before mutation', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => false)
  await expect(bridge.create({ quantity: 5 })).rejects.toThrow('不允许')
  expect(api.create).not.toHaveBeenCalled()
  vi.mocked(api.get).mockResolvedValueOnce({ ...row, fieldValues: { projectId: 100 } })
  vi.mocked(api.form).mockResolvedValueOnce(formValue({ ...row, fieldValues: { ...row.fieldValues, projectId: 100 } }))
  await expect(bridge.read(11)).rejects.toThrow('不属于当前项目')
})

it('locks the batch transport before model loading so a second click cannot replace the first operation', async () => {
  let release!: (value: any) => void
  const model = await api.model()
  vi.mocked(api.model).mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const transport = vi.fn(async () => receipt as any)
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  const first = bridge.createBatch({ values: { quantity: 2 }, equipmentIds: [21, 22] }, transport)
  await expect(bridge.create({ quantity: 9 })).rejects.toThrow('尚未完成')
  release(model)
  await first
  expect(transport).toHaveBeenCalledOnce()
  expect(api.create).not.toHaveBeenCalled()
  expect(bridge.preparing.value).toBe(false)
})

it('keeps a pinned update body unchanged after the server committed but the response was lost', async () => {
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  const values = { id: 11, version: 3, quantity: 5 }
  vi.mocked(api.update).mockRejectedValueOnce(new Error('Network Error'))
  await expect(bridge.update(values, 'pinned-save')).rejects.toThrow('Network Error')
  vi.mocked(api.get).mockResolvedValue({ ...row, concurrencyBasis: 4, fieldValues: { ...row.fieldValues, quantity: 5 } })
  vi.mocked(api.form).mockResolvedValue(formValue({ ...row, concurrencyBasis: 4, fieldValues: { ...row.fieldValues, quantity: 5 } }))
  await bridge.read(11)
  await bridge.update(values, 'pinned-save')
  const calls = vi.mocked(api.update).mock.calls
  expect(calls[1]).toEqual(calls[0])
  expect(calls[1][2]).toBe(3)
})
it.each(['unavailable', 'another identity'])('rejects a combined form containing %s before remembering it', async reason => {
  vi.mocked(api.form).mockResolvedValue(formValue(reason === 'unavailable' ? { ...row, available: false }
    : { ...row, ref: { ...row.ref, entityId: 12 } }))
  const bridge = createNativeBusinessBridge(api, () => 99, () => true)
  await expect(bridge.read(11)).rejects.toThrow('业务记录已不可用')
  expect(bridge.records.size).toBe(0)
  expect(bridge.forms.size).toBe(0)
})
