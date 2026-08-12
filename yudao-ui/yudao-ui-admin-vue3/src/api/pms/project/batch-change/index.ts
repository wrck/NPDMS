import request from '@/config/axios'

export interface TeamBatchChangeItemVO {
  id?: number
  batchId?: number
  projectId?: number
  projectName?: string
  teamMemberId: number
  beforeRole?: string
  afterRole?: string
  status: number
  errorMessage?: string
  createTime?: Date
}

export interface TeamBatchChangeVO {
  id?: number
  batchNo?: string
  sourceUserId: number
  targetUserId: number
  scopeType: string
  projectIds?: number[]
  reason?: string
  status?: number
  totalCount?: number
  successCount?: number
  failureCount?: number
  remark?: string
  version?: number
  createTime?: Date
  items?: TeamBatchChangeItemVO[]
}

const baseUrl = '/pms/batch-change'

export const getTeamBatchChangePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getTeamBatchChange = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createTeamBatchChange = (data: TeamBatchChangeVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateTeamBatchChange = (data: TeamBatchChangeVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteTeamBatchChange = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getTeamBatchChangeItems = (batchId: number) =>
  request.get({ url: `${baseUrl}/items`, params: { batchId } })
export const executeTeamBatchChange = (id: number) =>
  request.post({ url: `${baseUrl}/execute`, params: { id } })
