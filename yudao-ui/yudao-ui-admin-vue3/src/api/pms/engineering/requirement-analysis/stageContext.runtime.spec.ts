import { beforeEach, expect, it, vi } from 'vitest'
import { createInitialDraft, createNextDraft, getCurrent } from './index'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'

const transport = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
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
  await createInitialDraft(11, 'stage-initial', execution)
  expect(transport.post).toHaveBeenLastCalledWith({
    url: '/api/v1/pms/preparations', data: { projectId: 11, type: 'PRE_04', stageExecution: execution },
    headers: { 'Idempotency-Key': 'stage-initial' }
  })
  await createNextDraft(91, 7, 3, 'stage-rework', execution)
  expect(transport.post).toHaveBeenLastCalledWith({
    url: '/api/v1/pms/preparations/91/actions/create-draft', data: { stageExecution: execution },
    headers: { 'If-Match': '7', 'X-SOL-If-Match': '3', 'Idempotency-Key': 'stage-rework' }
  })
})

it('does not invent a stage context for the original task or standalone entry', async () => {
  await getCurrent(11)
  expect(transport.get).toHaveBeenCalledWith({ url: '/api/v1/pms/preparations', params: { projectId: 11, type: 'PRE_04' } })
  await createInitialDraft(11, 'original')
  expect(transport.post).toHaveBeenCalledWith({ url: '/api/v1/pms/preparations', data: { projectId: 11, type: 'PRE_04' }, headers: { 'Idempotency-Key': 'original' } })
})
