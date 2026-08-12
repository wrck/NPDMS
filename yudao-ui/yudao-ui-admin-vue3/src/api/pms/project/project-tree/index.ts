import request from '@/config/axios'

export interface ProjectTreeNodeVO {
  id?: number
  code?: string
  name?: string
  parentId?: number
  rootId?: number
  path?: string
  depth?: number
  sort?: number
  category?: string
  majorProjectFlag?: boolean
  managerUserId?: number
  status?: number
  createTime?: Date
  children?: ProjectTreeNodeVO[]
}

export interface ProjectTreeCreateChildReqVO {
  parentId: number
  code: string
  name: string
  customerId: number
  sort?: number
  category?: string
  majorProjectFlag?: boolean
  managerUserId?: number
}

export interface ProjectTreeMoveReqVO {
  projectId: number
  targetParentId: number
  reason?: string
}

const baseUrl = '/pms/project-tree'

export const createChildProject = (data: ProjectTreeCreateChildReqVO) =>
  request.post({ url: `${baseUrl}/create-child`, data })
export const moveSubtree = (data: ProjectTreeMoveReqVO) =>
  request.put({ url: `${baseUrl}/move`, data })
export const getProjectTree = (rootProjectId: number) =>
  request.get({ url: `${baseUrl}/tree`, params: { rootProjectId } })
export const getDescendants = (projectId: number) =>
  request.get({ url: `${baseUrl}/descendants`, params: { projectId } })
export const getProjectPath = (projectId: number) =>
  request.get({ url: `${baseUrl}/path`, params: { projectId } })
