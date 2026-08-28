import request from '@/config/axios'

export interface CustomerVO {
  id?: number
  code: string
  name: string
  shortName?: string
  status: number
  address?: string
  remark?: string
  version?: number
  createTime?: Date
  legacyReadOnly?: boolean
  replacementPath?: string
}

const baseUrl = '/pms/customer'

export const getCustomerPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })

export const getCustomer = (id: number) => request.get({ url: `${baseUrl}/get`, params: { id } })
