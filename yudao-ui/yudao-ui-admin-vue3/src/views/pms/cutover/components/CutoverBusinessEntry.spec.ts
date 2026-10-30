import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import CutoverBusinessEntry from './CutoverBusinessEntry.vue'
import { findByTestId, mount, textOf } from '../cutoverTestHarness'

const state = vi.hoisted(() => ({ push: vi.fn(), getApproval: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: state.push }) }))
vi.mock('@/api/pms/cutover', () => ({ getCutoverApproval: state.getApproval }))
const flush = async () => { for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() } }
const props = { projectId: '70', objectId: '9007199254740993', mode: 'APPROVAL', allowedActions: ['QUERY'] }

describe('CUT frozen business view current entry', () => {
  beforeEach(() => vi.resetAllMocks())
  it('resolves the task identity to its approval instance before navigating', async () => {
    state.getApproval.mockResolvedValue({ taskId: props.objectId, approvalInstanceId: '9007199254740999' })
    const mounted = mount(CutoverBusinessEntry, props)
    await (findByTestId(mounted.root, 'open-cutover-business')!.props!.onClick as () => unknown)()
    expect(state.getApproval).toHaveBeenCalledWith(props.objectId)
    expect(state.push).toHaveBeenCalledWith('/pms/cutover/approvals/9007199254740999')
    mounted.app.unmount()
  })
  it('keeps task navigation and project-only selection on the current routes', async () => {
    const task = mount(CutoverBusinessEntry, { ...props, mode: 'TASK' })
    await (findByTestId(task.root, 'open-cutover-business')!.props!.onClick as () => unknown)()
    expect(state.push).toHaveBeenLastCalledWith('/pms/cutover/tasks/9007199254740993')
    task.app.unmount()
    const list = mount(CutoverBusinessEntry, { ...props, objectId: undefined })
    await (findByTestId(list.root, 'open-cutover-business')!.props!.onClick as () => unknown)()
    expect(state.push).toHaveBeenLastCalledWith({ path: '/pms/cutover/tasks', query: { projectId: '70' } })
    list.app.unmount()
  })
  it('does not navigate on mismatched identity, lost permission or unmounted late responses', async () => {
    state.getApproval.mockResolvedValue({ taskId: 'other', approvalInstanceId: '701' })
    const mismatch = mount(CutoverBusinessEntry, props)
    await (findByTestId(mismatch.root, 'open-cutover-business')!.props!.onClick as () => unknown)()
    expect(textOf(mismatch.root)).toContain('割接审批加载失败')
    expect(state.push).not.toHaveBeenCalled()
    mismatch.app.unmount()
    const denied = mount(CutoverBusinessEntry, { ...props, allowedActions: [] })
    expect(findByTestId(denied.root, 'open-cutover-business')).toBeUndefined()
    denied.app.unmount()
    let resolve!: (value: unknown) => void
    state.getApproval.mockImplementation(() => new Promise(done => { resolve = done }))
    const late = mount(CutoverBusinessEntry, props)
    void (findByTestId(late.root, 'open-cutover-business')!.props!.onClick as () => unknown)()
    late.app.unmount()
    resolve({ taskId: props.objectId, approvalInstanceId: '701' })
    await flush()
    expect(state.push).not.toHaveBeenCalled()
  })
})
