import request from '@/config/axios'

export interface CursorPage<T> {
  items: T[]
  nextCursor?: string
  hasMore: boolean
}

export interface PreparationFormVO {
  formInstanceId: number
  formCode: string
  formVersion: number
  schemaSnapshot: string
  valueSnapshot: string
  status: string
  frozenAt?: string
  frozenBy?: number
  version: number
}

export interface PreparationItemVO {
  itemId: number
  sourceItemId?: number
  itemCode: string
  itemName: string
  sortOrder: number
  applicability: string
  confirmationStatus: string
  outsourced: boolean
  assigneeUserId?: number
  assigneeEffectiveFrom?: string
  notApplicableReason?: string
  siteResultCode?: string
  siteResultDetail?: string
  evidenceReferenceSnapshot?: string
  evidencePolicySnapshot?: string
  sourcePolicySnapshot?: string
  waiverPolicySnapshot?: string
  sources: PreparationSourceVO[]
  allowedActions: string[]
  version: number
  form: PreparationFormVO
}

export interface PreparationSourceVO {
  sourceReferenceId: number
  sourceTypeCode: string
  sourceObjectType: string
  sourceObjectId: string
  sourceReferenceKey: string
  normalizedResultCode?: string
  sourceFactVersion?: string
  sourceWatermark?: string
  syncStatusCode: string
  lastSuccessResultCode?: string
  lastSuccessFactVersion?: string
  lastSuccessWatermark?: string
  lastSuccessAt?: string
  lastSyncedAt?: string
  lastSyncErrorCode?: string
  sourceVersion: number
}

export interface PreparationVO {
  preparationId: number
  projectId: number
  preparationType: string
  businessVersion: number
  current: boolean
  templateId: number
  templateRevisionId: number
  fixedFormCatalogVersion: number
  status: string
  readinessStatus: string
  latestReadinessSnapshotId?: number
  inputVersion: number
  readinessVersion: number
  snapshotCurrent: boolean
  submittedAt?: string
  confirmedAt?: string
  returnedAt?: string
  returnReason?: string
  version: number
  createdAt: string
  allowedActions: string[]
}

export interface EvidenceReference {
  artifactId: number
  versionNo: number
  referenceKey: string
  fileFactVersion: {
    artifactVersion: number
    referenceVersion: number
    availabilityVersion: number
  }
  scopeVersion: number
}

export interface AssignmentCandidateVO {
  userId: number
  username: string
  nickname: string
  employeeNo?: string
  companyId?: number
  departmentId?: number
  departmentCode?: string
  departmentName?: string
}

export interface PatchPreparationItemReqVO {
  expectedPreparationVersion: number
  expectedInputVersion: number
  expectedReadinessVersion: number
  expectedFormVersion: number
  expectedProjectVersion: number
  applicabilityCode?: string
  outsourced?: boolean
  assigneeUserId?: number | null
  notApplicableReason?: string | null
  siteResultCode?: string | null
  siteResultDetail?: string | null
  formValueSnapshot?: string
  evidenceReferences?: EvidenceReference[]
}

export interface PreparationReadinessSnapshotVO {
  snapshotId: number
  snapshotNo: number
  result: string
  ruleVersion: number
  projectScopeVersion: number
  inputVersion: number
  preparationVersion: number
  readinessVersion: number
  itemFacts: string
  fileFacts: string
  sourceFacts: string
  waiverFacts: string
  blockers: string
  evaluatedBy: number
  evaluatedAt: string
}

export interface WaiverVO {
  waiverId: number
  preparationId: number
  itemId: number
  itemCode: string
  waiverNo: number
  statusCode: string
  blockerCodesSnapshot: string
  reason?: string
  risk?: string
  compensation?: string
  validFrom?: string | number
  validUntil?: string | number
  approvalRoleCode?: string
  applicantUserId: number
  submittedAt?: string
  decidedBy?: number
  decidedAt?: string
  decisionOpinion?: string
  withdrawnAt?: string
  version: number
  allowedActions: string[]
}

export interface WaiverCommandReqVO {
  expectedInputVersion: number
  expectedReadinessVersion: number
  expectedItemVersion: number
  expectedWaiverVersion?: number
  expectedProjectVersion: number
  blockerCodes?: string[]
  reason?: string
  risk?: string
  compensation?: string
  validFrom?: string | number
  validUntil?: string | number
  opinion?: string
}

export interface SourceRefreshReqVO {
  expectedPreparationVersion: number
  expectedInputVersion: number
  expectedReadinessVersion: number
  expectedItemVersion: number
  expectedSourceVersion?: number
  expectedProjectVersion: number
  sourceTypeCode: string
  sourceObjectType: string
  sourceObjectId: string
  sourceReferenceKey: string
}

