import request from '@/config/axios'

export interface ProjectTaskVO {
  id?: number
  projectId?: number
  parentId?: number
  rootId?: number
  path?: string
  depth?: number
  sort?: number
  name: string
  code?: string
  description?: string
  ownerUserId?: number
  assigneeUserId?: number
  status?: number
  priority?: number
  planStartTime?: Date
  planEndTime?: Date
  actualStartTime?: Date
  actualEndTime?: Date
  estimatedHours?: number
  actualHours?: number
  progress?: number
  aggregatedProgress?: number
  version?: number
  createTime?: Date
}

export interface ProjectTaskMoveReqVO {
  taskId: number
  targetParentId?: number
}

export interface ProjectTaskTreeVO {
  id?: number
  projectId?: number
  parentId?: number
  rootId?: number
  path?: string
  depth?: number
  sort?: number
  name?: string
  code?: string
  status?: number
  progress?: number
  ownerUserId?: number
  assigneeUserId?: number
  version?: number
  createTime?: Date
  children?: ProjectTaskTreeVO[]
}

const baseUrl = '/pms/project-task'

export const getProjectTaskPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectTask = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectTask = (data: ProjectTaskVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectTask = (data: ProjectTaskVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectTask = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getProjectTaskTree = (projectId: number) =>
  request.get({ url: `${baseUrl}/tree`, params: { projectId } })
export const getProjectTaskDescendants = (taskId: number) =>
  request.get({ url: `${baseUrl}/descendants`, params: { taskId } })
export const moveProjectTask = (data: ProjectTaskMoveReqVO) =>
  request.put({ url: `${baseUrl}/move`, data })
