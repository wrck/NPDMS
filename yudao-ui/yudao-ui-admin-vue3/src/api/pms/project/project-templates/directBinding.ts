import * as Definitions from './definitions'
import type { DefinitionRevision, DefinitionSave } from './definitions'
import type { TaskDef } from './index'
import * as Views from '@/api/pms/platform/business-view'
import type { BusinessViewComponentVO, BusinessViewId, BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'

// PM-03 / F-PROJ-009: configuration only. No Owner instance or completion commands.
export type EntitySource = 'REFERENCE_EXISTING' | 'READ_ONLY_AGGREGATE'
export type BindingSelection = {
  strategy: EntitySource
} & ({ view: BusinessViewRegistrationVO } | {
  component: BusinessViewComponentVO
  dynamicFormRevisionId?: BusinessViewId
})
export interface TaskContract {
  definition: DefinitionRevision
  binding: DefinitionRevision
  permission: DefinitionRevision
  completion: DefinitionRevision
}
interface DefinitionStep { id?: number; createKey: string; publishKey: string; body?: DefinitionSave }
interface BindingIntent {
  token: string
  view?: BusinessViewRegistrationVO
  viewCreateKey: string
  viewPublishKey: string
  binding: DefinitionStep
  task: DefinitionStep
  contract?: TaskContract
}
/** Keep this session until the whole template save succeeds; unchanged retries reuse IDs and keys. */
export const createBindingSaveSession = () => new Map<string, BindingIntent>()
export type BindingSaveSession = ReturnType<typeof createBindingSaveSession>
const published = async (id: number | undefined, kind: DefinitionRevision['definitionKind']) => {
  if (id == null) throw new Error('请先选择已发布的任务方案，保留其权限与完成依据。')
  const row = await Definitions.getDefinition(id)
  if (!Definitions.availableDefinition(row, kind)) throw new Error('引用的任务、权限或完成依据不可用，请重新选择；原配置未改变。')
  return row
}
export const loadTaskContract = async (task: TaskDef): Promise<TaskContract> => {
  const definition = await published(task.definitionRevisionId, 'TASK')
  const slot = (key: 'workBinding' | 'permissionPolicy' | 'completionRule') =>
    task[`${key}RevisionId`] ?? definition.references.find((ref) => ref.referenceKey === definition.payload[key])?.targetRevisionId
  const binding = await published(slot('workBinding'), 'WORK_BINDING')
  const permission = await published(slot('permissionPolicy'), 'PERMISSION_POLICY')
  const completion = await published(slot('completionRule'), 'COMPLETION_RULE')
  return { definition, binding, permission, completion }
}
export const containsNativeCompletion = (rule: Record<string, any>): boolean =>
  rule.predicate === 'TASK_NATIVE_STATUS' || (Array.isArray(rule.rules) && rule.rules.some(containsNativeCompletion))
export const bindingTarget = (view: Pick<BusinessViewComponentVO, 'entityType'>) => `PROJECT_${view.entityType}`
export const bindingContextMapping = (view: BusinessViewComponentVO): Record<string, string> => {
  const required = view.contextSchema.required
  const supported = new Set(['project', 'projectId', 'stageId', 'taskId', 'instanceId'])
  if (required != null && (!Array.isArray(required) || required.some((key) => typeof key !== 'string' || !supported.has(key)))) {
    throw new Error('该办理界面需要尚未接入的项目上下文，请使用高级配置核对；不会创建业务实例。')
  }
  return Object.fromEntries((Array.isArray(required) ? required : []).map((key: string) => [key, key]))
}
const step = (): DefinitionStep => ({ createKey: crypto.randomUUID(), publishKey: crypto.randomUUID() })
const ensurePublishedDefinition = async (state: DefinitionStep) => {
  state.id ??= await Definitions.createDefinition(state.body!, state.createKey)
  const row = await Definitions.getDefinition(state.id)
  if (row.disabledAt) throw new Error('本次配置定义已停用，请核对后重新配置。')
  if (row.revisionState !== 'PUBLISHED') await Definitions.publishDefinition(row.id, row.version, state.publishKey)
  return state.id
}
const ensureView = async (selection: BindingSelection, intent: BindingIntent) => {
  if ('view' in selection) {
    const view = await Views.getBusinessView(selection.view.id)
    if (view.status !== 'PUBLISHED' || view.disabledAt) throw new Error('所选办理界面已不可用，请重新选择；原绑定仍保留。')
    return view
  }
  if (selection.component.viewSource === 'DYNAMIC_FORM' && !selection.dynamicFormRevisionId) throw new Error('请选择已发布表单。')
  intent.view ??= await Views.createBusinessView({
    entityType: selection.component.entityType,
    viewKey: `PROJECT_VIEW_${intent.token}`,
    componentKey: selection.component.componentKey,
    componentVersion: selection.component.componentVersion,
    ...(selection.dynamicFormRevisionId ? { dynamicFormRevisionId: selection.dynamicFormRevisionId } : {})
  }, intent.viewCreateKey)
  // Refresh first: a previous publish may have succeeded even when its response was lost.
  intent.view = await Views.getBusinessView(intent.view.id)
  if (intent.view.disabledAt || intent.view.status === 'DISABLED') throw new Error('本次登记的办理界面已停用。')
  if (intent.view.status !== 'PUBLISHED') {
    if (!intent.view.allowedActions.includes('PUBLISH')) throw new Error('没有发布此办理界面的权限；已保留登记结果，可在获授权后重试。')
    intent.view = await Views.publishBusinessView(intent.view.id, intent.view.version, intent.viewPublishKey)
  }
  return intent.view
}
/** Read -> register/publish view if requested -> create/publish binding -> create/publish TASK.
 * The caller submits the whole draft LAST and only then replaces its local task objects.
 * HTTP steps are not a transaction; failed attempts leave the original binding untouched.
 */
export const prepareTaskBinding = async (
  task: TaskDef, selection: BindingSelection, session: BindingSaveSession
): Promise<TaskDef> => {
  if (!['REFERENCE_EXISTING', 'READ_ONLY_AGGREGATE'].includes(selection.strategy)) throw new Error('尚未支持自动创建此业务对象。')
  const signature = JSON.stringify({ task, selection })
  let intent = session.get(signature)
  if (!intent) {
    intent = { token: crypto.randomUUID().replaceAll('-', ''), viewCreateKey: crypto.randomUUID(), viewPublishKey: crypto.randomUUID(), binding: step(), task: step() }
    session.set(signature, intent)
  }
  intent.contract ??= await loadTaskContract(task)
  const { definition, permission, completion } = intent.contract
  const view = await ensureView(selection, intent)
  intent.binding.body ??= {
    definitionKind: 'WORK_BINDING', definitionCode: `PROJECT_BIND_${intent.token}`, schemaVersion: 1,
    payload: {
      bindingType: view.viewSource === 'DYNAMIC_FORM' ? 'DYNAMIC_FORM' : 'BUSINESS_COMPONENT',
      instanceResolutionStrategy: selection.strategy, businessViewRevisionId: view.id,
      targetContextCode: view.ownerContext, targetObjectType: view.entityType,
      targetObjectKey: bindingTarget(view), contextMapping: bindingContextMapping(view)
    }, references: []
  }
  const bindingId = await ensurePublishedDefinition(intent.binding)
  intent.task.body ??= {
    definitionKind: 'TASK', definitionCode: `PROJECT_TASK_${intent.token}`, schemaVersion: definition.schemaVersion,
    payload: { ...definition.payload, name: task.name, workBinding: 'workBinding', permissionPolicy: 'permissionPolicy', completionRule: 'completionRule' },
    references: [
      ...definition.references.filter((ref) => ![definition.payload.workBinding, definition.payload.permissionPolicy, definition.payload.completionRule, 'workBinding', 'permissionPolicy', 'completionRule'].includes(ref.referenceKey)),
      { referenceKey: 'workBinding', targetRevisionId: bindingId },
      { referenceKey: 'permissionPolicy', targetRevisionId: permission.id },
      { referenceKey: 'completionRule', targetRevisionId: completion.id }
    ]
  }
  const definitionId = await ensurePublishedDefinition(intent.task)
  return taskWithExecution(task, { definitionRevisionId: definitionId, workBindingRevisionId: bindingId, permissionPolicyRevisionId: permission.id, completionRuleRevisionId: completion.id })
}
/** Clear only derived execution fields when deliberately replacing a definition/binding. */
export const taskWithExecution = (task: TaskDef, links: Pick<TaskDef, 'definitionRevisionId' | 'workBindingRevisionId' | 'permissionPolicyRevisionId' | 'completionRuleRevisionId'>): TaskDef => {
  const result = { ...task, ...links }
  for (const key of ['definitionSnapshot', 'workBindingTypeCode', 'targetContextCode', 'targetObjectType', 'targetObjectKey', 'componentKey', 'dynamicFormRevisionId', 'approvalDefinitionKey', 'bindingConfig', 'permissionPolicyRef', 'completionRuleTypeCode', 'completionRuleConfig', 'definitionVersion'] as const) delete result[key]
  return result
}
