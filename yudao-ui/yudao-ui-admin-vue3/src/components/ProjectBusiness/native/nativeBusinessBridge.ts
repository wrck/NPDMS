import dayjs from 'dayjs'
import { ref } from 'vue'
import type { BusinessEntityData, BusinessEntityFormData, FieldFilter } from '@/api/pms/platform/businessmodel'
import type { BusinessId, ProjectBusinessApi } from '@/api/pms/platform/business'
import { useProjectBusiness } from '../useProjectBusiness'
import { getTenantId, getVisitTenantId } from '@/utils/auth'
import type { NativeValues } from './nativeBusinessContext'
import { hasBusinessBindingSelectors, requireBusinessBinding, type BusinessBindingParameters } from '../businessBinding'

export class NativeBusinessOperationFailure extends Error {
  constructor(message: string, readonly uncertain: boolean) { super(message) }
}

export const nativeId = (value: BusinessId): BusinessId => {
  const numeric = Number(value)
  return Number.isSafeInteger(numeric) && String(numeric) === String(value) ? numeric : value
}

/** Translates the dedicated pages' DTOs to the inherited API; state and receipts stay shared. */
export function createNativeBusinessBridge(api: ProjectBusinessApi, projectId: () => BusinessId | undefined,
  can: (action: string) => boolean, bindingParameters: () => BusinessBindingParameters = () => ({})) {
  let domainResult: unknown
  let createTransport: ProjectBusinessApi['create'] | undefined
  let deletionContext: NativeValues | undefined
  const client: ProjectBusinessApi = { ...api,
    create: (values, key) => (createTransport ?? api.create)(values, key),
    remove: (id, version, key) => deletionContext
      ? api.action(Object.hasOwn(deletionContext, 'confirmNoPrimary') ? 'delete-confirmed' : 'delete-with-context', id, version, key, deletionContext) : api.remove(id, version, key),
    action: async (...args) => {
    const result: any = await api.action(...args)
    domainResult = result
    return result.receipt ?? result
  } }
  const configurationName = () => typeof bindingParameters().configurationName === 'string' ? bindingParameters().configurationName as string : undefined
  const state = useProjectBusiness(() => client, projectId, configurationName, bindingParameters)
  const preparing = ref(false)
  const write = async <T>(operation: () => Promise<T>): Promise<T> => {
    if (preparing.value || state.executing.value) throw new NativeBusinessOperationFailure('当前操作尚未完成，请稍后再试', false)
    preparing.value = true
    try { return await operation() } finally { preparing.value = false }
  }
  const records = new Map<string, BusinessEntityData>()
  const forms = new Map<string, BusinessEntityFormData>()
  const formVersions = new Map<string, number | undefined>()
  let initialization: Promise<void> | undefined
  const ready = async () => {
    initialization ??= state.load(false)
    await initialization
    if (!state.model.value) { initialization = undefined; throw new Error(state.error.value || '业务模型读取失败') }
  }
  const remember = (row: BusinessEntityData) => {
    if (projectId() != null && String(row.fieldValues.projectId) !== String(projectId())) throw new Error('业务记录不属于当前项目')
    requireBusinessBinding(row.fieldValues, state.selectors())
    records.set(String(row.ref.entityId), row)
    return row
  }
  const dto = (row: BusinessEntityData, form?: BusinessEntityFormData): NativeValues => {
    const values: NativeValues = { ...form?.context, ...row.fieldValues, id: nativeId(row.ref.entityId), version: row.concurrencyBasis }
    for (const field of state.model.value?.fields || []) {
      const value = values[field.code]
      if (value == null) continue
      if (field.type === 'DATETIME') values[field.code] = typeof value === 'number' ? value : dayjs(value).valueOf()
      if (field.type === 'NUMBER' && /(^id$|Id$)/.test(field.code)) values[field.code] = nativeId(value)
    }
    return values
  }
  const read = async (id: BusinessId) => {
    const [, form] = await Promise.all([ready(), api.form(id)])
    const row = form.entity
    if (!row?.available || String(row.ref.entityId) !== String(id)) throw new Error('业务记录已不可用')
    remember(row); forms.set(String(id), form); formVersions.set(String(id), row.concurrencyBasis)
    return { row, form, value: dto(row, form) }
  }
  // The dedicated editor has just read this exact version. Share that presentation with its support panel.
  const readPresentation = async (id: BusinessId, version?: number) => {
    const row = records.get(String(id)), form = forms.get(String(id))
    if (version != null && row?.concurrencyBasis === version && formVersions.get(String(id)) === version && form)
      return { row, form, value: dto(row, form) }
    return read(id)
  }
  const reloadModel = async () => { state.model.value = await api.model(configurationName()) }
  const page = async (query: NativeValues = {}, aliases: Record<string, string> = {}) => {
    const search = Object.entries(query).filter(([key, value]) =>
      value != null && value !== '' && !['pageNo', 'pageSize', 'projectId'].includes(key))
    const scope = projectId() ?? (query.projectId === '' ? undefined : query.projectId)
    const loadPage = (filters: FieldFilter[]) => state.queryPage(query.pageNo || 1, query.pageSize || 10, filters, undefined, scope)
    // An unfiltered list has no field metadata dependency. Both endpoints still authorize
    // the request; expose records only after the model and scoped page have succeeded.
    if (!search.length && !hasBusinessBindingSelectors(bindingParameters())) {
      const [, result] = await Promise.all([ready(), loadPage([])])
      return { list: result.list.map(row => dto(remember(row))), total: result.total }
    }
    await ready()
    const filters: FieldFilter[] = []
    for (const [key, value] of search) {
      const code = aliases[key] || key
      const field = state.model.value!.fields.find(item => item.code === code && item.searchable)
      if (field) filters.push({ fieldCode: code, operator: ['name', 'code', 'title', 'productName'].includes(code) ? 'LIKE' : 'EQ', values: [value] })
    }
    const result = await loadPage(filters)
    return { list: result.list.map(row => dto(remember(row))), total: result.total }
  }
  const input = (data: NativeValues, previous?: BusinessEntityData) => {
    const result: NativeValues = {}
    for (const field of state.model.value!.fields.filter(item => item.writable)) {
      if (!Object.hasOwn(data, field.code) || data[field.code] === undefined) continue
      let value = data[field.code]
      if (field.type === 'DATETIME' && value != null && value !== '') value = dayjs(Number.isFinite(Number(value)) ? Number(value) : value).format('YYYY-MM-DD HH:mm:ss')
      const old = previous?.fieldValues[field.code]
      if (previous && (JSON.stringify(value ?? null) === JSON.stringify(old ?? null)
        || field.type === 'NUMBER' && value != null && old != null && String(value) === String(old))) continue
      result[field.code] = value === '' ? null : value
    }
    for (const key of ['$business', '$extensions', '$binding']) if (data[key] != null) result[key] = data[key]
    if (!previous && projectId() != null) result.projectId = projectId()
    if (!previous) {
      const selectors = state.selectors()
      requireBusinessBinding({ ...selectors, ...result }, selectors)
      for (const [code, value] of Object.entries(selectors)) {
        if (state.model.value!.fields.some(field => field.code === code && field.writable)) result[code] = value
      }
    }
    return result
  }
  const target = async (id: BusinessId, version?: number) => {
    const row = records.get(String(id)) ?? (await read(id)).row
    state.current.value = version == null ? row : { ...row, concurrencyBasis: version }
    return row
  }
  const execute = async (action: string, id?: BusinessId, values: NativeValues = {}, version?: number, key?: string) => {
    await ready()
    if (!can(action)) throw new NativeBusinessOperationFailure('当前业务上下文不允许此操作', false)
    if (action === 'create') state.current.value = undefined
    else if (id != null) await target(id, version)
    domainResult = undefined
    const receipt = await state.execute(action, values, key)
    if (!receipt || receipt.outcome === 'FAILED') throw new NativeBusinessOperationFailure(state.error.value || '业务操作未成功，请确认执行结果', !!state.pending.value)
    if (state.current.value?.available) remember(state.current.value)
    return { receipt, result: domainResult }
  }
  const create = (data: NativeValues, key?: string) => write(async () => {
    await ready()
    const values = input(data)
    const formRevisionId = state.model.value?.formRevisionId
    if (formRevisionId != null && values.$binding == null) {
      if (projectId() == null) throw new Error('创建已配置表单需要项目上下文')
      const options = await api.formOptions(projectId()!)
      const layout = options.find(option => String(option.layout.binding.formRevisionId) === String(formRevisionId))?.layout
      if (!layout) throw new Error('命名配置关联的表单版本不可用，请选择可用配置')
      values.$binding = { ...layout.binding, expectedVersion: 0, bindRemainingFields: true }
    }
    const { receipt } = await execute('create', undefined, values, undefined, key)
    if (!receipt.entityRef) throw new Error('已保存，但未返回业务身份，请先确认回执')
    return nativeId(receipt.entityRef.entityId)
  })
  const update = (data: NativeValues, key?: string) => write(async () => {
    await ready()
    const previous = await target(data.id, data.version)
    // Dedicated forms already pin their command. Replays must not change its body after a successful reread.
    return (await execute('save', data.id, input(data, key ? undefined : previous), data.version, key)).receipt
  })
  const remove = (id: BusinessId, version?: number, key?: string, context?: NativeValues) => write(async () => {
    deletionContext = context
    try { return (await execute('delete', id, context ?? {}, version, key)).receipt }
    finally { deletionContext = undefined }
  })
  const command = (action: string, id: BusinessId, values: NativeValues = {}, version?: number, key?: string) =>
    write(() => execute(action, id, values, version, key))
  const restore = (id: BusinessId, version: number, values: NativeValues) => write(async () => {
    await ready()
    const tenantId = Number(getVisitTenantId() || getTenantId())
    if (!Number.isSafeInteger(tenantId) || tenantId <= 0 || projectId() != null && String(projectId()) !== String(values.projectId))
      throw new Error('联系人恢复上下文无效')
    // A deleted row is intentionally unreadable. The authorized history supplies its identity and version;
    // the restore command revalidates them and project management rights on the server.
    records.set(String(id), { ref: { tenantId, ownerModule: state.model.value!.ownerModule,
      entityType: state.model.value!.entityType, entityId: nativeId(id) as number }, concurrencyBasis: version,
      available: false, fieldValues: { projectId: values.projectId } })
    return execute('restore', id, values, version)
  })
  const createBatch = (values: NativeValues, transport: ProjectBusinessApi['create']) => write(async () => {
    createTransport = transport
    try { return await execute('create', undefined, values) }
    finally { createTransport = undefined }
  })
  return { api, state, preparing, records, forms, ready, read, readPresentation, reloadModel, page, dto, input, create, update, remove, command, restore, createBatch }
}
export type NativeBusinessBridge = ReturnType<typeof createNativeBusinessBridge>
