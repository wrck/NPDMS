import { businessViewIdKey, sameBusinessViewId, legacyOwnerId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import { getTaskBusinessContext } from '@/api/pms/project/task-business'
import { getStageBusinessContext } from '@/api/pms/project/stage-business'
import type { ProjectBusinessExecutionSelection as SiteSurveyExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'
import { selectionClient, selectionIdentity } from '@/components/BusinessView/operationClient'
import { surveyExecutionQuery } from './siteSurveyOutsource'

/** A new command observes current versions of the same frozen binding and round. */
export const refreshSurveyExecution = async (request: {
  projectId: BusinessViewId; execution?: SiteSurveyExecutionSelection; stageCode?: string
}): Promise<SiteSurveyExecutionSelection | undefined> => {
  const expected = request.execution
  if (!expected || selectionClient(expected)) return expected
  const identity = selectionIdentity(expected)
  const current = await resolveSurveyExecution(request.projectId, surveyExecutionQuery(expected, request.stageCode))
  const before = expected.task || expected.stage
  const after = current?.task || current?.stage
  if (!current || selectionIdentity(current) !== identity || before?.roundNo !== after?.roundNo
    || expected.task && String(expected.task.stageExecutionId) !== String(current.task?.stageExecutionId))
    throw new Error('工勘绑定或执行轮次已变化，请从原任务或阶段重新进入')
  return current
}

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
