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

export const getProjectClosurePage = (params: PmsProjectPageParam) =>
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
export const submitProjectClosure = (id: number, treeVersion: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id }, headers: { 'If-Match': String(treeVersion) } })
// 后端提交以项目树版本做乐观锁（If-Match）；叶子项目无进度汇总快照，树版本取自项目树查询
export const getProjectTreeVersion = (projectId: number) =>
  request
    .get<{ treeVersion: number }>({
      url: `/pms/projects/${projectId}/tree`,
      params: { queryType: 'LOCATE', anchorId: projectId, pageSize: 1 }
    })
    .then((data) => data.treeVersion)
export const startApproveProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/start-approve`, params: { id } })
export const passProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/pass`, params: { id } })
export const rejectProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/reject`, params: { id } })
export const archiveProjectClosure = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
