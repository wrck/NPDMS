import request from '@/config/axios'

export interface OutsourceRequestVO {
  id?: number
  projectId: number
  code: string
  name: string
  outsourceType?: string
  workContent: string
  workQuantity?: number
  workUnit?: string
  plannedStartDate?: string
  plannedEndDate?: string
  estimatedCost?: number
  actualCost?: number
  currency?: string
  vendorId?: number
  vendorName?: string
  contactUserId?: number
  contactPhone?: string
  attachmentFiles?: string
  triggerSource?: string
  triggerRefId?: number
  applicantUserId: number
  applyTime: string
  approverUserId?: number
  approveTime?: string
  approveOpinion?: string
  approveAction?: string
  bpmProcessInstanceId?: string
  status?: number
  remark?: string
  version?: number
  createTime?: string
}

const baseUrl = '/pms/eng-outsource'

export const getOutsourceRequestPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getOutsourceRequest = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createOutsourceRequest = (data: OutsourceRequestVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateOutsourceRequest = (data: OutsourceRequestVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteOutsourceRequest = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitOutsourceRequest = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveOutsourceRequest = (data: { id: number; approveAction: string; approverUserId?: number; approveOpinion?: string }) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const withdrawOutsourceRequest = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
export const terminateOutsourceRequest = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
