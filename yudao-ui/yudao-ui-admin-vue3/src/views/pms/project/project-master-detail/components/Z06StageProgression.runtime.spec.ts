import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, type Component } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import StagePanel from './ProjectStageGatePanel.vue'

const api = vi.hoisted(() => ({
  getProjectStageAdvanceReadiness: vi.fn(),
  startProjectStageGateProcess: vi.fn(),
  advanceProjectStage: vi.fn()
}))
const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))

const apps: { unmount: () => void }[] = []
const render = (component: Component, onChanged = vi.fn()) => {
  const child = ref<any>()
  const wrapper = defineComponent({
    setup: () => () =>
      h(component, { projectId: 9, project: { id: 9, version: 4 }, ref: child, onChanged })
  })
  const { app } = mount(
    wrapper,
    {},
    { ElSelect: passthrough, ElOption: passthrough, ElForm: passthrough }
  )
  apps.push(app)
  return () => child.value.$.setupState
}
const flush = async () => {
  for (let i = 0; i < 5; i++) {
    await nextTick()
    await Promise.resolve()
  }
}
const ready = (allowed = true, currentStage = 'S0', nextStage: string | null = 'S4') => ({
  projectId: 9,
  projectVersion: 4,
  treeVersion: 2,
  currentStage,
  nextStage,
  advanceAllowed: allowed,
  gates: [],
  guidance: nextStage ? null : 'TERMINAL'
})

beforeEach(() => {
  vi.clearAllMocks()
  api.getProjectStageAdvanceReadiness.mockResolvedValue(ready())
  api.advanceProjectStage.mockResolvedValue({ afterStage: 'S4' })
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

describe('Z06 frozen progression retry', () => {
  it('uses the server frozen definition and reports failure without claiming a started process', async () => {
    const state = render(StagePanel)
    await flush()
    api.startProjectStageGateProcess.mockRejectedValueOnce(new Error('frozen definition unavailable'))
    await state().startProcess(22)
    expect(api.startProjectStageGateProcess).toHaveBeenCalledWith(9, 22, 4, expect.any(String))
    expect(state().startedProcesses[22]).toBeUndefined()
    expect(state().errorMessage).toContain('不会改用其他版本')
    expect(message.success).not.toHaveBeenCalled()
  })
  it('notifies the host only after a successful progression, not after a failed retry', async () => {
    const changed = vi.fn()
    const state = render(StagePanel, changed)
    await flush()
    api.advanceProjectStage.mockRejectedValueOnce(new Error('unavailable'))
    await state().recheckProgress()
    expect(changed).not.toHaveBeenCalled()
    await state().recheckProgress()
    expect(changed).toHaveBeenCalledTimes(1)
  })

  it('rechecks current versions and advances only the server-resolved target', async () => {
    const state = render(StagePanel)
    await flush()
    api.getProjectStageAdvanceReadiness
      .mockResolvedValueOnce({ ...ready(), projectVersion: 7 })
      .mockResolvedValueOnce(ready(false, 'S4', null))
    await state().recheckProgress()
    expect(api.advanceProjectStage).toHaveBeenCalledWith(
      9,
      expect.objectContaining({ projectVersion: 7 }),
      expect.any(String)
    )
    expect(state().readiness.currentStage).toBe('S4')
  })

  it('retains the current stage and a retryable error after progression fails', async () => {
    const state = render(StagePanel)
    await flush()
    api.advanceProjectStage.mockRejectedValueOnce(new Error('unavailable'))
    await state().recheckProgress()
    expect(state().readiness.currentStage).toBe('S0')
    expect(state().errorMessage).toContain('已保存的业务不受影响')
    expect(message.success).not.toHaveBeenCalled()
    await state().recheckProgress()
    expect(api.advanceProjectStage).toHaveBeenCalledTimes(2)
  })

  it('does not advance an unmet or terminal stage and never closes the project', async () => {
    const state = render(StagePanel)
    await flush()
    api.getProjectStageAdvanceReadiness.mockResolvedValueOnce(ready(false))
    await state().recheckProgress()
    api.getProjectStageAdvanceReadiness.mockResolvedValueOnce(ready(false, 'S4', null))
    await state().recheckProgress()
    expect(api.advanceProjectStage).not.toHaveBeenCalled()
  })
})
