import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, onUnmounted, reactive, ref } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import ProjectStageStatusPanel from './ProjectStageStatusPanel.vue'

const api = vi.hoisted(() => ({ getProjectInstances: vi.fn(), getNodeExecutions: vi.fn(), submitStageExecution: vi.fn() }))
const flow = vi.hoisted(() => ({ props: vi.fn(), unmounted: vi.fn(), leave: vi.fn(), changed: undefined as undefined | (() => void) }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectInstances: api.getProjectInstances }))
vi.mock('@/api/pms/project/projects/nodeExecutions', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
vi.mock('./ProjectExecutionHistory.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))
vi.mock('./ProjectPlanEditor.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))
vi.mock('./ProjectReworkPanel.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))
vi.mock('./ProjectFlowPanel.vue', () => ({ default: defineComponent({
  props: { projectId: Number, project: Object, selection: Object, showResponsibilities: Boolean },
  emits: ['changed'],
  setup(props, { expose, emit }) {
    expose({ requestLeave: flow.leave })
    flow.changed = () => emit('changed')
    onUnmounted(flow.unmounted)
    return () => { flow.props(props.project, props.selection); return h('section', '同一阶段办理表单') }
  }
}) }))
const drawer = defineComponent({
  props: { modelValue: Boolean, beforeClose: Function },
  setup: (props, { slots }) => () => props.modelValue ? h('section', slots.default?.()) : null
})
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const setup = async () => {
  const project = reactive({ id: 9, version: 1 })
  const panel = ref<InstanceType<typeof ProjectStageStatusPanel>>()
  const changed = vi.fn()
  const view = mount(defineComponent({ setup: () => () => h(ProjectStageStatusPanel, {
    ref: panel, projectId: project.id, project: { ...project }, onChanged: changed
  }) }), {}, { ElDrawer: drawer, ElDialog: drawer, ElTable: passthrough, ElTableColumn: tableColumn, ElInput: passthrough })
  apps.push(view.app)
  await flush()
  return { project, changed, state: () => (panel.value as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  flow.leave.mockResolvedValue(false)
  api.getProjectInstances.mockResolvedValue({ stages: [{ stageCode: 'PREP_WORK', name: '工前准备', status: 'ACTIVE' }] })
  api.getNodeExecutions.mockResolvedValue([{ id: '71', nodeKind: 'STAGE', nodeCode: 'PREP_WORK', roundNo: 2, canSubmit: false }])
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

it('opens the current stage in the canonical drawer without creating manual handling evidence', async () => {
  const view = await setup()
  view.state().openWorkbench('PREP_WORK')
  await flush()
  expect(flow.props.mock.lastCall![1]).toEqual({ kind: 'stage', stageCode: 'PREP_WORK' })
  expect(flow.props.mock.lastCall![0].id).toBe(9)
  expect(api.submitStageExecution).not.toHaveBeenCalled()
})

it('retains the mounted Owner form across project metadata/version refreshes', async () => {
  const view = await setup()
  view.state().openWorkbench('PREP_WORK')
  await flush()
  view.project.version = 2
  await flush()
  expect(api.getProjectInstances).toHaveBeenCalledTimes(2)
  expect(flow.unmounted).not.toHaveBeenCalled()
  expect(flow.props.mock.lastCall![0].version).toBe(2)
  expect(view.state().workbenchVisible).toBe(true)
})

it('refreshes state after Owner save but does not submit a second completion or unmount the form on read failure', async () => {
  const view = await setup()
  view.state().openWorkbench('PREP_WORK')
  await flush()
  flow.changed?.()
  await flush()
  expect(view.changed).toHaveBeenCalledOnce()
  api.getProjectInstances.mockRejectedValueOnce(new Error('read unavailable'))
  flow.changed?.()
  await flush()
  expect(view.state().error).toContain('阶段状态加载失败')
  expect(flow.unmounted).not.toHaveBeenCalled()
  expect(api.submitStageExecution).not.toHaveBeenCalled()
})

it('does not manufacture a drawer selection for a stage outside the loaded project', async () => {
  const view = await setup()
  view.state().openWorkbench('OTHER_STAGE')
  await flush()
  expect(view.state().workbenchVisible).toBe(false)
  expect(flow.props).not.toHaveBeenCalled()
})
