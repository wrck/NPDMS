import request from '@/config/axios'

export interface CutRiskVO {
  id?: number
  taskId: number
  code: string
  name: string
  riskType?: string
  description?: string
  impact?: string
  mitigation?: string
  ownerUserId?: number
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/cut-risk'

export const getCutRiskPage = (params: PmsTaskPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCutRisk = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCutRisk = (data: CutRiskVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCutRisk = (data: CutRiskVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCutRisk = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const startProcessCutRisk = (id: number) =>
  request.put({ url: `${baseUrl}/start-process`, params: { id } })
export const closeCutRisk = (id: number) =>
  request.put({ url: `${baseUrl}/close`, params: { id } })
export const suspendCutRisk = (id: number) =>
  request.put({ url: `${baseUrl}/suspend`, params: { id } })
