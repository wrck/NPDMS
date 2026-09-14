import { expect, it, vi } from 'vitest'
import { startStageApproval, type StageExecutionContext } from './index'
const api = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: api }))
it('sends the exact execution context and approval input with the stable intent header', async () => {
  const execution: StageExecutionContext = { projectId: '9007199254740993', projectVersion: 1, stageId: '11', stageVersion: 1,
    executionContractId: '41', contractVersion: 1, planVersionId: '21', executionId: '31', executionVersion: 2, roundNo: 3, writable: true }
  const approval = { variables: { note: '办理意见', optional: null }, selectedApprovers: { review: [12] } }
  await startStageApproval(execution.projectId, '工前 准备', execution, approval, 'same-intent')
  expect(api.post).toHaveBeenCalledWith({ url: `/api/v1/pms/projects/9007199254740993/stages/${encodeURIComponent('工前 准备')}/business/approvals`,
    headers: { 'Idempotency-Key': 'same-intent' }, data: { execution, approval } })
})
