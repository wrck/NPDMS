import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import ReviewDialog from './SolutionTieredReviewDialog.vue'

const api = vi.hoisted(() => ({ read: vi.fn(), definition: vi.fn(), submissionReadiness: vi.fn(), start: vi.fn(), open: vi.fn(), revise: vi.fn(), revised: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/engineering/solution-review', () => api)
vi.mock('@/views/pms/project/project-master-detail/components/projectApprovalDetail', () => ({
  projectApprovalDetailKey: Symbol(), useProjectApprovalDetail: () => api.open
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: vi.fn() }) }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
const apps: { unmount: () => void }[] = []
beforeEach(() => { api.submissionReadiness.mockResolvedValue({ allowed: true, blockers: [] }) })
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.resetAllMocks() })
const render = () => {
  const dialog = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(ReviewDialog, { ref: dialog, onRevised: api.revised }) }))
  apps.push(view.app)
  return dialog
}
const solution = { id: 12, projectId: 123, name: '方案', version: 4 }

it('never submits when prerequisites are unmet or readiness cannot be read', async () => {
  api.read.mockResolvedValue(null)
  api.definition.mockResolvedValue({ id: 'definition-1', nodes: [] })
  api.submissionReadiness.mockResolvedValue({ allowed: false, blockers: ['现场勘察'] })
  const dialog = render()
  await dialog.value.open(solution)
  await dialog.value.$.setupState.submit()
  expect(api.start).not.toHaveBeenCalled()
  expect(dialog.value.$.setupState.readiness.blockers).toEqual(['现场勘察'])
  api.submissionReadiness.mockRejectedValue(new Error('计划暂不可用'))
  await dialog.value.open(solution)
  await dialog.value.$.setupState.submit()
  expect(api.start).not.toHaveBeenCalled()
  expect(dialog.value.$.setupState.error).toContain('计划暂不可用')
})

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

it('returns the new draft ID after copying a rejected solution without replacing its approval record', async () => {
  const record = { status: 'REJECT', processInstanceId: 'rejected-instance', reviewsJson: '[]' }
  api.read.mockResolvedValue(record)
  api.revise.mockResolvedValue('2110018733130579973')
  const dialog = render()
  await dialog.value.open(solution)
  await dialog.value.$.setupState.revise()
  expect(api.revise).toHaveBeenCalledWith(123, 12)
  expect(api.revised).toHaveBeenCalledWith({ projectId: 123, sourceSolutionId: 12, solutionId: '2110018733130579973' })
  expect(record).toEqual({ status: 'REJECT', processInstanceId: 'rejected-instance', reviewsJson: '[]' })
  expect(api.start).not.toHaveBeenCalled()
})

it('does not navigate to a new draft when copying the rejected solution fails', async () => {
  api.read.mockResolvedValue({ status: 'REJECT', processInstanceId: 'rejected-instance', reviewsJson: '[]' })
  api.revise.mockRejectedValue(new Error('项目范围已变化'))
  const dialog = render()
  await dialog.value.open(solution)
  await dialog.value.$.setupState.revise()
  expect(api.revised).not.toHaveBeenCalled()
  expect(dialog.value.$.setupState.record.status).toBe('REJECT')
  expect(dialog.value.$.setupState.visible).toBe(true)
})

it('keeps the original source context when another solution is opened while copying', async () => {
  api.read.mockResolvedValue({ status: 'REJECT', processInstanceId: 'rejected-instance', reviewsJson: '[]' })
  let resolveRevision!: (id: string) => void
  api.revise.mockReturnValue(new Promise<string>(resolve => { resolveRevision = resolve }))
  const dialog = render()
  await dialog.value.open(solution)
  const copying = dialog.value.$.setupState.revise()
  await dialog.value.open({ ...solution, id: 13 })
  resolveRevision('2110018733130579973')
  await copying
  expect(api.revised).toHaveBeenCalledWith({ projectId: 123, sourceSolutionId: 12, solutionId: '2110018733130579973' })
  expect(dialog.value.$.setupState.solution.id).toBe(13)
  expect(dialog.value.$.setupState.visible).toBe(true)
})
