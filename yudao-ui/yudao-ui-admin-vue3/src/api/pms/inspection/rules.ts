import request, { writeHeaders } from './request'
import type { WireLong } from './request'
export type { WireLong } from './request'

const base = '/api/v1/pms/inspection-rules'
export interface CommandDraft {
  stableCommandKey: string
  commandContent: string
  executionOrder: number
  timeoutSeconds: number
  continueOnTimeout: boolean
}
export interface ProductTypeDraft {
  productTypeCode: string
  productTypeNameSnapshot: string
}
export interface DraftInput {
  inspectionItem?: string
  description?: string
  categoryCode?: string
  categoryNameSnapshot?: string
  severityCode?: string
  severityNameSnapshot?: string
  sortOrder?: number
  expectedResultRegex?: string
  thresholdDataType?: string
  thresholdOperator?: string
  thresholdValue?: number | string
  thresholdUnit?: string
  commands: CommandDraft[]
  productTypes: ProductTypeDraft[]
}
export interface RuleView extends DraftInput {
  ruleId: WireLong
  detectionId: string
  revisionId: WireLong
  revisionNo: number
  version: number
  statusCode: 'DRAFT' | 'PUBLISHED' | 'DISABLED'
  ruleNameSnapshot: string
  publishedAt?: number
  disabledAt?: number
}
export interface DraftResult {
  ruleId: WireLong
  revisionId: WireLong
  revisionNo: number
  version: number
}
export interface ValidationResult {
  errors: { location: string; code: string; message: string }[]
  dictionaryNameCandidates: { field: string; value: string; authoritativeDisplayName: string }[]
  productTypeNameCandidates: { productTypeCode: string; authoritativeDisplayName: string }[]
}
export interface SelectableRule {
  ruleId: WireLong
  detectionId: string
  revisionId: WireLong
  revisionNo: number
  categoryCode: string
  categoryName: string
  inspectionItem: string
  severityCode: string
  severityName: string
  sortOrder: number
  productTypeCode: string
  productTypeName: string
}
export const getSelectableRules = (deviceId: WireLong) =>
  request.get<SelectableRule[]>({ url: `${base}/selectable`, params: { deviceIds: String(deviceId) } })
export const getRulePage = (params: Record<string, unknown>) =>
  request.get<{ list: RuleView[]; total: number }>({ url: base, params })
export const getRuleRevision = (id: WireLong) =>
  request.get<RuleView>({ url: `${base}/revisions/${id}` })
export const createRule = (data: { detectionId: string; ruleName: string }, key: string) =>
  request.post<DraftResult>({ url: base, data, headers: writeHeaders(key) })
export const saveRuleRevision = (id: WireLong, data: DraftInput, version: number, key: string) =>
  request.put<DraftResult>({ url: `${base}/revisions/${id}`, data, headers: writeHeaders(key, version) })
export const copyRuleRevision = (id: WireLong, version: number, key: string) =>
  request.post<DraftResult>({ url: `${base}/revisions/${id}/actions/copy`, headers: writeHeaders(key, version) })
export const validateRuleRevision = (id: WireLong) =>
  request.post<ValidationResult>({ url: `${base}/revisions/${id}/actions/validate` })
export const recordRuleSecurityReview = (
  id: WireLong, data: { conclusionCode: string }, version: number, key: string
) => request.post({
  url: `${base}/revisions/${id}/actions/record-security-review`, data, headers: writeHeaders(key, version)
})
export const publishRuleRevision = (id: WireLong, version: number, key: string) =>
  request.post({ url: `${base}/revisions/${id}/actions/publish`, headers: writeHeaders(key, version) })
export const disableRuleRevision = (id: WireLong, version: number, key: string) =>
  request.post({ url: `${base}/revisions/${id}/actions/disable`, headers: writeHeaders(key, version) })
