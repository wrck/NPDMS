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

const baseUrl = '/pms/project-tree'

export const getProjectTree = (rootProjectId: number) =>
  request.get({ url: `${baseUrl}/tree`, params: { rootProjectId } })
export const getDescendants = (projectId: number) =>
  request.get({ url: `${baseUrl}/descendants`, params: { projectId } })
export const getProjectPath = (projectId: number) =>
  request.get({ url: `${baseUrl}/path`, params: { projectId } })
