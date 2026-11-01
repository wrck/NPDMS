import { expect, it, vi } from 'vitest'

const request = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: request }))
vi.mock('@/api/pms/customerConfirmation', () => ({ customerConfirmationBaseUrl: () => '' }))
import { inspectPublicQuestionnaire } from './index'

it('让公开问卷页面处理失效链接，同时保留请求的租户和匿名访问设置', async () => {
  await inspectPublicQuestionnaire('invalid', 1)
  expect(request.get).toHaveBeenCalledWith(expect.objectContaining({
    url: '/api/v1/pms/satisfaction-questionnaires/invalid',
    silentBusinessErrorCodes: [403],
    headers: { 'tenant-id': '1', isToken: false }
  }))
})
