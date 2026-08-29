import request from '@/config/axios'

export type CutoverConfigStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface CutoverDimension {
  code: string
  name: string
  dataType: string
  valueSource: string
  owner: string
  contextPath: string
  enabled: boolean
}

export interface CutoverPlanSection {
  stableSectionKey: string
  title: string
  sortOrder: number
  cutoverTypeCodes: string[]
  levelCodes: string[]
  required: boolean
}

export interface CutoverChecklistItem {
  stableItemKey: string
  itemType: 'BUSINESS_SURVEY' | 'RISK' | 'DUAL_MACHINE_CHECK'
  itemName: string
  itemDescription?: string
  interfaceFormat: string
  interfaceSchema: Record<string, unknown>
  feedbackFormat: string
  required: boolean
  workMode: string
  externalSourceConfig?: Record<string, unknown>
  subtableCode?: string
  enabled: boolean
  sortOrder: number
}

export interface CutoverBindingRule {
  stableRuleKey: string
  stableItemKey: string
  dimensionConditions: Record<string, unknown>
  priority: number
  enabled: boolean
}

export interface CutoverValidationError {
  location: string
  message: string
}

export interface CutoverConfiguration {
  id?: number
  configurationCode: string
  configurationName: string
  revisionNo?: number
  statusCode?: CutoverConfigStatus
  changeSummary?: string
  dictionarySnapshot: Record<string, unknown>
  dimensions: CutoverDimension[]
  planTemplateSections: CutoverPlanSection[]
  items: CutoverChecklistItem[]
  bindingRules: CutoverBindingRule[]
  validationErrors?: CutoverValidationError[]
  version?: number
  publishedAt?: string
  disabledAt?: string
  createTime?: string
  updateTime?: string
}

const BASE = '/api/v1/pms/cutover-config'

export const getPage = (params: Record<string, unknown>) =>
  request.get({ url: `${BASE}/revisions`, params })

export const getDetail = (revisionId: number) =>
  request.get({ url: `${BASE}/revisions/${revisionId}` })

export const createDraft = (data: CutoverConfiguration) =>
  request.post({ url: `${BASE}/revisions`, data })

export const updateDraft = (
  revisionId: number,
  expectedVersion: number,
  data: CutoverConfiguration
) =>
  request.put({
    url: `${BASE}/revisions/${revisionId}`,
    data,
    headers: { 'If-Match': String(expectedVersion) }
  })

export const copyRevision = (revisionId: number, expectedVersion: number) =>
  request.post({
    url: `${BASE}/revisions/${revisionId}/actions/copy`,
    headers: { 'If-Match': String(expectedVersion) }
  })

export const validateRevision = (revisionId: number) =>
  request.post({ url: `${BASE}/revisions/${revisionId}/actions/validate` })

export const publishRevision = (revisionId: number, expectedVersion: number) =>
  request.post({
    url: `${BASE}/revisions/${revisionId}/actions/publish`,
    headers: { 'If-Match': String(expectedVersion) }
  })

export const disableRevision = (revisionId: number, expectedVersion: number) =>
  request.post({
    url: `${BASE}/revisions/${revisionId}/actions/disable`,
    headers: { 'If-Match': String(expectedVersion) }
  })
