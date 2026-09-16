import request from '@/config/axios'
import type { DynamicFormFileFactVO, JsonObject } from '@/api/pms/platform/dynamic-form'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'

export type EntityId = string | number
export interface Revision {
  ref: { entity: { tenantId: EntityId; ownerModule: string; entityType: string; entityId: EntityId }; revisionId: EntityId }
  revisionNo: number
  sourceRevisionId?: EntityId
  baseEffectiveRevisionId?: EntityId
  baseEntityVersion?: number
  state: 'DRAFT' | 'FROZEN'
  effective: boolean
  version: number
  reason?: string
  frozenBy?: EntityId
  frozenAt?: string
}
export interface Field { code: string; type: string; required: boolean }
export interface FormLayout {
  binding: { formRevisionId: EntityId; extensionDefinitionRevisionId?: EntityId; fieldBindings: Record<string, string>; version: number }
  templateId: EntityId
  revisionNo: number
  formVersion: number
  engineCode: string
  designerVersion: string
  rendererVersion: string
  formConfJson: string
  formRulesJson: string
  fields: Array<{ fieldKey: string; controlledFile: boolean; required: boolean }>
}
export interface View {
  projectId: EntityId
  revision: Revision
  entityVersion?: number
  form?: FormLayout
  extensionDefinitionRevisionId?: EntityId
  extensionValueVersion: number
  values: JsonObject
  attachments: Array<{ key: { purposeCode: string }; scopeVersion: EntityId; activeFacts: DynamicFormFileFactVO[] }>
  allowedActions: string[]
  projectTemplateId?: EntityId
  projectTemplateRevisionId?: EntityId
  fieldCatalog: Field[]
}
export interface Workspace { projectId: EntityId; currentEffective: View | null; draft: View | null; allowedActions: string[] }
export interface FieldValue { readable: boolean; value: unknown }
export interface Difference { fieldCode: string; before: FieldValue; after: FieldValue }
export interface Patch {
  values: JsonObject
  extensionDefinitionRevisionId?: EntityId
  expectedExtensionVersion: number
  extensionValues?: JsonObject
  execution?: ProjectBusinessExecutionSelection
}
const baseUrl = '/api/v1/pms/requirement-analyses'
const revisionUrl = (revision: Revision) => `${baseUrl}/${revision.ref.entity.entityId}/revisions/${revision.ref.revisionId}`
const headers = (revision: Revision, key: string) => ({ 'If-Match': String(revision.version), 'Idempotency-Key': key })
export const workspace = (projectId: EntityId, stageId?: EntityId, taskId?: EntityId) =>
  request.get<Workspace>({ url: baseUrl, params: { projectId, stageId, taskId } })
export const read = (revisionId: EntityId) => request.get<View>({ url: `${baseUrl}/revisions/${revisionId}` })
export const create = (projectId: EntityId, key: string, execution?: ProjectBusinessExecutionSelection) =>
  request.post<Revision>({ url: baseUrl, data: { projectId, execution }, headers: { 'Idempotency-Key': key } })
export const save = (revision: Revision, data: Patch, key: string) =>
  request.put<Revision>({ method: 'PATCH', url: revisionUrl(revision), data, headers: headers(revision, key) })
export const complete = (revision: Revision, key: string, execution?: ProjectBusinessExecutionSelection) =>
  request.post<Revision>({ url: `${revisionUrl(revision)}/complete`, data: { execution }, headers: headers(revision, key) })
export const copy = (revision: Revision, key: string, execution?: ProjectBusinessExecutionSelection, reason?: string) =>
  request.post<Revision>({ url: `${revisionUrl(revision)}/copy`, data: { reason, execution }, headers: headers(revision, key) })
export const revisions = (entityId: EntityId, beforeId?: EntityId, limit = 20) =>
  request.get<Revision[]>({ url: `${baseUrl}/${entityId}/revisions`, params: { beforeId, limit } })
export const compare = (entityId: EntityId, leftRevisionId: EntityId, rightRevisionId: EntityId) =>
  request.get<Difference[]>({ url: `${baseUrl}/${entityId}/compare`, params: { leftRevisionId, rightRevisionId } })
