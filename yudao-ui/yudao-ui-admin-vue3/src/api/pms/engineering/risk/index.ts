import request from '@/config/axios'

export interface RiskVO {
  id?: number
  code: string
  projectId: number
  name: string
  riskType?: string
  deviceId?: number
  deviceSerial?: string
  deviceModel?: string
  scenario?: string
  riskLevel?: string
  status?: number
  version?: number
  crmSynced?: boolean
  crmSyncTime?: string
  handlerUserId?: number
  handleOpinion?: string
  handleTime?: string
  creatorUserId?: number
  remark?: string
  createTime?: string
}

const baseUrl = '/pms/eng-risk'

export const getRiskPage = (params: PmsProjectPageParam) => request.get({ url: `${baseUrl}/page`, params })
export const getRisk = (id: number) => request.get({ url: `${baseUrl}/get`, params: { id } })
export const createRisk = (data: RiskVO) => request.post({ url: `${baseUrl}/create`, data })
export const updateRisk = (data: RiskVO) => request.put({ url: `${baseUrl}/update`, data })
export const deleteRisk = (id: number) => request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const confirmRisk = (data: { id: number; handlerUserId?: number; handleOpinion?: string; version?: number }) =>
  request.put({ url: `${baseUrl}/confirm`, data })
export const syncCrmRisk = (id: number) => request.put({ url: `${baseUrl}/sync-crm`, params: { id } })
export const closeRisk = (data: { id: number; handlerUserId?: number; handleOpinion?: string; version?: number }) =>
  request.put({ url: `${baseUrl}/close`, data })
