import request from '@/config/axios'

export interface IssueVO {
  id?: number
  projectId: number
  code: string
  name: string
  description?: string
  source?: string
  severity: number
  ownerUserId?: number
  deadline?: Date
  solution?: string
  verificationStandard?: string
  verifyResult?: string
  verifiedBy?: number
  verifiedTime?: Date
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

export interface IssueVerifyVO {
  id: number
  verifyResult?: string
  verifiedBy?: number
  rejectReason?: string
  version?: number
  action: 'close' | 'reject'
}

const baseUrl = '/pms/eng-issue'

export const getIssuePage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getIssue = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createIssue = (data: IssueVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateIssue = (data: IssueVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteIssue = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const startRectifyIssue = (id: number) =>
  request.put({ url: `${baseUrl}/start-rectify`, params: { id } })
export const submitForVerifyIssue = (id: number) =>
  request.put({ url: `${baseUrl}/submit-for-verify`, params: { id } })
export const closeIssue = (data: IssueVerifyVO) =>
  request.put({ url: `${baseUrl}/close`, data })
export const rejectIssue = (data: IssueVerifyVO) =>
  request.put({ url: `${baseUrl}/reject`, data })
export const suspendIssue = (id: number) =>
  request.put({ url: `${baseUrl}/suspend`, params: { id } })
export const resumeIssue = (id: number) =>
  request.put({ url: `${baseUrl}/resume`, params: { id } })
export const validateAcceptance = (projectId: number) =>
  request.get({ url: `${baseUrl}/validate-acceptance`, params: { projectId } })
