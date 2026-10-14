import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { getParentScopes } from './parent-scope'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
describe('parent scope complete read boundaries', () => {
  beforeEach(() => vi.resetAllMocks())
  it('loads history beyond the first page without losing the project and order boundary', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({ list: Array.from({ length: 200 }, (_, id) => ({ id })), total: 201 })
      .mockResolvedValueOnce({ list: [{ id: 200, departmentName: '上海办事处' }], total: 201 })
    const rows = await getParentScopes(100, 51, true)
    expect(rows).toHaveLength(201); expect(rows[200].departmentName).toBe('上海办事处')
    expect(request.get).toHaveBeenLastCalledWith(expect.objectContaining({ params: { projectId: 100, orderLineId: 51, includeHistory: true, pageNo: 2, pageSize: 200 } }))
  })
})
