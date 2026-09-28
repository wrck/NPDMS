import request from '@/config/axios'

export interface DeliverableVO {
  id?: number
  projectId: number
  phaseId?: number
  code?: string
  name: string
  deliverableType: string
  sourceType?: string
  sourceId?: number
  fileUrl?: string
  fileSize?: number
  fileChecksum?: string
  status?: number
  archivedTime?: Date
  archivedBy?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/imp-deliverable'

// P06R 统一交付件后 imp-deliverable 只读：历史查询接口；新登记走统一交付件能力
export const getDeliverablePage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getDeliverable = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })

export interface DeliverableSummaryItemVO {
  category: string
  code?: string
  name?: string
  sourceLabel?: string
  status?: number
  archivedTime?: Date
  fileUrl?: string
  sourceType?: string
  sourceId?: number
  remark?: string
}

export const getDeliverableProjectSummary = (projectId: number) =>
  request.get({ url: `${baseUrl}/project-summary`, params: { projectId } })
