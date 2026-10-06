// 统一业务实体默认办理组合式函数：目录详情、受控查询、回执执行都来自统一客户端，
// 不按实体名称写死分支；可执行操作与原因由服务端返回，前端只呈现不替代授权。
import { computed, ref, shallowRef } from 'vue'
import {
  executeEntityOperation,
  recoverEntityOperation,
  getEntityData,
  getEntityForm,
  type BusinessEntityFormData,
  getEntityPage,
  getModelDetail,
  newIdempotencyKey,
  type BusinessEntityData,
  type BusinessEntitySlice,
  type BusinessOperationReceipt,
  type FieldFilter,
  type FieldVO,
  type ModelDetailVO,
  type OperationVO
} from '@/api/pms/platform/businessmodel'
import { businessIntentStorageKey, readBusinessIntent, writeBusinessIntent, clearBusinessIntent,
  businessIntentFingerprint, type PendingBusinessIntent } from './businessOperationIntent'


export const isConcurrencyConflict = (error: any) => {
  const status = error?.response?.status ?? error?.status
  const code = String(
    error?.response?.data?.code ?? error?.data?.code ?? error?.code ?? ''
  )
  // 服务端契约映射：1_010_006_001 并发依据过期、002 幂等摘要冲突、004 操作版本冲突。
  return (
    status === 409 ||
    ['1010006001', '1010006002', '1010006004'].includes(code) ||
    /CONCURRENCY_CONFLICT|OPTIMISTIC_LOCK|STALE_VERSION/.test(code)
  )
}

// 契约失败优先呈现服务端原因（如权限不足、能力不可用），axios 通用文案只是兜底。
export const serverErrorMessage = (error: any, fallback: string) =>
  error?.response?.data?.msg || error?.message || fallback

