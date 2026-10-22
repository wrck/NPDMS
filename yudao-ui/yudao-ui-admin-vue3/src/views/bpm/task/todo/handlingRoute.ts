import { getProcessInstance } from '@/api/bpm/processInstance'
import { getNormalClosureApplication } from '@/api/pms/acceptance/normal-closure'
import {
  businessViewIdKey,
  isBusinessViewId,
  sameBusinessViewId
} from '@/api/pms/platform/business-view/ids'

interface TodoTask {
  id: string
  processInstance: { id: string; processDefinitionId: string }
}

export const getTodoHandlingRoute = async (task: TodoTask) => {
  const instance = task.processInstance
  if (instance.processDefinitionId.split(':')[0] !== 'PMS_MINIMAL_NORMAL_CLOSURE') {
    return { name: 'BpmProcessInstanceDetail', query: { id: instance.id, taskId: task.id } }
  }

  const process = await getProcessInstance(instance.id)
  const applicationId = /^PROJECT_NORMAL_CLOSURE:([1-9][0-9]{0,18})$/.exec(process.businessKey)?.[1]
  if (process.id !== instance.id || !isBusinessViewId(applicationId)) {
    throw new Error('CLOSURE_PROCESS_IDENTITY_MISMATCH')
  }
  const detail = await getNormalClosureApplication(applicationId)
  if (
    !sameBusinessViewId(detail.application.id, applicationId) ||
    detail.application.businessKey !== process.businessKey ||
    detail.application.processInstanceId !== instance.id ||
    detail.application.processDefinitionId !== instance.processDefinitionId ||
    !sameBusinessViewId(detail.projectId, detail.application.projectId)
  ) {
    throw new Error('CLOSURE_APPLICATION_IDENTITY_MISMATCH')
  }
  return {
    path: '/pms/project-management/project-master-detail',
    query: { projectId: businessViewIdKey(detail.projectId), tab: 'closure' }
  }
}
