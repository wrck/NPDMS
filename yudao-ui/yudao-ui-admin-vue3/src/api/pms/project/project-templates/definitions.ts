import request from '@/config/axios'
import type { BusinessViewId } from '@/api/pms/platform/business-view'

// PM-03 / F-PROJ-009 — exact reusable revisions; configuration never grants object access.
export const definitionKinds = {
  STAGE: '阶段', TASK: '任务', DELIVERABLE: '交付件要求', WORK_BINDING: '工作绑定',
  COMPLETION_RULE: '完成规则', PERMISSION_POLICY: '权限策略', GATE: '门禁', MILESTONE: '里程碑'
} as const
export type DefinitionKind = keyof typeof definitionKinds
export type JsonObject = Record<string, any>
export interface DefinitionReference { referenceKey: string; targetRevisionId: number }
export interface DefinitionPayload extends JsonObject {
  /** Exact registration wire ID; Snowflake strings must never be coerced to Number. */
  businessViewRevisionId?: BusinessViewId
}
export interface DefinitionSave {
  definitionKind: DefinitionKind
  definitionCode: string
  schemaVersion: number
  payload: DefinitionPayload
  references: DefinitionReference[]
}
export interface DefinitionRevision extends DefinitionSave {
  id: number
  revisionNo: number
  revisionState: 'DRAFT' | 'PUBLISHED'
  publishedAt?: string
  disabledAt?: string
  version: number
}
export interface ValidationIssue { field: string; code: string; message: string }
export interface ValidationResult { valid: boolean; issues: ValidationIssue[] }
export interface DefinitionQuery {
  pageNo: number
  pageSize: number
  definitionKind?: DefinitionKind
  definitionCode?: string
  revisionState?: 'DRAFT' | 'PUBLISHED'
}
const baseUrl = '/api/v1/pms/delivery-definitions'
const headers = (version: number, key: string) => ({
  'If-Match': String(version), 'Idempotency-Key': key
})
const body = (data: DefinitionSave): DefinitionSave => ({
  definitionKind: data.definitionKind, definitionCode: data.definitionCode,
  schemaVersion: data.schemaVersion, payload: data.payload, references: data.references
})
export const getDefinitionPage = (params: DefinitionQuery) =>
  request.get<{ list: DefinitionRevision[]; total: number }>({ url: baseUrl, params })
export const getDefinition = (id: number) => request.get<DefinitionRevision>({ url: `${baseUrl}/${id}` })
export const createDefinition = (data: DefinitionSave, key: string) =>
  request.post<number>({ url: baseUrl, data: body(data), headers: { 'Idempotency-Key': key } })
export const updateDefinition = (id: number, version: number, data: DefinitionSave, key: string) =>
  request.put<number>({ url: `${baseUrl}/${id}`, data: body(data), headers: headers(version, key) })
const command = (id: number, action: string, version: number, key: string) =>
  request.post<number>({ url: `${baseUrl}/${id}/actions/${action}`, headers: headers(version, key) })
export const copyDefinition = (id: number, version: number, key: string) => command(id, 'copy', version, key)
export const publishDefinition = (id: number, version: number, key: string) => command(id, 'publish', version, key)
export const disableDefinition = (id: number, version: number, key: string) => command(id, 'disable', version, key)
export const validateDefinition = (id: number) =>
  request.post<ValidationResult>({ url: `${baseUrl}/${id}/actions/validate` })
export const availableDefinition = (row: DefinitionRevision, kind?: DefinitionKind) =>
  row.revisionState === 'PUBLISHED' && !row.disabledAt && (!kind || row.definitionKind === kind)
