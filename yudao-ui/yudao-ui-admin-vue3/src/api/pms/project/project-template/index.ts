import request from '@/config/axios'

export interface PhaseDef {
  phaseCode: string
  phaseName: string
  sortOrder: number
  entryCriteria?: string
  exitCriteria?: string
}

export interface TaskDef {
  taskCode: string
  taskName: string
  parentTaskCode?: string
  phaseCode?: string
  priority?: number
  sortOrder: number
  estimatedHours?: number
  description?: string
}

export interface TeamRoleDef {
  roleCode: string
  roleName: string
  requiredCount: number
}

export interface TemplateSnapshot {
  schemaVersion: number
  phases: PhaseDef[]
  tasks: TaskDef[]
  teamRoles: TeamRoleDef[]
}

export interface ProjectTemplateVO {
  id?: number
  code: string
  name: string
  projectType?: string
  description?: string
  status: number
  sort: number
  snapshotJson?: TemplateSnapshot
  createTime?: Date
}

export interface ProjectCreateFromTemplateVO {
  templateId: number
  code: string
  name: string
  customerId: number
  contractCode?: string
  sourceSystem: string
  sourceBusinessKey: string
  managerUserId?: number
}

const baseUrl = '/pms/project-template'

export const getProjectTemplatePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectTemplate = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectTemplate = (data: ProjectTemplateVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectTemplate = (data: ProjectTemplateVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectTemplate = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getEnabledProjectTemplateList = () =>
  request.get({ url: `${baseUrl}/enabled-list` })
export const getEnabledProjectTemplateListByType = (projectType: string) =>
  request.get({ url: `${baseUrl}/enabled-list-by-type`, params: { projectType } })
export const createProjectFromTemplate = (data: ProjectCreateFromTemplateVO) =>
  request.post({ url: `${baseUrl}/create-project`, data })
