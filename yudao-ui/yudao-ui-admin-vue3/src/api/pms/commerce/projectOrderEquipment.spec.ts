import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadProjectOrderEquipmentList } from './projectOrderEquipment'
const deviceApi = vi.hoisted(() => ({ getDevicePage: vi.fn() }))
const commerceApi = vi.hoisted(() => ({ getDeliveryScopePage: vi.fn(), getSalesOrderLinePage: vi.fn() }))
vi.mock('@/api/pms/asset/device', () => deviceApi)
vi.mock('./index', () => commerceApi)

const lineA = '2113088200020951202'
const lineB = '2113088200020951203'
const scope = (overrides = {}) => ({
  id: 'SCOPE-A', orderLineId: lineA, orderNo: 'SO-A', productCode: 'A', productDesc: '订单产品A',
  productModel: 'MODEL-A', productName: '产品A',
  allocatedQuantity: 4, scopeStatus: 'ACTIVE', details: [], ...overrides
})
const line = (overrides = {}) => ({
  id: lineA, productCode: 'A', orderQty: 8, deliveredQty: 3, openQty: 5, ...overrides
})

beforeEach(() => {
  vi.resetAllMocks()
  commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [scope()], total: 1 })
  commerceApi.getSalesOrderLinePage.mockResolvedValue({ list: [line()], total: 1 })
  deviceApi.getDevicePage.mockResolvedValue({ list: [
    { productCode: 'A', productModel: 'SN-MODEL-A', productName: '设备A', sn: 'A-1' },
    { productCode: 'DEVICE-ONLY', productModel: 'SN-ONLY', sn: 'EXTRA-1' }
  ], total: 2 })
})

