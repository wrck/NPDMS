import request from '@/config/axios'

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

export const getProjectTaskTree = (projectId: number) =>
  request.get({ url: `${baseUrl}/tree`, params: { projectId } })
