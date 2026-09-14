import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, onUnmounted, ref } from 'vue'
import StageBusinessPanel from './StageBusinessPanel.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ getStageBusinessContext: vi.fn() }))
const leave = vi.hoisted(() => vi.fn())
const ownerUnmounted = vi.hoisted(() => vi.fn())
const ownerContext = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/project/stage-business', () => api)
vi.mock('./StageApprovalPanel.vue', () => ({ default: defineComponent({
  props: ['workbench', 'disabled'],
  setup(props, { expose }) {
    expose({ requestLeave: leave, isBusy: () => false })
    return () => h('div', `阶段审批:${props.workbench.execution?.executionId}; disabled:${props.disabled}`)
  }
}) }))
vi.mock('@/components/BusinessView/BusinessViewHost.vue', () => ({ default: defineComponent({
  props: ['resolvedContext', 'readonly', 'allowedActions'],
  setup(props, { expose }) {
    expose({ requestLeave: leave }); onUnmounted(ownerUnmounted)
    return () => { ownerContext(props.resolvedContext); return h('div', `Owner:${props.resolvedContext.project.id}; readonly:${props.readonly}; actions:${props.allowedActions.join(',')}`) }
  }
}) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 6; i++) { await Promise.resolve(); await nextTick() } }
const native = { projectId: '9', stageId: '90', stageCode: 'S4', bindingType: 'STAGE_NATIVE', ownerActions: [], readonly: true }
const render = () => {
  const child = ref<any>()
  const project = ref({ id: 9, version: 2 })
  const view = mount(defineComponent({ setup: () => () => h(StageBusinessPanel, { ref: child, project: project.value, stageCode: 'S4' }) }))
  apps.push(view.app)
  return { ...view, project, component: () => child.value, state: () => child.value.$.setupState }
}
beforeEach(() => { vi.clearAllMocks(); leave.mockResolvedValue(false); api.getStageBusinessContext.mockResolvedValue(native) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('loads native stage from its own context endpoint without manufacturing an external page', async () => {
  const view = render(); await flush()
  expect(api.getStageBusinessContext).toHaveBeenCalledWith(9, 'S4')
  expect(textOf(view.root)).toContain('本阶段使用原生办理')
  expect(textOf(view.root)).not.toContain('Owner:')
})
it('passes the authorized current project and readonly result to the registered view', async () => {
  api.getStageBusinessContext.mockResolvedValue({ ...native, bindingType: 'BUSINESS_OBJECT', businessView: { id: 88 } })
  const view = render(); await flush()
  expect(textOf(view.root)).toContain('Owner:9; readonly:true')
  expect(await view.component().requestLeave()).toBe(false)
  await view.component().refresh()
  expect(api.getStageBusinessContext).toHaveBeenCalledTimes(1)
})
it('retains the loaded Owner body when a post-save context refresh fails', async () => {
  api.getStageBusinessContext.mockResolvedValueOnce({ ...native, readonly: false, ownerActions: ['QUERY', 'COMPLETE'], bindingType: 'BUSINESS_OBJECT', businessView: { id: 88 } })
    .mockRejectedValueOnce(new Error('unavailable'))
  const view = render(); await flush()
  await view.state().handleChanged(); await flush()
  expect(textOf(view.root)).toContain('Owner:9')
  expect(textOf(view.root)).toContain('readonly:true; actions:')
  expect(textOf(view.root)).not.toContain('actions:QUERY')
  expect(view.state().context.ownerActions).toEqual(['QUERY', 'COMPLETE'])
  expect(textOf(view.root)).toContain('阶段业务上下文加载失败')
})
it('rejects a context for another project instead of mounting its view', async () => {
  api.getStageBusinessContext.mockResolvedValue({ ...native, projectId: '10', bindingType: 'BUSINESS_OBJECT', businessView: { id: 88 } })
  const view = render(); await flush()
  expect(textOf(view.root)).not.toContain('Owner:')
  expect(textOf(view.root)).toContain('阶段业务上下文加载失败')
})
it('mounts stage approval in the shared business area and delegates its leave guard before refresh', async () => {
  api.getStageBusinessContext.mockResolvedValue({ ...native, bindingType: 'APPROVAL', execution: { executionId: '101' } })
  const view = render(); await flush()
  expect(textOf(view.root)).toContain('阶段审批:101'); expect(textOf(view.root)).not.toContain('Owner:')
  expect(await view.component().requestLeave()).toBe(false)
  await view.component().refresh(); expect(api.getStageBusinessContext).toHaveBeenCalledTimes(1)
  leave.mockResolvedValue(true); api.getStageBusinessContext.mockRejectedValueOnce(new Error('unavailable'))
  await view.component().refresh(); await flush()
  expect(textOf(view.root)).toContain('阶段审批:101; disabled:true')
})

it('passes the exact stage execution and retains the Owner page when the same project metadata refreshes', async () => {
  const execution = { projectId: '9', stageId: '90', executionId: '101', executionVersion: 2, roundNo: 2 }
  api.getStageBusinessContext.mockResolvedValue({ ...native, execution, bindingType: 'BUSINESS_OBJECT', businessView: { id: 88 } })
  const view = render(); await flush()
  expect(ownerContext.mock.lastCall![0].stageExecution).toEqual(execution)
  view.project.value = { id: 9, version: 3 }
  await flush()
  expect(api.getStageBusinessContext).toHaveBeenCalledTimes(1)
  expect(ownerUnmounted).not.toHaveBeenCalled()
  expect(ownerContext.mock.lastCall![0].project.version).toBe(3)
})
