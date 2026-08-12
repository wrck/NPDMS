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
}

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

const baseUrl = '/pms/customer'

export const getCustomerPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCustomer = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCustomer = (data: CustomerVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCustomer = (data: CustomerVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCustomer = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })

const contactBaseUrl = '/pms/customer-contact'

export const getContactPage = (params: PageParam) =>
  request.get({ url: `${contactBaseUrl}/page`, params })
export const getContact = (id: number) =>
  request.get({ url: `${contactBaseUrl}/get`, params: { id } })
export const createContact = (data: CustomerContactVO) =>
  request.post({ url: `${contactBaseUrl}/create`, data })
export const updateContact = (data: CustomerContactVO) =>
  request.put({ url: `${contactBaseUrl}/update`, data })
export const deleteContact = (id: number) =>
  request.delete({ url: `${contactBaseUrl}/delete`, params: { id } })
export const getContactListByCustomerId = (customerId: number) =>
  request.get({ url: `${contactBaseUrl}/list-by-customer`, params: { customerId } })
