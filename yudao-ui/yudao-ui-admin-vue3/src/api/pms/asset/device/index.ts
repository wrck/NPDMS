import request from '@/config/axios'

export type DeviceSyncStatus = 'FRESH' | 'STALE' | 'FAILED' | 'PENDING_MAPPING' | 'NOT_AVAILABLE'

export interface DevicePageReqVO extends PageParam {
  sn?: string
  productCode?: string
  projectId?: number
  customerId?: number
}

export interface DeviceListVO {
  deviceId: number
  sn: string
  productCode?: string
  productModel?: string
  productName?: string
  shipmentTime?: string
  packageNo?: string
  contractNo?: string
  shipmentRecordId?: number
  projectId?: number
  customerId?: number
  warrantyStartDate?: string
  warrantyEndDate?: string
  warrantyStatus?: string
  conpVersion?: string
  conpType?: string
  conpSeries?: string
  conpMark?: string
  syncStatus: DeviceSyncStatus
}

export interface DeviceSummaryVO {
  deviceId: number
  tenantId: number
  sn: string
  productCode?: string
  productModel?: string
  productName?: string
  shipmentTime?: string
  packageNo?: string
  contractNo?: string
  shipmentRecordId?: number
  projectId?: number
  projectAssignmentVersion: number
  customerId?: number
  customerAssignmentVersion: number
  warrantyStartDate?: string
  warrantyEndDate?: string
  warrantyStatus?: string
  conpVersion?: string
  conpType?: string
  conpSeries?: string
  conpMark?: string
}

export interface DeviceSourceSliceVO<T = Record<string, unknown>> {
  sourceSystem: string
  sourceKey?: string
  sourceVersion?: string
  sourceUpdatedAt?: string
  syncedAt?: string
  syncStatus: DeviceSyncStatus
  data?: T
}

export interface DeviceDetailVO {
  summary: DeviceSummaryVO
  factory: DeviceSourceSliceVO
  official: DeviceSourceSliceVO
  networkVersion: DeviceSourceSliceVO
  technicalNotice: DeviceSourceSliceVO
  warranty: DeviceSourceSliceVO
  configurationLog: DeviceSourceSliceVO
}

export interface DeviceProjectRelationshipVO {
  id: number
  deviceSn: string
  projectId: number
  relationshipType: string
  effectiveFrom: string
  effectiveTo?: string
  assignmentVersion: number
  reason?: string
  operationId?: string
  sourceSystem?: string
}

export interface DeviceCustomerRelationshipVO {
  id: number
  deviceSn: string
  customerId: number
  relationshipType: string
  effectiveFrom: string
  effectiveTo?: string
  assignmentVersion: number
  reason?: string
  operationId?: string
  sourceSystem?: string
}

export interface DeviceAssemblyVO {
  id: number
  parentDeviceSn: string
  childDeviceSn: string
  positionCode?: string
  assemblyType?: string
  effectiveFrom: string
  effectiveTo?: string
  sourceSystem?: string
}

export interface DeviceWarrantyVO {
  id: number
  deviceSn: string
  warrantyStartDate?: string
  warrantyEndDate?: string
  warrantyMonths?: number
  warrantyGrade?: string
  warrantyContractNo?: string
  warrantyProvider?: string
  warrantyType?: string
  warrantyStatus?: string
  remark?: string
  sourceSystem?: string
}

export interface DeviceWarrantyRecordVO {
  id: number
  deviceSn: string
  warrantyStartDate?: string
  warrantyEndDate?: string
  warrantyMonths?: number
  warrantyGrade?: string
  warrantyContractNo?: string
  extended?: boolean
  remark?: string
  sourceSystem?: string
}

export interface DeviceWarrantyResultVO {
  current?: DeviceWarrantyVO
  records: PageResult<DeviceWarrantyRecordVO[]>
}

export interface DeviceConfigurationLogVO {
  id: number
  configType?: string
  sourceSystem?: string
  collectedAt?: string
  fileHash?: string
  remark?: string
  downloadable: boolean
}

export interface DeviceDownloadGrantVO {
  downloadPath: string
  expiresAt: string
}

export interface DeviceAssignmentResultVO {
  assignmentVersion: number
  operationId: string
}

export interface DeviceProjectAssignReqVO {
  projectId: number
  reason: string
}

export interface DeviceCustomerAssignReqVO {
  customerId: number
  relationshipType: string
  reason: string
}

const baseUrl = '/pms/asset/devices'

export const getDevicePage = (params: DevicePageReqVO) =>
  request.get<PageResult<DeviceListVO[]>>({ url: `${baseUrl}/page`, params })

export const getDevice = (id: number) => request.get<DeviceDetailVO>({ url: `${baseUrl}/${id}` })

export const getAssignmentHistory = (id: number, params: PageParam) =>
  request.get<PageResult<DeviceProjectRelationshipVO[]>>({
    url: `${baseUrl}/${id}/assignment-history`,
    params
  })

export const getCustomerRelationships = (id: number, params: PageParam) =>
  request.get<PageResult<DeviceCustomerRelationshipVO[]>>({
    url: `${baseUrl}/${id}/customer-relationships`,
    params
  })

export const getAssemblyTree = (id: number) =>
  request.get<DeviceAssemblyVO[]>({ url: `${baseUrl}/${id}/assembly-tree` })

export const getWarrantyRecords = (id: number, params: PageParam) =>
  request.get<DeviceWarrantyResultVO>({ url: `${baseUrl}/${id}/warranty-records`, params })

export const getConfigurationLogs = (id: number) =>
  request.get<DeviceConfigurationLogVO[]>({ url: `${baseUrl}/${id}/configuration-logs` })

export const createConfigurationLogDownloadUrl = (id: number, logId: number) =>
  request.post<DeviceDownloadGrantVO>({
    url: `${baseUrl}/${id}/configuration-logs/${logId}/download-url`
  })

export const downloadConfigurationLog = (downloadPath: string) =>
  request.download<Blob>({ url: downloadPath })

export const assignProject = (
  id: number,
  data: DeviceProjectAssignReqVO,
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post<DeviceAssignmentResultVO>({
    url: `${baseUrl}/${id}/actions/assign-project`,
    data,
    headers: { 'If-Match': String(expectedVersion), 'Idempotency-Key': idempotencyKey }
  })

export const assignCustomer = (
  id: number,
  data: DeviceCustomerAssignReqVO,
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post<DeviceAssignmentResultVO>({
    url: `${baseUrl}/${id}/actions/assign-customer`,
    data,
    headers: { 'If-Match': String(expectedVersion), 'Idempotency-Key': idempotencyKey }
  })
