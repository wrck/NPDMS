import request from '@/config/axios'

export interface MaterialRequisitionVO {
  id?: number
  projectId: number
  code: string
  name: string
  requisitionType?: string
  equipmentId?: number
  materialName: string
  materialCode?: string
  specification?: string
  quantity: number
  unit?: string
  neededDate?: string
  warehouseId?: number
  warehouseName?: string
  stockStatus?: string
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

const baseUrl = '/pms/eng-material-req'

export const getMaterialRequisitionPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getMaterialRequisition = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createMaterialRequisition = (data: MaterialRequisitionVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateMaterialRequisition = (data: MaterialRequisitionVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteMaterialRequisition = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitMaterialRequisition = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveMaterialRequisition = (data: { id: number; approveAction: string; approverUserId?: number; approveOpinion?: string }) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const withdrawMaterialRequisition = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
export const terminateMaterialRequisition = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
