import type {
  DesignerTaskNode,
  JsonObject,
  PermissionRequirement,
  RuleSpec,
  WorkBindingSpec
} from './index'
import { needsRequirementSource, requirementSourceSnapshot, REQUIREMENT_BINDING } from './requirementBinding'
import * as Views from '@/api/pms/platform/business-view'
import type {
  BusinessViewComponentVO,
  BusinessViewId,
  BusinessViewRegistrationVO
} from '@/api/pms/platform/business-view'

// PM-03 V2: configuration only. A binding edit updates the TaskNode directly; it does not create
// WORK_BINDING / COMPLETION_RULE / TASK DefinitionRevision records.
export type EntitySource = 'REFERENCE_EXISTING' | 'READ_ONLY_AGGREGATE'
export type BindingSelection = {
  strategy: EntitySource
  requirementFormRevisionId?: BusinessViewId
  completion?: { factCode: string; quantifier: 'ALL' | 'ANY' }
} & (
  | { view: BusinessViewRegistrationVO }
  | { component: BusinessViewComponentVO; dynamicFormRevisionId?: BusinessViewId }
)

export interface TaskContract {
  binding: WorkBindingSpec
  permission: PermissionRequirement
  completion: RuleSpec
}

interface BindingIntent {
  token: string
  view?: BusinessViewRegistrationVO
  viewCreateKey: string
  viewPublishKey: string
}

/** Keep this session until the whole Designer save succeeds; unchanged retries reuse view IDs/keys. */
export const createBindingSaveSession = () => new Map<string, BindingIntent>()
export type BindingSaveSession = ReturnType<typeof createBindingSaveSession>

export const loadTaskContract = async (task: DesignerTaskNode): Promise<TaskContract> => ({
  binding: task.workBinding,
  permission: task.permission,
  completion: task.completionRule
})

export const containsNativeCompletion = (rule: Record<string, any> | undefined): boolean =>
  !!rule &&
  (rule.predicate === 'TASK_NATIVE_STATUS' ||
    (Array.isArray(rule.rules) && rule.rules.some((item: Record<string, any>) => containsNativeCompletion(item))))

export const bindingTarget = (view: Pick<BusinessViewComponentVO, 'entityType'>) =>
  `PROJECT_${view.entityType}`

export const bindingContextMapping = (view: BusinessViewComponentVO): Record<string, string> => {
  const required = view.contextSchema.required
  const supported = new Set(['project', 'projectId', 'stageId', 'taskId', 'instanceId'])
  if (
    required != null &&
    (!Array.isArray(required) ||
      required.some((key) => typeof key !== 'string' || !supported.has(key)))
  ) {
    throw new Error('该办理界面需要尚未接入的项目上下文，请核对后再配置；不会创建业务实例。')
  }
  return Object.fromEntries(
    (Array.isArray(required) ? required : []).map((key: string) => [key, key])
  )
}

const ensureView = async (selection: BindingSelection, intent: BindingIntent) => {
  if ('view' in selection) {
    const view = await Views.getBusinessView(selection.view.id)
    if (view.status !== 'PUBLISHED' || view.disabledAt)
      throw new Error('所选办理界面已不可用，请重新选择；原绑定仍保留。')
    return view
  }
  if (selection.component.viewSource === 'DYNAMIC_FORM' && !selection.dynamicFormRevisionId)
    throw new Error('请选择已发布表单。')
  intent.view ??= await Views.createBusinessView(
    {
      entityType: selection.component.entityType,
      viewKey: `PROJECT_VIEW_${intent.token}`,
      componentKey: selection.component.componentKey,
      componentVersion: selection.component.componentVersion,
      ...(selection.dynamicFormRevisionId
        ? { dynamicFormRevisionId: selection.dynamicFormRevisionId }
        : {})
    },
    intent.viewCreateKey
  )
  // A prior publish may have succeeded even if its response was lost.
  intent.view = await Views.getBusinessView(intent.view.id)
  if (intent.view.disabledAt || intent.view.status === 'DISABLED')
    throw new Error('本次登记的办理界面已停用。')
  if (intent.view.status !== 'PUBLISHED') {
    if (!intent.view.allowedActions.includes('PUBLISH'))
      throw new Error('没有发布此办理界面的权限；已保留登记结果，可在获授权后重试。')
    intent.view = await Views.publishBusinessView(
      intent.view.id,
      intent.view.version,
      intent.viewPublishKey
    )
  }
  return intent.view
}

const viewSnapshot = (view: BusinessViewRegistrationVO): JsonObject =>
  JSON.parse(JSON.stringify(view)) as JsonObject

/**
 * Resolve/register the BusinessView, then return a new TaskNode with fully embedded V2 semantics.
 * No template definition asset is created or published here.
 */
export const prepareTaskBinding = async (
  task: DesignerTaskNode,
  selection: BindingSelection,
  session: BindingSaveSession
): Promise<DesignerTaskNode> => {
  if (!['REFERENCE_EXISTING', 'READ_ONLY_AGGREGATE'].includes(selection.strategy))
    throw new Error('尚未支持自动创建此业务对象。')
  const selectedView = 'view' in selection ? selection.view : selection.component
  const requirement = needsRequirementSource(selectedView)
  if (requirement && !selection.requirementFormRevisionId)
    throw new Error('请选择需求分析使用的已发布表单；仅选择办理页面不能创建需求分析')

  const signature = JSON.stringify({ nodeKey: task.nodeKey, selection })
  let intent = session.get(signature)
  if (!intent) {
    intent = {
      token: crypto.randomUUID().replaceAll('-', ''),
      viewCreateKey: crypto.randomUUID(),
      viewPublishKey: crypto.randomUUID()
    }
    session.set(signature, intent)
  }
  const view = await ensureView(selection, intent)
  const contextMapping = bindingContextMapping(view)
  const requirementSource = requirement
    ? await requirementSourceSnapshot(selection.requirementFormRevisionId)
    : undefined

  const binding: WorkBindingSpec = {
    type: requirement
      ? REQUIREMENT_BINDING.bindingType
      : view.viewSource === 'DYNAMIC_FORM'
        ? 'DYNAMIC_FORM'
        : 'BUSINESS_COMPONENT',
    targetContextCode: view.ownerContext,
    targetObjectType: view.entityType,
    targetObjectKey: requirement ? REQUIREMENT_BINDING.targetObjectKey : bindingTarget(view),
    componentKey: view.componentKey,
    ...(view.dynamicFormRevisionId ? { dynamicFormRevisionId: view.dynamicFormRevisionId } : {}),
    parameters: {
      instanceResolutionStrategy: selection.strategy,
      contextMapping,
      businessViewRevisionId: view.id,
      ...(requirementSource ?? {})
    },
    businessViewSnapshot: viewSnapshot(view)
  }

  const completionRule: RuleSpec = selection.completion
    ? {
        expression: {
          predicate: 'BUSINESS_FACT',
          parameters: {
            factCode: selection.completion.factCode,
            quantifier: selection.completion.quantifier
          }
        }
      }
    : task.completionRule

  return {
    ...task,
    workBinding: binding,
    completionRule,
    // Changed semantics no longer claim to be the old reusable binding/rule revision.
    source: task.source
      ? {
          ...task.source,
          workBindingRevisionId: undefined,
          completionRuleRevisionId: undefined
        }
      : undefined
  }
}
