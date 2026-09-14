import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import StageGateProcessPanel from './StageGateProcessPanel.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { StageGateReference, StageGateWorkbench } from '@/api/pms/project/stage-gates'

const api = vi.hoisted(() => ({ start: vi.fn(), push: vi.fn(), permission: true, leave: vi.fn(), formBusy: false }))
vi.mock('@/api/pms/project/projects', () => ({ startProjectStageGateProcess: api.start }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => api.permission }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }) }))
vi.mock('@/views/pms/project/inheritance/detail/TaskApprovalForm.vue', () => ({ default: defineComponent({
  props: ['definitionId', 'definitionKey', 'submitApproval', 'disabled'], emits: ['submitted'],
  setup(props, { expose }) {
    expose({ requestLeave: api.leave, isBusy: () => api.formBusy })
    return () => h('div', `原审批表单 ${props.definitionId} ${props.definitionKey}`)
  }
}) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await nextTick() } }
const submission = { variables: { note: '办理意见', optional: null }, selectedApprovers: { approve: [12] } }
const render = (status: NonNullable<StageGateReference['process']>['status'] = 'NOT_STARTED') => {
  const reference = reactive<StageGateReference>({ gateReferenceId: '9007199254740993', refType: 'APPROVAL', refCode: 'review',
    refVersion: 'review:2:222', canStart: ['NOT_STARTED', 'REJECTED', 'CANCELLED'].includes(status),
    process: { processInstanceId: status === 'NOT_STARTED' || status === 'UNKNOWN' ? null : 'pi-1', status,
      outcome: status === 'UNKNOWN' ? 'DEPENDENCY_UNAVAILABLE' : status === 'APPROVED' ? 'SATISFIED' : 'UNSATISFIED', reasonCode: null } })
  const workbench = reactive<StageGateWorkbench>({ projectId: '9', projectVersion: 4, planVersionId: '51', stageId: '11',
    stageCode: 'PREP', executionId: '61', executionRound: 2, recoverableError: null, gates: [] })
  const child = ref<any>(), editing = ref(false)
  const view = mount(defineComponent({ setup: () => () => h(StageGateProcessPanel, { ref: child, reference, workbench, editing: editing.value }) }))
  apps.push(view.app)
  return { ...view, reference, workbench, editing, state: () => child.value.$.setupState, exposed: () => child.value }
}
beforeEach(() => {
  vi.clearAllMocks(); api.permission = true; api.formBusy = false; api.leave.mockResolvedValue(true)
  api.start.mockResolvedValue({ outcome: 'STARTED', processInstanceId: 'pi-2', processDefinitionId: 'review:2:222' })
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('loads the shared form only after choosing to edit and submits the exact version and untouched string identity', async () => {
  const view = render(); await flush()
  expect(textOf(view.root)).toContain('填写审批表单'); expect(textOf(view.root)).not.toContain('原审批表单')
  view.editing.value = true; await flush()
  expect(textOf(view.root)).toContain('原审批表单 review:2:222 review')
  await view.state().submit(submission)
  expect(api.start).toHaveBeenCalledWith('9', '9007199254740993', 4, expect.any(String), 'review:2:222', submission)
})

it.each(['RUNNING', 'APPROVED', 'UNKNOWN'] as const)('does not offer a new process for %s', async status => {
  const view = render(status); view.editing.value = true; await flush()
  expect(textOf(view.root)).not.toContain('填写审批表单'); expect(textOf(view.root)).not.toContain('原审批表单')
  await expect(view.state().submit(submission)).rejects.toThrow()
  expect(api.start).not.toHaveBeenCalled()
})

it('reuses the same intent after a timeout, changes it for edited input, and stops after permission revocation', async () => {
  const view = render('REJECTED'); view.editing.value = true; await flush()
  api.start.mockRejectedValueOnce(new Error('timeout'))
  await expect(view.state().submit(submission)).rejects.toThrow()
  await view.state().submit(submission)
  expect(api.start.mock.calls[0][3]).toBe(api.start.mock.calls[1][3])
  await view.state().submit({ ...submission, variables: { note: '修改意见' } })
  expect(api.start.mock.calls[2][3]).not.toBe(api.start.mock.calls[1][3])
  api.permission = false
  await expect(view.state().submit(submission)).rejects.toThrow()
  expect(api.start).toHaveBeenCalledTimes(3)
})

it('keeps leave protection and refuses overlapping submissions until the current request settles', async () => {
  const view = render(); view.editing.value = true; await flush()
  api.leave.mockResolvedValue(false)
  expect(await view.exposed().requestLeave()).toBe(false)
  let finish!: (result: unknown) => void
  api.start.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const pending = view.state().submit(submission); await flush()
  expect(view.exposed().isBusy()).toBe(true); expect(await view.exposed().requestLeave()).toBe(false)
  await expect(view.state().submit(submission)).rejects.toThrow()
  expect(api.start).toHaveBeenCalledTimes(1)
  finish({ outcome: 'REPLAYED', processInstanceId: 'pi-2', processDefinitionId: 'review:2:222' }); await pending
})

it('uses the original BPM detail route and rejects an unconfirmed or wrong-version start response', async () => {
  const view = render('CANCELLED'); await flush()
  await view.state().openDetail()
  expect(api.push).toHaveBeenCalledWith({ name: 'BpmProcessInstanceDetail', query: { id: 'pi-1' } })
  view.editing.value = true; await flush()
  api.start.mockResolvedValue({ outcome: 'STARTED', processInstanceId: 'pi-2', processDefinitionId: 'latest:3' })
  await expect(view.state().submit(submission)).rejects.toThrow('not confirmed')
})
