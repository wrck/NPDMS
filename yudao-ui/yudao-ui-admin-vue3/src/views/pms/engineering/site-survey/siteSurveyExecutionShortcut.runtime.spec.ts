import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resolveSurveyExecution } from './siteSurveyExecutionShortcut'
import { outsourceShortcutRoute, outsourceDetailUrl } from './siteSurveyOutsource'
import { getTaskBusinessContext } from '@/api/pms/project/task-business'
import { getStageBusinessContext } from '@/api/pms/project/stage-business'

vi.mock('@/api/pms/project/task-business', () => ({ getTaskBusinessContext: vi.fn() }))
vi.mock('@/api/pms/project/stage-business', () => ({ getStageBusinessContext: vi.fn() }))
const task = { projectId: '9007199254740993', taskId: '9007199254740994', executionId: '9007199254740995', executionVersion: 3, writable: true } as any
const stage = { projectId: task.projectId, stageId: '9007199254740996', executionId: '9007199254740997', executionVersion: 4, writable: true } as any
const taskContext = () => ({ projectId: task.projectId, taskId: task.taskId, ownerContext: 'SOL', objectType: 'SITE_SURVEY',
  businessView: { status: 'PUBLISHED' }, executionAllowed: true, execution: { ...task } }) as any
const stageContext = () => ({ projectId: stage.projectId, stageId: stage.stageId, stageCode: '工前/准备', readonly: false,
  businessView: { ownerContext: 'SOL', entityType: 'SITE_SURVEY' }, execution: { ...stage } }) as any
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getTaskBusinessContext).mockResolvedValue(taskContext())
  vi.mocked(getStageBusinessContext).mockResolvedValue(stageContext())
})
describe('survey outsourcing keeps the same selected execution', () => {
  it('uses only precise identities in the URL and observes the current version of that task', async () => {
    const route = outsourceShortcutRoute('9007199254740998' as any, { task: { ...task, executionVersion: 1 } })
    expect(route.query).toEqual({ siteSurveyId: '9007199254740998', surveyTaskId: task.taskId, surveyExecutionId: task.executionId })
    expect(await resolveSurveyExecution(task.projectId, route.query)).toEqual({ task })
    expect(getTaskBusinessContext).toHaveBeenCalledWith(task.taskId)
    expect(getStageBusinessContext).not.toHaveBeenCalled()
  })
  it('reads the exact stage and encodes its code without fabricating a task', async () => {
    const query = outsourceShortcutRoute(42, { stage }, '工前/准备').query
    expect(await resolveSurveyExecution(stage.projectId, query)).toEqual({ stage })
    expect(getStageBusinessContext).toHaveBeenCalledWith(stage.projectId, '工前/准备')
    const url = new URL(outsourceDetailUrl(12, { stage }, '工前/准备'), 'http://localhost')
    expect(url.searchParams.get('surveyStageCode')).toBe('工前/准备')
    expect(url.searchParams.has('surveyTaskId')).toBe(false)
  })
  it.each(['project', 'node', 'round', 'permission', 'owner', 'disabled'])('does not fall back after a task %s mismatch', async (kind) => {
    const context = taskContext()
    if (kind === 'project') context.projectId = '10'
    if (kind === 'node') context.execution.taskId = '10'
    if (kind === 'round') context.execution.executionId = '10'
    if (kind === 'permission') context.executionAllowed = false
    if (kind === 'owner') context.ownerContext = 'OTHER'
    if (kind === 'disabled') context.businessView.status = 'DISABLED'
    vi.mocked(getTaskBusinessContext).mockResolvedValue(context)
    await expect(resolveSurveyExecution(task.projectId, outsourceShortcutRoute(42, { task }).query)).rejects.toThrow()
    expect(getStageBusinessContext).not.toHaveBeenCalled()
  })
  it.each(['round', 'node', 'readonly', 'unavailable'])('rejects an unavailable stage: %s', async (kind) => {
    const context = stageContext()
    if (kind === 'round') context.execution.executionId = '10'
    if (kind === 'node') context.stageId = '10'
    if (kind === 'readonly') context.readonly = true
    if (kind === 'unavailable') context.recoverableError = 'UNAVAILABLE'
    vi.mocked(getStageBusinessContext).mockResolvedValue(context)
    await expect(resolveSurveyExecution(stage.projectId, outsourceShortcutRoute(42, { stage }, '工前/准备').query)).rejects.toThrow()
  })
  it('leaves unscoped menus unscoped and rejects partial, mixed or malformed pointers before any request', async () => {
    expect(await resolveSurveyExecution(task.projectId, {})).toBeUndefined()
    for (const query of [
      { surveyTaskId: task.taskId }, { surveyExecutionId: task.executionId },
      { surveyTaskId: task.taskId, surveyStageId: stage.stageId, surveyExecutionId: task.executionId },
      { surveyTaskId: [task.taskId], surveyExecutionId: task.executionId }
    ]) await expect(resolveSurveyExecution(task.projectId, query)).rejects.toThrow()
    expect(getTaskBusinessContext).not.toHaveBeenCalled()
    expect(getStageBusinessContext).not.toHaveBeenCalled()
    expect(() => outsourceShortcutRoute(42, { task, stage }, '工前/准备')).toThrow()
    expect(() => outsourceShortcutRoute(42, { stage })).toThrow()
  })
})
