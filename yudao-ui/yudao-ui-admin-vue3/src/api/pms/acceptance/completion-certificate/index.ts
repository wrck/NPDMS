import request from '@/config/axios'

export interface CompletionCertificateVO {
  id?: number
  projectId: number
  code: string
  name: string
  customerId?: number
  certificateNo?: string
  signedDate?: Date
  satisfactionScore?: number
  customerOpinion?: string
  signatureUrl?: string
  attachmentUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/acc-completion-certificate'

export const getCompletionCertificatePage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCompletionCertificate = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCompletionCertificate = (data: CompletionCertificateVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCompletionCertificate = (data: CompletionCertificateVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCompletionCertificate = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回
export const submitCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const customerConfirmCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/customer-confirm`, params: { id } })
export const rejectCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/reject`, params: { id } })
export const archiveCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
