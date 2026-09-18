import request from '@/config/axios'

export interface DeviceArchiveVO {
  id?: number
  sn: string
  name: string
  productModel?: string
  status?: string
  customerId?: number
  projectId?: number
  siteId?: number
  siteLocationId?: number
  locationResolutionStatus?: 'RESOLVED' | 'UNRESOLVED'
  locationSnapshot?: string
  locationEffectiveFrom?: Date
  locationRecordId?: number
  warrantyStartDate?: Date
  warrantyEndDate?: Date
  remark?: string
  version?: number
  createTime?: Date
}

export type DeviceArchiveSaveReqVO = Omit<DeviceArchiveVO, 'createTime'>

export interface DeviceArchiveStatusChangeReqVO {
  id?: number
  action: string
  targetStatus?: string
  changeDescription?: string
}

export interface DeviceArchiveVersionVO {
  id?: number
  deviceId?: number
  versionNo?: number
  changeType?: string
  changeDescription?: string
  beforeSnapshot?: string
  afterSnapshot?: string
  creator?: string
  createTime?: Date
}

export interface DeviceConfigLogVO {
  id?: number
  deviceId?: number
  configType?: string
  configContent?: string
  sourceSystem?: string
  collectedAt?: Date
  fileUrl?: string
  fileHash?: string
  remark?: string
  createTime?: Date
}

export interface DeviceConfigLogPageParam extends PageParam {
  deviceId?: number | string
  configType?: string
  sourceSystem?: string
}

const baseUrl = '/pms/asset/devices'

export const getDeviceArchivePage = (params: DeviceArchivePageParam) =>
  request.get({ url: `${baseUrl}/archive-page`, params })
export const getDeviceArchiveRecord = (id: number | string) =>
  request.get({ url: `${baseUrl}/${id}/archive-record` })
export const createDeviceArchive = (data: DeviceArchiveSaveReqVO) => request.post({ url: baseUrl, data })
export const updateDeviceArchive = (id: number | string, data: DeviceArchiveSaveReqVO) =>
  request.put({ url: `${baseUrl}/${id}`, data })
export const deleteDeviceArchive = (id: number) => request.delete({ url: `${baseUrl}/${id}` })
export const changeDeviceArchiveStatus = (id: number | string, data: DeviceArchiveStatusChangeReqVO) =>
  request.post({ url: `${baseUrl}/${id}/actions/status-change`, data })
export const getDeviceArchiveVersions = (id: number | string) =>
  request.get({ url: `${baseUrl}/${id}/archive-versions` })
export const getDeviceConfigLogPage = (params: DeviceConfigLogPageParam) =>
  request.get({ url: `${baseUrl}/configuration-logs/page`, params })

export interface DeviceArchivePageParam extends PageParam {
  sn?: string
  name?: string
  status?: string
  projectId?: number
  customerId?: number
}
