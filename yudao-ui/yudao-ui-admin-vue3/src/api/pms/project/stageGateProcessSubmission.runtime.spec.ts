import { expect, it, vi } from 'vitest'
import { startProjectStageGateProcess } from './projects'
const post = vi.hoisted(() => vi.fn().mockResolvedValue({ outcome: 'STARTED' }))
vi.mock('@/config/axios', () => ({ default: { post } }))

it('sends form values and selected approvers to the existing command with frozen ID and stable concurrency headers', async () => {
  const approval = { variables: { note: '本轮办理', optional: null }, selectedApprovers: { approve: [12] } }
  await startProjectStageGateProcess('9', '9007199254740993', 4, 'same-intent', 'review:2:222', approval)
  expect(post).toHaveBeenCalledWith({
    url: '/api/v1/pms/projects/9/stage-gates/9007199254740993/actions/start-process',
    data: { processDefinitionId: 'review:2:222', ...approval },
    headers: { 'If-Match': '4', 'Idempotency-Key': 'same-intent' }
  })
})
