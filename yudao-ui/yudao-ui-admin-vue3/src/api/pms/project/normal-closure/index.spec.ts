import { beforeEach, describe, expect, it, vi } from 'vitest'
import { checkNormalClosure, submitNormalClosure, type ClosureOverview } from './index'
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: http }))
beforeEach(() => vi.clearAllMocks())
describe('正常闭环公开命令', () => {
  const state = { projectId: '9007199254740993', version: 3, treeVersion: 1 } as ClosureOverview
  it('校验携带项目和树版本，不从客户端提交通过结果', async () => {
    await checkNormalClosure(state, 'check-idem')
    expect(http.post).toHaveBeenCalledWith({
      url: '/api/v1/pms/projects/9007199254740993/normal-closure/actions/check',
      data: { expectedProjectVersion: 3, expectedTreeVersion: 1 },
      headers: { 'Idempotency-Key': 'check-idem' }
    })
  })
  it('提交只引用实际校验快照，不发送批准或终态', async () => {
    await submitNormalClosure(state, '9007199254740994', 'submit-idem')
    expect(http.post).toHaveBeenCalledWith({
      url: '/api/v1/pms/projects/9007199254740993/normal-closure/actions/submit',
      data: { snapshotId: '9007199254740994', expectedProjectVersion: 3, expectedTreeVersion: 1 },
      headers: { 'Idempotency-Key': 'submit-idem' }
    })
  })
})
