import { afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import TaskApprovalPanel from './TaskApprovalPanel.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { TaskWorkbench } from '@/api/pms/project/task-workbench'

const api = vi.hoisted(() => ({ execute: vi.fn() }))
vi.mock('@/api/pms/project/task-workbench', () => ({ executeTaskAction: api.execute }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn(), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('./TaskApprovalForm.vue', () => ({ default: defineComponent({
  setup: () => () => h('div', '审批提交表单')
}) }))
const apps: { unmount: () => void }[] = []
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.clearAllMocks() })
const render = (outcome: 'MATCHED' | 'NOT_MATCHED' | 'UNKNOWN') => {
  const workbench = reactive<TaskWorkbench>({ task: { taskId: 11, treeDepth: 0, placeholder: false, status: 'PENDING_ASSIGN', version: 1 },
    bindingType: 'APPROVAL', allowedActions: ['START'], executionContractId: 91, contractVersion: 1,
    startAdmission: { outcome, conditions: [{ label: '业务联调', outcome }] },
    approval: { definitionKey: 'review', definitionId: 'review:1', executionId: 61,
      current: { outcome: 'NOT_SATISFIED', status: 'NOT_STARTED' } } })
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(TaskApprovalPanel, { ref: child, workbench }) }))
  apps.push(view.app)
  return { ...view, workbench, state: () => child.value.$.setupState }
}

it.each(['NOT_MATCHED', 'UNKNOWN'] as const)('blocks submission for %s even with a stale START action', async outcome => {
  const view = render(outcome)
  expect(textOf(view.root)).toContain('暂不能提交审批')
  if (outcome === 'NOT_MATCHED') expect(textOf(view.root)).toContain('业务联调（未满足）')
  expect(textOf(view.root)).not.toContain('审批提交表单')
  await expect(view.state().submit({ variables: {}, selectedApprovers: {} })).rejects.toThrow()
  expect(api.execute).not.toHaveBeenCalled()
})

it('offers submission after admission refresh and preserves retry for an already started round', async () => {
  const view = render('NOT_MATCHED')
  view.workbench.startAdmission!.outcome = 'MATCHED'; await nextTick()
  expect(textOf(view.root)).toContain('审批提交表单')
  expect(textOf(view.root)).not.toContain('暂不能提交审批')
  view.workbench.task.status = 'IN_PROGRESS'
  view.workbench.approval!.current.status = 'REJECTED'
  view.workbench.allowedActions = ['APPROVAL']
  view.workbench.startAdmission = undefined
  await nextTick()
  expect(textOf(view.root)).toContain('审批提交表单')
  await view.state().submit({ variables: {}, selectedApprovers: {} })
  expect(api.execute).toHaveBeenCalledWith(11, 'APPROVAL', expect.anything(), 1, expect.any(String))
})
