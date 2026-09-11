import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import StageBusinessPanel from './StageBusinessPanel.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ getStageBusinessContext: vi.fn() }))
const leave = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/project/stage-business', () => api)
vi.mock('@/components/BusinessView/BusinessViewHost.vue', () => ({ default: defineComponent({
  props: ['resolvedContext', 'readonly', 'allowedActions'],
  setup(props, { expose }) { expose({ requestLeave: leave }); return () => h('div', `Owner:${props.resolvedContext.project.id}; readonly:${props.readonly}; actions:${props.allowedActions.join(',')}`) }
}) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 6; i++) { await Promise.resolve(); await nextTick() } }
const native = { projectId: '9', stageId: '90', stageCode: 'S4', bindingType: 'STAGE_NATIVE', ownerActions: [], readonly: true }
const render = () => {
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(StageBusinessPanel, { ref: child, project: { id: 9, version: 2 }, stageCode: 'S4' }) }))
  apps.push(view.app)
  return { ...view, component: () => child.value, state: () => child.value.$.setupState }
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
