import { defineAsyncComponent, markRaw, type Component } from 'vue'
import OwnerCompletionEntry from './OwnerCompletionEntry.vue'
import ProjectMembersBusinessView from './ProjectMembersBusinessView.vue'
import { businessPageRoutes, validatePagePresentation, type PagePresentation } from './presentationRoute'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import type { BusinessViewRegistrationVO, BusinessViewId } from '@/api/pms/platform/business-view'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'
import type { TaskExecutionContext } from '@/api/pms/project/task-business'
import { isBusinessViewId, legacyOwnerId } from '@/api/pms/platform/business-view/ids'
import ProjectRequirementAnalysisPanel from '@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue'
import DynamicFormInstanceContent from '@/views/pms/platform/dynamic-form/instance/DynamicFormInstanceContent.vue'
import SiteSurveyPage from '@/views/pms/delivery-business/site-survey/index.vue'
import AcceptanceReportPage from '@/views/pms/acceptance/acceptance-report/index.vue'
import ProjectDurationPanel from '@/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue'
import ProjectSchedulePanel from '@/views/pms/project/project-master-detail/components/ProjectSchedulePanel.vue'
import SolutionWorkbench from '@/views/pms/engineering/solution-reviewed/index.vue'

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
  presentation?: PagePresentation
}
interface Adapter {
  componentKey: string
  componentVersion: string
  entityType: string
  ownerContext: string
  viewSource: 'PAGE' | 'DYNAMIC_FORM'
  pageUrl?: string
  component: Component
  /** Optional independent catalog presentation reuses this same professional component. */
  businessEntityViewCode?: string
  standaloneProps?: (project: ProjectMasterVO) => Record<string, unknown>
  resolve: (target: BusinessViewTarget) => Record<string, unknown> | undefined
}
const positiveId = isBusinessViewId
// Paths only select these statically imported components; URL metadata never becomes an import or command.
const adapters: readonly Adapter[] = [
  {
    ...businessPageRoutes.PROJ_PROJECT_ASSIGNMENT,
    component: markRaw(ProjectMembersBusinessView),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null && positiveId(resolvedContext.project?.id)
        ? { project: { ...resolvedContext.project, id: legacyOwnerId(resolvedContext.project.id) } }
        : undefined
  },
  {
    ...businessPageRoutes.PLN_STAGE_PLAN_APPROVAL,
    component: markRaw(ProjectSchedulePanel),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null && positiveId(resolvedContext.project?.id)
        ? { project: { ...resolvedContext.project, id: legacyOwnerId(resolvedContext.project.id) } }
        : undefined
  },
  {
    ...businessPageRoutes.SOL_IMPLEMENTATION_SOLUTION,
    component: markRaw(SolutionWorkbench),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null && positiveId(resolvedContext.project?.id)
        ? { projectId: legacyOwnerId(resolvedContext.project.id) }
        : undefined
  },
  {
    ...businessPageRoutes.CUT_CUTOVER_COMPLETION,
    component: markRaw(OwnerCompletionEntry),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null && positiveId(resolvedContext.project?.id)
        ? { projectId: resolvedContext.project.id, label: '割接上线', tab: 'cutover' }
        : undefined
  },
  {
    ...businessPageRoutes.ACC_ACCEPTANCE_REPORT,
    component: markRaw(AcceptanceReportPage),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null &&
      positiveId(resolvedContext.project?.id) &&
      (resolvedContext.businessObjectId == null || positiveId(resolvedContext.businessObjectId))
        ? { projectId: resolvedContext.project.id, objectId: resolvedContext.businessObjectId }
        : undefined
  },
  {
    ...businessPageRoutes.SOL_SITE_SURVEY,
    component: markRaw(SiteSurveyPage),
    businessEntityViewCode: 'sol_site_survey',
    standaloneProps: (project) => ({ projectId: project.id }),
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
    ...businessPageRoutes.PROJ_REQUIREMENT_ANALYSIS,
    component: markRaw(ProjectRequirementAnalysisPanel),
    businessEntityViewCode: 'sol_requirement_analysis',
    standaloneProps: (project) => ({ project }),
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
    ...businessPageRoutes.PLN_CONSTRUCTION_PLAN,
    component: markRaw(ProjectDurationPanel),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null && positiveId(resolvedContext.project?.id)
        ? { project: { ...resolvedContext.project, id: legacyOwnerId(resolvedContext.project.id) } }
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
export const resolveStandaloneBusinessEntityView = (viewCode: string | undefined) => {
  const adapter = adapters.find((entry) => !!entry.standaloneProps &&
    (entry.componentKey === viewCode || entry.businessEntityViewCode === viewCode))
  return adapter ? { component: adapter.component, resolve: adapter.standaloneProps! } : undefined
}

const declaredComponent = markRaw(defineAsyncComponent(() => import('./DeclaredBusinessView.vue')))
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
  if (!adapter && registration.componentKey.startsWith('DECLARED_BUSINESS_') && registration.componentVersion === '1'
      && registration.viewSource === 'PAGE' && registration.dynamicFormRevisionId == null && !target.presentation
      && positiveId(target.resolvedContext.businessObjectId)) {
    const readonly = target.readonly === true || registration.status === 'DISABLED'
    return { component: declaredComponent, props: {
      ownerModule: registration.ownerContext, entityType: registration.entityType,
      stableCode: registration.componentKey.slice('DECLARED_BUSINESS_'.length),
      entityId: target.resolvedContext.businessObjectId, readonly, allowedActions: readonly ? [] : [...target.allowedActions]
    } }
  }
  if (!adapter)
    return { error: '该精确组件版本尚未部署或与Owner/实体不匹配，请联系配置负责人后重试。' }
  const context = adapter.resolve(target)
  if (!context)
    return { error: '缺少已授权的业务上下文或冻结修订不匹配；注册元数据不能替代对象授权。' }
  let pageUrl: string | undefined
  if (target.presentation) {
    try { pageUrl = validatePagePresentation(target.presentation, adapter.pageUrl, target.resolvedContext) }
    catch { return { error: '页面路径或参数与冻结Owner上下文不匹配，禁止回退到默认页面。' } }
  }
  const readonly = target.readonly === true || registration.status === 'DISABLED'
  return {
    component: adapter.component,
    pageUrl,
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
