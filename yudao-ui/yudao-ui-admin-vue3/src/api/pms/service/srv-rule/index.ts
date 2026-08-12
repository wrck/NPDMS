import request from '@/config/axios'

export interface SrvRuleVO {
  id?: number
  code: string
  name: string
  ruleType?: string
  ruleVersion?: string
  content?: string
  status?: number
  effectiveTime?: Date
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/srv-rule'

export const getSrvRulePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSrvRule = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createSrvRule = (data: SrvRuleVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateSrvRule = (data: SrvRuleVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteSrvRule = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const publishSrvRule = (id: number) =>
  request.put({ url: `${baseUrl}/publish`, params: { id } })
export const disableSrvRule = (id: number) =>
  request.put({ url: `${baseUrl}/disable`, params: { id } })
