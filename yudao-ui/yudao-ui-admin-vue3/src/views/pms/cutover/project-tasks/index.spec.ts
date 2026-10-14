import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { findByTestId, mount, textOf } from '../cutoverTestHarness'
import ProjectTasks from './index.vue'

const api = vi.hoisted(() => ({ getCutoverTaskPage: vi.fn(), getCutoverDashboardKpis: vi.fn() }))
vi.mock('@/api/pms/cutover', () => api)
vi.mock('../components/CutoverCreateWizard.vue', () => ({ default: defineComponent({ render: () => null }) }))
vi.mock('../task-detail/index.vue', () => ({ default: defineComponent({
  props: ['taskId', 'projectId', 'readonly'], emits: ['close'], setup(props, { emit }) {
    return () => h('section', { 'data-testid': 'shared-detail', ...props }, [
      `项目${props.projectId}任务${props.taskId}`,
      h('button', { 'data-testid': 'return-list', onClick: () => emit('close') }, '返回')
    ])
  }
}) }))

const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const row = (id: string) => ({ id, projectId: '201', taskName: `任务${id}`, taskNo: id, currentStage: 'P6', taskStatus: 'ARCHIVED', intakeSourceType: 'SELF_CREATED' })

describe('项目内割接任务列表', () => {
  beforeEach(() => vi.resetAllMocks())

  it('固定项目查询，不加载个人待办或跨项目统计，点击任务复用详情', async () => {
    api.getCutoverTaskPage.mockResolvedValue({ list: [row('9007199254740993')], total: 1 })
    const page = mount(ProjectTasks, { projectId: '201', readonly: true })
    await flush()
    expect(api.getCutoverTaskPage).toHaveBeenCalledWith({ projectId: '201', pageNo: 1, pageSize: 10 })
    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(1)
    expect(api.getCutoverDashboardKpis).not.toHaveBeenCalled()
    expect(textOf(page.root)).not.toContain('我的审批待办')
    await (findByTestId(page.root, 'open-project-cutover')!.props!.onClick as () => void)()
    await flush()
    expect(findByTestId(page.root, 'shared-detail')?.props).toMatchObject({ taskId: '9007199254740993', projectId: '201', readonly: true })
    await (findByTestId(page.root, 'return-list')!.props!.onClick as () => void)()
    await flush()
    expect(api.getCutoverTaskPage).toHaveBeenCalledTimes(2)
    page.app.unmount()
  })

  it('切换项目清空旧详情，迟到的旧列表不能覆盖新项目', async () => {
    let finishOld!: (value: unknown) => void
    api.getCutoverTaskPage.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve })).mockResolvedValue({ list: [row('new')], total: 1 })
    const projectId = ref('201')
    const host = defineComponent({ setup: () => () => h(ProjectTasks, { projectId: projectId.value }) })
    const page = mount(host)
    await flush()
    projectId.value = '202'
    await flush()
    finishOld({ list: [row('old')], total: 1 })
    await flush()
    expect(textOf(page.root)).toContain('任务new')
    expect(textOf(page.root)).not.toContain('任务old')
    expect(api.getCutoverTaskPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: '202' }))
    page.app.unmount()
  })

  it('查询失败明确显示错误，不能冒充项目没有任务', async () => {
    api.getCutoverTaskPage.mockRejectedValue(new Error('unavailable'))
    const page = mount(ProjectTasks, { projectId: '201' })
    await flush()
    expect(textOf(page.root)).toContain('当前项目的割接任务加载失败')
    expect(findByTestId(page.root, 'create-project-cutover')).toBeUndefined()
    page.app.unmount()
  })
})
