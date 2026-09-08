import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { getManagerCandidates, getProjectManagers, updateMembers } from './index'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

describe('PM-01 member HTTP contract', () => {
  beforeEach(() => vi.clearAllMocks())
  it('uses the versioned member endpoints without accepting company or role overrides', async () => {
    await getProjectManagers(9)
    await getManagerCandidates(9, '张', 2)
    expect(request.get).toHaveBeenNthCalledWith(1, { url: '/api/v1/pms/projects/9/project-managers' })
    expect(request.get).toHaveBeenNthCalledWith(2, {
      url: '/api/v1/pms/projects/9/project-manager-candidates', params: { keyword: '张', pageNo: 2, pageSize: 20 }
    })
  })
  it('sends one atomic intent with the original version and retry key', async () => {
    const data = { addUserIds: [2], removeUserIds: [1], primaryUserId: 2, reason: '交接' }
    await updateMembers(9, data, 3, 'same-intent')
    expect(request.post).toHaveBeenCalledOnce()
    expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/projects/9/actions/update-members', data,
      headers: { 'If-Match': '3', 'Idempotency-Key': 'same-intent' } })
  })
})
