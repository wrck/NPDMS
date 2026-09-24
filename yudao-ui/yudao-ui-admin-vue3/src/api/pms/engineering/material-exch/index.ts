import request from '@/config/axios'
import { withRequestTimestamp } from '../requestTime'

export interface MaterialExchangeSerialVO {
  /** 设备清单行引用：交付范围明细拆分行（=合同对应销售订单行的分配明细） */
  scopeDetailId?: number
  /** 设备清单行引用：无明细拆分的交付范围行 */
  scopeId?: number
  /** 换货数量，按清单行填写；缺省按 1 台处理 */
  quantity?: number
  orderNo?: string
  lineNo?: string
  itemCode?: string
  productName?: string
  productCode?: string
  deviceTypeCode?: string
  deviceTypeName?: string
  /** 换货产品：产品信息引用；可留空草稿后补 */
  productId?: number
  /** 兼容旧序列号快照：原设备ID */
  deviceId?: number
  /** 兼容旧序列号快照 */
  sn?: string
  productModel?: string
  contractNo?: string
}

export interface MaterialExchangeVO {
  id?: number
  projectId: number
  code: string
  name: string
  exchangeType?: string
  deviceId?: number
  serials?: MaterialExchangeSerialVO[]
  productName?: string
  productCode?: string
  productModel?: string
  quantity: number
  unit?: string
  originalOrderNo?: string
  reason: string
  reasonFiles?: string
  crmPushStatus?: string
  crmPushTime?: string
  crmOrderNo?: string
  newDeviceId?: number
  exchangeProgress?: string
  applicantUserId: number
  applyTime: string | number
  approverUserId?: number
  approveTime?: string
  approveOpinion?: string
  approveAction?: string
  status?: number
  remark?: string
  version?: number
  createTime?: string
}

const baseUrl = '/pms/imp-material-exch'

export const getMaterialExchangePage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getMaterialExchange = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createMaterialExchange = (data: MaterialExchangeVO) =>
  request.post({ url: `${baseUrl}/create`, data: withRequestTimestamp(data) })
export const updateMaterialExchange = (data: MaterialExchangeVO) =>
  request.put({ url: `${baseUrl}/update`, data: withRequestTimestamp(data) })
export const deleteMaterialExchange = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitMaterialExchange = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const approveMaterialExchange = (data: { id: number; approveAction: string; approverUserId?: number; approveOpinion?: string }) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const withdrawMaterialExchange = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
export const terminateMaterialExchange = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
export const pushCrmMaterialExchange = (id: number, crmOrderNo?: string) =>
  request.put({ url: `${baseUrl}/push-crm`, params: { id, crmOrderNo } })
