import request from '@/config/axios'

export type CustomerSourceType = 'CRM_SYNC' | 'PLATFORM_CREATED' | 'PLATFORM_TEMPORARY'
export type CustomerLifecycleStatus = 'ENABLED' | 'DISABLED' | 'DELETED'

export interface CustomerPageReqVO extends PageParam {
  code?: string
  name?: string
  departmentCode?: string
  marketCode?: string
  systemCode?: string
  expendCode?: string
  industryCode?: string
  lifecycleStatus?: CustomerLifecycleStatus
  sourceType?: CustomerSourceType
}

export interface CustomerCreateReqVO {
  code: string
  name: string
  shortName?: string
  remark?: string
  sourceType: CustomerSourceType
  sourceKey?: string
  sourceVersion?: string
  temporaryReason?: string
  reconciliationPending: boolean
  departmentCode: string
  marketCode: string
  systemCode: string
  expendCode: string
  industryCode: string
}

export interface CustomerUpdateReqVO {
  name?: string
  shortName?: string
  remark?: string
  departmentCode?: string
  marketCode?: string
  systemCode?: string
  expendCode?: string
  industryCode?: string
  changedFields: string[]
}

export interface CustomerLifecycleReqVO {
  reason: string
}

export interface CustomerRespVO {
  id: number
  code: string
  name: string
  shortName?: string
  lifecycleStatus: CustomerLifecycleStatus
  sourceType: CustomerSourceType
  syncStatus?: string
  dataAsOf?: string
  reconciliationPending: boolean
  temporaryReason?: string
  contactPhone?: string
  contactEmail?: string
  departmentCode: string
  departmentName?: string
  marketCode: string
  marketName?: string
  systemCode: string
  systemName?: string
  expendCode: string
  expendName?: string
  industryCode: string
  industryName?: string
  remark?: string
  version: number
}

export interface CustomerRelationSummaryItem {
  projectId?: number
  projectCode?: string
  projectName?: string
  deviceId?: number
  deviceCode?: string
  deviceName?: string
  status: string
}

export interface CustomerRelationSummarySlice {
  provider: string
  available: boolean
  dataAsOf?: string
  items: CustomerRelationSummaryItem[]
  total: number
}

export interface CustomerLocationVO {
  locationType: string
  locationId: number
  sourceVersion: number
  effectiveFrom: string
}

export interface CustomerHistoryVO {
  fieldName: string
  fieldOwner: string
  beforeValueDigest?: string
  afterValueDigest?: string
  sourceType: string
  operationId: string
  operatorId?: number
  occurredAt: string
}

export interface CustomerDetailRespVO extends CustomerRespVO {
  locations: CustomerLocationVO[]
  projects: CustomerRelationSummarySlice
  devices: CustomerRelationSummarySlice
  history: CustomerHistoryVO[]
}

export interface CustomerCommandResult {
  customerId: number
  version: number
  replayed: boolean
}

const baseUrl = '/pms/customers'

export const getCustomerPage = (params: CustomerPageReqVO) => request.get({ url: baseUrl, params })

export const getCustomer = (id: number) => request.get({ url: `${baseUrl}/${id}` })

export const createCustomer = (data: CustomerCreateReqVO, idempotencyKey: string) =>
  request.post({
    url: baseUrl,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const updateCustomer = (
  id: number,
  data: CustomerUpdateReqVO,
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.put({
    url: `${baseUrl}/${id}`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedVersion) }
  })

const lifecycleAction = (
  id: number,
  action: 'disable' | 'delete' | 'restore',
  data: CustomerLifecycleReqVO,
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${id}/actions/${action}`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedVersion) }
  })

export const disableCustomer = (
  id: number,
  data: CustomerLifecycleReqVO,
  expectedVersion: number,
  idempotencyKey: string
) => lifecycleAction(id, 'disable', data, expectedVersion, idempotencyKey)

export const deleteCustomer = (
  id: number,
  data: CustomerLifecycleReqVO,
  expectedVersion: number,
  idempotencyKey: string
) => lifecycleAction(id, 'delete', data, expectedVersion, idempotencyKey)

export const restoreCustomer = (
  id: number,
  data: CustomerLifecycleReqVO,
  expectedVersion: number,
  idempotencyKey: string
) => lifecycleAction(id, 'restore', data, expectedVersion, idempotencyKey)
