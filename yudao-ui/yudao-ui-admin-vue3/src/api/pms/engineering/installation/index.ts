import request from '@/config/axios'

export interface InstallationVO {
  id?: number
  projectId: number
  code: string
  equipmentId?: number
  installLocation?: string
  installTime?: Date
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

const baseUrl = '/pms/eng-installation'

export const getInstallationPage = (params: PageParam) =>
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
