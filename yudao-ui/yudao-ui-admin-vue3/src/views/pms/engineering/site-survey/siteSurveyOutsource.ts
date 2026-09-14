import { isBusinessViewId, legacyOwnerId, businessViewIdKey } from '@/api/pms/platform/business-view/ids'
import type { SiteSurveyExecutionSelection } from '@/api/pms/engineering/site-survey'

const outsourcePath = '/pms/engineering/procurement/eng-outsource'
export const surveyPath = '/pms/engineering/preparation/eng-site-survey'
export const positiveShortcutId = (value: unknown): number | undefined => {
  return typeof value === 'string' && isBusinessViewId(value) ? legacyOwnerId(value) : undefined
}
// URL carries node identity only, not business content or independently editable rules.
export const surveyExecutionQuery = (execution?: SiteSurveyExecutionSelection, stageCode?: string): Record<string, string> => {
  if (!execution) return {}
  if (!!execution.task === !!execution.stage) throw new Error('工勘执行上下文必须明确选择任务或阶段')
  if (execution.task) return {
    surveyTaskId: businessViewIdKey(execution.task.taskId), surveyExecutionId: businessViewIdKey(execution.task.executionId)
  }
  if (!stageCode?.trim()) throw new Error('工勘阶段编号缺失，请重新加载阶段')
  return {
    surveyStageId: businessViewIdKey(execution.stage!.stageId), surveyStageCode: stageCode,
    surveyExecutionId: businessViewIdKey(execution.stage!.executionId)
  }
}
export const outsourceShortcutRoute = (surveyId: number, execution?: SiteSurveyExecutionSelection, stageCode?: string) => ({
  path: outsourcePath,
  query: { siteSurveyId: businessViewIdKey(surveyId), ...surveyExecutionQuery(execution, stageCode) }
})
export const outsourceDetailUrl = (id: number, execution?: SiteSurveyExecutionSelection, stageCode?: string) =>
  `${outsourcePath}?${new URLSearchParams({ requestId: businessViewIdKey(id), ...surveyExecutionQuery(execution, stageCode) })}`
