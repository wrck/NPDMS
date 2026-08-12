import request from '@/config/axios'

export interface CustomerServiceLevelVO {
  id?: number
  customerId: number
  customerName?: string
  level: string
  validFrom?: string
  validTo?: string
  status: number
  responseTimeHours?: number
  proactiveService?: boolean
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/service-level'

export const getServiceLevelPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getServiceLevel = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createServiceLevel = (data: CustomerServiceLevelVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateServiceLevel = (data: CustomerServiceLevelVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteServiceLevel = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
