import request from '@/config/axios'

export interface AuthorizationVO {
  id?: number
  code: string
  projectId: number
  name: string
  authorizationType?: string
  deviceId?: number
  deviceSerial?: string
  deviceModel?: string
  licenseKey?: string
  licenseType?: string
  applyStartDate?: string
  applyEndDate?: string
  actualEndDate?: string
  usageLimit?: number
  usedCount?: number
  status?: number
  version?: number
  submitUserId?: number
  submitTime?: string
  approverUserId?: number
  approveOpinion?: string
  approveTime?: string
  recallUserId?: number
  recallTime?: string
  processInstanceId?: string
  creatorUserId?: number
  remark?: string
  createTime?: string
}

const baseUrl = '/pms/eng-authorization'

export const getAuthorizationPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getAuthorization = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createAuthorization = (data: AuthorizationVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateAuthorization = (data: AuthorizationVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteAuthorization = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitAuthorization = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveAuthorization = (data: {
  id: number
  approveAction: string
  approverUserId?: number
  approveOpinion?: string
  version?: number
}) => request.put({ url: `${baseUrl}/approve`, data })
export const recallAuthorization = (id: number) =>
  request.put({ url: `${baseUrl}/recall`, params: { id } })
export const terminateAuthorization = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
