import { markRaw, type Component } from 'vue'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import type { BusinessViewRegistrationVO, BusinessViewId } from '@/api/pms/platform/business-view'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'
import type { TaskExecutionContext } from '@/api/pms/project/task-business'
import { isBusinessViewId, legacyOwnerId } from '@/api/pms/platform/business-view/ids'
import ProjectRequirementAnalysisPanel from '@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue'
import DynamicFormInstanceContent from '@/views/pms/platform/dynamic-form/instance/DynamicFormInstanceContent.vue'
import SiteSurveyPage from '@/views/pms/delivery-business/site-survey/index.vue'
import AcceptanceReportPage from '@/views/pms/project/acceptance-report/index.vue'

// PM-03. These references come from the application's authorized Owner result, not registration JSON.
export interface BusinessViewResolvedContext {
  project?: Omit<ProjectMasterVO, 'id'> & { id?: BusinessViewId }
  instanceId?: BusinessViewId
  businessObjectId?: BusinessViewId
  taskId?: BusinessViewId
  stageExecution?: StageExecutionContext
  stageCode?: string
  taskExecution?: TaskExecutionContext
}
export interface BusinessViewTarget {
  registration: BusinessViewRegistrationVO
  resolvedContext: BusinessViewResolvedContext
  allowedActions: string[]
  readonly?: boolean
}
interface Adapter {
  componentKey: string
  componentVersion: string
  entityType: string
  ownerContext: string
  viewSource: 'PAGE' | 'DYNAMIC_FORM'
  component: Component
  resolve: (target: BusinessViewTarget) => Record<string, unknown> | undefined
}
const positiveId = isBusinessViewId
// Add new dedicated pages here plus their Owner provider. No URL, import path or script from metadata.
const adapters: readonly Adapter[] = [
  {
    componentKey: 'ACC_ACCEPTANCE_REPORT',
    componentVersion: '1',
    entityType: 'ACCEPTANCE',
    ownerContext: 'ACC',
    viewSource: 'PAGE',
    component: markRaw(AcceptanceReportPage),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null &&
      positiveId(resolvedContext.project?.id) &&
      (resolvedContext.businessObjectId == null || positiveId(resolvedContext.businessObjectId))
        ? { projectId: resolvedContext.project.id, objectId: resolvedContext.businessObjectId }
        : undefined
  },
  {
    componentKey: 'SOL_SITE_SURVEY',
    componentVersion: '1',
    entityType: 'SITE_SURVEY',
    ownerContext: 'SOL',
    viewSource: 'PAGE',
    component: markRaw(SiteSurveyPage),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null &&
      positiveId(resolvedContext.project?.id) &&
      (resolvedContext.businessObjectId == null || positiveId(resolvedContext.businessObjectId)) &&
      (resolvedContext.taskId == null || positiveId(resolvedContext.taskId)) &&
      !(resolvedContext.taskExecution && resolvedContext.stageExecution) &&
      (resolvedContext.taskExecution == null || (
        positiveId(resolvedContext.taskExecution.executionId) &&
        String(resolvedContext.taskExecution.projectId) === String(resolvedContext.project.id) &&
        String(resolvedContext.taskExecution.taskId) === String(resolvedContext.taskId))) &&
      (resolvedContext.stageExecution == null || (
        resolvedContext.taskId == null && positiveId(resolvedContext.stageExecution.stageId) &&
        positiveId(resolvedContext.stageExecution.executionId) &&
        String(resolvedContext.stageExecution.projectId) === String(resolvedContext.project.id)))
        ? {
            projectId: resolvedContext.project.id,
            objectId: resolvedContext.businessObjectId,
            taskId: resolvedContext.taskId,
            ...(resolvedContext.taskExecution ? { taskExecution: resolvedContext.taskExecution } : {}),
            ...(resolvedContext.stageExecution ? { stageExecution: resolvedContext.stageExecution, stageCode: resolvedContext.stageCode } : {})
          }
        : undefined
  },
  {
    componentKey: 'PROJ_REQUIREMENT_ANALYSIS',
    componentVersion: '1',
    entityType: 'REQUIREMENT_ANALYSIS',
    ownerContext: 'SOL',
    viewSource: 'PAGE',
    component: markRaw(ProjectRequirementAnalysisPanel),
      resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null && positiveId(resolvedContext.project?.id)
      && (resolvedContext.businessObjectId == null || positiveId(resolvedContext.businessObjectId))
      && !(resolvedContext.taskExecution && resolvedContext.stageExecution)
      && (resolvedContext.taskId == null || resolvedContext.taskExecution != null)
      && (resolvedContext.taskExecution == null || (
        positiveId(resolvedContext.taskExecution.taskId) && positiveId(resolvedContext.taskExecution.executionId)
        && String(resolvedContext.taskExecution.taskId) === String(resolvedContext.taskId)
        && String(resolvedContext.taskExecution.projectId) === String(resolvedContext.project.id)))
      && (resolvedContext.stageExecution == null || (
        resolvedContext.taskId == null && positiveId(resolvedContext.stageExecution.stageId) && positiveId(resolvedContext.stageExecution.executionId)
        && String(resolvedContext.stageExecution.projectId) === String(resolvedContext.project.id)))
        ? { project: { ...resolvedContext.project, id: legacyOwnerId(resolvedContext.project.id) },
            ...(resolvedContext.stageExecution ? { stageExecution: resolvedContext.stageExecution } : {}),
            ...(resolvedContext.taskExecution ? { taskExecution: resolvedContext.taskExecution } : {}),
            ...(resolvedContext.businessObjectId == null ? {} : { revisionId: resolvedContext.businessObjectId }) }
        : undefined
  },
  {
    componentKey: 'PLATFORM_DYNAMIC_FORM',
    componentVersion: '1',
    entityType: 'DYNAMIC_FORM_INSTANCE',
    ownerContext: 'PLATFORM',
    viewSource: 'DYNAMIC_FORM',
    component: markRaw(DynamicFormInstanceContent),
    resolve: ({ registration, resolvedContext }) =>
      positiveId(resolvedContext.instanceId) && positiveId(registration.dynamicFormRevisionId)
        ? {
            instanceId: resolvedContext.instanceId,
            expectedRevisionId: registration.dynamicFormRevisionId
          }
        : undefined
  }
]
export const resolveBusinessView = (target: BusinessViewTarget) => {
  const registration = target.registration
  if (!['PUBLISHED', 'DISABLED'].includes(registration.status))
    return { error: '草稿注册不能作为业务视图装载。' }
  const adapter = adapters.find(
    (item) =>
      item.componentKey === registration.componentKey &&
      item.componentVersion === registration.componentVersion &&
      item.entityType === registration.entityType &&
      item.ownerContext === registration.ownerContext &&
      item.viewSource === registration.viewSource
  )
  if (!adapter)
    return { error: '该精确组件版本尚未部署或与Owner/实体不匹配，请联系配置负责人后重试。' }
  const context = adapter.resolve(target)
  if (!context)
    return { error: '缺少已授权的业务上下文或冻结修订不匹配；注册元数据不能替代对象授权。' }
  const readonly = target.readonly === true || registration.status === 'DISABLED'
  return {
    component: adapter.component,
    props: {
      ...context,
      readonly,
      allowedActions: readonly ? [] : [...(target.allowedActions || [])]
    }
  }
}
export const businessViewTargetKey = (target: BusinessViewTarget) =>
  JSON.stringify([
    target.registration.id,
    target.registration.version,
    target.registration.componentKey,
    target.registration.componentVersion,
    target.registration.entityType,
    target.registration.ownerContext,
    target.registration.viewSource,
    target.registration.dynamicFormRevisionId,
    target.resolvedContext.project?.id,
    target.resolvedContext.instanceId,
    target.resolvedContext.businessObjectId,
    target.resolvedContext.taskId,
    target.resolvedContext.stageExecution?.stageId,
    target.resolvedContext.stageExecution?.executionId
  ])
