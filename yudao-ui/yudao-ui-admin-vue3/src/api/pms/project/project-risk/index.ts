import request from '@/config/axios'

export interface ProjectRiskVO {
  id?: number
  projectId?: number
  title: string
  riskLevel: string
  riskType?: string
  cause?: string
  impact?: string
  mitigation?: string
  contingency?: string
  ownerUserId?: number
  status?: number
  warningThreshold?: string
  reviewNotes?: string
  identifiedAt?: Date
  closedAt?: Date
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/project-risk'

export const getProjectRiskPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectRisk = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectRisk = (data: ProjectRiskVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectRisk = (data: ProjectRiskVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectRisk = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getProjectRiskListByProjectId = (projectId: number) =>
  request.get({ url: `${baseUrl}/list-by-project`, params: { projectId } })
export const transitionProjectRiskStatus = (
  riskId: number,
  targetStatus: number,
  version?: number
) =>
  request.put({
    url: `${baseUrl}/transition-status`,
    params: { riskId, targetStatus, version }
  })
