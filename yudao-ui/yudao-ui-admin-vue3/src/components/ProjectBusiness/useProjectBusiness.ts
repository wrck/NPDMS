import { computed, ref, shallowRef } from 'vue'
import type { BusinessEntityData, BusinessOperationReceipt, FieldFilter, ModelDetailVO } from '@/api/pms/platform/businessmodel'
import type { BusinessId, BusinessSort, ProjectBusinessApi } from '@/api/pms/platform/business'
import { businessIntentStorageKey, readBusinessIntent, writeBusinessIntent, clearBusinessIntent,
  businessIntentFingerprint, type PendingBusinessIntent } from '../BusinessEntity/businessOperationIntent'

const message = (error: any, fallback: string) => error?.response?.data?.msg || error?.message || fallback
export function useProjectBusiness(api: () => ProjectBusinessApi, projectId: () => BusinessId | undefined = () => undefined) {
  const model = shallowRef<ModelDetailVO>(), current = shallowRef<BusinessEntityData>()
  const rows = shallowRef<BusinessEntityData[]>([]), total = ref(0), page = ref(1), filters = ref<FieldFilter[]>([])
  const sorts = ref<BusinessSort[]>([])
  const error = ref(''), loading = ref(false), executing = ref(false), receipt = shallowRef<BusinessOperationReceipt>()
  const pending = shallowRef<PendingBusinessIntent>()
  let generation = 0, detailGeneration = 0
  const storageKey = () => businessIntentStorageKey('DIRECT_BUSINESS_API', api().base)
  const operation = (kind: string) => model.value?.operations.find(item => item.kind === kind)
  const readableFields = computed(() => model.value?.fields.filter(field => field.readable) || [])
  const writableFields = computed(() => model.value?.fields.filter(field => field.writable) || [])
  const loadPage = async (reset = true, search?: FieldFilter[], ordering?: BusinessSort[], requestedPage?: number) => {
    if (loading.value && !reset) return
    if (ordering) sorts.value = ordering.map(sort => ({ ...sort }))
    if (search) filters.value = search.map(filter => ({ ...filter, values: [...(filter.values || [])] }))
    const active = ++generation, client = api(), next = reset ? requestedPage ?? 1 : page.value + 1
    loading.value = true; error.value = ''
    try {
      const scope=projectId()
      const result = await (scope==null?client.page(next,20,filters.value,sorts.value):client.page(next,20,filters.value,sorts.value,scope))
      if (active !== generation || client.base !== api().base) return
      rows.value = reset ? result.list : [...rows.value, ...result.list]; total.value = result.total; page.value = next
    } catch (failure) { if (active === generation) error.value = message(failure, '业务列表读取失败') }
    finally { if (active === generation) loading.value = false }
  }
  const load = async () => {
    const client = api(), active = ++detailGeneration
    ++generation; current.value = undefined; rows.value = []; model.value = undefined; receipt.value = undefined; filters.value = []; sorts.value = []; error.value = ''
    try {
      const detail = await client.model()
      if (active !== detailGeneration || client.base !== api().base) return
      model.value = detail; pending.value = readBusinessIntent(storageKey()); await loadPage(true)
    } catch (failure) { if (active === detailGeneration) error.value = message(failure, '业务模型读取失败') }
  }
  const open = async (id: BusinessId) => {
    const client = api(), active = ++detailGeneration; error.value = ''
    try {
      const data = await client.get(id)
      if (active !== detailGeneration || client.base !== api().base) return false
      if (!data.available || String(data.ref.entityId) !== String(id)) throw new Error('业务读取身份不匹配或已不可用')
      if(projectId()!=null && String(data.fieldValues.projectId)!==String(projectId()))throw new Error('业务记录不属于当前项目')
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
  const execute = async (action: string, values: Record<string, unknown> = {}) => {
    if (executing.value) return
    const client = api(), target = current.value, key = storageKey()
    if (!key) { error.value = '登录上下文不可用，不能安全保存操作回执'; return }
    const allowed = ['create','save','delete'].includes(action) ? operation(action === 'create' ? 'CREATE' : action === 'save' ? 'UPDATE' : 'DELETE') : model.value?.operations.find(item=>item.code===action && item.kind==='DOMAIN_COMMAND')
    if (!allowed?.executable || action !== 'create' && (!target || target.concurrencyBasis == null)) { error.value = allowed?.reason || '当前操作不可执行'; return }
    executing.value = true; error.value = ''
    let attempt: PendingBusinessIntent | undefined, previous = false
    try {
      const fingerprint = await businessIntentFingerprint({ base: client.base, action, values,
        id: action === 'create' ? undefined : String(target!.ref.entityId), version: action === 'create' ? undefined : target!.concurrencyBasis })
      const stored = readBusinessIntent(key); previous = !!stored
      if (stored && stored.fingerprint !== fingerprint) throw new Error('上次操作结果仍未知，请先确认回执')
      attempt = stored || { key: crypto.randomUUID(), fingerprint, operation: action, operationVersion: 1,
        entityId: action === 'create' ? undefined : String(target!.ref.entityId), concurrencyBasis: action === 'create' ? undefined : target!.concurrencyBasis }
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
    executing.value = true; error.value = ''
    try {
      const result = await client.receipt(intent.operation, intent.key)
      if (!result) { error.value = '尚未取得回执，请恢复原输入后使用原请求重试'; return }
      clearBusinessIntent(key, intent)
      if (client.base === api().base) { pending.value = readBusinessIntent(key); await applyReceipt(result) }
      return result
    } catch (failure) { if (client.base === api().base) error.value = message(failure, '回执查询失败') }
    finally { executing.value = false }
  }
  return { model, current, rows, total, page, filters, sorts, error, loading, executing, receipt, pending,
    readableFields, writableFields, operation, load, loadPage, open, execute, recover }
}
