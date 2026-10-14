import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getScopeOptions, restoreItems } from './workspace'
import { getDeliveryScopePage } from '@/api/pms/commerce'

vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/pms/commerce', () => ({ getDeliveryScopePage: vi.fn() }))

describe('split scope choices', () => {
  beforeEach(() => vi.clearAllMocks())
  it('reads all pages, merges active quantities and excludes released scopes', async () => {
    const row = (id: number, quantity: number, status = 'ACTIVE') => ({ orderLineId: id, orderNo: 'QA-ORDER', lineNo: '1',
      allocatedQuantity: quantity, scopeStatus: status, details: [{ status: 'ACTIVE', serialNo: 'QA-SN' }, { status: 'RELEASED', serialNo: 'OLD-SN' }] })
    vi.mocked(getDeliveryScopePage).mockResolvedValueOnce({ list: [row(10, 2)], total: 3 } as never)
      .mockResolvedValueOnce({ list: [row(10, 3), row(20, 5, 'RELEASED')], total: 3 } as never)
    const choices = await getScopeOptions(100)
    expect(getDeliveryScopePage).toHaveBeenLastCalledWith({ projectId: 100, pageNo: 2, pageSize: 200, includeHistory: false })
    expect(choices).toEqual([{ orderLineId: 10, label: 'QA-ORDER · 行 1', quantity: 5, serialNumbers: ['QA-SN'] }])
  })

  it('preserves separate department combinations while regrouping serials', () => {
    const result = restoreItems({ items: [{ clientItemKey: 'a', projectName: 'QA', scopes: [
      { orderLineId: 10, departmentCode: 'HZ', allocatedQty: 1, serialNo: 'A' },
      { orderLineId: 10, departmentCode: 'HZ', allocatedQty: 1, serialNo: 'B' },
      { orderLineId: 10, departmentCode: 'BJ', allocatedQty: 1, serialNo: 'C' },
      { orderLineId: 10, allocatedQty: 3 }
    ] }] } as never)
    expect(result[0].scopes.map(scope => [scope.departmentCode, scope.quantity, scope.serialNumbers])).toEqual([
      ['HZ', 2, ['A', 'B']], ['BJ', 1, ['C']], [undefined, 3, []]
    ])
  })

  it('regroups serials when the server returns a null department', () => {
    const result = restoreItems({ items: [{ clientItemKey: 'a', projectName: 'QA', scopes: [
      { orderLineId: 10, departmentCode: null, allocatedQty: 1, serialNo: 'A' },
      { orderLineId: 10, departmentCode: null, allocatedQty: 1, serialNo: 'B' }
    ] }] } as never)
    expect(result[0].scopes).toEqual([{ orderLineId: 10, departmentCode: undefined, quantity: 2, serialNumbers: ['A', 'B'] }])
  })
})
