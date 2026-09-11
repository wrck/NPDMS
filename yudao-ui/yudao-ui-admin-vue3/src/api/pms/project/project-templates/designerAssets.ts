import * as Definitions from './definitions'
import type { DefinitionRevision, DefinitionKind } from './definitions'
import * as Views from '@/api/pms/platform/business-view'
import type {
  DesignerDeliverableNode,
  DesignerStageNode,
  DesignerTaskNode,
  JsonObject,
  PermissionRequirement,
  RuleSpec,
  TemplateSourcePin,
  WorkBindingSpec
} from './index'

const requiredPublished = async (id: number | undefined, kind: DefinitionKind) => {
  if (id == null) throw new Error(`${kind} 引用缺失`)
  const row = await Definitions.getDefinition(id)
  if (!Definitions.availableDefinition(row, kind)) throw new Error(`${kind} 引用不是可用的已发布版本`)
  return row
}

const target = (row: DefinitionRevision, field: 'workBinding' | 'permissionPolicy' | 'completionRule') => {
  const key = row.payload[field]
  return row.references.find((ref) => ref.referenceKey === key)?.targetRevisionId
}

const snapshot = (value: unknown): JsonObject => JSON.parse(JSON.stringify(value ?? {})) as JsonObject

const workBinding = async (id: number): Promise<WorkBindingSpec> => {
  const row = await requiredPublished(id, 'WORK_BINDING')
  const payload = row.payload
  const viewId = payload.businessViewRevisionId
  let businessViewSnapshot: JsonObject | undefined
  let componentKey = payload.componentKey as string | undefined
  let dynamicFormRevisionId = payload.dynamicFormRevisionId as number | string | undefined
  if (viewId != null) {
    const view = await Views.getBusinessView(viewId)
    if (view.status !== 'PUBLISHED' || view.disabledAt) throw new Error('WorkBinding引用的BusinessView不可用')
    businessViewSnapshot = snapshot(view)
    componentKey = view.componentKey
    dynamicFormRevisionId = view.dynamicFormRevisionId as number | string | undefined
  }
  return {
    type: String(payload.bindingType ?? ''),
    targetContextCode: payload.targetContextCode as string | undefined,
    targetObjectType: payload.targetObjectType as string | undefined,
    targetObjectKey: payload.targetObjectKey as string | undefined,
    componentKey,
    dynamicFormRevisionId,
    approvalDefinitionKey: payload.approvalDefinitionKey as string | undefined,
    parameters: snapshot(payload),
    businessViewSnapshot,
    sourceRevisionId: row.id
  }
}

const permission = async (id: number): Promise<PermissionRequirement> => {
  const row = await requiredPublished(id, 'PERMISSION_POLICY')
  return { policyRef: row.definitionCode, policySnapshot: snapshot(row.payload), sourceRevisionId: row.id }
}
const rule = async (id: number): Promise<RuleSpec> => {
  const row = await requiredPublished(id, 'COMPLETION_RULE')
  return { expression: snapshot(row.payload), sourceRevisionId: row.id }
}

const source = (
  definitionRevisionId: number,
  workBindingRevisionId?: number,
  permissionPolicyRevisionId?: number,
  completionRuleRevisionId?: number
): TemplateSourcePin => ({
  definitionRevisionId,
  workBindingRevisionId,
  permissionPolicyRevisionId,
  completionRuleRevisionId
})

export const stageFromDefinition = async (row: DefinitionRevision): Promise<DesignerStageNode> => {
  if (!Definitions.availableDefinition(row, 'STAGE')) throw new Error('请选择已发布阶段定义')
  const bindingId = target(row, 'workBinding')
  const permissionId = target(row, 'permissionPolicy')
  const completionId = target(row, 'completionRule')
  if (!bindingId || !permissionId || !completionId) throw new Error('阶段定义缺少绑定、权限或完成规则引用')
  return {
    nodeKey: `stage:${String(row.payload.stageCode)}`,
    code: String(row.payload.stageCode ?? ''),
    name: String(row.payload.name ?? row.definitionCode),
    sortOrder: 0,
    start: Boolean(row.payload.start),
    terminal: Boolean(row.payload.terminal),
    entryCriteria: row.payload.entryCriteria as string | undefined,
    exitCriteria: row.payload.exitCriteria as string | undefined,
    workBinding: await workBinding(bindingId),
    permission: await permission(permissionId),
    completionRule: await rule(completionId),
    source: source(row.id, bindingId, permissionId, completionId)
  }
}

export const taskFromDefinition = async (
  row: DefinitionRevision,
  stageCode: string,
  sortOrder: number
): Promise<DesignerTaskNode> => {
  if (!Definitions.availableDefinition(row, 'TASK')) throw new Error('请选择已发布任务定义')
  const bindingId = target(row, 'workBinding')
  const permissionId = target(row, 'permissionPolicy')
  const completionId = target(row, 'completionRule')
  if (!bindingId || !permissionId || !completionId) throw new Error('任务定义缺少绑定、权限或完成规则引用')
  return {
    nodeKey: `task:${crypto.randomUUID()}`,
    code: `TASK_${crypto.randomUUID().replaceAll('-', '')}`,
    name: String(row.payload.name ?? row.definitionCode),
    stageCode,
    priority: Number(row.payload.priority ?? 0),
    sortOrder,
    estimatedHours: row.payload.estimatedHours as number | undefined,
    description: row.payload.description as string | undefined,
    workBinding: await workBinding(bindingId),
    permission: await permission(permissionId),
    completionRule: await rule(completionId),
    source: source(row.id, bindingId, permissionId, completionId)
  }
}

export const deliverableFromDefinition = async (
  row: DefinitionRevision,
  stageCode: string,
  taskCode?: string
): Promise<DesignerDeliverableNode> => {
  if (!Definitions.availableDefinition(row, 'DELIVERABLE')) throw new Error('请选择已发布交付要求')
  return {
    nodeKey: `deliverable:${crypto.randomUUID()}`,
    code: `DEL_${crypto.randomUUID().replaceAll('-', '')}`,
    name: String(row.payload.name ?? row.payload.deliverableType ?? row.definitionCode),
    stageCode,
    taskCode,
    required: Boolean(row.payload.required),
    source: { definitionRevisionId: row.id }
  }
}
