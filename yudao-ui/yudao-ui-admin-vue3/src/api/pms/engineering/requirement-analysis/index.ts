import request from '@/config/axios'
import type { DynamicFormFileFactVO, JsonObject } from '@/api/pms/platform/dynamic-form'

export type RequirementAnalysisAction =
  | 'CREATE_INITIAL_DRAFT'
  | 'PATCH_FORM'
  | 'COMPLETE'
  | 'CREATE_DRAFT'

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
  | 'FORM_VALUE_INVALID'
  | 'CONTROLLED_FILE_INVALID'
  | 'FACT_PROVIDER_UNAVAILABLE'

export interface RequirementAnalysisCompletionBlockerVO {
  code: RequirementAnalysisCompletionBlockerCode
  fieldKey?: string
  message?: string
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
  dynamicFormInstanceId: number
  dynamicFormInstanceVersion: number
  dynamicFormRevisionNo: number
  completedBy?: number
  completedAt?: string
  completionBlockers: RequirementAnalysisCompletionBlockerVO[]
  allowedActions: RequirementAnalysisAction[]
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
  engineCode: string
  designerVersion: string
  rendererVersion: string
  formConfJson: JsonObject
  formRulesJson: JsonObject[]
  values: JsonObject
  controlledFiles: Record<string, DynamicFormFileFactVO[]>
  declarativeValidationResult: 'VALID' | 'INVALID' | 'UNKNOWN'
  /** 已取消章节候选的只读兼容字段；新工作区不读取。 */
  sections?: RequirementAnalysisSectionVO[]
}

export interface RequirementAnalysisOverviewVO {
  projectId: number
  currentEffective: RequirementAnalysisVersionSummaryVO | null
  draft: RequirementAnalysisVersionSummaryVO | null
  allowedActions: RequirementAnalysisAction[]
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

export interface RequirementAnalysisFieldDiffVO {
  fieldKey: string
  fieldLabel?: string
  changeType: 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED'
  sourceValue?: unknown
  targetValue?: unknown
  controlledFilesChanged: boolean
}

export interface RequirementAnalysisCompareVO {
  sourcePreparationId: number
  sourceBusinessVersion: number
  targetPreparationId: number
  targetBusinessVersion: number
  fields: RequirementAnalysisFieldDiffVO[]
  /** 已取消章节候选的兼容返回；新对比抽屉不读取。 */
  sections?: RequirementAnalysisSectionDiffVO[]
}

export interface PatchRequirementAnalysisFormReqVO {
  values: JsonObject
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

export const patchForm = (
  preparationId: number,
  expectedInstanceVersion: number,
  expectedSolVersion: number,
  data: PatchRequirementAnalysisFormReqVO
) =>
  request.put<RequirementAnalysisCommandResultVO>({
    method: 'PATCH',
    url: `${baseUrl}/${preparationId}/form`,
    data,
    headers: {
      'If-Match': String(expectedInstanceVersion),
      'X-SOL-If-Match': String(expectedSolVersion)
    }
  })

export const completeDraft = (
  preparationId: number,
  expectedInstanceVersion: number,
  expectedSolVersion: number,
  idempotencyKey: string
) =>
  request.post<RequirementAnalysisCommandResultVO>({
    url: `${baseUrl}/${preparationId}/actions/submit`,
    params: { type: 'PRE_04' },
    headers: {
      'If-Match': String(expectedInstanceVersion),
      'X-SOL-If-Match': String(expectedSolVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const createNextDraft = (
  preparationId: number,
  expectedInstanceVersion: number,
  expectedSolVersion: number,
  idempotencyKey: string
) =>
  request.post<RequirementAnalysisCommandResultVO>({
    url: `${baseUrl}/${preparationId}/actions/create-draft`,
    headers: {
      'If-Match': String(expectedInstanceVersion),
      'X-SOL-If-Match': String(expectedSolVersion),
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
