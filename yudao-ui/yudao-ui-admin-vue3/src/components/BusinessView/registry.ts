import { markRaw, type Component } from 'vue'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import type { BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'
import ProjectRequirementAnalysisPanel from '@/views/pms/project/project-master-detail/components/ProjectRequirementAnalysisPanel.vue'
import DynamicFormInstanceContent from '@/views/pms/platform/dynamic-form/instance/DynamicFormInstanceContent.vue'

// PM-03. These references come from the application's authorized Owner result, not registration JSON.
export interface BusinessViewResolvedContext {
  project?: ProjectMasterVO
  instanceId?: number
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
const positiveId = (value: unknown): value is number =>
  Number.isSafeInteger(value) && Number(value) > 0
// Add new dedicated pages here plus their Owner provider. No URL, import path or script from metadata.
const adapters: readonly Adapter[] = [
  {
    componentKey: 'PROJ_REQUIREMENT_ANALYSIS',
    componentVersion: '1',
    entityType: 'REQUIREMENT_ANALYSIS',
    ownerContext: 'SOL',
    viewSource: 'PAGE',
    component: markRaw(ProjectRequirementAnalysisPanel),
    resolve: ({ registration, resolvedContext }) =>
      registration.dynamicFormRevisionId == null && positiveId(resolvedContext.project?.id)
        ? { project: resolvedContext.project }
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
    target.resolvedContext.instanceId
  ])
