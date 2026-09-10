import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, type Component } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import StagePanel from './ProjectStageGatePanel.vue'
import TaskPanel from './ProjectTaskPanel.vue'

const api = vi.hoisted(() => ({
  getProjectStageAdvanceReadiness: vi.fn(), advanceProjectStage: vi.fn(),
  getProjectWorkspace: vi.fn(), createTask: vi.fn()
}))
const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => api)
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('./ProjectTaskTree.vue', () => ({ default: { render: () => null } }))
vi.mock('./ProjectTaskWorkbenchDrawer.vue', () => ({ default: { render: () => null } }))

const apps: { unmount: () => void }[] = []
const render = (component: Component) => {
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(component, { projectId: 9, ref: child }) })
  const { app } = mount(wrapper, {}, { ElSelect: passthrough, ElOption: passthrough, ElForm: passthrough })
  apps.push(app)
  return () => child.value.$.setupState
}
const flush = async () => {
  for (let i = 0; i < 5; i++) { await nextTick(); await Promise.resolve() }
}
const ready = (allowed = true, currentStage = 'S0', nextStage: string | null = 'S4') => ({
  projectId: 9, projectVersion: 4, treeVersion: 2, currentStage, nextStage,
  advanceAllowed: allowed, gates: [], guidance: nextStage ? null : 'TERMINAL'
})

beforeEach(() => {
  vi.clearAllMocks()
  api.getProjectStageAdvanceReadiness.mockResolvedValue(ready())
  api.advanceProjectStage.mockResolvedValue({ afterStage: 'S4' })
  api.getProjectWorkspace.mockResolvedValue({
    projectId: 9, taskTreeVersion: 2, allowedActions: ['CREATE'],
    stageTaskNavigation: [{ stageCode: 'S0', stageName: '立项' }, { stageCode: 'S4', stageName: '实施' }]
  })
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

describe('Z06 frozen progression retry and S0 creation guard', () => {
  it('rechecks current versions and advances only the server-resolved target', async () => {
    const state = render(StagePanel); await flush()
    api.getProjectStageAdvanceReadiness.mockResolvedValueOnce({ ...ready(), projectVersion: 7 })
      .mockResolvedValueOnce(ready(false, 'S4', null))
    await state().recheckProgress()
    expect(api.advanceProjectStage).toHaveBeenCalledWith(9, expect.objectContaining({ projectVersion: 7 }), expect.any(String))
    expect(state().readiness.currentStage).toBe('S4')
  })

  it('retains the current stage and a retryable error after progression fails', async () => {
    const state = render(StagePanel); await flush()
    api.advanceProjectStage.mockRejectedValueOnce(new Error('unavailable'))
    await state().recheckProgress()
    expect(state().readiness.currentStage).toBe('S0')
    expect(state().errorMessage).toContain('已保存的业务不受影响')
    expect(message.success).not.toHaveBeenCalled()
    await state().recheckProgress()
    expect(api.advanceProjectStage).toHaveBeenCalledTimes(2)
  })

  it('does not advance an unmet or terminal stage and never closes the project', async () => {
    const state = render(StagePanel); await flush()
    api.getProjectStageAdvanceReadiness.mockResolvedValueOnce(ready(false))
    await state().recheckProgress()
    api.getProjectStageAdvanceReadiness.mockResolvedValueOnce(ready(false, 'S4', null))
    await state().recheckProgress()
    expect(api.advanceProjectStage).not.toHaveBeenCalled()
  })

  it('preserves S0 navigation while excluding S0 from new-task choices and default', async () => {
    const state = render(TaskPanel); await flush()
    expect(state().workspace.stageTaskNavigation.map((s: any) => s.stageCode)).toEqual(['S0', 'S4'])
    expect(state().creatableStages.map((s: any) => s.stageCode)).toEqual(['S4'])
    state().openCreate()
    expect(state().createForm.stageCode).toBe('S4')
    state().createForm.stageCode = 'S0'
    await state().create()
    expect(api.createTask).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalled()
  })

  it('does not open a task-creation form for a project with only S0', async () => {
    api.getProjectWorkspace.mockResolvedValueOnce({
      projectId: 9, taskTreeVersion: 2, allowedActions: ['CREATE'],
      stageTaskNavigation: [{ stageCode: 'S0', stageName: '立项' }]
    })
    const state = render(TaskPanel); await flush()
    state().openCreate()
    expect(state().createVisible).toBe(false)
  })
})
