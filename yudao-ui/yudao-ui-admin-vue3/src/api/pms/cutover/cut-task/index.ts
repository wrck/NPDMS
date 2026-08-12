import request from '@/config/axios'

export interface CutTaskVO {
  id?: number
  projectId: number
  code: string
  name: string
  cutoverType?: string
  networkMode?: string
  sourceType?: string
  sourceId?: number
  riskLevel?: string
  scheduledTime?: Date
  actualTime?: Date
  status?: number
  approvalOpinion?: string
  remark?: string
  version?: number
  createTime?: Date
}

export interface CutTaskApproveVO {
  id: number
  approvalOpinion?: string
  version?: number
}

const baseUrl = '/pms/cut-task'

export const getCutTaskPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCutTask = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCutTask = (data: CutTaskVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCutTask = (data: CutTaskVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCutTask = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitForReview = (id: number) =>
  request.put({ url: `${baseUrl}/submit-for-review`, params: { id } })
export const approveCutTask = (data: CutTaskApproveVO) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const rejectCutTask = (data: CutTaskApproveVO) =>
  request.put({ url: `${baseUrl}/reject`, data })
export const startExecution = (id: number) =>
  request.put({ url: `${baseUrl}/start-execution`, params: { id } })
export const completeExecution = (id: number) =>
  request.put({ url: `${baseUrl}/complete-execution`, params: { id } })
export const startObservation = (id: number) =>
  request.put({ url: `${baseUrl}/start-observation`, params: { id } })
export const completeObservation = (id: number) =>
  request.put({ url: `${baseUrl}/complete-observation`, params: { id } })
export const rollbackCutTask = (id: number) =>
  request.put({ url: `${baseUrl}/rollback`, params: { id } })
export const terminateCutTask = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
