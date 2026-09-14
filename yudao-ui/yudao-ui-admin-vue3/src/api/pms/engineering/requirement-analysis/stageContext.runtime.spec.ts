import { beforeEach, expect, it, vi } from 'vitest'
import { createInitialDraft, createNextDraft, getCurrent, patchForm, completeDraft } from './index'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'

const transport = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: transport }))
const execution: StageExecutionContext = {
  projectId: 11, projectVersion: 1, stageId: '2099999999999999997', stageVersion: 1,
  executionContractId: '2099999999999999996', contractVersion: 1,
  planVersionId: '2099999999999999995', executionId: '2099999999999999994',
  executionVersion: 2, roundNo: 2, writable: true
}
beforeEach(() => vi.clearAllMocks())

it('keeps stage IDs intact and sends observed execution in the existing Owner create commands', async () => {
  await getCurrent(11, execution.stageId)
  expect(transport.get).toHaveBeenCalledWith({ url: '/api/v1/pms/preparations', params: { projectId: 11, type: 'PRE_04', stageId: execution.stageId } })
  await createInitialDraft(11, 'stage-initial', { stage: execution })
  expect(transport.post).toHaveBeenLastCalledWith({
    url: '/api/v1/pms/preparations', data: { projectId: 11, type: 'PRE_04', execution: { stage: execution } },
    headers: { 'Idempotency-Key': 'stage-initial' }
  })
  await createNextDraft(91, 7, 3, 'stage-rework', { stage: execution })
  expect(transport.post).toHaveBeenLastCalledWith({
    url: '/api/v1/pms/preparations/91/actions/create-draft', data: { execution: { stage: execution } },
    headers: { 'If-Match': '7', 'X-SOL-If-Match': '3', 'Idempotency-Key': 'stage-rework' }
  })
})

it('does not invent a node context for the standalone entry', async () => {
  await getCurrent(11)
  expect(transport.get).toHaveBeenCalledWith({ url: '/api/v1/pms/preparations', params: { projectId: 11, type: 'PRE_04' } })
  await createInitialDraft(11, 'original')
  expect(transport.post).toHaveBeenCalledWith({ url: '/api/v1/pms/preparations', data: { projectId: 11, type: 'PRE_04' }, headers: { 'Idempotency-Key': 'original' } })
})

it('sends the selected task ID and execution without rounding or falling back to stage identity', async () => {
  const task = {
    projectId: 11, projectVersion: 1, taskId: '2099999999999999997', taskVersion: 1,
    executionContractId: execution.executionContractId, contractVersion: 1,
    planVersionId: execution.planVersionId, executionId: execution.executionId,
    executionVersion: 2, roundNo: 2, stageExecutionId: '2099999999999999993', stageExecutionVersion: 1, writable: true
  }
  await getCurrent(11, undefined, task.taskId)
  expect(transport.get).toHaveBeenCalledWith({ url: '/api/v1/pms/preparations', params: { projectId: 11, type: 'PRE_04', taskId: task.taskId } })
  await createInitialDraft(11, 'task-initial', { task })
  expect(transport.post).toHaveBeenLastCalledWith({
    url: '/api/v1/pms/preparations', data: { projectId: 11, type: 'PRE_04', execution: { task } },
    headers: { 'Idempotency-Key': 'task-initial' }
  })
  await createNextDraft(91, 7, 3, 'task-rework', { task })
  expect(transport.post).toHaveBeenLastCalledWith({
    url: '/api/v1/pms/preparations/91/actions/create-draft', data: { execution: { task } },
    headers: { 'If-Match': '7', 'X-SOL-If-Match': '3', 'Idempotency-Key': 'task-rework' }
  })
  await patchForm(91, 7, 3, { values: { enabled: false }, execution: { task } })
  expect(transport.put).toHaveBeenCalledWith({
    method: 'PATCH', url: '/api/v1/pms/preparations/91/form',
    data: { values: { enabled: false }, execution: { task } },
    headers: { 'If-Match': '7', 'X-SOL-If-Match': '3' }
  })
  await completeDraft(91, 7, 3, 'task-complete', { task })
  expect(transport.post).toHaveBeenLastCalledWith({
    url: '/api/v1/pms/preparations/91/actions/submit', params: { type: 'PRE_04' },
    data: { execution: { task } },
    headers: { 'If-Match': '7', 'X-SOL-If-Match': '3', 'Idempotency-Key': 'task-complete' }
  })
})
