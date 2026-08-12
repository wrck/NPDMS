import request from '@/config/axios'

export interface ProjectClosureVO {
  id?: number
  projectId: number
  code: string
  name: string
  applicationDate?: Date
  approverUserId?: number
  approvalDate?: Date
  approvalOpinion?: string
  carryoverIssues?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/acc-project-closure'

export const getProjectClosurePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectClosure = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectClosure = (data: ProjectClosureVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectClosure = (data: ProjectClosureVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectClosure = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1待审批 2审批中 3已通过 4已驳回 5已归档
export const submitProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const startApproveProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/start-approve`, params: { id } })
export const passProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/pass`, params: { id } })
export const rejectProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/reject`, params: { id } })
export const archiveProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
