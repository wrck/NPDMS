import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import Panel from './StageApprovalPanel.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { StageBusinessContext } from '@/api/pms/project/stage-business'
const api = vi.hoisted(() => ({ start: vi.fn(), push: vi.fn(), leave: vi.fn(), guard: vi.fn(), permission: true, formBusy: false }))
vi.mock('@/api/pms/project/stage-business', () => ({ startStageApproval: api.start }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => api.permission }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }), onBeforeRouteLeave: api.guard }))
vi.mock('./TaskApprovalForm.vue', () => ({ default: defineComponent({
  props: ['definitionId', 'definitionKey', 'disabled', 'submitApproval'], emits: ['submitted'],
  setup(props, { expose }) {
    expose({ requestLeave: api.leave, isBusy: () => api.formBusy })
    return () => h('div', `原审批表单 ${props.definitionId} disabled:${props.disabled}`)
  }
}) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const form = { variables: { note: '本轮证据', optional: null }, selectedApprovers: { review: [12] } }
const result = { outcome: 'NOT_SATISFIED', status: 'RUNNING', processInstanceId: 'pi', definitionId: 'review:1' }
const render = async (status = 'NOT_STARTED') => {
  const workbench = reactive<StageBusinessContext>({ projectId: '9007199254740993', stageId: '11', stageCode: 'PREP',
    executionContractId: '41', contractVersion: 1, bindingType: 'APPROVAL', ownerActions: ['QUERY', 'APPROVAL'], readonly: false,
    execution: { projectId: '9007199254740993', projectVersion: 1, stageId: '11', stageVersion: 1,
      executionContractId: '41', contractVersion: 1, planVersionId: '21', executionId: '31', executionVersion: 2, roundNo: 3, writable: true },
    approval: { definitionKey: 'review', definitionId: 'review:1', executionId: '31',
      current: { status, outcome: status === 'UNKNOWN' ? 'UNKNOWN' : status === 'APPROVED' ? 'SATISFIED' : 'NOT_SATISFIED',
        processInstanceId: status === 'NOT_STARTED' ? undefined : 'pi-old' } } })
  const child = ref<any>(), disabled = ref(false), changed = vi.fn()
  const view = mount(defineComponent({ setup: () => () => h(Panel, { ref: child, workbench, disabled: disabled.value, onChanged: changed }) }))
  apps.push(view.app); await flush()
  return { ...view, workbench, disabled, changed, state: () => child.value.$.setupState, exposed: () => child.value }
}
beforeEach(() => { vi.clearAllMocks(); api.permission = true; api.formBusy = false; api.leave.mockResolvedValue(true); api.start.mockResolvedValue(result) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('reuses the BPM form and submits all execution versions without coercing string IDs', async () => {
  const view = await render(); view.state().editing = true; await flush()
  expect(textOf(view.root)).toContain('原审批表单 review:1')
  await view.state().submit(form)
  expect(api.start).toHaveBeenCalledWith('9007199254740993', 'PREP', view.workbench.execution, form, expect.any(String))
  expect(view.changed).not.toHaveBeenCalled()
  view.state().handleSubmitted(); await flush()
  expect(view.changed).toHaveBeenCalledTimes(1); expect(textOf(view.root)).not.toContain('原审批表单')
})
it.each(['RUNNING', 'APPROVED', 'UNKNOWN'])('does not offer or start a new approval when %s', async status => {
  const view = await render(status)
  expect(textOf(view.root)).not.toContain('填写审批表单')
  view.state().editing = true; await expect(view.state().submit(form)).rejects.toThrow()
  expect(api.start).not.toHaveBeenCalled()
})
it('reuses the intent on timeout and rotates it when input or execution changes', async () => {
  const view = await render('REJECTED'); view.state().editing = true
  api.start.mockRejectedValueOnce(new Error('timeout'))
  await expect(view.state().submit(form)).rejects.toThrow(); await view.state().submit(form)
  expect(api.start.mock.calls[0][4]).toBe(api.start.mock.calls[1][4])
  await view.state().submit({ ...form, variables: { note: '新意见' } })
  expect(api.start.mock.calls[2][4]).not.toBe(api.start.mock.calls[1][4])
  view.workbench.execution!.executionVersion++
  await view.state().submit(form)
  expect(api.start.mock.calls[3][4]).not.toBe(api.start.mock.calls[0][4])
})
it('blocks stale readonly or revoked operations while retaining the form', async () => {
  const view = await render(); view.state().editing = true; await flush()
  api.permission = false
  await expect(view.state().submit(form)).rejects.toThrow()
  view.workbench.readonly = true; await flush()
  expect(textOf(view.root)).toContain('原审批表单 review:1 disabled:true')
  expect(api.start).not.toHaveBeenCalled()
})
it('blocks overlapping submission and delegates leave confirmation to the shared form', async () => {
  const view = await render(); view.state().editing = true; await flush()
  api.leave.mockResolvedValue(false); expect(await view.exposed().requestLeave()).toBe(false)
  let finish!: (v: unknown) => void
  api.start.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const pending = view.state().submit(form); await flush()
  expect(await view.exposed().requestLeave()).toBe(false)
  await expect(view.state().submit(form)).rejects.toThrow()
  finish(result); await pending
  expect(api.start).toHaveBeenCalledTimes(1)
})
it('uses the original detail route and rejects an unconfirmed response', async () => {
  const view = await render('CANCELLED'); await view.state().openDetail()
  expect(api.push).toHaveBeenCalledWith({ name: 'BpmProcessInstanceDetail', query: { id: 'pi-old' } })
  view.state().editing = true; api.start.mockResolvedValue({ ...result, definitionId: 'latest:2' })
  await expect(view.state().submit(form)).rejects.toThrow('not confirmed')
  expect(api.guard).toHaveBeenCalledWith(view.exposed().requestLeave)
})
