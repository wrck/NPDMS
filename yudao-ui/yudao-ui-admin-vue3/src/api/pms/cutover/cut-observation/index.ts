import request from '@/config/axios'

export interface CutObservationVO {
  id?: number
  taskId: number
  code: string
  observationStart?: Date
  observationEnd?: Date
  observerUserId?: number
  leftoverItems?: string
  leftoverStatus?: number
  conclusion?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/cut-observation'

export const getCutObservationPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCutObservation = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCutObservation = (data: CutObservationVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCutObservation = (data: CutObservationVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCutObservation = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const passCutObservation = (id: number) =>
  request.put({ url: `${baseUrl}/pass`, params: { id } })
export const markAbnormalCutObservation = (id: number) =>
  request.put({ url: `${baseUrl}/mark-abnormal`, params: { id } })
export const archiveCutObservation = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
