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

/** V1.7历史只读证据；PRE-01写入统一使用construction-plan API。 */
export const getScheduleBackwardPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getScheduleBackward = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const getScheduleBackwardItems = (backwardId: number) =>
  request.get({ url: `${baseUrl}/items`, params: { backwardId } })
