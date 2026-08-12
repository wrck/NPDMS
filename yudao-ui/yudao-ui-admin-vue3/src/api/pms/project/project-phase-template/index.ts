import request from '@/config/axios'

export interface ProjectPhaseTemplateVO {
  id?: number
  name: string
  code: string
  projectType?: string
  description?: string
  status: number
  sort?: number
  entryCriteria?: string
  exitCriteria?: string
  responsibleRole?: string
  createTime?: Date
}

// 注意：后端 ProjectPhaseTemplateController 的实际 @RequestMapping 为 `/pms/phase-template`，
// 与任务表格中给出的 `/pms/project-phase-template` 不同。此处以后端实际路径为准。
const baseUrl = '/pms/phase-template'

export const getProjectPhaseTemplatePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectPhaseTemplate = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectPhaseTemplate = (data: ProjectPhaseTemplateVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectPhaseTemplate = (data: ProjectPhaseTemplateVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectPhaseTemplate = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getEnabledProjectPhaseTemplateList = () =>
  request.get({ url: `${baseUrl}/enabled-list` })
export const getEnabledProjectPhaseTemplateListByType = (projectType: string) =>
  request.get({ url: `${baseUrl}/enabled-list-by-type`, params: { projectType } })
