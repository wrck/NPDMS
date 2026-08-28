import request from '@/config/axios'

export type DurationCalculationBasis = 'DATE_RANGE' | 'DURATION_FROM_START'
export type DurationChangeStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'

export interface ConstructionPlanRevisionVO {
  revisionId: number
  revisionNo: number
  calculationBasis: DurationCalculationBasis
  startDate: string
  endDate: string
  durationDays: number
  sourceChangeId?: number
  frozenAt?: string
  effectiveAt?: string
  createdBy: number
  createdAt: string
  version: number
  current: boolean
}

export interface ConstructionPlanChangeVO {
  changeId: number
  baseRevisionId: number
  candidateRevisionId: number
  candidateRevision: ConstructionPlanRevisionVO
  status: DurationChangeStatus
  reasonType: string
  reasonDetail?: string
  customerEvidenceRequired: boolean
  customerEvidenceFileId?: number
  customerEvidenceFileVersion?: number
  customerEvidenceReferenceKey?: string
  processDefinitionKey?: string
  processInstanceId?: string
  submittedAt?: string
  applicantUserId: number
  approverUserId?: number
  approvedAt?: string
  approvalOpinion?: string
  createdAt: string
  version: number
}

export interface ConstructionPlanVO {
  planId: number
  projectId: number
  currentRevision: ConstructionPlanRevisionVO
  pendingChangeSummary?: ConstructionPlanChangeVO
  planRecalculationStatus: 'PENDING_RECALCULATION' | 'RECALCULATED' | 'RECALCULATION_FAILED'
  planRecalculationSourceRevisionId?: number
  planVersion: number
  allowedActions: string[]
}

export interface CursorPage<T> {
  items: T[]
  nextCursor?: string
  hasMore: boolean
}

export interface DurationInput {
  calculationBasis: DurationCalculationBasis
  startDate: string
  endDate?: string
  durationDays?: number
}

export interface CreateConstructionPlanReqVO extends DurationInput {
  projectId: number
  expectedProjectVersion: number
}

export interface CreateDurationChangeReqVO extends DurationInput {
  expectedProjectVersion: number
  reasonType: string
  reasonDetail?: string
  customerEvidenceFileId?: number
  customerEvidenceFileVersion?: number
  customerEvidenceReferenceKey?: string
}

export interface PatchDurationChangeReqVO {
  expectedProjectVersion: number
  calculationBasis?: DurationCalculationBasis
  startDate?: string
  endDate?: string | null
  durationDays?: number | null
  reasonType?: string
  reasonDetail?: string | null
  customerEvidenceFileId?: number | null
  customerEvidenceFileVersion?: number | null
  customerEvidenceReferenceKey?: string | null
}

export interface SubmitDurationChangeRespVO {
  changeId: number
  status: DurationChangeStatus
  processInstanceId: string
  changeVersion: number
  planVersion: number
}

const baseUrl = '/api/v1/pms/construction-plans'

export const getByProjectId = (projectId: number) =>
  request.get<ConstructionPlanVO | null>({ url: baseUrl, params: { projectId } })

export const getPlan = (planId: number) =>
  request.get<ConstructionPlanVO>({ url: `${baseUrl}/${planId}` })

export const getRevisions = (planId: number, params: { cursor?: string; pageSize?: number }) =>
  request.get<CursorPage<ConstructionPlanRevisionVO>>({
    url: `${baseUrl}/${planId}/revisions`,
    params
  })

export const getChanges = (planId: number, params: { cursor?: string; pageSize?: number }) =>
  request.get<CursorPage<ConstructionPlanChangeVO>>({
    url: `${baseUrl}/${planId}/changes`,
    params
  })

export const getChange = (planId: number, changeId: number) =>
  request.get<ConstructionPlanChangeVO>({ url: `${baseUrl}/${planId}/changes/${changeId}` })

export const createInitial = (data: CreateConstructionPlanReqVO, idempotencyKey: string) =>
  request.post<ConstructionPlanVO>({
    url: baseUrl,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const createChange = (
  planId: number,
  data: CreateDurationChangeReqVO,
  planVersion: number,
  idempotencyKey: string
) =>
  request.post<ConstructionPlanChangeVO>({
    url: `${baseUrl}/${planId}/changes`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(planVersion) }
  })

export const patchChange = (
  planId: number,
  changeId: number,
  data: PatchDurationChangeReqVO,
  changeVersion: number
) =>
  request.put<ConstructionPlanChangeVO>({
    method: 'PATCH',
    url: `${baseUrl}/${planId}/changes/${changeId}`,
    data,
    headers: { 'If-Match': String(changeVersion) }
  })

export const submitChange = (
  planId: number,
  changeId: number,
  expectedProjectVersion: number,
  changeVersion: number,
  idempotencyKey: string
) =>
  request.post<SubmitDurationChangeRespVO>({
    url: `${baseUrl}/${planId}/actions/submit`,
    data: { changeId, expectedProjectVersion },
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(changeVersion) }
  })
