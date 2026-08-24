import request from '@/config/axios'

export interface SrvReportVO {
  id?: number
  taskId: number
  code: string
  reportType?: string
  content?: string
  snapshot?: string
  generatedBy?: number
  generatedTime?: Date
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/srv-report'

export const getSrvReportPage = (params: PmsTaskPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSrvReport = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createSrvReport = (data: SrvReportVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateSrvReport = (data: SrvReportVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteSrvReport = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const generateSrvReport = (id: number) =>
  request.put({ url: `${baseUrl}/generate`, params: { id } })
export const archiveSrvReport = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
