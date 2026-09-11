import { beforeEach, expect, it, vi } from 'vitest'
import { getProjectTasks, type TaskNode } from '@/api/pms/project/task-workbench'
import { FLOW_TASK_PAGE_SIZE, mergeTaskRows, taskForest, useFlowTaskPaging } from './flowTaskPaging'

vi.mock('@/api/pms/project/task-workbench', () => ({ getProjectTasks: vi.fn() }))
const row = (taskId: number, extra: Partial<TaskNode> = {}): TaskNode => ({
  taskId, treeDepth: 0, placeholder: false, stageCode: 'S1', name: `任务${taskId}`, ...extra
})
beforeEach(() => vi.clearAllMocks())

it('keeps loading by cursor beyond ten pages, with no automatic all-tree fetch', async () => {
  vi.mocked(getProjectTasks).mockImplementation(async (_id, query) => {
    expect(query.mode).toBe('DIRECT_CHILDREN')
    const page = Number(query.cursor || 0)
    return { rows: Array.from({ length: FLOW_TASK_PAGE_SIZE }, (_, i) => row(page * FLOW_TASK_PAGE_SIZE + i)),
      nextCursor: page < 11 ? String(page + 1) : undefined, taskTreeVersion: 1 }
  })
  const state = useFlowTaskPaging(() => 9, () => 'S1')
  await state.reload()
  expect(getProjectTasks).toHaveBeenCalledTimes(1)
  for (let i = 0; i < 11; i++) await state.more()
  expect(state.rows.value).toHaveLength(1200)
  expect(state.hasMore.value).toBe(false)
})

it('retains loaded rows and the failed cursor for retry; merges repeated ancestor paths', async () => {
  vi.mocked(getProjectTasks).mockResolvedValueOnce({ rows: [row(1)], nextCursor: 'next', taskTreeVersion: 1 })
    .mockRejectedValueOnce(new Error('unavailable'))
    .mockResolvedValueOnce({ rows: [row(1, { placeholder: true }), row(2, { parentTaskId: 1 })], taskTreeVersion: 1 })
  const state = useFlowTaskPaging(() => 9, () => 'S1')
  await state.reload(); await state.more()
  expect(state.rows.value.map(r => r.taskId)).toEqual([1])
  expect(state.error.value).toContain('已加载内容保留')
  await state.retry()
  expect(vi.mocked(getProjectTasks).mock.calls[2][1].cursor).toBe('next')
  expect(state.rows.value.map(r => r.taskId)).toEqual([1, 2])
  expect(state.rows.value[0].placeholder).toBe(false)
})

it('ignores a previous project response after resetting the project context', async () => {
  let resolve!: (value: Awaited<ReturnType<typeof getProjectTasks>>) => void
  vi.mocked(getProjectTasks).mockReturnValueOnce(new Promise(done => { resolve = done }))
    .mockResolvedValueOnce({ rows: [row(99)], taskTreeVersion: 2 })
  let id = 9
  const state = useFlowTaskPaging(() => id, () => undefined)
  const previous = state.reload()
  state.reset(); id = 10
  await state.reload()
  resolve({ rows: [row(1)], nextCursor: 'stale', taskTreeVersion: 1 })
  await previous
  expect(state.rows.value.map(r => r.taskId)).toEqual([99])
  expect(state.hasMore.value).toBe(false)
})

it('keeps arbitrary depth and upgrades a permission/path placeholder without duplicate nodes', () => {
  const initial = [row(1, { placeholder: true }), row(2, { parentTaskId: 1 })]
  const rows = mergeTaskRows(initial, [row(1), row(3, { parentTaskId: 2 }), row(4, { parentTaskId: 3 })])
  const tree = taskForest(rows, 'S1')
  expect(tree).toHaveLength(1)
  expect(tree[0].children?.[0].children?.[0].children?.[0].taskId).toBe(4)
  expect(tree[0].placeholder).toBe(false)
})

it('loads children by parent identity and preserves independent child cursors and failures', async () => {
  vi.mocked(getProjectTasks).mockResolvedValueOnce({ rows: [row(1), row(2)], taskTreeVersion: 1 })
    .mockResolvedValueOnce({ rows: [row(11, { parentTaskId: 1 })], nextCursor: 'child-next', taskTreeVersion: 1 })
    .mockResolvedValueOnce({ rows: [], taskTreeVersion: 1 })
    .mockRejectedValueOnce(new Error('retry'))
    .mockResolvedValueOnce({ rows: [row(12, { parentTaskId: 1 })], taskTreeVersion: 1 })
  const state = useFlowTaskPaging(() => 9, () => 'S1')
  await state.reload(); await state.loadChildren(1); await state.loadChildren(2)
  expect(vi.mocked(getProjectTasks).mock.calls[1][1]).toMatchObject({ mode: 'DIRECT_CHILDREN', parentTaskId: 1, stageCode: 'S1' })
  expect(state.childState(1)?.cursor).toBe('child-next')
  expect(state.childState(2)?.cursor).toBeUndefined()
  await state.loadChildren(1)
  expect(state.childState(1)?.error).toBeTruthy()
  expect(state.rows.value.map(r => r.taskId)).toEqual([1, 2, 11])
  await state.loadChildren(1)
  expect(vi.mocked(getProjectTasks).mock.calls[4][1].cursor).toBe('child-next')
  expect(taskForest(state.rows.value, 'S1')[0].children?.map(r => r.taskId)).toEqual([11, 12])
})
