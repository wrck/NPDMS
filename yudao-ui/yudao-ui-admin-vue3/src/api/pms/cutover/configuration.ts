import request from './request'
import type { WireLong, WireDateTime } from './types'

export type CutoverConfigStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type CutoverNavigationTarget = 'CURRENT_STAGE_WORKBENCH' | 'TASK_OVERVIEW'

export interface CutoverNavigationRule {
  target: CutoverNavigationTarget
}

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
  businessCategoryCode?: string
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
  requiredResult?: boolean
  enabled: boolean
}

export interface CutoverValidationError {
  location: string
  message: string
}

export interface CutoverConfiguration {
  id?: WireLong
  configurationCode: string
  configurationName: string
  revisionNo?: number
  statusCode?: CutoverConfigStatus
  changeSummary?: string
  navigationRule: CutoverNavigationRule | null
  dictionarySnapshot: Record<string, unknown>
  dimensions: CutoverDimension[]
  planTemplateSections: CutoverPlanSection[]
  items: CutoverChecklistItem[]
  bindingRules: CutoverBindingRule[]
  validationErrors?: CutoverValidationError[]
  version?: WireLong
  publishedAt?: WireDateTime
  disabledAt?: WireDateTime
  createTime?: WireDateTime
  updateTime?: WireDateTime
}

const BASE = '/api/v1/pms/cutover-configurations'

export interface CutoverExternalField { key: string; label: string; valueType: string; required: boolean }
export interface CutoverExternalCatalog {
  interfaces: Array<{ code: string; name: string; parameters: CutoverExternalField[]; responseFields: CutoverExternalField[] }>
  contextFields: CutoverExternalField[]
  bulletinFields: CutoverExternalField[]
}
export const getExternalInterfaces = () => request.get<CutoverExternalCatalog>({ url: `${BASE}/external-interfaces` })

export const getPage = (params: Record<string, unknown>) =>
  request.get({ url: `${BASE}/revisions`, params })

export const getDetail = (revisionId: WireLong) =>
  request.get({ url: `${BASE}/revisions/${revisionId}` })

export const createDraft = (command: { data: CutoverConfiguration; idempotencyKey: string }) =>
  request.post<WireLong>({ url: `${BASE}/revisions`, data: command.data, headers: { 'Idempotency-Key': command.idempotencyKey } })

export interface ConfigurationRevisionCommand { revisionId: WireLong; expectedVersion: WireLong; idempotencyKey: string }
const writeHeaders = (command: ConfigurationRevisionCommand) => ({
  'If-Match': String(command.expectedVersion), 'Idempotency-Key': command.idempotencyKey
})

export const updateDraft = (command: ConfigurationRevisionCommand & { data: CutoverConfiguration }) =>
  request.put({
    url: `${BASE}/revisions/${command.revisionId}`,
    data: command.data,
    headers: writeHeaders(command)
  })

export const copyRevision = (command: ConfigurationRevisionCommand) =>
  request.post({
    url: `${BASE}/revisions/${command.revisionId}/actions/copy`,
    headers: writeHeaders(command)
  })

export const validateRevision = (revisionId: WireLong) =>
  request.post({ url: `${BASE}/revisions/${revisionId}/actions/validate` })

export const publishRevision = (command: ConfigurationRevisionCommand) =>
  request.post({
    url: `${BASE}/revisions/${command.revisionId}/actions/publish`,
    headers: writeHeaders(command)
  })

export const disableRevision = (command: ConfigurationRevisionCommand) =>
  request.post({
    url: `${BASE}/revisions/${command.revisionId}/actions/disable`,
    headers: writeHeaders(command)
  })

export const initializeApprovalWorkflow = (idempotencyKey: string) => request.post<string>({ url: `${BASE}/approval-workflow/actions/initialize`, headers: { 'Idempotency-Key': idempotencyKey } })
