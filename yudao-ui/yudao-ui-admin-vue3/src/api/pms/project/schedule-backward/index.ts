import request from '@/config/axios'

export interface ScheduleBackwardItemVO {
  id?: number
  backwardId?: number
  phaseId?: number
  phaseName?: string
  plannedStartDate?: string
  plannedEndDate?: string
  recommendedLatestDate?: string
  hasConflict?: boolean
  conflictReason?: string
  sort?: number
  createTime?: Date
}

export interface ScheduleBackwardVO {
  id?: number
  projectId: number
  targetDate: string
  projectType: string
  status?: number
  conflictSummary?: string
  remark?: string
  version?: number
  createTime?: Date
  items?: ScheduleBackwardItemVO[]
}

const baseUrl = '/pms/schedule-backward'

export const getScheduleBackwardPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getScheduleBackward = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createScheduleBackward = (data: ScheduleBackwardVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateScheduleBackward = (data: ScheduleBackwardVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteScheduleBackward = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getScheduleBackwardItems = (backwardId: number) =>
  request.get({ url: `${baseUrl}/items`, params: { backwardId } })
export const calculateScheduleBackward = (id: number) =>
  request.post({ url: `${baseUrl}/calculate`, params: { id } })
export const applyScheduleBackward = (id: number) =>
  request.post({ url: `${baseUrl}/apply`, params: { id } })
