import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import Form from './TaskApprovalForm.vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ getProcessDefinition: vi.fn(), getApprovalDetail: vi.fn(), validate: vi.fn(), disabled: vi.fn(), hidden: vi.fn(), getRule: vi.fn() }))
const message = vi.hoisted(() => ({ confirm: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/bpm/definition', () => ({ getProcessDefinition: api.getProcessDefinition }))
vi.mock('@/api/bpm/processInstance', () => ({ getApprovalDetail: api.getApprovalDetail }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/utils/constants', () => ({ BpmModelFormType: { NORMAL: 10 }, BpmModelType: { BPMN: 10, SIMPLE: 20 } }))
vi.mock('@/components/SimpleProcessDesignerV2/src/consts', () => ({
  CandidateStrategy: { START_USER_SELECT: 35 }, NodeId: { START_USER_NODE_ID: 'start' }, FieldPermissionType: { READ: '1', WRITE: '2', NONE: '3' }
}))
vi.mock('@form-create/element-ui', () => ({ default: defineComponent({
  emits: ['update:api'], setup(_, { emit }) { emit('update:api', api); return () => h('div', 'form-create') }
}) }))
vi.mock('@/utils/formCreate', () => ({ setConfAndFields2: (form: any, _conf: string, fields: string[]) => {
  form.value.rule = fields.map(field => JSON.parse(field)); form.value.option = {}; form.value.value = {}
} }))
vi.mock('@/views/bpm/processInstance/detail/ProcessInstanceTimeline.vue', () => ({ default: defineComponent({ setup: () => () => h('div', '审批时间线') }) }))
vi.mock('@/views/bpm/processInstance/detail/ProcessInstanceBpmnViewer.vue', () => ({ default: defineComponent({ setup: () => () => h('div', 'BPMN流程图') }) }))
vi.mock('@/views/bpm/processInstance/detail/ProcessInstanceSimpleViewer.vue', () => ({ default: defineComponent({ setup: () => () => h('div', 'Simple流程图') }) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 10; i++) { await Promise.resolve(); await nextTick() } }
const render = async () => {
  const child = ref<any>(), submit = vi.fn().mockResolvedValue(undefined)
  const submitted = vi.fn(() => child.value.isBusy())
  const view = mount(defineComponent({ setup: () => () => h(Form, { ref: child, definitionId: 'review:1', definitionKey: 'review', submitApproval: submit, onSubmitted: submitted }) }), {},
    { ElCollapse: passthrough, ElCollapseItem: passthrough })
  apps.push(view.app); await flush()
  return { ...view, submit, submitted, state: () => child.value.$.setupState, exposed: () => child.value }
}
const prediction = (id = 1) => ({ activityNodes: [{ id, name: '复核', candidateStrategy: 35 }], formFieldsPermission: {} })
beforeEach(() => {
  vi.clearAllMocks()
  api.getProcessDefinition.mockResolvedValue({ id: 'review:1', key: 'review', suspensionState: 1, formType: 10,
    modelType: 10, bpmnXml: '<definitions/>', formConf: '{}', formFields: [] })
  api.getApprovalDetail.mockResolvedValue(prediction())
  api.validate.mockResolvedValue(true); message.confirm.mockResolvedValue(undefined)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('loads the exact definition and supports a form with no fields but required approver selection', async () => {
  const view = await render()
  expect(api.getProcessDefinition).toHaveBeenCalledWith('review:1')
  expect(textOf(view.root)).toContain('form-create')
  expect(textOf(view.root)).toContain('BPMN流程图')
  await view.state().submit(); expect(view.submit).not.toHaveBeenCalled()
  expect(message.warning).toHaveBeenCalledWith('请选择复核的候选人')
  view.state().selectUsers('1', [{ id: 8 }]); await view.state().submit()
  expect(view.submit).toHaveBeenCalledWith({ variables: {}, selectedApprovers: { '1': [8] } })
  expect(view.submitted).toHaveBeenCalledTimes(1)
  expect(view.submitted).toHaveReturnedWith(false) // Parent refresh/leave runs only after submission and dirty state settle.
})
it('rejects a missing or different definition without substituting a latest version', async () => {
  api.getProcessDefinition.mockResolvedValueOnce({ id: 'review:2', key: 'review' })
  const view = await render(); await view.state().submit()
  expect(textOf(view.root)).toContain('冻结审批定义或表单读取失败')
  expect(api.getApprovalDetail).not.toHaveBeenCalled(); expect(view.submit).not.toHaveBeenCalled()
})
it('does not redirect a custom business form outside task execution context', async () => {
  api.getProcessDefinition.mockResolvedValueOnce({ id: 'review:1', key: 'review', suspensionState: 1, formType: 20 })
  const view = await render(); await view.state().submit()
  expect(textOf(view.root)).toContain('请通过业务页面绑定办理')
  expect(view.submit).not.toHaveBeenCalled()
})
it('discards late predictions and re-predicts when the last field is cleared', async () => {
  const view = await render()
  let resolveOld!: (value: unknown) => void
  api.getApprovalDetail.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
  view.state().form.value.reason = 'a'
  api.getApprovalDetail.mockResolvedValueOnce(prediction(2))
  delete view.state().form.value.reason
  await flush(); resolveOld(prediction(3)); await flush()
  expect(view.state().nodes[0].id).toBe(2)
  expect(api.getApprovalDetail.mock.calls.at(-1)?.[0].processVariablesStr).toBe('{}')
})
it('blocks prediction and validation failures, preserves dirty input, and retries without clearing the form', async () => {
  const view = await render()
  view.state().form.value.reason = '本轮材料'; await flush()
  view.state().selectUsers('1', [{ id: 8 }])
  api.getApprovalDetail.mockRejectedValueOnce(new Error('unavailable'))
  await view.state().submit(); expect(view.submit).not.toHaveBeenCalled()
  api.validate.mockRejectedValueOnce(new Error('invalid'))
  await view.state().submit(); expect(view.submit).not.toHaveBeenCalled()
  message.confirm.mockRejectedValueOnce(new Error('stay'))
  expect(await view.exposed().requestLeave()).toBe(false)
  await view.state().submit()
  expect(view.submit).toHaveBeenCalledWith({ variables: { reason: '本轮材料' }, selectedApprovers: { '1': [8] } })
  expect(await view.exposed().requestLeave()).toBe(true)
})
it('restores original required validation when a field becomes writable again', async () => {
  const rule = { $required: true, validate: [{ required: true }] }
  api.getRule.mockReturnValue(rule)
  api.getApprovalDetail.mockResolvedValueOnce({ ...prediction(), formFieldsPermission: { reason: '1' } })
  const view = await render()
  expect(rule.validate).toEqual([]); expect(rule.$required).toBe(false)
  api.getApprovalDetail.mockResolvedValueOnce({ ...prediction(), formFieldsPermission: { reason: '2' } })
  await view.state().predict()
  expect(rule.validate).toEqual([{ required: true }]); expect(rule.$required).toBe(true)
  expect(api.disabled).toHaveBeenLastCalledWith(false, 'reason')
})
it('prevents leaving during submission and rejects values changed while asynchronous validation runs', async () => {
  const view = await render()
  view.state().selectUsers('1', [{ id: 8 }])
  let finish!: (value: boolean) => void
  api.validate.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const pending = view.state().submit(); await flush()
  expect(await view.exposed().requestLeave()).toBe(false)
  view.state().form.value.reason = 'validation changed'
  finish(true); await pending
  expect(view.submit).not.toHaveBeenCalled()
  expect(textOf(view.root)).toContain('表单在校验期间发生变化')
})
