import request from '@/config/axios'
import { executeEntityOperation, type BusinessOperationReceipt } from '@/api/pms/platform/businessmodel'
import type { DynamicFormFileFactVO, JsonObject } from '@/api/pms/platform/dynamic-form'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'
import { selectionClient } from '@/components/BusinessView/operationClient'
import { requirementAnalysisResult } from './operationResults'

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
  operationReceipt?: BusinessOperationReceipt
}
export interface Field { code: string; type: string; required: boolean }
export interface FormLayout {
  binding: { formRevisionId?: EntityId; extensionDefinitionRevisionId?: EntityId; fieldBindings: Record<string, string>; version: number }
  templateId?: EntityId
  revisionNo: number
  formVersion: number
  engineCode: string
  designerVersion: string
  rendererVersion: string
  formConfJson: string
  formRulesJson: string
  fields: Array<{ fieldKey: string; componentType?: string; controlledFile: boolean; required: boolean }>
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
const publicCommand = async (code: string, input: Record<string, unknown>, key: string, revision?: Revision): Promise<Revision> => {
  const receipt = await executeEntityOperation('SOL', 'requirementAnalysis', code, revision?.ref.entity.entityId, {
    idempotencyKey: key, concurrencyBasis: revision?.version, revisionId: revision?.ref.revisionId,
    entryKind: 'INDEPENDENT', input
  })
  if (receipt.outcome !== 'SAVED' && receipt.outcome !== 'EFFECTED') throw new Error(receipt.failureReason || '需求分析办理未完成')
  const reference = receipt.references?.find(value => value.kind === 'COMMAND' && value.ownerModule === 'SOL')
  if (!reference) throw new Error('回执缺少可重开的修订身份')
  const response = JSON.parse(reference.value) as Revision
  if (response.ref?.entity?.ownerModule !== 'SOL' || response.ref.entity.entityType !== 'REQUIREMENT_ANALYSIS'
    || !response.ref.revisionId || !Number.isInteger(response.version)) throw new Error('回执修订身份无效')
  return { ...response, operationReceipt: receipt }
}
export const workspace = (projectId: EntityId, stageId?: EntityId, taskId?: EntityId) =>
  request.get<Workspace>({ url: baseUrl, params: { projectId, stageId, taskId } })
export const read = (revisionId: EntityId) => request.get<View>({ url: `${baseUrl}/revisions/${revisionId}` })
export const create = async (projectId: EntityId, key: string, execution?: ProjectBusinessExecutionSelection) => {
  const client = selectionClient(execution)
  if (!client) return publicCommand('create', { projectId, execution }, key)
  if (String(projectId) !== String(client.target.projectId)) throw new Error('BUSINESS_PROJECT_MISMATCH')
  return (await client.execute({ operationCode: 'SOL.REQUIREMENT_ANALYSIS.CREATE', input: {}, key, validateResult: requirementAnalysisResult('CREATE') })).response as Revision
}
export const save = async (revision: Revision, data: Patch, key: string) => {
  const client = selectionClient(data.execution)
  if (!client) return publicCommand('save', { ...data }, key, revision)
  const { execution: _execution, ...input } = data
  return (await client.execute({ operationCode: 'SOL.REQUIREMENT_ANALYSIS.SAVE', objectId: revision.ref.revisionId,
    expectedBusinessVersion: revision.version, input, key, validateResult: requirementAnalysisResult('SAVE') })).response as Revision
}
export const complete = async (revision: Revision, key: string, execution?: ProjectBusinessExecutionSelection) => {
  const client = selectionClient(execution)
  if (!client) return publicCommand('complete', { execution }, key, revision)
  return (await client.execute({ operationCode: 'SOL.REQUIREMENT_ANALYSIS.COMPLETE', objectId: revision.ref.revisionId,
    expectedBusinessVersion: revision.version, input: {}, key, validateResult: requirementAnalysisResult('COMPLETE') })).response as Revision
}
export const copy = async (revision: Revision, key: string, execution?: ProjectBusinessExecutionSelection, reason?: string) => {
  const client = selectionClient(execution)
  if (!client) return publicCommand('copy', { reason, execution }, key, revision)
  return (await client.execute({ operationCode: 'SOL.REQUIREMENT_ANALYSIS.COPY', objectId: revision.ref.revisionId,
    expectedBusinessVersion: revision.version, input: { reason }, key, validateResult: requirementAnalysisResult('COPY') })).response as Revision
}
export const revisions = (entityId: EntityId, beforeId?: EntityId, limit = 20) =>
  request.get<Revision[]>({ url: `${baseUrl}/${entityId}/revisions`, params: { beforeId, limit } })
export const compare = (entityId: EntityId, leftRevisionId: EntityId, rightRevisionId: EntityId) =>
  request.get<Difference[]>({ url: `${baseUrl}/${entityId}/compare`, params: { leftRevisionId, rightRevisionId } })

export interface PresentationOption { name: string; layout: FormLayout }
export interface Presentations { defaultTemplateId?: EntityId; defaultRevisionId?: EntityId; options: PresentationOption[] }
export const presentations = (revisionId: EntityId, execution?: ProjectBusinessExecutionSelection) =>
  request.get<Presentations>({ url: `${baseUrl}/revisions/${revisionId}/presentations`,
    params: { stageId: execution?.stage?.stageId, taskId: execution?.task?.taskId } })
