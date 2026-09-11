import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import Actions from './TaskStateActions.vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ executeTaskAction: vi.fn() }))
const messages = vi.hoisted(() => ({ warning: vi.fn(), success: vi.fn(), prompt: vi.fn() }))
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => messages }))
const apps: { unmount: () => void }[] = []
const render = async (fact: string | undefined = 'owner-vector', beforeAction = async () => true) => {
  const component = ref<any>()
  const workbench = { task: { taskId: '2098262374805766146', version: 3 }, executionContractId: 91, contractVersion: 2,
    allowedActions: ['START', 'COMPLETE'] }
  const changed = vi.fn()
  const { app } = mount(defineComponent({ setup: () => () => h(Actions, { ref: component, workbench,
    businessBound: true, businessFactVersion: fact, beforeAction, onChanged: changed } as any) }))
  apps.push(app); await nextTick()
  return { state: () => component.value.$.setupState, changed }
}
beforeEach(() => { vi.clearAllMocks(); api.executeTaskAction.mockResolvedValue({ taskId: '2098262374805766146', status: 'DONE', taskVersion: 4 }) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('uses the existing command API with exact task, contract and Owner versions', async () => {
  const view = await render(); await view.state().execute('COMPLETE')
  expect(api.executeTaskAction).toHaveBeenCalledWith('2098262374805766146', 'COMPLETE', expect.objectContaining({
    executionContractId: 91, contractVersion: 2, factObjectKey: '2098262374805766146', expectedBusinessFactVersion: 'owner-vector', factVersion: undefined
  }), 3, expect.any(String))
  expect(view.changed).toHaveBeenCalledTimes(1)
})
it('blocks missing business facts and the Owner leave refusal', async () => {
  const missing = await render(''); await missing.state().execute('COMPLETE')
  const dirty = await render('owner-vector', async () => false); await dirty.state().execute('START')
  expect(api.executeTaskAction).not.toHaveBeenCalled()
})
it('retries an uncertain response with the same intent key', async () => {
  api.executeTaskAction.mockRejectedValueOnce(new Error('response lost'))
  const view = await render(); await view.state().execute('COMPLETE'); await view.state().execute('COMPLETE')
  expect(api.executeTaskAction.mock.calls[1]).toEqual(api.executeTaskAction.mock.calls[0])
  expect(view.changed).toHaveBeenCalledTimes(1)
})
it('does not announce completion when the server leaves the task pending', async () => {
  api.executeTaskAction.mockResolvedValueOnce({ status: 'PENDING_ACCEPT' })
  const view = await render(); await view.state().execute('COMPLETE')
  expect(messages.success).not.toHaveBeenCalled()
  expect(messages.warning).toHaveBeenCalledWith(expect.stringContaining('未满足'))
})
