import request from '@/config/axios'

export interface SrvIssueVO {
  id?: number
  taskId: number
  code: string
  name: string
  description?: string
  severity?: string
  ownerUserId?: number
  deadline?: Date
  solution?: string
  verifyResult?: string
  verifiedBy?: number
  verifiedTime?: Date
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

export interface SrvIssueAssignVO {
  id: number
  ownerUserId: number
  deadline?: Date
  version?: number
}

export interface SrvIssueActionVO {
  id: number
  solution?: string
  verifyResult?: string
  version?: number
}

const baseUrl = '/pms/srv-issue'

export const getSrvIssuePage = (params: PmsTaskPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSrvIssue = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createSrvIssue = (data: SrvIssueVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateSrvIssue = (data: SrvIssueVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteSrvIssue = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getSrvIssueListByTask = (taskId: number) =>
  request.get({ url: `${baseUrl}/list-by-task`, params: { taskId } })
export const assignIssue = (data: SrvIssueAssignVO) =>
  request.put({ url: `${baseUrl}/assign`, data })
export const resolveIssue = (data: SrvIssueActionVO) =>
  request.put({ url: `${baseUrl}/resolve`, data })
export const verifyIssue = (data: SrvIssueActionVO) =>
  request.put({ url: `${baseUrl}/verify`, data })
export const validateInspectionClosure = (taskId: number) =>
  request.get({ url: `${baseUrl}/validate-closure`, params: { taskId } })
