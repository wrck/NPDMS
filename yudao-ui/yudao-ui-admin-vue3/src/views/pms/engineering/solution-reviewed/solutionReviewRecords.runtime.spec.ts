import { afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import ReviewDialog from './SolutionTieredReviewDialog.vue'

const api = vi.hoisted(() => ({ read: vi.fn(), definition: vi.fn(), start: vi.fn(), open: vi.fn() }))
vi.mock('@/api/pms/engineering/solution-review', () => api)
vi.mock('@/views/pms/project/project-master-detail/components/projectApprovalDetail', () => ({
  projectApprovalDetailKey: Symbol(), useProjectApprovalDetail: () => api.open
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
const apps: { unmount: () => void }[] = []
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.resetAllMocks() })
const render = () => {
  const dialog = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(ReviewDialog, { ref: dialog }) }))
  apps.push(view.app)
  return dialog
}
const solution = { id: 12, projectId: 123, name: '方案', version: 4 }

it('opens live process records for a running review whose terminal reviewsJson is empty', async () => {
  api.read.mockResolvedValue({ status: 'RUNNING', processInstanceId: 'running-instance', reviewsJson: null })
  const dialog = render()
  await dialog.value.open(solution)
  expect(api.open).toHaveBeenCalledWith('running-instance')
  expect(api.definition).not.toHaveBeenCalled()
})

it('shows the newly started process immediately after successful submission', async () => {
  api.read.mockResolvedValue(null)
  api.definition.mockResolvedValue({ id: 'definition-1', nodes: [] })
  api.start.mockResolvedValue({ status: 'RUNNING', processInstanceId: 'new-instance' })
  const dialog = render()
  await dialog.value.open(solution); await nextTick()
  await dialog.value.$.setupState.submit()
  expect(api.start).toHaveBeenCalledWith({ projectId: 123, solutionId: 12, expectedVersion: 4, processDefinitionId: 'definition-1' })
  expect(api.open).toHaveBeenCalledWith('new-instance')
})

it('preserves terminal review actions and does not open a process after a failed submission', async () => {
  api.read.mockResolvedValue({ status: 'APPROVE', processInstanceId: 'approved-instance', reviewsJson: '[]' })
  const dialog = render()
  await dialog.value.open(solution)
  expect(api.open).not.toHaveBeenCalled()
  api.read.mockResolvedValue(null)
  api.definition.mockResolvedValue({ id: 'definition-1', nodes: [] })
  api.start.mockRejectedValue(new Error('审批人未配置'))
  await dialog.value.open(solution)
  await dialog.value.$.setupState.submit()
  expect(api.open).not.toHaveBeenCalled()
})
