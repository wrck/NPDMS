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

const baseUrl = '/pms/plan-change'

/** V1.7历史只读证据；PRE-01写入统一使用construction-plan API。 */
export const getPlanChangePage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getPlanChange = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const getPlanChangeSnapshots = (changeRequestId: number) =>
  request.get({ url: `${baseUrl}/snapshots`, params: { changeRequestId } })
