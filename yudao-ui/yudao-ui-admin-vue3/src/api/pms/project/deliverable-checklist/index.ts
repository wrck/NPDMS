import request from '@/config/axios'

export interface DeliverableChecklistVO {
  id?: number
  projectId: number
  code: string
  name: string
  deliverableType?: string
  version?: string
  signedFlag?: boolean
  validFlag?: boolean
  submittedDate?: Date
  attachmentUrl?: string
  status?: number
  remark?: string
  versionNum?: number
  createTime?: Date
}

const baseUrl = '/pms/acc-deliverable-checklist'

export const getDeliverableChecklistPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getDeliverableChecklist = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createDeliverableChecklist = (data: DeliverableChecklistVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateDeliverableChecklist = (data: DeliverableChecklistVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteDeliverableChecklist = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1已提交 2已通过 3已驳回
export const submitDeliverableChecklist = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const passDeliverableChecklist = (id: number) =>
  request.put({ url: `${baseUrl}/pass`, params: { id } })
export const rejectDeliverableChecklist = (id: number) =>
  request.put({ url: `${baseUrl}/reject`, params: { id } })
