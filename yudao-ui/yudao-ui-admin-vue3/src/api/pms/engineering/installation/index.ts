import request from '@/config/axios'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'

export interface InstallationVO {
  id?: number
  projectId: number
  code?: string
  equipmentId?: number | string
  installLocation?: string
  locationMaintenance?: LocationMaintainRequest
  addressId?: number
  addressVersion?: number
  siteId?: number
  siteVersion?: number
  siteLocationId?: number
  siteLocationVersion?: number
  locationResolutionStatus?: 'RESOLVED' | 'UNRESOLVED'
  addressSnapshot?: string
  locationSnapshot?: string
  effectiveFrom?: Date
  effectiveTo?: Date
  installTime?: number
  installerUserId?: number
  environmentCheck?: string
  specCheck?: string
  photoUrl?: string
  result?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/imp-installation'

export interface InstallationPageRequest extends PmsProjectPageParam {
  equipmentId?: number | string
  status?: number
}
export const getInstallationPage = (params: InstallationPageRequest) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getInstallation = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createInstallation = (data: InstallationVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateInstallation = (data: InstallationVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteInstallation = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const startInstallation = (id: number) =>
  request.put({ url: `${baseUrl}/start`, params: { id } })
export const completeInstallation = (id: number) =>
  request.put({ url: `${baseUrl}/complete`, params: { id } })
export const markAbnormalInstallation = (id: number) =>
  request.put({ url: `${baseUrl}/mark-abnormal`, params: { id } })

export interface InstallationAddressCompletion {
  totalDevices: number
  addressedDevices: number
  completed: boolean
}
export const getProjectInstallationAddressCompletion = (projectId: number): Promise<InstallationAddressCompletion> =>
  request.get({ url: `/api/v1/pms/implementation/projects/${projectId}/installation-address-completion` })

export type InstallationBatchCreateRequest = Omit<InstallationVO, 'id' | 'equipmentId' | 'status' | 'version'> & { equipmentIds: number[] }
export const createInstallationBatch = (data: InstallationBatchCreateRequest): Promise<number[]> =>
  request.post({ url: '/api/v1/pms/implementation/installation-records/batch', data })

export interface InstallationCurrentAddress {
  equipmentId: number | string
  installLocation?: string
}
export const getCurrentInstallationAddresses = (projectId: number, equipmentIds: (number | string)[]) =>
  request.get<InstallationCurrentAddress[]>({ url: '/api/v1/pms/implementation/installation-records/current-addresses', params: { projectId, equipmentIds } })
