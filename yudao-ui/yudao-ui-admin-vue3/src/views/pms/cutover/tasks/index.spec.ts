import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { findByTestId, mount, textOf, type TestNode } from '../cutoverTestHarness'
import TaskList from './index.vue'

const api = vi.hoisted(() => ({ getCutoverTaskPage: vi.fn() }))
const navigation = vi.hoisted(() => ({ route: { query: {} as Record<string, unknown> }, push: vi.fn() }))
vi.mock('@/api/pms/cutover', () => api)
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  navigation.route = reactive(navigation.route)
  return { useRoute: () => navigation.route, useRouter: () => navigation }
})
const all = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(all)]
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const row = (id: string, extra = {}) => ({ id, taskNo: `CUT-${id}`, taskName: `任务${id}`, projectId: '21',
  projectName: '项目甲', intakeSourceType: 'ITR', manualGrade: 'A', currentStage: 'P6', taskStatus: 'ARCHIVED', generatedAt: 1788400800000, ...extra })
const page = (rows: unknown[]) => ({ list: rows, total: rows.length })
const click = async (root: TestNode, label: string) => {
  const button = all(root).find(node => node.type === 'button' && textOf(node) === label)!
  await (button.props!.onClick as () => unknown)(); await flush()
}

describe('独立割接任务列表', () => {
  beforeEach(() => { vi.clearAllMocks(); api.getCutoverTaskPage.mockReset(); navigation.route.query = {} })

  it('默认查询全部来源和已归档任务，按任务ID打开统一详情', async () => {
    api.getCutoverTaskPage.mockResolvedValue(page([row('9007199254740993')]))
    const mounted = mount(TaskList)
    await flush()
    expect(api.getCutoverTaskPage).toHaveBeenCalledWith(expect.objectContaining({ listKind: 'ALL', pageNo: 1, pageSize: 20 }))
    expect(textOf(mounted.root)).toContain('任务9007199254740993')
    expect(textOf(mounted.root)).toContain('已归档')
    await (findByTestId(mounted.root, 'open-cutover-task')!.props!.onClick as () => unknown)()
    expect(navigation.push).toHaveBeenCalledWith('/pms/cutover/tasks/9007199254740993')
    mounted.app.unmount()
  })

  it('项目、阶段、状态和分页传给统一查询，重置回到第一页', async () => {
    api.getCutoverTaskPage.mockResolvedValue(page([]))
    const mounted = mount(TaskList)
    await flush()
    for (const [placeholder, value] of [['输入项目名称查找', '21'], ['全部阶段', 'P5'], ['全部状态', 'APPROVING']]) {
      const control = all(mounted.root).find(node => node.props?.placeholder === placeholder)!
      ;(control.props!['onUpdate:modelValue'] as (value: string) => void)(value)
    }
    const form = all(mounted.root).find(node => typeof node.props?.onSubmit === 'function')!
    ;(form.props!.onSubmit as (event: unknown) => void)({ preventDefault() {} })
    await flush()
    expect(api.getCutoverTaskPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: '21', currentStage: 'P5', taskStatus: 'APPROVING', pageNo: 1 }))
    const pagination = all(mounted.root).find(node => typeof node.props?.onPagination === 'function')!
    ;(pagination.props!['onUpdate:page'] as (value: number) => void)(2)
    ;(pagination.props!.onPagination as () => void)()
    await flush()
    expect(api.getCutoverTaskPage).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2, projectId: '21' }))
    await click(mounted.root, '重置')
    expect(api.getCutoverTaskPage).toHaveBeenLastCalledWith({ listKind: 'ALL', pageNo: 1, pageSize: 20,
      projectId: undefined, currentStage: undefined, taskStatus: undefined })
    mounted.app.unmount()
  })

  it('审批受限记录按审批实例打开，不先读取普通任务详情', async () => {
    api.getCutoverTaskPage.mockResolvedValue(page([row('101', { approvalOnly: true, approvalInstanceId: '701' })]))
    const mounted = mount(TaskList)
    await flush()
    await (findByTestId(mounted.root, 'open-cutover-task')!.props!.onClick as () => unknown)()
    expect(navigation.push).toHaveBeenCalledWith('/pms/cutover/approvals/701')
    mounted.app.unmount()
  })

  it('切换项目后拒绝迟到响应，查询失败显示错误而不保留过期结果', async () => {
    let resolveOld!: (value: unknown) => void
    api.getCutoverTaskPage.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce(page([row('202')])).mockRejectedValueOnce(new Error('offline'))
    const mounted = mount(TaskList)
    await flush()
    navigation.route.query = { projectId: '22' }
    await flush()
    resolveOld(page([row('101')]))
    await flush()
    expect(textOf(mounted.root)).toContain('任务202')
    expect(textOf(mounted.root)).not.toContain('任务101')
    await click(mounted.root, '重置')
    expect(textOf(mounted.root)).toContain('割接任务加载失败')
    expect(textOf(mounted.root)).not.toContain('任务202')
    mounted.app.unmount()
  })
})
