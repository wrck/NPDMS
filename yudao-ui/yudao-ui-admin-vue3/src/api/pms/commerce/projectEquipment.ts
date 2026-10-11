import {
  getDeliveryScopePage,
  getSalesOrderLinePage,
  type DeliveryScopeRespVO,
  type SalesOrderLineRespVO
} from './index'
import { getDevicePage, type DeviceListVO } from '@/api/pms/asset/device'

export interface ProjectEquipmentProduct {
  productCode: string
  productModel: string
  productName: string
  orderNo?: string
  projectQty?: number
  deliveredQty?: number
  openQty?: number
  deviceCount: number
}

type EquipmentGroup = ProjectEquipmentProduct & { orderNos: Set<string>; orderLineIds: Set<string> }

const loadPages = async <T>(
  load: (pageNo: number) => Promise<{ list: T[]; total: number }>,
  isCurrent: () => boolean
) => {
  const all: T[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await load(pageNo)
    if (!isCurrent()) return
    all.push(...page.list)
    if (all.length >= page.total || !page.list.length) return all
  }
}

/** 保留原清单的完整产品集合；设备数量与可追溯的订单数量分别汇总。 */
export const loadProjectEquipmentList = async (
  projectId: number,
  isCurrent: () => boolean = () => true
) => {
  const [devices, scopes] = await Promise.all([
    loadPages<DeviceListVO>((pageNo) => getDevicePage({ projectId, pageNo, pageSize: 100 }), isCurrent),
    loadPages<DeliveryScopeRespVO>(
      (pageNo) => getDeliveryScopePage({ projectId, pageNo, pageSize: 100, includeHistory: false }),
      isCurrent
    ).catch(() => [])
  ])
  if (!devices || !scopes || !isCurrent()) return
  const snProducts = new Map(devices.filter((device) => device.sn?.trim()).map((device) => [device.sn.trim(), device]))
  const grouped = new Map<string, EquipmentGroup>()
  const ensure = (code: string) => {
    const row: EquipmentGroup = grouped.get(code) ?? {
      productCode: code,
      productModel: '',
      productName: '',
      deviceCount: 0,
      orderNos: new Set<string>(),
      orderLineIds: new Set<string>()
    }
    grouped.set(code, row)
    return row
  }
  const fillProduct = (row: ProjectEquipmentProduct, device?: DeviceListVO) => {
    row.productModel ||= device?.productModel?.trim() || ''
    row.productName ||= device?.productName?.trim() || ''
  }
  const addOrder = (row: EquipmentGroup, scope: DeliveryScopeRespVO) => {
    if (scope.orderNo?.trim()) row.orderNos.add(scope.orderNo.trim())
    if (scope.orderLineId !== undefined) row.orderLineIds.add(String(scope.orderLineId))
  }
  // 原清单先归集范围明细，再补齐未出现在订单范围中的项目设备产品。
  for (const scope of scopes) {
    for (const detail of scope.details || []) {
      const device = snProducts.get(detail.serialNo?.trim() || '')
      const row = ensure(device?.productCode?.trim() || detail.productCode?.trim() || '—')
      fillProduct(row, device)
      row.projectQty = (row.projectQty ?? 0) + Number(detail.allocatedQuantity)
      addOrder(row, scope)
    }
  }
  for (const device of devices) {
    const row = ensure(device.productCode?.trim() || device.productModel?.trim() || '—')
    fillProduct(row, device)
    row.deviceCount += 1
  }
  // 迁入的订单范围可能没有逐台明细，仍按产品编码关联其订单号与分配数量。
  const scopeQuantities = new Map<string, number>()
  for (const scope of scopes) {
    const code = scope.productCode?.trim()
    if (!code) continue
    const row = ensure(code)
    row.productName ||= scope.productDesc?.trim() || ''
    scopeQuantities.set(code, (scopeQuantities.get(code) ?? 0) + Number(scope.allocatedQuantity))
    row.projectQty = scopeQuantities.get(code)
    addOrder(row, scope)
  }
  const orderNos = new Set([...grouped.values()].flatMap((row) => [...row.orderNos]))
  const orders = await Promise.all([...orderNos].map((orderNo) =>
    loadPages<SalesOrderLineRespVO>(
      (pageNo) => getSalesOrderLinePage({ orderNo, pageNo, pageSize: 100 }),
      isCurrent
    ).catch(() => [])
  ))
  if (!isCurrent()) return
  const lines = new Map(orders.flatMap((order) => order ?? []).map((line) => [String(line.id), line]))
  const list: ProjectEquipmentProduct[] = [...grouped.values()].map(({ orderNos, orderLineIds, ...row }) => {
    const matched = [...orderLineIds].map((id) => lines.get(id))
    const complete = matched.length > 0 && matched.every((line) => line?.productCode?.trim() === row.productCode)
    return {
      ...row,
      orderNo: orderNos.size ? [...orderNos].join('、') : undefined,
      deliveredQty: complete ? matched.reduce((sum, line) => sum + Number(line!.deliveredQty), 0) : undefined,
      openQty: complete ? matched.reduce((sum, line) => sum + Number(line!.openQty), 0) : undefined
    }
  })
  return { list, scopes }
}
