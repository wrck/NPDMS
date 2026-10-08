import { expect, it } from 'vitest'
import { withRequestTimestamp } from './requestTime'
it('retains absent historical application time without inventing an epoch or current date', () => {
  expect(withRequestTimestamp({ applyTime: null, id: '9007199254740993' })).toEqual({ applyTime: undefined, id: '9007199254740993' })
  expect(withRequestTimestamp({ applyTime: undefined })).toEqual({ applyTime: undefined })
  expect(withRequestTimestamp({ applyTime: 0 })).toEqual({ applyTime: 0 })
})
it('continues to convert valid native dates and reject invalid explicit input', () => {
  const value = '2026-10-06 08:00:00'
  expect(withRequestTimestamp({ applyTime: value }).applyTime).toBe(new Date(value.replace(' ', 'T')).getTime())
  expect(() => withRequestTimestamp({ applyTime: '' })).toThrow('申请时间无效')
  expect(() => withRequestTimestamp({ applyTime: 'invalid' })).toThrow('申请时间无效')
})
