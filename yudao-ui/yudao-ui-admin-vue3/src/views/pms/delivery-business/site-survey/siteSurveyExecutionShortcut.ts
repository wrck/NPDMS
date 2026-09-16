import { businessViewIdKey, sameBusinessViewId, legacyOwnerId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import { getTaskBusinessContext } from '@/api/pms/project/task-business'
import { getStageBusinessContext } from '@/api/pms/project/stage-business'
import type { ProjectBusinessExecutionSelection as SiteSurveyExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'

// Read a fresh version of this exact execution; never switch to another round or matching node.
export const resolveSurveyExecution = async (
  projectId: BusinessViewId, query: Record<string, unknown>
): Promise<SiteSurveyExecutionSelection | undefined> => {
  const { surveyTaskId, surveyStageId, surveyStageCode, surveyExecutionId } = query
  if ([surveyTaskId, surveyStageId, surveyStageCode, surveyExecutionId].every(value => value == null)) return
  if ((surveyTaskId == null) === (surveyStageId == null)) throw new Error('工勘节点上下文无效')
  const expectedId = businessViewIdKey(surveyExecutionId)
  if (surveyTaskId != null) {
    if (surveyStageCode != null) throw new Error('工勘节点上下文混用')
    const taskId = businessViewIdKey(surveyTaskId)
    const context = await getTaskBusinessContext(taskId)
    const task = context.execution
    if (!task || context.executionAllowed !== true || context.recoverableError || context.businessView?.status !== 'PUBLISHED'
      || context.ownerContext !== 'SOL' || context.objectType !== 'SITE_SURVEY'
      || !sameBusinessViewId(context.projectId, projectId) || !sameBusinessViewId(context.taskId, taskId)
      || !sameBusinessViewId(task.projectId, projectId) || !sameBusinessViewId(task.taskId, taskId)
      || !sameBusinessViewId(task.executionId, expectedId) || !task.writable)
      throw new Error('工勘任务或执行轮次已变化，请从任务重新进入')
    return { task: { ...task } }
  }
  const stageId = businessViewIdKey(surveyStageId)
  if (typeof surveyStageCode !== 'string' || !surveyStageCode.trim()) throw new Error('工勘阶段编号无效')
  const context = await getStageBusinessContext(legacyOwnerId(projectId), surveyStageCode)
  const stage = context.execution
  if (!stage || context.readonly || context.recoverableError || context.businessView?.ownerContext !== 'SOL'
    || context.businessView.entityType !== 'SITE_SURVEY' || context.stageCode !== surveyStageCode
    || !sameBusinessViewId(context.projectId, projectId) || !sameBusinessViewId(context.stageId, stageId)
    || !sameBusinessViewId(stage.projectId, projectId) || !sameBusinessViewId(stage.stageId, stageId)
    || !sameBusinessViewId(stage.executionId, expectedId) || !stage.writable)
    throw new Error('工勘阶段或执行轮次已变化，请从阶段重新进入')
  return { stage: { ...stage } }
}
