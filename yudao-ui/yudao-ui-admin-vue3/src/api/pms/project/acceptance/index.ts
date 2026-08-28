import request from '@/config/axios'

export interface AcceptanceVO {
  id?: number
  projectId: number
  code: string
  name: string
  acceptanceType?: string
  signedDate?: Date
  conclusion?: string
  opinion?: string
  attachmentUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/acc-acceptance'

export const getAcceptancePage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getAcceptance = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createAcceptance = (data: AcceptanceVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateAcceptance = (data: AcceptanceVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteAcceptance = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1待提交 2审批中 3已通过 4已驳回 5已归档
export const submitAcceptance = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveAcceptance = (id: number) =>
  request.put({ url: `${baseUrl}/approve`, params: { id } })
export const passAcceptance = (id: number) =>
  request.put({ url: `${baseUrl}/pass`, params: { id } })
export const rejectAcceptance = (id: number) =>
  request.put({ url: `${baseUrl}/reject`, params: { id } })
export const archiveAcceptance = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
