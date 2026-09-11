import { getRevision } from '@/api/pms/platform/dynamic-form'
import type { BusinessViewId } from '@/api/pms/platform/business-view'
import type { TaskDef } from './index'

// PRE-04 WorkBinding V2 contract. All template UI consumers use this single definition.
export const REQUIREMENT_BINDING = {
  bindingType: 'BUSINESS_OBJECT', targetContextCode: 'SOL', targetObjectType: 'REQUIREMENT_ANALYSIS',
  targetObjectKey: 'PRE_04_REQUIREMENT_ANALYSIS', schemaVersion: 2
} as const
export const needsRequirementSource = (view?: { ownerContext: string; entityType: string }) =>
  view?.ownerContext === REQUIREMENT_BINDING.targetContextCode && view.entityType === REQUIREMENT_BINDING.targetObjectType

export const savedRequirementSource = (task: TaskDef): BusinessViewId | undefined => {
  if (task.targetObjectKey !== REQUIREMENT_BINDING.targetObjectKey || !task.bindingConfig) return undefined
  try {
    const source = JSON.parse(task.bindingConfig)
    return source.schemaVersion === REQUIREMENT_BINDING.schemaVersion ? source.dynamicFormTemplateRevisionId : undefined
  } catch { return undefined }
}

export const requirementSourceSnapshot = async (revisionId?: BusinessViewId) => {
  if (!revisionId) throw new Error('请选择需求分析使用的已发布表单；仅选择办理页面不能创建需求分析')
  const revision = await getRevision(revisionId)
  if (String(revision.revisionId) !== String(revisionId) || revision.status !== 'PUBLISHED'
      || !revision.templateId || revision.revisionNo <= 0 || revision.revisionVersion <= 0) {
    throw new Error('需求分析表单修订无效，请重新选择已发布修订')
  }
  return JSON.stringify({ schemaVersion: REQUIREMENT_BINDING.schemaVersion, dynamicFormTemplateId: revision.templateId,
    dynamicFormTemplateRevisionId: revision.revisionId, dynamicFormRevisionNo: revision.revisionNo,
    dynamicFormRevisionFactVersion: revision.revisionVersion })
}
