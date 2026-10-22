import { beforeEach, expect, it, vi } from 'vitest'
import { getProcessInstance } from '@/api/bpm/processInstance'
import { getNormalClosureApplication } from '@/api/pms/acceptance/normal-closure'
import { getTodoHandlingRoute } from './handlingRoute'

vi.mock('@/api/bpm/processInstance', () => ({ getProcessInstance: vi.fn() }))
vi.mock('@/api/pms/acceptance/normal-closure', () => ({ getNormalClosureApplication: vi.fn() }))

const applicationId = '2099999999999999996'
const projectId = '2099999999999999999'
const task = {
  id: 'task-1',
  processInstance: {
    id: 'process-1',
    processDefinitionId: 'PMS_MINIMAL_NORMAL_CLOSURE:1:definition'
  }
}
const application = {
  id: applicationId,
  projectId,
  businessKey: `PROJECT_NORMAL_CLOSURE:${applicationId}`,
  processInstanceId: task.processInstance.id,
  processDefinitionId: task.processInstance.processDefinitionId
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getProcessInstance).mockResolvedValue({
    id: 'process-1',
    businessKey: application.businessKey
  })
  vi.mocked(getNormalClosureApplication).mockResolvedValue({ projectId, application } as any)
})

it('opens the matching project closure tab without losing Long ID precision', async () => {
  await expect(getTodoHandlingRoute(task)).resolves.toEqual({
    path: '/pms/project-management/project-master-detail',
    query: { projectId, tab: 'closure' }
  })
  expect(getNormalClosureApplication).toHaveBeenCalledWith(applicationId)
})

it('keeps other approvals on their original route without extra API requests', async () => {
  const other = {
    ...task,
    processInstance: { ...task.processInstance, processDefinitionId: 'OTHER:1:id' }
  }
  await expect(getTodoHandlingRoute(other)).resolves.toEqual({
    name: 'BpmProcessInstanceDetail',
    query: { id: 'process-1', taskId: 'task-1' }
  })
  expect(getProcessInstance).not.toHaveBeenCalled()
  expect(getNormalClosureApplication).not.toHaveBeenCalled()
})

it.each(['PROJECT_NORMAL_CLOSURE:9223372036854775808', 'OTHER:123'])(
  'rejects invalid business key %s',
  async (businessKey) => {
    vi.mocked(getProcessInstance).mockResolvedValue({ id: 'process-1', businessKey })
    await expect(getTodoHandlingRoute(task)).rejects.toThrow('CLOSURE_PROCESS_IDENTITY_MISMATCH')
    expect(getNormalClosureApplication).not.toHaveBeenCalled()
  }
)

it.each([
  { id: '11' },
  { projectId: '12' },
  { businessKey: 'PROJECT_NORMAL_CLOSURE:13' },
  { processInstanceId: 'another-process' },
  { processDefinitionId: 'OTHER:1:id' }
])('rejects mismatched application identity %j', async (patch) => {
  vi.mocked(getNormalClosureApplication).mockResolvedValue({
    projectId,
    application: { ...application, ...patch }
  } as any)
  await expect(getTodoHandlingRoute(task)).rejects.toThrow('CLOSURE_APPLICATION_IDENTITY_MISMATCH')
})

it('does not navigate when the application is inaccessible', async () => {
  vi.mocked(getNormalClosureApplication).mockRejectedValue(new Error('Forbidden'))
  await expect(getTodoHandlingRoute(task)).rejects.toThrow('Forbidden')
})
