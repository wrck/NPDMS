import request from '@/config/axios'
export interface InstallationSourceDetails {
  sourceProductCode?: string
  sourceProductModel?: string
  sourceProductName?: string
  sourceReceiverName?: string
  sourceExpressNo?: string
  sourceCarrierName?: string
  sourceShipmentTime?: string
  sourceContractNo?: string
  changedProjectKey?: string
  changedContractNo?: string
  transferProjectKey?: string
  transferContractNo?: string
  sourceTransferFlag?: number
  sourceCreatedAt?: string
  sourceCreatedBy?: string
  sourceUpdatedAt?: string
  sourceUpdatedBy?: string | null
}
export interface HistoricalInstallationSummary {
  id: number | string
  projectId: number | string
  equipmentId: number | string
  code: string
  sourceRecordKey: string
  sourceDeviceSn?: string
  sourceProjectKey?: string
  sourceHistory: boolean
  status?: number
  installLocation?: string
  addressId?: number | string
  addressVersion?: number | string
  locationResolutionStatus?: string
  currentEquipmentId?: number | string
  effectiveFrom?: string
  effectiveTo?: string
  version?: number | string
}
export interface InstallationSourceRecord {
  id: number | string
  projectId: number | string
  equipmentId: number | string
  sourceRecordKey: string
  sourceDeviceSn?: string
  sourceProjectKey?: string
  sourceId: number | string
  sourceProjectId?: number | string
  installLocation?: string
  addressId?: number | string
  addressVersion?: number | string
  locationResolutionStatus: string
  values: InstallationSourceDetails
  historicalInstallation?: HistoricalInstallationSummary | null
}
export const getInstallationSourceRecords = (params: PageParam & { equipmentId: number }) =>
  request.get<PageResult<InstallationSourceRecord[]>>({
    url: '/api/v1/pms/installation-source-records',
    params
  })
