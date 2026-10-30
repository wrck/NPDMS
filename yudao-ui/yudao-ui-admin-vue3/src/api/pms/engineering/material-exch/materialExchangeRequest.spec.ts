import { beforeEach, expect, it, vi } from 'vitest'
import { createMaterialExchange, updateMaterialExchange, type MaterialExchangeVO } from './index'
const request = vi.hoisted(() => ({ post: vi.fn(), put: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: request }))
const application = (): MaterialExchangeVO => ({ projectId: 10, code: 'TEST', name: '回归草稿', reason: '原因', quantity: 2, applicantUserId: 999, serials: [{ scopeId: 60, quantity: 2 }], exchangeType: 'INCOMPATIBLE' })
beforeEach(() => vi.clearAllMocks())
it('creation with no client date sends the application without applicant or time', async () => {
  request.post.mockResolvedValue(100)
  const data=application()
  expect(await createMaterialExchange(data)).toBe(100)
  const sent=request.post.mock.calls[0][0]
  expect(sent.url).toBe('/pms/imp-material-exch/create')
  expect(sent.data).toEqual({ projectId: 10, code: 'TEST', name: '回归草稿', reason: '原因', quantity: 2, serials: [{ scopeId: 60, quantity: 2 }], exchangeType: 'INCOMPATIBLE' })
  expect(data.applicantUserId).toBe(999)
})
it('editing a historical application preserves version/content and does not parse a missing or invalid metadata date', async () => {
  request.put.mockResolvedValue(undefined)
  const data={ ...application(), id: 100, version: 3, exchangeType: 'DAMAGED', applyTime: 'invalid historical display value', reasonFiles: '原附件', remark: '原备注' }
  await updateMaterialExchange(data)
  expect(request.put).toHaveBeenCalledWith({ url: '/pms/imp-material-exch/update', data: { projectId: 10, code: 'TEST', name: '回归草稿', reason: '原因', quantity: 2, serials: [{ scopeId: 60, quantity: 2 }], id: 100, version: 3, exchangeType: 'DAMAGED', reasonFiles: '原附件', remark: '原备注' } })
  expect(data.applyTime).toBe('invalid historical display value')
})
it('server failures propagate so the application can retain its selection and reason', async () => {
  request.post.mockRejectedValue(new Error('scope rejected'))
  await expect(createMaterialExchange(application())).rejects.toThrow('scope rejected')
})
