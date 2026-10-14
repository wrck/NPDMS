import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { findByTestId, mount, textOf } from '../cutoverTestHarness'
import Detail from './index.vue'

const api = vi.hoisted(() => ({ getCutoverTaskDetail: vi.fn(), saveCutoverAssessment: vi.fn(), submitCutoverAssessment: vi.fn() }))
vi.mock('@/api/pms/cutover', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), warning: vi.fn() }) }))
vi.mock('../components/CutoverProjectContextCard.vue', () => ({ default: defineComponent({ render: () => null }) }))
vi.mock('../components/CutoverChecklistPanel.vue', () => ({ default: defineComponent({ render: () => null }) }))
vi.mock('../components/CutoverPlanPanel.vue', () => ({ default: defineComponent({ render: () => null }) }))
vi.mock('../components/CutoverApprovalPanel.vue', () => ({ default: defineComponent({ render: () => null }) }))
vi.mock('../components/CutoverClosurePanel.vue', () => ({ default: defineComponent({
  props: ['taskId', 'readonly'], setup(props) { return () => h('section', { 'data-testid': 'closure', readonly: props.readonly }, `闭环:${props.taskId}`) }
}) }))

const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const task = (id = '9007199254740993', projectId = '201') => ({
  task: { id, projectId, taskName: `任务${id}`, taskNo: 'CUT-1', currentStage: 'P6', taskStatus: 'ARCHIVED', version: 1 },
  devices: [], allowedActions: [], workbenchSteps: [{ stage: 'P6', label: '闭环', state: 'CURRENT' }]
})

describe('共享割接任务详情', () => {
  beforeEach(() => vi.resetAllMocks())

  it('仅按任务ID加载详情，项目上下文只用于约束归属', async () => {
    api.getCutoverTaskDetail.mockResolvedValue(task())
    const page = mount(Detail, { taskId: '9007199254740993', projectId: '201', readonly: true })
    await flush()
    expect(api.getCutoverTaskDetail).toHaveBeenCalledWith('9007199254740993')
    expect(textOf(page.root)).toContain('闭环:9007199254740993')
    expect(findByTestId(page.root, 'closure')?.props?.readonly).toBe(true)
    page.app.unmount()
  })

  it('拒绝把其他项目的任务详情显示到当前项目', async () => {
    api.getCutoverTaskDetail.mockResolvedValue(task('100', '202'))
    const page = mount(Detail, { taskId: '100', projectId: '201' })
    await flush()
    expect(textOf(page.root)).toContain('当前无法办理任务')
    expect(findByTestId(page.root, 'closure')).toBeUndefined()
    expect(textOf(page.root)).not.toContain('任务100')
    page.app.unmount()
  })

  it('切换任务后忽略上一任务迟到的响应', async () => {
    let finishOld!: (value: unknown) => void
    api.getCutoverTaskDetail.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve })).mockResolvedValue(task('200'))
    const current = ref('100')
    const host = defineComponent({ setup: () => () => h(Detail, { taskId: current.value, projectId: '201' }) })
    const page = mount(host)
    await flush()
    current.value = '200'
    await flush()
    finishOld(task('100'))
    await flush()
    expect(textOf(page.root)).toContain('任务200')
    expect(textOf(page.root)).not.toContain('任务100')
    page.app.unmount()
  })

  it('刷新失败后保留已读取内容并禁止继续办理', async () => {
    api.getCutoverTaskDetail.mockResolvedValueOnce(task()).mockRejectedValueOnce(new Error('unavailable'))
    const page = mount(Detail, { taskId: '9007199254740993' })
    await flush()
    await (findByTestId(page.root, 'refresh-cutover-detail')!.props!.onClick as () => Promise<void>)()
    await flush()
    expect(textOf(page.root)).toContain('闭环:9007199254740993')
    expect(findByTestId(page.root, 'closure')?.props?.readonly).toBe(true)
    page.app.unmount()
  })
})
