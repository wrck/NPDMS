import request from '@/config/axios'

export interface SoftwareVersionValues {
  conpVersion?: string | null
  conpType?: string | null
  conpSeries?: string | null
  conpMark?: string | null
  bootVersion?: string | null
  cpldVersion?: string | null
  pcbVersion?: string | null
  customized?: boolean | null
  releaseDate?: string | null
  versionDesc?: string | null
}
export interface SoftwareVersionCurrent {
  values: SoftwareVersionValues | null
  version: number
  recordedAt?: string | number | null
  canMaintain: boolean
}
export interface SoftwareVersionHistory {
  record: SoftwareVersionValues & {
    id: number
    projectId?: number | null
    previousRecordId?: number | null
    recordedAt?: string | number | null
    dataSource: string
    deviceSn?: string | null
    contractNo?: string | null
    productCode?: string | null
    changeBatchId?: number | null
    parsedFactId?: number | null
    executeTime?: string | number | null
    dataState?: number | null
    customInfo?: string | null
    creator?: string | null
    createTime?: string | number | null
    updater?: string | null
    updateTime?: string | number | null
  }
  previous: SoftwareVersionValues | null
  legacyAttributes: Record<string, string | number | null>
}
export const getSoftwareVersionCurrent = (deviceId: number | string) =>
  request.get<SoftwareVersionCurrent>({ url: `/api/v1/pms/devices/${deviceId}/software-version` })
export const getSoftwareVersionHistory = (deviceId: number | string, params: PageParam) =>
  request.get<{ list: SoftwareVersionHistory[]; total: number }>({
    url: `/api/v1/pms/devices/${deviceId}/software-version/history`,
    params
  })
export const registerSoftwareVersion = (
  deviceId: number | string,
  data: {
    projectId: number | string
    expectedVersion: number
    requestKey: string
    values: SoftwareVersionValues
  }
) =>
  request.post<{ recordId: number; replayed: boolean }>({
    url: `/api/v1/pms/devices/${deviceId}/software-version/registrations`,
    data
  })

export type DeviceSyncStatus = 'FRESH' | 'STALE' | 'FAILED' | 'PENDING_MAPPING' | 'NOT_AVAILABLE'

export interface DevicePageReqVO extends PageParam {
  sn?: string
  name?: string
  status?: string
  productCode?: string
  projectId?: number | string
  customerId?: number
  projectName?: string
  customerName?: string
  departmentName?: string
}

export interface DeviceOrganizationVO {
  companyId?: number
  companyName?: string
  departmentId?: number
  departmentCode?: string
  departmentName?: string
  source: 'PROJECT' | 'CONTRACT' | 'UNRESOLVED'
}

export interface DeviceListVO {
  warrantyMonths?: number
  projectCode?: string
  projectName?: string
  customerName?: string
  bootVersion?: string
  cpldVersion?: string
  extendedCount?: number
  organization?: DeviceOrganizationVO
  name?: string
  status?: string
  locationSnapshot?: string
  locationResolutionStatus?: string
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
  name?: string
  locationSnapshot?: string
}

