interface CustomStartContext {
  processInstanceId: string
  processDefinitionId: string
  businessKey: string
  formCustomCreatePath: string
}

export const getCustomStartRoute = async (context: CustomStartContext) => {
  if (context.processDefinitionId.split(':')[0] !== 'pms-sol-duration-change') {
    return { path: context.formCustomCreatePath, query: { id: context.businessKey } }
  }

  const process = await getProcessInstance(context.processInstanceId)
  const projectId = process.formVariables?.projectId
  if (
    process.id !== context.processInstanceId ||
    process.processDefinitionId !== context.processDefinitionId ||
    !sameBusinessViewId(process.businessKey, context.businessKey) ||
    !sameBusinessViewId(process.formVariables?.durationChangeId, context.businessKey) ||
    !isBusinessViewId(projectId)
  ) {
    throw new Error('无法确认原工期变更所属项目，请刷新后重试。')
  }
  // Another change starts from the current project plan; the approved revision stays frozen.
  return {
    path: '/pms/project-management/project-master-detail',
    query: { projectId: businessViewIdKey(projectId), tab: 'schedule' }
  }
}
import { getProcessInstance } from '@/api/bpm/processInstance'
import { businessViewIdKey, isBusinessViewId, sameBusinessViewId } from '@/api/pms/platform/business-view/ids'
