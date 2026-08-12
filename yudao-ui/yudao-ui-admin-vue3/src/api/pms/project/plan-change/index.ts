import request from '@/config/axios'

export interface PlanChangePhaseSnapshotVO {
  id?: number
  changeRequestId?: number
  phaseId: number
  phaseName?: string
  beforePlanStart?: string
  beforePlanEnd?: string
  afterPlanStart?: string
  afterPlanEnd?: string
  changeRemark?: string
  createTime?: Date
}

export interface PlanChangeVO {
  id?: number
  projectId: number
  changeNo: string
  title: string
  changeType: string
  reason: string
  customerProofFiles?: string
  applicantUserId: number
  applyTime: string
  approverUserId?: number
  approveTime?: string
  approveOpinion?: string
  approveAction?: string
  baselineVersion?: number
  newBaselineVersion?: number
  status?: number
  remark?: string
  version?: number
  createTime?: Date
  phaseSnapshots: PlanChangePhaseSnapshotVO[]
}

export interface PlanChangeApproveReqVO {
  id: number
  approveAction: string
  approveOpinion?: string
  approverUserId: number
}

const baseUrl = '/pms/plan-change'

export const getPlanChangePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getPlanChange = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createPlanChange = (data: PlanChangeVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updatePlanChange = (data: PlanChangeVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deletePlanChange = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getPlanChangeSnapshots = (changeRequestId: number) =>
  request.get({ url: `${baseUrl}/snapshots`, params: { changeRequestId } })
export const submitPlanChange = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approvePlanChange = (data: PlanChangeApproveReqVO) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const withdrawPlanChange = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
export const terminatePlanChange = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
export const applyPlanChange = (id: number) =>
  request.put({ url: `${baseUrl}/apply`, params: { id } })