export function useBusinessEntity(ownerModule: () => string, entityType: () => string) {
  const detail = shallowRef<ModelDetailVO>()
  const loadError = ref('')
  const loading = ref(false)

  const loadDetail = async () => {
    loading.value = true
    loadError.value = ''
    try {
      detail.value = await getModelDetail(ownerModule(), entityType())
      pendingIntent.value = readBusinessIntent(businessIntentStorageKey(ownerModule(), entityType()))
    } catch (error: any) {
      loadError.value = serverErrorMessage(error, '统一模型目录加载失败')
    } finally {
      loading.value = false
    }
  }

  const writableFields = computed<FieldVO[]>(() =>
    (detail.value?.fields || []).filter((field) => field.writable)
  )
  const readableFields = computed<FieldVO[]>(() =>
    (detail.value?.fields || []).filter((field) => field.readable)
  )

  const operations = computed<OperationVO[]>(() => detail.value?.operations || [])
  const operationOfKind = (kind: string) =>
    operations.value.find((operation) => operation.kind === kind)
  const createOperation = computed(() => operationOfKind('CREATE'))
  const updateOperation = computed(() => operationOfKind('UPDATE'))

  const listLoading = ref(false)
  const listError = ref('')
  const rows = shallowRef<BusinessEntityData[]>([])
  const cursor = ref<string>()
  const sliceComplete = ref(false)

  const loadPage = async (filters?: FieldFilter[], reset = false) => {
    listLoading.value = true
    listError.value = ''
    try {
      const slice: BusinessEntitySlice = await getEntityPage(ownerModule(), entityType(), {
        pageSize: 20,
        filters,
        cursor: reset ? undefined : cursor.value
      })
      if (slice.completeness === 'UNAVAILABLE') {
        listError.value = slice.unavailableReason || '查询暂不可用'
        return
      }
      rows.value = reset ? slice.members : [...rows.value, ...slice.members]
      cursor.value = slice.nextCursor
      sliceComplete.value = slice.completeness === 'COMPLETE' || !slice.nextCursor
    } catch (error: any) {
      listError.value = serverErrorMessage(error, '实体查询失败')
    } finally {
      listLoading.value = false
    }
  }

  const current = shallowRef<BusinessEntityData>()
  const formPresentation = shallowRef<BusinessEntityFormData>()
  const readEntity = async (id: string | number, revisionId?: string | number) => {
    const data = await getEntityData(ownerModule(), entityType(), { id, revisionId })
    if (!data.available) {
      throw new Error(data.unavailableReason || '实体不可用')
    }
    const form = !revisionId && detail.value?.capabilities.some(item => item.type === 'DYNAMIC_FORM' && item.enabled)
      ? await getEntityForm(ownerModule(), entityType(), id) : undefined
    formPresentation.value = form
    current.value = data
    return data
  }

  const executing = ref(false)
  const pendingIntent = shallowRef<PendingBusinessIntent>()
  const recover = async (): Promise<BusinessOperationReceipt | null> => {
    if (executing.value) throw new Error('操作正在执行')
    const storageKey = businessIntentStorageKey(ownerModule(), entityType())
    const pending = readBusinessIntent(storageKey)
    pendingIntent.value = pending
    if (!pending || !storageKey) return null
    executing.value = true
    try {
      const receipt = await recoverEntityOperation(ownerModule(), entityType(), pending.operation, pending.operationVersion, pending.key)
      if (receipt) {
        clearBusinessIntent(storageKey, pending)
        pendingIntent.value = readBusinessIntent(storageKey)
      }
      return receipt
    } finally { executing.value = false }
  }
  const execute = async (
    operation: OperationVO,
    entityId: string | number | undefined,
    input: Record<string, unknown>,
    concurrencyBasis?: number,
    revisionId?: string | number
  ): Promise<BusinessOperationReceipt> => {
    if (executing.value) throw new Error('操作正在执行')
    const storageKey = businessIntentStorageKey(ownerModule(), entityType())
    if (!storageKey) throw new Error('登录上下文不可用，无法保存操作恢复信息')
    executing.value = true
    let alreadyUnknown = false
    let attempt: PendingBusinessIntent | undefined
    try {
      const fingerprint = await businessIntentFingerprint({ ownerModule: ownerModule(), entityType: entityType(),
        operation: operation.code, operationVersion: operation.version, entityId: entityId == null ? undefined : String(entityId), input,
        concurrencyBasis, revisionId: revisionId == null ? undefined : String(revisionId) })
      // Re-read after hashing, so two mounted callers share an existing intent rather than mint two keys.
      const stored = readBusinessIntent(storageKey)
      alreadyUnknown = !!stored
      if (stored && stored.fingerprint !== fingerprint) throw new Error('上次操作结果仍未确定，请先确认原操作')
      const pending = stored || { fingerprint, key: newIdempotencyKey(), operation: operation.code,
        operationVersion: operation.version, entityId: entityId == null ? undefined : String(entityId), concurrencyBasis,
        revisionId: revisionId == null ? undefined : String(revisionId) }
      attempt = pending
      writeBusinessIntent(storageKey, pending)
      pendingIntent.value = pending
      const receipt = await executeEntityOperation(ownerModule(), entityType(), operation.code, entityId, {
        idempotencyKey: pending.key, concurrencyBasis, revisionId, input
      })
      clearBusinessIntent(storageKey, pending)
      pendingIntent.value = readBusinessIntent(storageKey)
      return receipt
    } catch (error: any) {
      const status = error?.response?.status ?? error?.status
      const code = error?.response?.data?.code ?? error?.data?.code
      // Denial on a retry cannot prove whether an earlier unknown request committed.
      if (attempt && !alreadyUnknown && (error === 'error' || (status >= 400 && status < 500 && status !== 408) || (code != null && Number(code) !== 500))) {
        clearBusinessIntent(storageKey, attempt)
        pendingIntent.value = readBusinessIntent(storageKey)
      }
      throw error
    } finally { executing.value = false }
  }

  return {
    detail,
    loading,
    loadError,
    loadDetail,
    writableFields,
    readableFields,
    createOperation,
    updateOperation,
    listLoading,
    listError,
    rows,
    sliceComplete,
    loadPage,
    current,
    formPresentation,
    readEntity,
    executing,
    pendingIntent,
    recover,
    execute
  }
}
