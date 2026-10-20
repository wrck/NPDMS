import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import TaskRoute from './route.vue'
import ApprovalRoute from '../approval-form/route.vue'
import { findByTestId, mount, textOf, type TestNode } from '../cutoverTestHarness'

const state = vi.hoisted(() => ({ route: { params: {} as Record<string, unknown>, query: {} as Record<string, unknown> }, push: vi.fn(), back: vi.fn(),
  beforeLeave: vi.fn(), beforeUpdate: vi.fn(), saveBeforeLeave: vi.fn(),
  options: { history: { state: { back: '' } } }, mountedTasks: [] as string[], mountedApprovals: [] as string[] }))
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  state.route = reactive(state.route)
  return { useRoute: () => state.route, useRouter: () => state, onBeforeRouteLeave: state.beforeLeave, onBeforeRouteUpdate: state.beforeUpdate }
})
vi.mock('./index.vue', () => ({ default: defineComponent({ props: { taskId: { type: String, required: true } }, emits: ['close'], setup(props, { emit, expose }) {
  expose({ saveBeforeLeave: state.saveBeforeLeave })
  state.mountedTasks.push(props.taskId)
  return () => h('button', { 'data-testid': 'task-detail', onClick: () => emit('close') }, props.taskId)
} }) }))
vi.mock('../approval-form/index.vue', () => ({ default: defineComponent({ props: { id: { type: String, required: true } }, setup(props) {
  state.mountedApprovals.push(props.id)
  return () => h('span', { 'data-testid': 'approval-detail' }, props.id)
} }) }))
const flush = async () => { await nextTick(); await nextTick() }
const all = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(all)]

describe('割接详情路由定位', () => {
  beforeEach(() => { vi.clearAllMocks(); state.route.params = {}; state.options.history.state.back = ''; state.mountedTasks = []; state.mountedApprovals = [] })
  it('blocks route changes while saving fails and permits navigation after the draft is saved', async () => {
    state.route.params = { taskId: '101' }
    state.saveBeforeLeave.mockResolvedValue(false)
    const page = mount(TaskRoute); await flush()
    const leave = state.beforeLeave.mock.calls[0][0]
    const update = state.beforeUpdate.mock.calls[0][0]
    expect(await leave()).toBe(false)
    expect(await update({ params: { taskId: '102' } }, { params: { taskId: '101' } })).toBe(false)
    expect(await update({ params: { taskId: '101' } }, { params: { taskId: '101' } })).toBe(true)
    state.saveBeforeLeave.mockResolvedValue(true)
    expect(await leave()).toBe(true)
    page.app.unmount()
  })
  it('direct load, task switch and browser history changes mount the requested task identity', async () => {
    state.route.params = { taskId: '9007199254740993' }
    const page = mount(TaskRoute)
    await flush()
    expect(textOf(page.root)).toContain('9007199254740993')
    state.route.params = { taskId: '102' }
    await flush()
    state.route.params = { taskId: '9007199254740993' }
    await flush()
    expect(state.mountedTasks).toEqual(['9007199254740993', '102', '9007199254740993'])
    await (findByTestId(page.root, 'task-detail')!.props!.onClick as () => unknown)()
    expect(state.push).toHaveBeenCalledWith('/pms/cutover/tasks')
    state.options.history.state.back = '/pms/cutover/workbench'
    await (findByTestId(page.root, 'task-detail')!.props!.onClick as () => unknown)()
    expect(state.back).toHaveBeenCalledOnce()
    page.app.unmount()
    const refreshed = mount(TaskRoute)
    await flush()
    expect(state.mountedTasks.at(-1)).toBe('9007199254740993')
    refreshed.app.unmount()
  })
  it('approval pages use approvalId, change instances and return to the workbench on direct load', async () => {
    state.route.params = { taskId: '101', approvalId: '701' }
    const page = mount(ApprovalRoute)
    await flush()
    state.route.params = { approvalId: '702' }
    await flush()
    expect(state.mountedApprovals).toEqual(['701', '702'])
    const back = all(page.root).find(node => node.type === 'button')!
    await (back.props!.onClick as () => unknown)()
    expect(state.push).toHaveBeenCalledWith('/pms/cutover/workbench')
    page.app.unmount()
  })
  it('invalid or array route parameters never load a business object', async () => {
    for (const value of ['0', '-1', 'abc', ['101']]) {
      state.route.params = { taskId: value, approvalId: value }
      const task = mount(TaskRoute), approval = mount(ApprovalRoute)
      await flush()
      expect(findByTestId(task.root, 'task-detail')).toBeUndefined()
      expect(findByTestId(approval.root, 'approval-detail')).toBeUndefined()
      task.app.unmount(); approval.app.unmount()
    }
    expect(state.mountedTasks).toEqual([])
    expect(state.mountedApprovals).toEqual([])
  })
})
