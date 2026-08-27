import request from '@/config/axios'

export type RequirementAnalysisFieldType =
  | 'RICH_TEXT'
  | 'TEXT'
  | 'NUMBER'
  | 'BOOLEAN'
  | 'SINGLE_SELECT'
  | 'MULTI_SELECT'

export interface RequirementAnalysisOptionVO {
  code: string
  label: string
}

export interface RequirementAnalysisAttachmentVO {
  artifactId: number
  versionNo: number
  referenceId?: number
  referenceKey: string
  name?: string
  sizeBytes?: number
  mediaType?: string
  availabilityStatus?: string
  referenceStatus?: string
  fileFactVersion: {
    artifactVersion: number
    referenceVersion: number
    availabilityVersion: number
  }
  scopeVersion: number
}

export type RequirementAnalysisAttachmentSyncStatus = 'IN_SYNC' | 'PENDING' | 'UNKNOWN'

export type RequirementAnalysisAttachmentSyncErrorCode =
  | 'ATTACHMENT_SET_PENDING'
  | 'FACT_PROVIDER_UNAVAILABLE'

export type RequirementAnalysisCompletionBlockerCode =
  | 'REQUIRED_VALUE_MISSING'
  | 'VALUE_INVALID'
  | 'ATTACHMENT_SET_PENDING'
  | 'ATTACHMENT_FACT_INVALID'
  | 'FACT_PROVIDER_UNAVAILABLE'

export interface RequirementAnalysisCompletionBlockerVO {
  code: RequirementAnalysisCompletionBlockerCode
  sectionCode: string
}

export interface RequirementAnalysisVersionSummaryVO {
  preparationId: number
  projectId: number
  businessVersion: number
  sourcePreparationId?: number
  status: 'DRAFT' | 'COMPLETED'
  currentDraft: boolean
  currentEffective: boolean
  contentVersion: number
  version: number
  templateId: number
  templateRevisionId: number
  completedBy?: number
  completedAt?: string
  completionBlockers: RequirementAnalysisCompletionBlockerVO[]
  allowedActions: string[]
}

export interface RequirementAnalysisSectionVO {
  sectionId: number
  sourceSectionId?: number
  sectionCode: string
  sectionName: string
  sectionKind: 'CORE' | 'EXTENSION'
  fieldType: RequirementAnalysisFieldType
  required: boolean
  dictionaryType?: string
  sortOrder: number
  schemaSnapshot?: string
  valueSnapshot?: string | null
  attachments: RequirementAnalysisAttachmentVO[]
  attachmentSyncStatus: RequirementAnalysisAttachmentSyncStatus
  currentActiveFacts?: RequirementAnalysisAttachmentVO[] | null
  attachmentSyncErrorCode?: RequirementAnalysisAttachmentSyncErrorCode | null
  version: number
  allowedActions: string[]
}

export interface RequirementAnalysisDetailVO extends RequirementAnalysisVersionSummaryVO {
  sections: RequirementAnalysisSectionVO[]
}

export interface RequirementAnalysisOverviewVO {
  projectId: number
  currentEffective: RequirementAnalysisVersionSummaryVO | null
  draft: RequirementAnalysisVersionSummaryVO | null
  allowedActions: string[]
}

export interface RequirementAnalysisCommandResultVO {
  operationId?: string
  preparationId?: number
  version?: number
}

export interface RequirementAnalysisHistoryVO {
  items: RequirementAnalysisVersionSummaryVO[]
  nextCursor?: string
  hasMore: boolean
}

export interface RequirementAnalysisSectionDiffVO {
  sectionCode: string
  changeType: 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED'
  contentChanged: boolean
  attachmentsChanged: boolean
}

export interface RequirementAnalysisCompareVO {
  sourcePreparationId: number
  sourceBusinessVersion: number
  targetPreparationId: number
  targetBusinessVersion: number
  sections: RequirementAnalysisSectionDiffVO[]
}

export interface PatchRequirementAnalysisSectionReqVO {
  submittedFields: Array<'value' | 'attachments'>
  value?: unknown
  attachments?: Array<
    Pick<
      RequirementAnalysisAttachmentVO,
      'artifactId' | 'versionNo' | 'referenceKey' | 'fileFactVersion' | 'scopeVersion'
    >
  >
  expectedPreparationVersion: number
  expectedContentVersion: number
  expectedProjectVersion: number
}

const baseUrl = '/api/v1/pms/preparations'

export const getCurrent = (projectId: number) =>
  request.get<RequirementAnalysisOverviewVO>({
    url: baseUrl,
    params: { projectId, type: 'PRE_04' }
  })

export const createInitialDraft = (projectId: number, idempotencyKey: string) =>
  request.post<RequirementAnalysisCommandResultVO>({
    url: baseUrl,
    data: { projectId, type: 'PRE_04' },
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const getDetail = (preparationId: number) =>
  request.get<RequirementAnalysisDetailVO>({
    url: `${baseUrl}/${preparationId}`,
    params: { type: 'PRE_04' }
  })

export const patchSection = (
  preparationId: number,
  sectionId: number,
  data: PatchRequirementAnalysisSectionReqVO
) =>
  request.put<RequirementAnalysisCommandResultVO>({
    method: 'PATCH',
    url: `${baseUrl}/${preparationId}/items/${sectionId}`,
    params: { type: 'PRE_04' },
    data,
    headers: { 'If-Match': String(data.expectedPreparationVersion) }
  })

export const completeDraft = (
  preparationId: number,
  expectedPreparationVersion: number,
  expectedContentVersion: number,
  expectedProjectVersion: number,
  idempotencyKey: string
) =>
  request.post<RequirementAnalysisCommandResultVO>({
    url: `${baseUrl}/${preparationId}/actions/submit`,
    params: { type: 'PRE_04' },
    data: { expectedContentVersion, expectedProjectVersion },
    headers: {
      'If-Match': String(expectedPreparationVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const createNextDraft = (
  preparationId: number,
  expectedPreparationVersion: number,
  expectedContentVersion: number,
  expectedProjectVersion: number,
  idempotencyKey: string
) =>
  request.post<RequirementAnalysisCommandResultVO>({
    url: `${baseUrl}/${preparationId}/actions/create-draft`,
    data: { expectedContentVersion, expectedProjectVersion },
    headers: {
      'If-Match': String(expectedPreparationVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const getHistory = (projectId: number, params: { cursor?: string; pageSize?: number }) =>
  request.get<RequirementAnalysisHistoryVO>({
    url: baseUrl,
    params: { projectId, type: 'PRE_04', history: true, ...params }
  })

export const compareVersions = (preparationId: number, targetPreparationId: number) =>
  request.get<RequirementAnalysisCompareVO>({
    url: `${baseUrl}/${preparationId}/compare`,
    params: { targetPreparationId }
  })