const baseUrl = '/api/v1/pms/preparations'

export const getCurrent = (projectId: number) =>
  request.get<PreparationVO | null>({ url: baseUrl, params: { projectId, type: 'PRE_02' } })

export const getItems = (preparationId: number, params: { cursor?: string; pageSize?: number }) =>
  request.get<CursorPage<PreparationItemVO>>({
    url: `${baseUrl}/${preparationId}/items`,
    params
  })

export const getAssignmentCandidates = (
  preparationId: number,
  params: { keyword?: string; pageNo?: number; pageSize?: number }
) =>
  request.get<PageResult<AssignmentCandidateVO[]>>({
    url: `${baseUrl}/${preparationId}/assignment-candidates`,
    params
  })

export const patchItem = (
  preparationId: number,
  itemId: number,
  itemVersion: number,
  data: PatchPreparationItemReqVO
) =>
  request.put({
    method: 'PATCH',
    url: `${baseUrl}/${preparationId}/items/${itemId}`,
    data,
    headers: { 'If-Match': String(itemVersion) }
  })

export const submit = (
  preparation: PreparationVO,
  expectedProjectVersion: number,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${preparation.preparationId}/actions/submit`,
    data: { expectedProjectVersion },
    headers: {
      'If-Match': String(preparation.version),
      'Idempotency-Key': idempotencyKey
    }
  })

export const reviewItem = (
  preparation: PreparationVO,
  item: PreparationItemVO,
  action: 'confirm' | 'confirm-not-applicable' | 'return',
  expectedProjectVersion: number,
  reason: string | undefined,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${preparation.preparationId}/items/${item.itemId}/actions/${action}`,
    data: { expectedPreparationVersion: preparation.version, expectedProjectVersion, reason },
    headers: { 'If-Match': String(item.version), 'Idempotency-Key': idempotencyKey }
  })

export const evaluateReadiness = (
  preparation: PreparationVO,
  expectedProjectVersion: number,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${preparation.preparationId}/actions/evaluate-readiness`,
    data: { expectedProjectVersion },
    headers: {
      'If-Match': String(preparation.version),
      'Idempotency-Key': idempotencyKey
    }
  })

export const refreshSource = (
  preparationId: number,
  itemId: number,
  data: SourceRefreshReqVO,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${preparationId}/items/${itemId}/sources/actions/refresh`,
    data,
    headers: {
      'If-Match': String(data.expectedPreparationVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const getWaivers = (
  preparationId: number,
  itemId: number,
  params: { cursor?: string; pageSize?: number }
) =>
  request.get<CursorPage<WaiverVO>>({
    url: `${baseUrl}/${preparationId}/items/${itemId}/waivers`,
    params
  })

export const createWaiver = (
  preparation: PreparationVO,
  item: PreparationItemVO,
  expectedProjectVersion: number,
  data: Omit<
    WaiverCommandReqVO,
    | 'expectedInputVersion'
    | 'expectedReadinessVersion'
    | 'expectedItemVersion'
    | 'expectedProjectVersion'
  >,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${preparation.preparationId}/items/${item.itemId}/waivers`,
    data: {
      ...data,
      expectedInputVersion: preparation.inputVersion,
      expectedReadinessVersion: preparation.readinessVersion,
      expectedItemVersion: item.version,
      expectedProjectVersion
    },
    headers: { 'If-Match': String(preparation.version), 'Idempotency-Key': idempotencyKey }
  })

export const actWaiver = (
  preparation: PreparationVO,
  item: PreparationItemVO,
  waiver: WaiverVO,
  action: 'submit' | 'approve' | 'reject' | 'withdraw',
  expectedProjectVersion: number,
  opinion: string | undefined,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${preparation.preparationId}/items/${item.itemId}/waivers/${waiver.waiverId}/actions/${action}`,
    data: {
      expectedInputVersion: preparation.inputVersion,
      expectedReadinessVersion: preparation.readinessVersion,
      expectedItemVersion: item.version,
      expectedWaiverVersion: waiver.version,
      expectedProjectVersion,
      opinion
    },
    headers: { 'If-Match': String(preparation.version), 'Idempotency-Key': idempotencyKey }
  })

export const getReadinessSnapshots = (
  preparationId: number,
  params: { cursor?: string; pageSize?: number }
) =>
  request.get<CursorPage<PreparationReadinessSnapshotVO>>({
    url: `${baseUrl}/${preparationId}/readiness-snapshots`,
    params
  })
