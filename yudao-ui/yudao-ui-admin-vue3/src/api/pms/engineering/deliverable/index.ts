import request from '@/config/axios'

export interface DeliverableVO {
  id?: number
  projectId: number
  phaseId?: number
  code: string
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

const baseUrl = '/pms/eng-deliverable'

export const getDeliverablePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getDeliverable = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createDeliverable = (data: DeliverableVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateDeliverable = (data: DeliverableVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteDeliverable = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const archiveDeliverable = (id: number, archivedBy?: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id, archivedBy } })
export const voidDeliverable = (id: number) =>
  request.put({ url: `${baseUrl}/void`, params: { id } })
