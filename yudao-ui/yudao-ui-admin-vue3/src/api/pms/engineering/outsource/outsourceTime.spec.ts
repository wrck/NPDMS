import { describe, expect, it, vi } from 'vitest'
vi.mock('@/config/axios', () => ({ default: {} }))
import { outsourceSavePayload, type OutsourceRequestVO } from './index'

describe('outsourcing request timestamp boundary', () => {
  const request = { projectId: 1, code: 'OS', name: 'request', workContent: 'work', applicantUserId: 1 } as OutsourceRequestVO
  it('serializes picker strings as timestamps and preserves existing numeric timestamps', () => {
    const timestamp = new Date(2026, 8, 9, 17, 11, 55).getTime()
    expect(outsourceSavePayload({ ...request, applyTime: '2026-09-09 17:11:55' }).applyTime).toBe(timestamp)
    expect(outsourceSavePayload({ ...request, applyTime: timestamp }).applyTime).toBe(timestamp)
  })
  it('rejects invalid time instead of producing an epoch-zero request', () => {
    expect(() => outsourceSavePayload({ ...request, applyTime: 'bad date' })).toThrow('申请时间无效')
  })
})
