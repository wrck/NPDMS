// 统一业务实体默认办理组合式函数：目录详情、受控查询、回执执行都来自统一客户端，
// 不按实体名称写死分支；可执行操作与原因由服务端返回，前端只呈现不替代授权。
import { computed, ref, shallowRef } from 'vue'
import {
  executeEntityOperation,
  getEntityData,
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
  const readEntity = async (id: number, revisionId?: number) => {
    const data = await getEntityData(ownerModule(), entityType(), { id, revisionId })
    if (!data.available) {
      throw new Error(data.unavailableReason || '实体不可用')
    }
    current.value = data
    return data
  }

  const executing = ref(false)
  const execute = async (
    operation: OperationVO,
    entityId: number | undefined,
    input: Record<string, unknown>,
    concurrencyBasis?: number
  ): Promise<BusinessOperationReceipt> => {
    executing.value = true
    try {
      return await executeEntityOperation(ownerModule(), entityType(), operation.code, entityId, {
        idempotencyKey: newIdempotencyKey(),
        concurrencyBasis,
        input
      })
    } finally {
      executing.value = false
    }
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
    readEntity,
    executing,
    execute
  }
}
