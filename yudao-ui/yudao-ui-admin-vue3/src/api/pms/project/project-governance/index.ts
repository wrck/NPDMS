import request from '@/config/axios'

export interface ProjectGovernanceVO {
  id?: number
  projectId: number
  actionNo: string
  actionType: string
  reason: string
  proofFiles?: string
  applicantUserId: number
  applyTime: string
  approverUserId?: number
  approveTime?: string
  approveOpinion?: string
  beforeProjectStatus?: number
  afterProjectStatus?: number
  beforeManagerUserId?: number
  afterManagerUserId?: number
  executeTime?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

export interface ProjectGovernanceApproveReqVO {
  id: number
  approveAction: string
  approveOpinion?: string
  approverUserId: number
}

const baseUrl = '/pms/project-governance'

export const getGovernanceActionPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getGovernanceAction = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createGovernanceAction = (data: ProjectGovernanceVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateGovernanceAction = (data: ProjectGovernanceVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteGovernanceAction = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitGovernanceAction = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveGovernanceAction = (data: ProjectGovernanceApproveReqVO) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const withdrawGovernanceAction = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
