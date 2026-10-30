import {
  getDeliveryScopePage,
  getSalesOrderLinePage,
  type DeliveryScopeRespVO,
  type SalesOrderLineRespVO
} from './index'
import type { ProjectEquipmentProduct } from './projectEquipment'

export type ProjectOrderEquipmentProduct = Omit<ProjectEquipmentProduct, 'deviceCount'>
interface ProjectOrderEquipmentQuery {
  projectId: number
  isCurrent?: () => boolean
}

const loadPages = async <T>({ load, isCurrent }: {
  load: (pageNo: number) => Promise<{ list: T[]; total: number }>
  isCurrent: () => boolean
}) => {
  const all: T[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await load(pageNo)
    if (!isCurrent()) return
    all.push(...page.list)
    if (all.length >= page.total || !page.list.length) return all
  }
}

/** 项目详情按当前已分配订单行汇总；设备/SN及范围拆分明细不构造产品行。 */
export const loadProjectOrderEquipmentList = async ({
  projectId, isCurrent = () => true
}: ProjectOrderEquipmentQuery) => {
  const assigned = await loadPages<DeliveryScopeRespVO>({
    load: (pageNo) => getDeliveryScopePage({ projectId, pageNo, pageSize: 100, includeHistory: false }),
    isCurrent
  })
  if (!assigned || !isCurrent()) return
  const scopes = assigned.filter((scope) => scope.scopeStatus === 'ACTIVE' && !scope.effectiveTo)
  const grouped = new Map<string, DeliveryScopeRespVO[]>()
  for (const scope of scopes) {
    const code = scope.productCode || '—'
    const group = grouped.get(code) ?? []
    group.push(scope)
    grouped.set(code, group)
  }
  const orderNos = [...new Set(scopes.map((scope) => scope.orderNo?.trim()).filter(Boolean))]
  const orderPages = await Promise.all(orderNos.map((orderNo) => loadPages<SalesOrderLineRespVO>({
      load: (pageNo) => getSalesOrderLinePage({ orderNo, pageNo, pageSize: 100 }),
      isCurrent
    }).catch(() => [])))
  if (!isCurrent()) return
  const lines = new Map(orderPages.flatMap((page) => page ?? []).map((line) => [String(line.id), line]))
  const list: ProjectOrderEquipmentProduct[] = [...grouped].map(([code, group]) => {
    const orderLineIds = new Set(group.map((scope) => String(scope.orderLineId)))
    const matched = [...orderLineIds].map((id) => lines.get(id))
    const complete = matched.every((line) => line?.productCode === code)
    const quantity = (field: 'orderQty' | 'deliveredQty' | 'openQty') =>
      complete && matched.every((line) => line?.[field] != null)
        ? matched.reduce((sum, line) => sum + Number(line![field]), 0) : undefined
    return {
      productCode: code,
      productModel: group.find((scope) => scope.productModel)?.productModel || '',
      productName: group.find((scope) => scope.productDesc?.trim())?.productDesc?.trim()
        || group.find((scope) => scope.productName)?.productName || '',
      orderNo: [...new Set(group.map((scope) => scope.orderNo?.trim()).filter(Boolean))].join('、') || undefined,
      projectQty: group.every((scope) => scope.allocatedQuantity != null)
        ? group.reduce((sum, scope) => sum + Number(scope.allocatedQuantity), 0) : undefined,
      orderQty: quantity('orderQty'),
      deliveredQty: quantity('deliveredQty'),
      openQty: quantity('openQty')
    }
  })
  return { list, scopes }
}
