import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { getAllProjectDevices } from './selection'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))
beforeEach(() => vi.resetAllMocks())
it('reads every page for the same project using the shared project endpoint', async () => {
  vi.mocked(request.get).mockResolvedValueOnce({ list: [{ deviceId: '401', serialNumber: 'SN-401' }], total: 2 })
    .mockResolvedValueOnce({ list: [{ deviceId: '402', serialNumber: 'SN-402' }], total: 2 })
  expect((await getAllProjectDevices('9007199254740999')).map(row => row.deviceId)).toEqual(['401', '402'])
  expect(request.get).toHaveBeenNthCalledWith(1, { url: '/api/v1/pms/projects/9007199254740999/devices', params: { pageNo: 1, pageSize: 100 } })
  expect(request.get).toHaveBeenNthCalledWith(2, { url: '/api/v1/pms/projects/9007199254740999/devices', params: { pageNo: 2, pageSize: 100 } })
})
it('an empty authorized page stays empty without a broader fallback query', async () => {
  vi.mocked(request.get).mockResolvedValue({ list: [], total: 0 })
  expect(await getAllProjectDevices('101')).toEqual([])
  expect(request.get).toHaveBeenCalledTimes(1)
})
