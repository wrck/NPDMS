import request from '@/config/axios'

export interface CustomerContactVO {
  id?: number
  customerId?: number
  name: string
  department?: string
  title?: string
  mobile?: string
  phone?: string
  email?: string
  primaryFlag: boolean
  status: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/customer-contact'

export const getContactPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getContact = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createContact = (data: CustomerContactVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateContact = (data: CustomerContactVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteContact = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getContactListByCustomerId = (customerId: number) =>
  request.get({ url: `${baseUrl}/list-by-customer`, params: { customerId } })
