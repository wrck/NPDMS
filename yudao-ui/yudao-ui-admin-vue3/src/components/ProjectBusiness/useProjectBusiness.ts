import { computed, ref, shallowRef } from 'vue'
import type { BusinessEntityData, BusinessOperationReceipt, FieldFilter, ModelDetailVO } from '@/api/pms/platform/businessmodel'
import type { BusinessId, BusinessSort, ProjectBusinessApi } from '@/api/pms/platform/business'
import { businessBindingSelectors, businessBindingFilters, requireBusinessBinding, type BusinessBindingParameters } from './businessBinding'
import { businessIntentStorageKey, readBusinessIntent, writeBusinessIntent, clearBusinessIntent,
  businessIntentFingerprint, type PendingBusinessIntent } from '../BusinessEntity/businessOperationIntent'

const message = (error: any, fallback: string) => error?.response?.data?.msg || error?.message || fallback
export function useProjectBusiness(api: () => ProjectBusinessApi, projectId: () => BusinessId | undefined = () => undefined,configurationName:()=>string|undefined=()=>undefined,
  bindingParameters: () => BusinessBindingParameters = () => ({})) {
  const model = shallowRef<ModelDetailVO>(), current = shallowRef<BusinessEntityData>()
  const rows = shallowRef<BusinessEntityData[]>([]), total = ref(0), page = ref(1), filters = ref<FieldFilter[]>([])
  const sorts = ref<BusinessSort[]>([])
  const error = ref(''), loading = ref(false), executing = ref(false), receipt = shallowRef<BusinessOperationReceipt>()
  const pending = shallowRef<PendingBusinessIntent>()
  let retryableIntent: { storageKey: string; intent: PendingBusinessIntent } | undefined
  let generation = 0, detailGeneration = 0
  const storageKey = () => businessIntentStorageKey('DIRECT_BUSINESS_API', api().base)
  const operation = (kind: string) => model.value?.operations.find(item => item.kind === kind)
  const readableFields = computed(() => model.value?.fields.filter(field => field.readable) || [])
  const writableFields = computed(() => model.value?.fields.filter(field => field.writable) || [])
  const selectors = () => businessBindingSelectors(bindingParameters(), model.value?.fields || [])
  const queryPage: ProjectBusinessApi['page'] = (pageNo, pageSize, search, ordering, requestedProject) => {
    const scope = projectId() ?? requestedProject, configuration = configurationName()
    const scopedFilters = [...search, ...businessBindingFilters(selectors())]
    return configuration != null ? api().page(pageNo, pageSize, scopedFilters, ordering, scope, configuration)
      : scope == null ? api().page(pageNo, pageSize, scopedFilters, ordering) : api().page(pageNo, pageSize, scopedFilters, ordering, scope)
  }
  const loadPage = async (reset = true, search?: FieldFilter[], ordering?: BusinessSort[], requestedPage?: number) => {
    if (loading.value && !reset) return
    if (ordering) sorts.value = ordering.map(sort => ({ ...sort }))
    if (search) filters.value = search.map(filter => ({ ...filter, values: [...(filter.values || [])] }))
    const active = ++generation, client = api(), next = reset ? requestedPage ?? 1 : page.value + 1
    loading.value = true; error.value = ''
    try {
      const result = await queryPage(next, 20, filters.value, sorts.value)
      if (active !== generation || client.base !== api().base) return
      rows.value = reset ? result.list : [...rows.value, ...result.list]; total.value = result.total; page.value = next
    } catch (failure) { if (active === generation) error.value = message(failure, '业务列表读取失败') }
    finally { if (active === generation) loading.value = false }
  }
  const load = async (includePage = true) => {
    const client = api(), active = ++detailGeneration
    retryableIntent = undefined
    ++generation; current.value = undefined; rows.value = []; model.value = undefined; receipt.value = undefined; filters.value = []; sorts.value = []; error.value = ''
    try {
      const detail = await (configurationName()==null?client.model():client.model(configurationName()))
      if (active !== detailGeneration || client.base !== api().base) return
      model.value = detail; pending.value = readBusinessIntent(storageKey()); if (includePage) await loadPage(true)
    } catch (failure) { if (active === detailGeneration) error.value = message(failure, '业务模型读取失败') }
  }
  const open = async (id: BusinessId) => {
    const client = api(), active = ++detailGeneration; error.value = ''
    try {
      const data = await client.get(id)
      if (active !== detailGeneration || client.base !== api().base) return false
      if (!data.available || String(data.ref.entityId) !== String(id)) throw new Error('业务读取身份不匹配或已不可用')
      if(projectId()!=null && String(data.fieldValues.projectId)!==String(projectId()))throw new Error('业务记录不属于当前项目')
      requireBusinessBinding(data.fieldValues, selectors())
      current.value = data; return true
    } catch (failure) { if (active === detailGeneration) error.value = message(failure, '业务读取失败'); return false }
  }
  const applyReceipt = async (result: BusinessOperationReceipt) => {
    receipt.value = result
    if (result.outcome === 'DELETED') current.value = undefined
    else if (result.outcome !== 'FAILED' && result.entityRef) {
      // Preserve committed identity if the follow-up read fails; never offer another create as a retry.
      current.value = { ref: result.entityRef, concurrencyBasis: result.newConcurrencyBasis,
        fieldValues: {}, available: false, unavailableReason: '操作已成功，详情读取尚未完成' }
      await open(result.entityRef.entityId)
    }
    await loadPage(true)
  }
  const execute = async (action: string, values: Record<string, unknown> = {}, idempotencyKey?: string) => {
    if (executing.value) return
    const client = api(), target = current.value, key = storageKey()
    if (!key) { error.value = '登录上下文不可用，不能安全保存操作回执'; return }
    const allowed = ['create','save','delete'].includes(action) ? operation(action === 'create' ? 'CREATE' : action === 'save' ? 'UPDATE' : 'DELETE') : model.value?.operations.find(item=>item.code===action && item.kind==='DOMAIN_COMMAND')
    if (!allowed?.executable || action !== 'create' && (!target || target.concurrencyBasis == null)) { error.value = allowed?.reason || '当前操作不可执行'; return }
    executing.value = true; error.value = ''
    let attempt: PendingBusinessIntent | undefined, previous = false
    try {
      if (action === 'create') {
        const scope = selectors()
        if (Object.keys(scope).some(code => !model.value?.fields.some(field => field.code === code && field.writable)))
          throw new Error('当前绑定含服务端只读条件，请关联符合条件的已有业务')
        requireBusinessBinding(values, scope)
      }
      const fingerprint = await businessIntentFingerprint({ base: client.base, action, values,
        id: action === 'create' ? undefined : String(target!.ref.entityId), version: action === 'create' ? undefined : target!.concurrencyBasis })
      const stored = readBusinessIntent(key); previous = !!stored
      const entityId = action === 'create' ? undefined : String(target!.ref.entityId)
      const canCorrect = stored && retryableIntent?.storageKey === key
        && retryableIntent.intent.key === stored.key && retryableIntent.intent.fingerprint === stored.fingerprint
        && stored.operation === action && stored.entityId === entityId
      if (stored && stored.fingerprint !== fingerprint && !canCorrect) throw new Error('上次操作结果仍未知，请先确认回执')
      // Retain the key even after an empty lookup: a late original commit must conflict, never duplicate.
      attempt = { key: stored?.key ?? idempotencyKey ?? crypto.randomUUID(), fingerprint, operation: action, operationVersion: 1,
        entityId, concurrencyBasis: action === 'create' ? undefined : target!.concurrencyBasis }
      retryableIntent = undefined
      writeBusinessIntent(key, attempt); pending.value = attempt
      const result = action === 'create' ? await client.create(values, attempt.key)
        : action === 'save' ? await client.update(target!.ref.entityId, values, target!.concurrencyBasis!, attempt.key)
          : action === 'delete' ? await client.remove(target!.ref.entityId, target!.concurrencyBasis!, attempt.key)
            : await client.action(action,target!.ref.entityId,target!.concurrencyBasis!,attempt.key,values)
      clearBusinessIntent(key, attempt)
      if (client.base === api().base) { pending.value = readBusinessIntent(key); await applyReceipt(result) }
      return result
    } catch (failure: any) {
      const status = failure?.response?.status ?? failure?.status, code = failure?.response?.data?.code ?? failure?.data?.code
      if (attempt && !previous && (failure === 'error' || (status >= 400 && status < 500 && status !== 408) || (code != null && Number(code) !== 500))) clearBusinessIntent(key, attempt)
      if (client.base === api().base) { pending.value = readBusinessIntent(key); error.value = message(failure, '业务操作失败，请先确认执行结果再重试') }
    } finally { executing.value = false }
  }
  const recover = async () => {
    if (executing.value) return
    const client = api(), key = storageKey(), intent = readBusinessIntent(key)
    if (!key || !intent) return
    retryableIntent = undefined
    executing.value = true; error.value = ''
    try {
      const result = await client.receipt(intent.operation, intent.key)
      if (!result) {
        if (key === storageKey()) {
          retryableIntent = { storageKey: key, intent }
          error.value = '尚无已完成回执，确认表单后可再次保存；系统会防止重复提交'
        }
        return
      }
      clearBusinessIntent(key, intent)
      if (client.base === api().base) { pending.value = readBusinessIntent(key); await applyReceipt(result) }
      return result
    } catch (failure) { if (client.base === api().base) error.value = message(failure, '回执查询失败') }
    finally { executing.value = false }
  }
  return { model, current, rows, total, page, filters, sorts, error, loading, executing, receipt, pending,
    readableFields, writableFields, selectors, queryPage, operation, load, loadPage, open, execute, recover }
}
