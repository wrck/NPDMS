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
  it('fills missing models for assigned products without devices from authorized scope metadata', async () => {
    deviceApi.getDevicePage.mockResolvedValue({ list: devices('02010078', 24), total: 24 })
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [
      { id: 1, orderLineId: 'L1', orderNo: 'SO120068', productCode: '02011299', productModel: 'QSFP28-LX2-SM1310-CWDM4', allocatedQuantity: 6 },
      { id: 2, orderLineId: 'L2', orderNo: 'SO120068', productCode: '02011299', productModel: 'QSFP28-LX2-SM1310-CWDM4', allocatedQuantity: 6 },
      { id: 3, orderLineId: 'L3', orderNo: 'SO120068', productCode: '02010257', productModel: 'QSFP-LX10-SM1310', allocatedQuantity: 12 },
      { id: 4, productCode: '02010078', productModel: 'CATALOG-MODEL', allocatedQuantity: 24 }
    ], total: 4 })
    commerceApi.getSalesOrderLinePage.mockResolvedValue({ list: [
      { id: 'L1', productCode: '02011299', orderQty: 6, deliveredQty: 6, openQty: 0 },
      { id: 'L2', productCode: '02011299', orderQty: 6, deliveredQty: 6, openQty: 0 },
      { id: 'L3', productCode: '02010257', orderQty: 12, deliveredQty: 12, openQty: 0 }
    ], total: 3 })
    const result = await loadProjectEquipmentList(84499)
    expect(result?.list).toHaveLength(3)
    expect(result?.list.find((row) => row.productCode === '02011299')).toMatchObject({
      productModel: 'QSFP28-LX2-SM1310-CWDM4', projectQty: 12, orderQty: 12, deliveredQty: 12, deviceCount: 0
    })
    expect(result?.list.find((row) => row.productCode === '02010257')?.productModel).toBe('QSFP-LX10-SM1310')
    expect(result?.list.find((row) => row.productCode === '02010078')?.productModel).toBe('MODEL-02010078')
  })

  it('keeps a missing model unknown without discarding the assigned product', async () => {
    deviceApi.getDevicePage.mockResolvedValue({ list: [], total: 0 })
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [
      { id: 1, productCode: 'A', allocatedQuantity: 2 }
    ], total: 1 })
    const result = await loadProjectEquipmentList(84499)
    expect(result?.list).toHaveLength(1)
    expect(result?.list[0]).toMatchObject({ productCode: 'A', productModel: '', projectQty: 2 })
  })

  it('uses the source master name when an assigned product has no device or scope description', async () => {
    deviceApi.getDevicePage.mockResolvedValue({ list: [], total: 0 })
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [
      { id: 1, productCode: 'A', productName: 'MASTER-A', allocatedQuantity: 2 },
      { id: 2, productCode: 'B', productDesc: 'SCOPE-B', productName: 'MASTER-B', allocatedQuantity: 3 }
    ], total: 2 })
    const rows = (await loadProjectEquipmentList(84499))?.list
    expect(rows?.map((row) => [row.productCode, row.productName, row.projectQty])).toEqual([
      ['A', 'MASTER-A', 2], ['B', 'SCOPE-B', 3]
    ])
  })

  it('keeps the current source model unchanged when no device model is present', async () => {
    deviceApi.getDevicePage.mockResolvedValue({ list: [], total: 0 })
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [
      { id: 1, productCode: 'A', productModel: ' MASTER ', allocatedQuantity: 2 }
    ], total: 1 })
    expect((await loadProjectEquipmentList(84499))?.list[0].productModel).toBe(' MASTER ')
  })

  it('keeps product codes with surrounding whitespace separate from the canonical code', async () => {
    deviceApi.getDevicePage.mockResolvedValue({ list: [], total: 0 })
    commerceApi.getDeliveryScopePage.mockResolvedValue({ list: [
      { id: 1, productCode: ' A ', allocatedQuantity: 2 },
      { id: 2, productCode: 'A', productModel: 'MASTER', allocatedQuantity: 3 }
    ], total: 2 })
    const rows = (await loadProjectEquipmentList(84499))?.list
    expect(rows?.map((row) => [row.productCode, row.productModel, row.projectQty])).toEqual([
      [' A ', '', 2], ['A', 'MASTER', 3]
    ])
  })

  it.each(['forbidden', 'source unavailable'])('propagates %s scope metadata failures instead of returning a device-only list', async (message) => {
    commerceApi.getDeliveryScopePage.mockRejectedValue(new Error(message))
    await expect(loadProjectEquipmentList(84499)).rejects.toThrow(message)
  })

  it('keeps device-only products and distinguishes full device counts from paged order quantities without counting a shared order line twice', async () => {
    const lineA = '2101600914900267749', lineB = '2101600914900267750'
    commerceApi.getDeliveryScopePage.mockResolvedValueOnce({ total: 101, list:
      Array.from({ length: 100 }, (_, index) => ({ id: index + 1, orderLineId: lineA,
        orderNo: 'SO-A', lineNo: '1', productCode: 'A', allocatedQuantity: 1, details: [] }))
    }).mockResolvedValueOnce({ total: 101, list: [{ id: 101, orderLineId: lineB,
      orderNo: 'SO-A', lineNo: '2', productCode: 'B', allocatedQuantity: 2, details: [] }] })
    commerceApi.getSalesOrderLinePage.mockResolvedValueOnce({ total: 101, list: [
      { id: lineA, productCode: 'A', orderQty: 10, deliveredQty: 3, openQty: 7 },
      ...Array.from({ length: 99 }, (_, index) => ({ id: `OTHER-${index}`, productCode: 'OTHER', deliveredQty: 99, openQty: 99 }))
    ] }).mockResolvedValueOnce({ total: 101, list: [{ id: lineB, productCode: 'B', orderQty: 2, deliveredQty: 0, openQty: 2 }] })
    const result = await loadProjectEquipmentList(198)
    expect(result?.list).toEqual([
      { productCode: 'A', productName: '设备A', productModel: 'MODEL-A', deviceCount: 4, orderNo: 'SO-A', projectQty: 100, orderQty: 10, deliveredQty: 3, openQty: 7 },
      { productCode: 'B', productName: '设备B', productModel: 'MODEL-B', deviceCount: 2, orderNo: 'SO-A', projectQty: 2, orderQty: 2, deliveredQty: 0, openQty: 2 },
      { productCode: 'C', productName: '设备C', productModel: 'MODEL-C', deviceCount: 1, orderNo: undefined, orderQty: undefined, deliveredQty: undefined, openQty: undefined }
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
  it.each([null, undefined])('keeps missing order-line shipment quantity %s unknown even when devices exist', async (deliveredQty) => {
    commerceApi.getDeliveryScopePage.mockResolvedValue({ total: 1, list: [{
      id: 1, orderLineId: 'LINE-A', orderNo: 'SO-A', productCode: 'A', allocatedQuantity: 4, details: []
    }] })
    commerceApi.getSalesOrderLinePage.mockResolvedValue({ total: 1, list: [{
      id: 'LINE-A', productCode: 'A', orderQty: 4, deliveredQty, openQty: 4
    }] })
    const result = await loadProjectEquipmentList(198)
    expect(result?.list[0]).toMatchObject({ deviceCount: 4, orderQty: 4 })
    expect(result?.list[0].deliveredQty).toBeUndefined()
  })
})
