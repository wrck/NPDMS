import request from '@/config/axios'

/**
 * 旧链项目 API（F-PM01 存量冻结后仅只读）
 *
 * 写端点（create/update/delete/classify/assign-manager）已随 F-PM01 退役，
 * 新链复数路由 /pms/projects（api/pms/project/projects）承接全部写语义；
 * 本模块仅保留 get/page 只读查询，供旧页面选择器过渡消费。
 */
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

const baseUrl = '/pms/project'

// 只读查询
export const getProjectPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProject = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })

// 简易列表（用于选择器，不分页）
export const getProjectSimpleList = (params?: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params: { pageNo: 1, pageSize: 100, ...params } })
