import request from '@/config/axios'

export interface ProjectTeamMemberVO {
  id?: number
  projectId?: number
  userId?: number
  roleCode: string
  roleName?: string
  status: number
  remark?: string
  createTime?: Date
  updateTime?: Date
}

const baseUrl = '/pms/project-team'

export const getProjectTeamPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectTeamMember = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectTeamMember = (data: ProjectTeamMemberVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectTeamMember = (data: ProjectTeamMemberVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectTeamMember = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getProjectTeamListByProjectId = (projectId: number) =>
  request.get({ url: `${baseUrl}/list-by-project`, params: { projectId } })
