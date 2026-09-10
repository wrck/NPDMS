import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getTaskBusinessContext, linkTaskBusinessObject, unlinkTaskBusinessObject } from './index'
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: http }))
beforeEach(() => vi.clearAllMocks())
describe('任务业务关系HTTP契约', () => {
  it('使用真实context路由并保留大ID', async () => {
    await getTaskBusinessContext('9223372036854775807')
    expect(http.get).toHaveBeenCalledWith({
      url: '/api/v1/pms/project-tasks/9223372036854775807/business/context'
    })
  })
  it('关联与解除都携带节点版本合同版本及独立幂等键', async () => {
    await linkTaskBusinessObject('10', '9007199254740993', 3, 2, 'link-key')
    expect(http.post).toHaveBeenLastCalledWith({
      url: '/api/v1/pms/project-tasks/10/business/links',
      data: { objectId: '9007199254740993', expectedTaskVersion: 3, expectedContractVersion: 2 },
      headers: { 'If-Match': '2', 'Idempotency-Key': 'link-key' }
    })
    await unlinkTaskBusinessObject('10', '11', 3, 2, 'unlink-key')
    expect(http.post).toHaveBeenLastCalledWith({
      url: '/api/v1/pms/project-tasks/10/business/links/11/actions/unlink',
      data: { expectedTaskVersion: 3, expectedContractVersion: 2 },
      headers: { 'If-Match': '2', 'Idempotency-Key': 'unlink-key' }
    })
  })
})
