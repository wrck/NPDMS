import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import Panel from './TaskApprovalPanel.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ executeTaskAction: vi.fn() }))
const navigation = vi.hoisted(() => ({ push: vi.fn(), leave: vi.fn(), guard: vi.fn() }))
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('vue-router', () => ({ useRouter: () => navigation, onBeforeRouteLeave: navigation.guard }))
vi.mock('./TaskApprovalForm.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: navigation.leave }); return () => h('div', '原审批表单') }
}) }))
const apps: { unmount: () => void }[] = []
const render = async (status = 'NOT_STARTED', allowedActions = ['START']) => {
  const child = ref<any>(), changed = vi.fn()
  const workbench = reactive({ task: { taskId: '2098262374805766146', version: 3, status: status === 'NOT_STARTED' ? 'PENDING_START' : 'IN_PROGRESS' },
    bindingType: 'APPROVAL', executionContractId: 91, contractVersion: 2, allowedActions,
    approval: { definitionKey: 'review', definitionId: 'review:1', executionId: '61',
      current: { status, outcome: 'NOT_SATISFIED', processInstanceId: status === 'NOT_STARTED' ? undefined : 'attempt-1' } } })
  const view = mount(defineComponent({ setup: () => () => h(Panel, { ref: child, workbench, onChanged: changed } as any) }))
  apps.push(view.app); await nextTick()
  return { ...view, workbench, changed, state: () => child.value.$.setupState }
}
beforeEach(() => { vi.clearAllMocks(); navigation.leave.mockResolvedValue(true); api.executeTaskAction.mockResolvedValue({}) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
const form = { variables: { reason: '本轮依据' }, selectedApprovers: { review: [8] } }
it('submits the frozen task identity and form through the project command, with stable retry intent', async () => {
  const view = await render()
  api.executeTaskAction.mockRejectedValueOnce(new Error('response lost'))
  await expect(view.state().submit(form)).rejects.toThrow()
  await view.state().submit(form)
  expect(api.executeTaskAction.mock.calls[0]).toEqual(api.executeTaskAction.mock.calls[1])
  expect(api.executeTaskAction).toHaveBeenCalledWith('2098262374805766146', 'START', {
    executionContractId: 91, contractVersion: 2, approval: form
  }, 3, expect.any(String))
  expect(view.changed).toHaveBeenCalledTimes(1)
})
it('uses a new approval operation after rejection and rotates intent when the submission changes', async () => {
  const view = await render('REJECTED', ['APPROVAL'])
  await view.state().submit(form)
  await view.state().submit({ ...form, variables: { reason: '新证据' } })
  expect(api.executeTaskAction.mock.calls[0][1]).toBe('APPROVAL')
  expect(api.executeTaskAction.mock.calls[0][4]).not.toEqual(api.executeTaskAction.mock.calls[1][4])
})
it.each(['RUNNING', 'APPROVED', 'UNKNOWN'])('does not submit against %s or lack of action permission', async status => {
  const view = await render(status, ['APPROVAL'])
  await expect(view.state().submit(form)).rejects.toThrow()
  expect(textOf(view.root)).not.toContain('原审批表单')
  view.workbench.approval.current.status = 'REJECTED'; view.workbench.allowedActions = []
  await expect(view.state().submit(form)).rejects.toThrow()
  expect(api.executeTaskAction).not.toHaveBeenCalled()
})
it('opens the original BPM details only after the dirty form allows leaving', async () => {
  const view = await render('REJECTED', ['APPROVAL'])
  navigation.push.mockImplementation(async () => { await navigation.guard.mock.calls[0][0]() })
  navigation.leave.mockResolvedValueOnce(false)
  expect(await view.state().requestLeave()).toBe(false)
  await view.state().openDetail()
  expect(navigation.push).toHaveBeenCalledWith({ name: 'BpmProcessInstanceDetail', query: { id: 'attempt-1' } })
  expect(navigation.leave).toHaveBeenCalledTimes(2)
})
