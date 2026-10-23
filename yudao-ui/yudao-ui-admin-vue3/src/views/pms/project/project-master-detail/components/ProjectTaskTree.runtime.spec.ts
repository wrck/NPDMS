import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Tree from './ProjectTaskTree.vue'
import * as Api from '@/api/pms/project/task-workbench'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const feedback = vi.hoisted(() => ({ error: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => feedback }))
vi.mock('@/api/pms/project/task-workbench', () => ({ getProjectTasks: vi.fn() }))
const row = (taskId = 1, status = 'IN_PROGRESS') => ({ taskId, name: `Task ${taskId}`, status, progress: 0 })
const result = (rows: any[], nextCursor?: string) => ({ rows, nextCursor, taskTreeVersion: 2 })
const render = () => {
  const view = mount(Tree, { projectId: 7, stageCode: 'S1', compact: true }, { ElTree: { setup(_: unknown, { expose }: any) { expose({ getNode: () => undefined }); return () => null } } })
  return { ...view, state: (view.vm as any).$.setupState }
}
const flush = async () => { for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() } }
beforeEach(() => vi.clearAllMocks())
it('retains existing navigation while refreshing and updates the same nodes in place', async () => {
  const view = render()
  try {
    vi.mocked(Api.getProjectTasks).mockResolvedValueOnce(result([row()]) as any)
    await view.state.loadNode({ level: 0 }, vi.fn())
    const original = view.state.rootRows[0], renderKey = view.state.renderKey
    let finish!: (value: any) => void
    vi.mocked(Api.getProjectTasks).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const pending = view.state.search(); await flush()
    expect(view.state.rootRows[0]).toBe(original)
    expect(view.state.renderKey).toBe(renderKey)
    expect(view.state.loading).toBe(false)
    finish(result([row(1, 'DONE')]))
    await pending
    expect(view.state.rootRows[0]).toBe(original)
    expect(original.status).toBe('DONE')
    expect(view.state.renderKey).toBe(renderKey)
  } finally { view.app.unmount() }
})
it('retains navigation and reports a refresh failure instead of clearing the tree', async () => {
  const view = render()
  try {
    vi.mocked(Api.getProjectTasks).mockResolvedValueOnce(result([row()]) as any)
    await view.state.loadNode({ level: 0 }, vi.fn())
    const original = view.state.rootRows[0]
    vi.mocked(Api.getProjectTasks).mockRejectedValueOnce(new Error('offline'))
    await view.state.search()
    expect(view.state.rootRows[0]).toBe(original)
    expect(feedback.error).toHaveBeenCalledWith('任务树刷新失败，已保留当前导航，请重试')
  } finally { view.app.unmount() }
})
it('refreshes all previously loaded pages without discarding later siblings', async () => {
  const view = render()
  try {
    const rows = Array.from({ length: 60 }, (_, index) => row(index + 1))
    vi.mocked(Api.getProjectTasks).mockResolvedValueOnce(result(rows) as any)
    await view.state.loadNode({ level: 0 }, vi.fn())
    vi.mocked(Api.getProjectTasks).mockResolvedValueOnce(result(rows.slice(0, 50), 'next') as any)
      .mockResolvedValueOnce(result(rows.slice(50)) as any)
    await view.state.search()
    expect(view.state.rootRows).toHaveLength(60)
    expect(Api.getProjectTasks).toHaveBeenLastCalledWith(7, expect.objectContaining({ cursor: 'next' }))
  } finally { view.app.unmount() }
})