describe('project detail equipment uses assigned order lines', () => {
  it('loads every assigned page, ignores SN-only and detail products, and counts each exact order line once', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValueOnce({ total: 101, list:
      Array.from({ length: 100 }, (_, index) => scope({ id: `SCOPE-${index}`, allocatedQuantity: 1,
        details: [{ productCode: 'DETAIL-ONLY', serialNo: 'EXTRA-1', allocatedQuantity: 50 }] }))
    }).mockResolvedValueOnce({ total: 101, list: [scope({ id: 'SCOPE-B', orderLineId: lineB,
      productCode: 'B', productDesc: '订单产品B', allocatedQuantity: 2 })] })
    commerceApi.getSalesOrderLinePage.mockResolvedValueOnce({ total: 101, list: [line(),
      ...Array.from({ length: 99 }, (_, index) => line({ id: `OTHER-${index}`, deliveredQty: 99 }))
    ] }).mockResolvedValueOnce({ total: 101, list: [line({ id: lineB, productCode: 'B', orderQty: 2, deliveredQty: 0, openQty: 2 })] })
    const result = await loadProjectOrderEquipmentList({ projectId: 84459 })
    expect(result?.list.map((row) => row.productCode)).toEqual(['A', 'B'])
    expect(result?.list[0]).toMatchObject({ productName: '订单产品A', productModel: 'MODEL-A', projectQty: 100,
      orderNo: 'SO-A', orderQty: 8, deliveredQty: 3, openQty: 5 })
    expect(result?.list[1]).toMatchObject({ productName: '订单产品B', projectQty: 2, deliveredQty: 0, openQty: 2 })
    expect(deviceApi.getDevicePage).not.toHaveBeenCalled()
    expect(commerceApi.getDeliveryScopePage).toHaveBeenLastCalledWith({ projectId: 84459, includeHistory: false, pageNo: 2, pageSize: 100 })
    expect(commerceApi.getSalesOrderLinePage).toHaveBeenLastCalledWith({ orderNo: 'SO-A', pageNo: 2, pageSize: 100 })
  })

  it('keeps an empty assigned scope empty without expanding to devices or all order lines', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [], total: 0 })
    expect((await loadProjectOrderEquipmentList({ projectId: 84459 }))?.list).toEqual([])
    expect(commerceApi.getSalesOrderLinePage).not.toHaveBeenCalled()
    expect(deviceApi.getDevicePage).not.toHaveBeenCalled()
  })

  it('does not accept incomplete assignment pages or fall back to device products after a source failure', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValueOnce({ list: [scope()], total: 2 })
      .mockRejectedValueOnce(new Error('范围后续页失败'))
    await expect(loadProjectOrderEquipmentList({ projectId: 84459 })).rejects.toThrow('范围后续页失败')
    expect(commerceApi.getSalesOrderLinePage).not.toHaveBeenCalled()
  })

  it('preserves assigned products and quantities but no partial shipping totals if a later order page fails', async () => {
    commerceApi.getSalesOrderLinePage.mockResolvedValueOnce({ list: [line()], total: 2 })
      .mockRejectedValueOnce(new Error('订单后续页失败'))
    const result = await loadProjectOrderEquipmentList({ projectId: 84459 })
    expect(result?.list).toHaveLength(1)
    expect(result?.list[0]).toMatchObject({ productCode: 'A', projectQty: 4, productName: '订单产品A' })
    expect(result?.list[0].deliveredQty).toBeUndefined()
    expect(result?.list[0].openQty).toBeUndefined()
  })

  it.each([null, undefined])('keeps missing order-line quantities %s unknown', async (quantity) => {
    commerceApi.getSalesOrderLinePage.mockResolvedValue({ list: [line({ deliveredQty: quantity, openQty: quantity })], total: 1 })
    const result = await loadProjectOrderEquipmentList({ projectId: 84459 })
    expect(result?.list[0].deliveredQty).toBeUndefined()
    expect(result?.list[0].openQty).toBeUndefined()
  })

  it('excludes released and historical assignments from the product set', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [scope(),
      scope({ id: 'RELEASED', productCode: 'R', scopeStatus: 'RELEASED' }),
      scope({ id: 'HISTORICAL', productCode: 'H', effectiveTo: '2026-10-01T00:00:00' })
    ], total: 3 })
    expect((await loadProjectOrderEquipmentList({ projectId: 84459 }))?.list.map((row) => row.productCode)).toEqual(['A'])
  })

  it('keeps an absent authorized model unknown instead of using device metadata', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [scope({ productModel: null })], total: 1 })
    const result = await loadProjectOrderEquipmentList({ projectId: 84459 })
    expect(result?.list[0]).toMatchObject({ productName: '订单产品A', productModel: '', deliveredQty: 3 })
  })

  it('uses current product name only when the assigned order description is missing', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [scope({ productDesc: null })], total: 1 })
    expect((await loadProjectOrderEquipmentList({ projectId: 84459 }))?.list[0].productName).toBe('产品A')
  })

  it('keeps the current source model unchanged', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [scope({ productModel: ' MASTER ' })], total: 1 })
    expect((await loadProjectOrderEquipmentList({ projectId: 84459 }))?.list[0].productModel).toBe(' MASTER ')
  })

  it('does not merge product codes that differ by surrounding whitespace', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [
      scope({ productCode: ' A ', productModel: null, allocatedQuantity: 2 }),
      scope({ id: 'SCOPE-B', orderLineId: lineB, productCode: 'A', productModel: 'MASTER', allocatedQuantity: 3 })
    ], total: 2 })
    const rows = (await loadProjectOrderEquipmentList({ projectId: 84499 }))?.list
    expect(rows?.map((row) => [row.productCode, row.productModel, row.projectQty])).toEqual([
      [' A ', '', 2], ['A', 'MASTER', 3]
    ])
  })

  it('discards a late result after changing the current project', async () => {
    const result = await loadProjectOrderEquipmentList({ projectId: 84459, isCurrent: () => false })
    expect(result).toBeUndefined()
    expect(commerceApi.getSalesOrderLinePage).not.toHaveBeenCalled()
  })
})
