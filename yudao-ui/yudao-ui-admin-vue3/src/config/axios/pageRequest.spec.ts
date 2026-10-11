import { expect, it, vi } from 'vitest'
import { getPagedResult } from './pageRequest'
it('keeps ordinary and non-page requests unchanged', async () => {
  const fetch = vi.fn().mockResolvedValue({ value: 1 })
  const option = { url: '/page', params: { pageNo: 2, pageSize: 20 } }
  expect(await getPagedResult(option, fetch)).toEqual({ value: 1 })
  expect(fetch).toHaveBeenCalledExactlyOnceWith(option)
})
it('combines all pages without changing filters, authorization options or caller query', async () => {
  const records = Array.from({ length: 401 }, (_, i) => i)
  const fetch = vi.fn(async option => ({ list: records.slice((option.params.pageNo - 1) * 200, option.params.pageNo * 200), total: 401 }))
  const option = { url: '/page', headers: { 'tenant-id': '1' }, params: { pageNo: 1, pageSize: 401, projectId: 1002, sn: 'filter' } }
  expect(await getPagedResult(option, fetch)).toEqual({ list: records, total: 401 })
  expect(fetch).toHaveBeenCalledTimes(3)
  expect(fetch.mock.calls.map(([q]) => q.params.pageSize)).toEqual([200, 200, 200])
  expect(fetch.mock.calls[2][0]).toEqual({ ...option, params: { ...option.params, pageNo: 3, pageSize: 200 } })
  expect(option.params.pageSize).toBe(401)
})
it('preserves offsets for large ordinary pages and rejects truncated or failed responses', async () => {
  const records = Array.from({ length: 500 }, (_, i) => i)
  const fetch = vi.fn(async option => ({ list: records.slice((option.params.pageNo - 1) * 200, option.params.pageNo * 200), total: 500 }))
  expect((await getPagedResult({ params: { pageNo: 2, pageSize: 201 } }, fetch)).list).toEqual(records.slice(201, 402))
  fetch.mockResolvedValueOnce({ list: records.slice(0, 200), total: 401 }).mockResolvedValueOnce({ list: [], total: 401 })
  await expect(getPagedResult({ params: { pageNo: 1, pageSize: 401 } }, fetch)).rejects.toThrow('分页结果不完整')
  fetch.mockRejectedValueOnce(new Error('forbidden'))
  await expect(getPagedResult({ params: { pageNo: 1, pageSize: 401 } }, fetch)).rejects.toThrow('forbidden')
})
