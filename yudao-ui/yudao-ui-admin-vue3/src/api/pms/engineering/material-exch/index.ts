import request from '@/config/axios'

export interface MaterialExchangeVO {
  id?: number
  projectId: number
  code: string
  name: string
  exchangeType?: string
  equipmentId?: number
  materialName: string
  materialCode?: string
  specification?: string
  quantity: number
  unit?: string
  originalOrderNo?: string
  reason: string
  reasonFiles?: string
  crmPushStatus?: string
  crmPushTime?: string
  crmOrderNo?: string
  newEquipmentId?: number
  exchangeProgress?: string
  applicantUserId: number
  applyTime: string
  approverUserId?: number
  approveTime?: string
  approveOpinion?: string
  approveAction?: string
  status?: number
  remark?: string
  version?: number
  createTime?: string
}

const baseUrl = '/pms/eng-material-exch'

export const getMaterialExchangePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getMaterialExchange = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createMaterialExchange = (data: MaterialExchangeVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateMaterialExchange = (data: MaterialExchangeVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteMaterialExchange = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitMaterialExchange = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveMaterialExchange = (data: { id: number; approveAction: string; approverUserId?: number; approveOpinion?: string }) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const withdrawMaterialExchange = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
export const terminateMaterialExchange = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
export const pushCrmMaterialExchange = (id: number, crmOrderNo?: string) =>
  request.put({ url: `${baseUrl}/push-crm`, params: { id, crmOrderNo } })
