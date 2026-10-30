import { beforeEach, expect, it, vi } from 'vitest'
import { getProcessInstance } from '@/api/bpm/processInstance'
import { getCustomStartRoute } from './customStartRoute'

vi.mock('@/api/bpm/processInstance', () => ({ getProcessInstance: vi.fn() }))

const context = {
  processInstanceId: 'duration-process-1',
  processDefinitionId: 'pms-sol-duration-change:1:definition',
  businessKey: '2114242591332409346',
  formCustomCreatePath: '/pms/engineering/duration-change/ApprovalDetail'
}
const projectId = '2114242591332409301'
const process = {
  id: context.processInstanceId,
  processDefinitionId: context.processDefinitionId,
  businessKey: context.businessKey,
  formVariables: { projectId, durationChangeId: context.businessKey }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getProcessInstance).mockResolvedValue(process)
})

it('opens the original project schedule for another duration change without losing Long IDs', async () => {
  await expect(getCustomStartRoute(context)).resolves.toEqual({
    path: '/pms/project-management/project-master-detail',
    query: { projectId, tab: 'schedule' }
  })
  expect(getProcessInstance).toHaveBeenCalledWith(context.processInstanceId)
})

it('preserves the configured custom form route for other business workflows', async () => {
  await expect(getCustomStartRoute({ ...context, processDefinitionId: 'other:1:definition' }))
    .resolves.toEqual({ path: context.formCustomCreatePath, query: { id: context.businessKey } })
  expect(getProcessInstance).not.toHaveBeenCalled()
})

it.each([
  { id: 'different-process' },
  { processDefinitionId: 'other:1:definition' },
  { businessKey: '11' },
  { formVariables: { projectId, durationChangeId: '12' } },
  { formVariables: { projectId: 2114242591332409301, durationChangeId: context.businessKey } },
  { formVariables: { projectId: '', durationChangeId: context.businessKey } }
])('refuses a missing or mismatched project reference %j', async (patch) => {
  vi.mocked(getProcessInstance).mockResolvedValue({ ...process, ...patch })
  await expect(getCustomStartRoute(context)).rejects.toThrow('无法确认原工期变更所属项目')
})

it('does not navigate when the original workflow is inaccessible', async () => {
  vi.mocked(getProcessInstance).mockRejectedValue(new Error('Forbidden'))
  await expect(getCustomStartRoute(context)).rejects.toThrow('Forbidden')
})
