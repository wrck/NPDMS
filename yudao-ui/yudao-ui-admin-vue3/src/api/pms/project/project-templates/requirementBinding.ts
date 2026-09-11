import { getRevision } from '@/api/pms/platform/dynamic-form'
import type { BusinessViewId } from '@/api/pms/platform/business-view'
import type { DesignerTaskNode } from './index'

// PRE-04 WorkBinding V2 contract. The template stores the frozen source facts directly in WorkBinding.parameters.
export const REQUIREMENT_BINDING = {
  bindingType: 'BUSINESS_OBJECT',
  targetContextCode: 'SOL',
  targetObjectType: 'REQUIREMENT_ANALYSIS',
  targetObjectKey: 'PRE_04_REQUIREMENT_ANALYSIS',
  schemaVersion: 2
} as const

export const needsRequirementSource = (view?: { ownerContext: string; entityType: string }) =>
  view?.ownerContext === REQUIREMENT_BINDING.targetContextCode &&
  view.entityType === REQUIREMENT_BINDING.targetObjectType

export const savedRequirementSource = (task: DesignerTaskNode): BusinessViewId | undefined => {
  if (task.workBinding.targetObjectKey !== REQUIREMENT_BINDING.targetObjectKey) return undefined
  const source = task.workBinding.parameters as Record<string, any> | undefined
  return source?.schemaVersion === REQUIREMENT_BINDING.schemaVersion
    ? source.dynamicFormTemplateRevisionId
    : undefined
}

export const requirementSourceSnapshot = async (revisionId?: BusinessViewId) => {
  if (!revisionId)
    throw new Error('请选择需求分析使用的已发布表单；仅选择办理页面不能创建需求分析')
  const revision = await getRevision(revisionId)
  if (
    String(revision.revisionId) !== String(revisionId) ||
    revision.status !== 'PUBLISHED' ||
    !revision.templateId ||
    revision.revisionNo <= 0 ||
    revision.revisionVersion <= 0
  ) {
    throw new Error('需求分析表单修订无效，请重新选择已发布修订')
  }
  return {
    schemaVersion: REQUIREMENT_BINDING.schemaVersion,
    dynamicFormTemplateId: revision.templateId,
    dynamicFormTemplateRevisionId: revision.revisionId,
    dynamicFormRevisionNo: revision.revisionNo,
    dynamicFormRevisionFactVersion: revision.revisionVersion
  }
}
