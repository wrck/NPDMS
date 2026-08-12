import request from '@/config/axios'

export interface FormInstanceVO {
  id?: number
  code: string
  projectId: number
  templateId: number
  templateSnapshot?: string
  formData?: string
  name?: string
  status?: number
  version?: number
  submitTime?: string
  approverUserId?: number
  approveOpinion?: string
  approveTime?: string
  fillerUserId?: number
  remark?: string
  createTime?: string
}

const baseUrl = '/pms/eng-form-instance'

export const getFormInstancePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getFormInstance = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createFormInstance = (data: FormInstanceVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateFormInstance = (data: FormInstanceVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteFormInstance = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const saveFormInstance = (data: FormInstanceVO) =>
  request.put({ url: `${baseUrl}/save`, data })
export const submitFormInstance = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveFormInstance = (data: { id: number; approveAction: string; approverUserId?: number; approveOpinion?: string; version?: number }) =>
  request.put({ url: `${baseUrl}/approve`, data })