/** Long标识保留服务端原始字符串，不经Number转换。 */
export type DeviceCurrentSummaryVO = Omit<DeviceSummaryVO, 'deviceId' | 'tenantId' | 'projectId' | 'customerId' | 'shipmentRecordId'> & {
  deviceId: number | string
  tenantId: number | string
  projectId?: number | string
  customerId?: number | string
  shipmentRecordId?: number | string
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
  organization?: DeviceOrganizationVO
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
  combinedDeviceSn?: string
  productCode?: string
  productName?: string
  productDescription?: string
  gradeName?: string
  shipmentTime?: string
  warrantyFlag?: string
  sourceWarrantyStartTime?: string
  sourceWarrantyEndTime?: string
  originalWarrantyStartTime?: string
  originalWarrantyEndTime?: string
  remainingDaysAtSource?: number
  originalRemainingDaysAtSource?: number
  contractType?: string
  contractTypeName?: string
  projectName?: string
  customerName?: string
  finalCustomerName?: string
  agentName?: string
  officeCode?: string
  officeName?: string
  marketCode?: string
  marketName?: string
  systemCode?: string
  systemName?: string
  secondarySn?: string
  secondaryItem?: string
  secondaryProductName?: string
  secondaryProductDescription?: string
  sourceSyncTime?: string
  sourceShipmentKey?: string
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

export const getDevice = (id: number | string) => request.get<DeviceDetailVO>({ url: `${baseUrl}/${id}` })

export const getCurrentDeviceSummaries = (deviceIds: (number | string)[], projectId?: number | string) =>
  request.post<DeviceCurrentSummaryVO[]>({ url: `${baseUrl}/current-summaries`, data: { deviceIds, ...(projectId == null ? {} : { projectId }) } })

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

export interface DeviceShipmentVO {
  id: number | string
  deviceSn: string
  packageNo?: string
  shipmentTime?: string
  contractNo?: string
  legacyPackageKey?: string
  rmaRelatedSn?: string
  rmaMarked?: number
  orderNo?: string
  lineNo?: string
  productCode?: string
  productModel?: string
  productName?: string
  secondaryProductModel?: string
  secondaryProductName?: string
  sourceUpdatedAt?: string
  secondarySn?: string
  secondaryItem?: string
  internalSerialNo?: string
  isRma?: number
  profitCenter?: string
  soleAgentSuffix?: string
  syncTime?: string
  uuid?: string
  warrantyStartDate?: string
  warrantyMonths?: number
  rmaNo?: string
}
export const getDeviceShipments = (
  deviceId: number,
  params: { pageNo: number; pageSize: number }
): Promise<{ list: DeviceShipmentVO[]; total: number }> =>
  request.get({ url: '/pms/asset/devices/' + deviceId + '/shipments', params })

export interface DeviceContractRelationship {
  id: number | string
  sourceDeviceSn: string
  targetDeviceSn: string
  sourceItem?: string | null
  targetItem?: string | null
  contractNo?: string | null
  effectiveFrom?: string | number | null
  effectiveTo?: string | number | null
  createTime?: string | number | null
  updateTime?: string | number | null
}
export const getDeviceContractRelationships = (
  deviceId: number,
  params: { pageNo: number; pageSize: number }
): Promise<{ list: DeviceContractRelationship[]; total: number }> =>
  request.get({ url: '/api/v1/pms/device-source-relationships', params: { ...params, deviceId } })

export interface DeviceSourceSoftwareObservation {
  sourceId: number | string
  sourceProjectId: number | string
  logId?: number | string
  contractNo?: string
  itemCode?: string
  barCode?: string
  conp?: string
  conpType?: string
  conpSeries?: string
  conpMark?: string
  conpBak?: string
  conpChange?: number
  cpld?: string
  cpldBak?: string
  cpldChange?: number
  boot?: string
  bootBak?: string
  bootChange?: number
  pcb?: string
  pcbBak?: string
  pcbChange?: number
  executeTime?: string
  datastate?: number
  sourceCreatedAt?: string
  sourceCreatedBy?: string
  sourceUpdatedAt?: string
  sourceUpdatedBy?: string
  customInfo?: string
}
export interface DeviceSourceFactoryVersion {
  deviceSn: string
  conpVersion?: string
  bootVersion?: string
  cpldVersion?: string
  pcbVersion?: string
}
export interface DeviceSourceVersionRecord<T> {
  id: number | string
  projectId: number | string
  sourceProjectKey: string
  sourceSystem: string
  sourceKey: string
  sourceVersion?: string
  sourceReadAt?: string
  sourceChecksum: string
  values: T
}
export const getDeviceSourceSoftwareObservations = (deviceId: number, params: PageParam) =>
  request.get<PageResult<DeviceSourceVersionRecord<DeviceSourceSoftwareObservation>[]>>({
    url: '/api/v1/pms/devices/' + deviceId + '/source-software-observations',
    params
  })
export const getDeviceSourceFactoryVersions = (deviceId: number, params: PageParam) =>
  request.get<
    PageResult<
      { id: number | string; projectId: number | string; values: DeviceSourceFactoryVersion }[]
    >
  >({
    url: '/api/v1/pms/devices/' + deviceId + '/source-factory-versions',
    params
  })
