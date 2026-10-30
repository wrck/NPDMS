import { beforeEach, expect, it, vi } from 'vitest'
import { getContractPage } from '@/api/pms/commerce'
import { getSelectableContracts } from './contractSelection'

vi.mock('@/api/pms/commerce', () => ({ getContractPage: vi.fn() }))
beforeEach(() => vi.clearAllMocks())

it('reuses the scoped contract query with the trimmed contractNo keyword', async () => {
  const contract = { contractNo: 'HT-2026-001', customerCode: 'C00015', customerName: '客户一' }
  vi.mocked(getContractPage).mockResolvedValueOnce({ list: [contract] })
  expect(await getSelectableContracts({ pageNo: 1, pageSize: 50, keyword: ' HT-2026-001 ' }))
    .toEqual({ list: [contract] })
  expect(getContractPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 50, contractNo: 'HT-2026-001' })
})

it('returns the plain master page when no keyword is given', async () => {
  vi.mocked(getContractPage).mockResolvedValueOnce({ list: [] })
  expect(await getSelectableContracts({ pageNo: 1, pageSize: 50 })).toEqual({ list: [] })
  expect(getContractPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 50 })
})
