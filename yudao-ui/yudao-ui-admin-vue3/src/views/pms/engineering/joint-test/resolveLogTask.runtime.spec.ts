import { beforeEach, expect, it, vi } from 'vitest'
import { getProjectPlan } from '@/api/pms/project/projects/projectPlan'
import { getProjectTasks } from '@/api/pms/project/task-workbench'
import { resolveJointLogTask } from './resolveLogTask'

vi.mock('@/api/pms/project/projects/projectPlan', () => ({ getProjectPlan: vi.fn() }))
vi.mock('@/api/pms/project/task-workbench', () => ({ getProjectTasks: vi.fn() }))

const definition = (code = 'CUSTOM_JOINT') => ({
  code, stageCode: 'S4', name: '自定义联调任务',
  workBinding: { type: 'PAGE', parameters: { routePath: '/pms/engineering/execution/imp-joint-test' } }
})
const plan = (tasks: unknown[]) => ({ effective: { designer: { tasks } } }) as any
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getProjectPlan).mockResolvedValue(plan([definition()]))
  vi.mocked(getProjectTasks).mockResolvedValue({ rows: [], taskTreeVersion: 1 })
})

it('uses effective route binding and preserves large identifiers without conversion', async () => {
  const project = '2110018733130579974'
  vi.mocked(getProjectTasks).mockResolvedValue({ rows: [
    { taskId: 11, taskCode: 'CONFIG', stageCode: 'S4', name: '业务联调' },
    { taskId: '2107838747850678283', taskCode: 'CUSTOM_JOINT', stageCode: 'S4', placeholder: false }
  ] } as any)
  expect(await resolveJointLogTask(project)).toBe('2107838747850678283')
  expect(getProjectPlan).toHaveBeenCalledWith(project)
  expect(getProjectTasks).toHaveBeenCalledTimes(1)
})
it('follows the cursor rather than losing a task outside the first page', async () => {
  vi.mocked(getProjectTasks).mockResolvedValueOnce({ rows: [], nextCursor: 'next', taskTreeVersion: 1 })
    .mockResolvedValueOnce({ rows: [{ taskId: 22, taskCode: 'CUSTOM_JOINT', stageCode: 'S4', placeholder: false, treeDepth: 0 }], taskTreeVersion: 1 })
  expect(await resolveJointLogTask(9)).toBe(22)
  expect(getProjectTasks).toHaveBeenLastCalledWith(9, { mode: 'LOCATE', keyword: 'CUSTOM_JOINT', pageSize: 100, cursor: 'next' })
})
it('rejects multiple task bindings without guessing the first one', async () => {
  vi.mocked(getProjectPlan).mockResolvedValue(plan([definition(), definition('OTHER')]))
  await expect(resolveJointLogTask(9)).rejects.toThrow('AMBIGUOUS')
  expect(getProjectTasks).not.toHaveBeenCalled()
})
it('does not treat a configuration binding as a joint-test binding', async () => {
  vi.mocked(getProjectPlan).mockResolvedValue(plan([{ ...definition(), workBinding: { type: 'PAGE', parameters: { routePath: '/pms/engineering/execution/imp-configuration' } } }]))
  await expect(resolveJointLogTask(9)).rejects.toThrow('AMBIGUOUS')
  expect(getProjectTasks).not.toHaveBeenCalled()
})
it('rejects placeholders or a matching code belonging to a different stage', async () => {
  vi.mocked(getProjectTasks).mockResolvedValue({ rows: [
    { taskId: 11, taskCode: 'CUSTOM_JOINT', stageCode: 'S4', placeholder: true },
    { taskId: 12, taskCode: 'CUSTOM_JOINT', stageCode: 'S3', placeholder: false }
  ] } as any)
  await expect(resolveJointLogTask(9)).rejects.toThrow('UNAVAILABLE')
})
it('propagates failed authorization or loading and never returns an unscoped task', async () => {
  vi.mocked(getProjectPlan).mockRejectedValue(new Error('denied'))
  await expect(resolveJointLogTask(9)).rejects.toThrow('denied')
  expect(getProjectTasks).not.toHaveBeenCalled()
})
