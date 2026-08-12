import request from '@/config/axios'

export interface ProjectVO {
  id?: number
  code: string
  name: string
  customerId?: number
  customerName?: string
  contractCode?: string
  officeId?: number
  salesUserId?: number
  industry?: string
  implementationMode?: string
  projectType?: string
  shipmentStatus?: string
  sourceSystem?: string
  sourceBusinessKey?: string
  status?: number
  parentId?: number
  rootId?: number
  path?: string
  depth?: number
  sort?: number
  category?: string
  majorProjectFlag?: boolean
  managerUserId?: number
  remark?: string
  version?: number
  createTime?: Date
}

export interface ProjectClassifyReqVO {
  projectId: number
  category?: string
  majorProjectFlag?: boolean
}

export interface ProjectAssignManagerReqVO {
  projectId: number
  managerUserId: number
}

const baseUrl = '/pms/project'

// 基础 CRUD
export const getProjectPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProject = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProject = (data: ProjectVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProject = (data: ProjectVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProject = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })

// 业务动作
export const classifyProject = (data: ProjectClassifyReqVO) =>
  request.put({ url: `${baseUrl}/classify`, data })
export const assignProjectManager = (data: ProjectAssignManagerReqVO) =>
  request.put({ url: `${baseUrl}/assign-manager`, data })

// 简易列表（用于选择器，不分页）
export const getProjectSimpleList = (params?: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params: { pageNo: 1, pageSize: 100, ...params } })
