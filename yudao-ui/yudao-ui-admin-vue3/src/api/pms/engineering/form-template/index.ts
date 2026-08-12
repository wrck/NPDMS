import request from '@/config/axios'

export interface FormTemplateVO {
  id?: number
  code: string
  name: string
  productType?: string
  conf: string
  fields: string
  description?: string
  status?: number
  version?: number
  createTime?: string
}

const baseUrl = '/pms/eng-form-template'

export const getFormTemplatePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getFormTemplate = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createFormTemplate = (data: FormTemplateVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateFormTemplate = (data: FormTemplateVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteFormTemplate = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const publishFormTemplate = (id: number) =>
  request.put({ url: `${baseUrl}/publish`, params: { id } })
export const disableFormTemplate = (id: number) =>
  request.put({ url: `${baseUrl}/disable`, params: { id } })
export const enableFormTemplate = (id: number) =>
  request.put({ url: `${baseUrl}/enable`, params: { id } })
export const getPublishedFormTemplateList = (productType?: string) =>
  request.get({ url: `${baseUrl}/published-list`, params: { productType } })
