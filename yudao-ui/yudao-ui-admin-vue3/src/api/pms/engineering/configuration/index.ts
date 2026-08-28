import request from '@/config/axios'

export interface ConfigurationVO {
  id?: number
  projectId: number
  code: string
  equipmentId?: number
  configLogUrl?: string
  debugResult?: string
  debuggerUserId?: number
  debugTime?: Date
  configSnapshot?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/eng-configuration'

export const getConfigurationPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getConfiguration = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createConfiguration = (data: ConfigurationVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateConfiguration = (data: ConfigurationVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteConfiguration = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const startConfiguration = (id: number) =>
  request.put({ url: `${baseUrl}/start`, params: { id } })
export const completeConfiguration = (id: number) =>
  request.put({ url: `${baseUrl}/complete`, params: { id } })
export const markAbnormalConfiguration = (id: number) =>
  request.put({ url: `${baseUrl}/mark-abnormal`, params: { id } })
