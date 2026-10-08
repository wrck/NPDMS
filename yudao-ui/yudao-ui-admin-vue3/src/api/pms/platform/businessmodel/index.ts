// 统一业务模型公共客户端：目录、详情、受控查询与操作执行全部由目录数据驱动，
// 不按实体名称写死任何调用分支；前端禁用状态不代替后端授权。
import request from '@/config/axios'

const baseUrl = '/api/v1/pms/business-models'

export interface ModelSummaryVO {
  ownerModule: string
  entityType: string
  stableCode: string
  title: string
  viewCode?: string
}

export type FieldType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'TEXT_LIST' | 'OBJECT_LIST'

export interface FieldVO {
  displayOrder?: number
  listVisible?: boolean
  searchable?: boolean
  sortable?: boolean
  code: string
  name: string
  type: FieldType
  required: boolean
  readable: boolean
  writable: boolean
}

export interface OperationVO {
  code: string
  name: string
  version: number
  kind: string
  executable: boolean
  reason?: string
}

export interface CapabilityVO {
  type: 'DELIVERY' | 'APPROVAL' | 'CONTENT_HISTORY' | 'DYNAMIC_FORM'
  configRef?: string
  enabled: boolean
}

export interface ExecutionBackendCapabilityVO {
  backendId: string
  supportedSemantics: string[]
  unsupportedSemantics: string[]
  supportsRecovery: boolean
  supportsInFlightMigration: boolean
}

export interface ModelDetailVO {
  ownerModule: string
  entityType: string
  stableCode: string
  title: string
  viewCode?: string
  fields: FieldVO[]
  operations: OperationVO[]
  capabilities: CapabilityVO[]
}

export type FilterOperator = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN'

export interface FieldFilter {
  fieldCode: string
  operator: FilterOperator
  values: (string | number | boolean | null)[]
}

export interface EntityRef {
  tenantId: number
  ownerModule: string
  entityType: string
  entityId: number
}

export interface BusinessEntityData {
  ref: EntityRef
  revisionId?: number
  fieldValues: Record<string, unknown>
  concurrencyBasis?: number
  available: boolean
  unavailableReason?: string
}

export interface BusinessEntitySlice {
  members: BusinessEntityData[]
  nextCursor?: string
  completeness: 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE'
  unavailableReason?: string
}

export type ReceiptOutcome = 'DELETED' | 'SAVED' | 'ACCEPTED' | 'APPROVAL_PENDING' | 'EFFECTED' | 'FAILED'

export interface ResultReference {
  kind: 'FILE' | 'APPROVAL' | 'RESULT' | 'COMMAND'
  ownerModule: string
  value: string
}

export interface BusinessOperationReceipt {
  outcome: ReceiptOutcome
  entityRef?: EntityRef
  newConcurrencyBasis?: number
  references?: ResultReference[]
  recoveryState?: string
  failureReason?: string
}

export interface OperationExecuteRequest {
  idempotencyKey: string
  concurrencyBasis?: number
  revisionId?: string | number
  entryKind?: 'INDEPENDENT' | 'PROJECT_NODE' | 'IMPORT' | 'BATCH' | 'SYSTEM' | 'PUBLIC_LINK'
  entryCorrelationId?: string
  input: Record<string, unknown>
}

export const getModelCatalog = () =>
  request.get<ModelSummaryVO[]>({ url: baseUrl, silentError: true })

export const getModelDetail = (ownerModule: string, entityType: string) =>
  request.get<ModelDetailVO>({
    url: `${baseUrl}/${ownerModule}/${entityType}`,
    silentError: true
  })

export const getExecutionCapabilities = () =>
  request.get<ExecutionBackendCapabilityVO[]>({ url: `${baseUrl}/execution-capabilities`, silentError: true })

export const getEntityPage = (
  ownerModule: string,
  entityType: string,
  data: { sceneCode?: string; filters?: FieldFilter[]; pageSize: number; cursor?: string }
) =>
  request.post<BusinessEntitySlice>({
    url: `${baseUrl}/${ownerModule}/${entityType}/page`,
    data,
    silentError: true
  })

export const getEntityData = (
  ownerModule: string,
  entityType: string,
  params: { id: string | number; revisionId?: string | number }
) =>
  request.get<BusinessEntityData>({
    url: `${baseUrl}/${ownerModule}/${entityType}/data`,
    params,
    silentError: true
  })

export const executeEntityOperation = (
  ownerModule: string,
  entityType: string,
  operationCode: string,
  entityId: string | number | undefined,
  data: OperationExecuteRequest
) =>
  request.post<BusinessOperationReceipt>({
    url: `${baseUrl}/${ownerModule}/${entityType}/operations/${operationCode}`,
    params: entityId == null ? undefined : { entityId },
    data,
    // 契约结果（并发冲突、权限拒绝等）由办理页按回执与原因呈现，不重复弹全局错误提示。
    silentError: true
  })

export const recoverEntityOperation = (ownerModule: string, entityType: string, operationCode: string,
  operationVersion: number, idempotencyKey: string) =>
  request.get<BusinessOperationReceipt | null>({
    url: `${baseUrl}/${ownerModule}/${entityType}/operations/${operationCode}/receipt`,
    params: { operationVersion, idempotencyKey }, silentError: true
  })

export const newIdempotencyKey = () => crypto.randomUUID()


export interface BusinessEntityFormData {
  context?: Record<string, unknown>
  layout?: {
    binding: { formRevisionId: string | number; extensionDefinitionRevisionId?: string | number; fieldBindings: Record<string, string>; version: number }
    templateId?: string | number
    revisionNo?: number
    formVersion?: number
    formConfJson: string
    formRulesJson: string
  }
  extensions: { definitionRevisionId?: string | number; fields: Record<string, unknown>; version: number }
  definitions: Array<{ code: string; label: string; type: FieldType; required: boolean; maxLength?: number; allowedValues?: string[] }>
}
export const getEntityForm = (ownerModule: string, entityType: string, entityId: string | number) =>
  request.get<BusinessEntityFormData>({ url: `${baseUrl}/${ownerModule}/${entityType}/form`, params: { entityId }, silentError: true })
