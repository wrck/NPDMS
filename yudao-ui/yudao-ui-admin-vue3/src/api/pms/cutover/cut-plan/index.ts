import request from '@/config/axios'

export interface CutPlanVO {
  id?: number
  taskId: number
  code: string
  name: string
  preCheck?: string
  procedure?: string
  verification?: string
  rollback?: string
  level?: string
  status?: number
  approvedBy?: number
  approvedTime?: Date
  approvalOpinion?: string
  baselineVersion?: number
  remark?: string
  version?: number
  createTime?: Date
}

export interface CutPlanApproveVO {
  id: number
  approvalOpinion?: string
  version?: number
}

const baseUrl = '/pms/cut-plan'

export const getCutPlanPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCutPlan = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCutPlan = (data: CutPlanVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCutPlan = (data: CutPlanVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCutPlan = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitPlanForReview = (id: number) =>
  request.put({ url: `${baseUrl}/submit-for-review`, params: { id } })
export const approveCutPlan = (data: CutPlanApproveVO) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const rejectCutPlan = (data: CutPlanApproveVO) =>
  request.put({ url: `${baseUrl}/reject`, data })
export const terminateCutPlan = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
