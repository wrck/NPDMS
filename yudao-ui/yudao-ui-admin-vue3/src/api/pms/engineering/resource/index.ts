import request from '@/config/axios'

export interface ResourceReadyVO {
  id?: number
  projectId: number
  code: string
  name: string
  resourceType?: string
  equipmentId?: number
  quantity?: number
  readyStatus?: number
  readyTime?: Date
  readyUserId?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/eng-resource'

export const getResourceReadyPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getResourceReady = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createResourceReady = (data: ResourceReadyVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateResourceReady = (data: ResourceReadyVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteResourceReady = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const markReady = (id: number) =>
  request.put({ url: `${baseUrl}/mark-ready`, params: { id } })
export const markAbnormal = (id: number) =>
  request.put({ url: `${baseUrl}/mark-abnormal`, params: { id } })
export const resetToNotReady = (id: number) =>
  request.put({ url: `${baseUrl}/reset-not-ready`, params: { id } })
