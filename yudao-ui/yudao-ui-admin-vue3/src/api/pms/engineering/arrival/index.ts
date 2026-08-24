import request from '@/config/axios'

export interface ArrivalVO {
  id?: number
  projectId: number
  code: string
  arrivalTime?: Date
  receiverUserId?: number
  equipmentId?: number
  quantity?: number
  inspectionResult?: string
  exceptionRecord?: string
  attachmentUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/eng-arrival'

export const getArrivalPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getArrival = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createArrival = (data: ArrivalVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateArrival = (data: ArrivalVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteArrival = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const signArrival = (id: number) =>
  request.put({ url: `${baseUrl}/sign`, params: { id } })
export const markAbnormalArrival = (id: number) =>
  request.put({ url: `${baseUrl}/mark-abnormal`, params: { id } })
