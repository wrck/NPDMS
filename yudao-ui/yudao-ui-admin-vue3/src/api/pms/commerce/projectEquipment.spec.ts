import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadProjectEquipmentList } from './projectEquipment'

const deviceApi = vi.hoisted(() => ({ getDevicePage: vi.fn() }))
const commerceApi = vi.hoisted(() => ({ getDeliveryScopePage: vi.fn(), getSalesOrderLinePage: vi.fn() }))
vi.mock('@/api/pms/asset/device', () => deviceApi)
vi.mock('./index', () => commerceApi)

const devices = (code: string, count: number) => Array.from({ length: count }, (_, index) => ({
  sn: `SN-${code}-${index}`, productCode: code, productName: `设备${code}`, productModel: `MODEL-${code}`
}))
beforeEach(() => {
  vi.resetAllMocks()
  deviceApi.getDevicePage.mockResolvedValue({ list: [...devices('A', 4), ...devices('B', 2), ...devices('C', 1)], total: 7 })
})

describe('project equipment order fields and device quantities', () => {
  it('keeps device-only products and distinguishes full device counts from paged order quantities without counting a shared order line twice', async () => {
    const lineA = '2101600914900267749', lineB = '2101600914900267750'
    commerceApi.getDeliveryScopePage.mockResolvedValueOnce({ total: 101, list:
      Array.from({ length: 100 }, (_, index) => ({ id: index + 1, orderLineId: lineA,
        orderNo: 'SO-A', lineNo: '1', productCode: ' A ', allocatedQuantity: 1, details: [] }))
    }).mockResolvedValueOnce({ total: 101, list: [{ id: 101, orderLineId: lineB,
      orderNo: 'SO-A', lineNo: '2', productCode: 'B', allocatedQuantity: 2, details: [] }] })
    commerceApi.getSalesOrderLinePage.mockResolvedValueOnce({ total: 101, list: [
      { id: lineA, productCode: 'A', deliveredQty: 4, openQty: 6 },
      ...Array.from({ length: 99 }, (_, index) => ({ id: `OTHER-${index}`, productCode: 'OTHER', deliveredQty: 99, openQty: 99 }))
    ] }).mockResolvedValueOnce({ total: 101, list: [{ id: lineB, productCode: 'B', deliveredQty: 2, openQty: 0 }] })
    const result = await loadProjectEquipmentList(198)
    expect(result?.list).toEqual([
      { productCode: 'A', productName: '设备A', productModel: 'MODEL-A', deviceCount: 4, orderNo: 'SO-A', projectQty: 100, deliveredQty: 4, openQty: 6 },
      { productCode: 'B', productName: '设备B', productModel: 'MODEL-B', deviceCount: 2, orderNo: 'SO-A', projectQty: 2, deliveredQty: 2, openQty: 0 },
      { productCode: 'C', productName: '设备C', productModel: 'MODEL-C', deviceCount: 1, orderNo: undefined, deliveredQty: undefined, openQty: undefined }
    ])
    expect(commerceApi.getDeliveryScopePage).toHaveBeenLastCalledWith({ projectId: 198, pageNo: 2, pageSize: 100, includeHistory: false })
    expect(commerceApi.getSalesOrderLinePage).toHaveBeenLastCalledWith({ orderNo: 'SO-A', pageNo: 2, pageSize: 100 })
  })
  it('keeps shipment quantities unknown when a later order page fails while preserving equipment and assigned quantities', async () => {
    commerceApi.getDeliveryScopePage.mockResolvedValueOnce({ total: 1, list: [{
      id: 1, orderLineId: 'LINE-A', orderNo: 'SO-A', lineNo: '1', productCode: 'A', allocatedQuantity: 4, details: []
    }] })
    commerceApi.getSalesOrderLinePage.mockResolvedValueOnce({ total: 101, list: [
      { id: 'LINE-A', productCode: 'A', deliveredQty: 4, openQty: 0 },
      ...Array.from({ length: 99 }, (_, index) => ({ id: `OTHER-${index}`, productCode: 'OTHER', deliveredQty: 99, openQty: 99 }))
    ] }).mockRejectedValueOnce(new Error('后续订单行不可读'))
    const result = await loadProjectEquipmentList(198)
    expect(result?.list).toHaveLength(3)
    expect(result?.list[0]).toMatchObject({ deviceCount: 4, projectQty: 4, orderNo: 'SO-A' })
    expect(result?.list[0].deliveredQty).toBeUndefined()
    expect(result?.list[0].openQty).toBeUndefined()
  })
})
