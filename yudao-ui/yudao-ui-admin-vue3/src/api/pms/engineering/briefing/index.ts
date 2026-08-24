import request from '@/config/axios'

export interface BriefingVO {
  id?: number
  code: string
  projectId: number
  name: string
  briefingType?: string
  templateId?: number
  templateSnapshot?: string
  sourceSnapshot?: string
  content?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  fileChecksum?: string
  status?: number
  version?: number
  generateTime?: string
  publishTime?: string
  approverUserId?: number
  approveOpinion?: string
  approveTime?: string
  creatorUserId?: number
  remark?: string
  createTime?: string
}

const baseUrl = '/pms/eng-briefing'

export const getBriefingPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getBriefing = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createBriefing = (data: BriefingVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateBriefing = (data: BriefingVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteBriefing = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const generateBriefing = (data: { id: number; templateId?: number; sourceSnapshot?: string; version?: number }) =>
  request.put({ url: `${baseUrl}/generate`, data })
export const approveBriefing = (data: { id: number; approveAction: string; approverUserId?: number; approveOpinion?: string; version?: number }) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const publishBriefing = (id: number) =>
  request.put({ url: `${baseUrl}/publish`, params: { id } })
export const terminateBriefing = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
