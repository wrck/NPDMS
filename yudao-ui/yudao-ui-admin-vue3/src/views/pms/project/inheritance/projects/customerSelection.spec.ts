import { beforeEach, expect, it, vi } from 'vitest'
import { getCustomerPage } from '@/api/pms/customer'
import { getSelectableCustomers } from './customerSelection'

vi.mock('@/api/pms/customer', () => ({ getCustomerPage: vi.fn() }))
beforeEach(() => vi.clearAllMocks())

it('reuses both scoped name/code queries and returns each enabled customer once', async () => {
  const customer = { id: 1, code: 'C-001', name: '客户一', lifecycleStatus: 'ENABLED' }
  vi.mocked(getCustomerPage).mockResolvedValueOnce({ list: [customer] })
    .mockResolvedValueOnce({ list: [customer, { id: 2, code: 'C-002', lifecycleStatus: 'DISABLED' }] })
  expect(await getSelectableCustomers({ pageNo: 1, pageSize: 50, keyword: ' C-0 ' })).toEqual({ list: [customer] })
  expect(getCustomerPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 50, lifecycleStatus: 'ENABLED', code: 'C-0' })
  expect(getCustomerPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 50, lifecycleStatus: 'ENABLED', name: 'C-0' })
})

it('does not invent a customer when the directory is empty or unavailable', async () => {
  vi.mocked(getCustomerPage).mockResolvedValueOnce({ list: [] })
  expect(await getSelectableCustomers({ pageNo: 1, pageSize: 50 })).toEqual({ list: [] })
  expect(getCustomerPage).toHaveBeenCalledTimes(1)
  vi.mocked(getCustomerPage).mockRejectedValueOnce(new Error('directory unavailable'))
  await expect(getSelectableCustomers({ pageNo: 1, pageSize: 50 })).rejects.toThrow('directory unavailable')
})
