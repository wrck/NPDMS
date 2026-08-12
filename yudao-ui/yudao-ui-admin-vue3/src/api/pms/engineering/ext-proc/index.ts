import request from '@/config/axios'

export interface ExternalProcurementVO {
  id?: number
  projectId: number
  code: string
  name: string
  procurementType?: string
  materialName: string
  materialCode?: string
  specification?: string
  brand?: string
  model?: string
  quantity: number
  unit?: string
  unitPrice?: number
  totalPrice?: number
  currency?: string
  supplierName?: string
  supplierContact?: string
  supplierPhone?: string
  neededDate?: string
  expectedDeliveryDate?: string
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

const baseUrl = '/pms/eng-ext-proc'

export const getExternalProcurementPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getExternalProcurement = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createExternalProcurement = (data: ExternalProcurementVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateExternalProcurement = (data: ExternalProcurementVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteExternalProcurement = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitExternalProcurement = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveExternalProcurement = (data: { id: number; approveAction: string; approverUserId?: number; approveOpinion?: string }) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const withdrawExternalProcurement = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
export const terminateExternalProcurement = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
