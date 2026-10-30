import request from '@/config/axios'
import { getDeliveryScopePage, type DeliveryScopeRespVO } from '@/api/pms/commerce'
import type { ChildTemplateOption, LongId, ProjectSplitPreviewVO } from './index'

export type { ChildTemplateOption, LongId, ProjectSplitPreviewVO }

// 与现有 ProjectSplitDraftSaveReqVO / ProjectSplitRequestRespVO 保持一致。
export interface SplitScope {
  orderLineId?: number
  quantity: number
  departmentCode?: string
  serialNumbers: string[]
}

export interface SplitItem {
  clientItemKey: string
  projectName: string
  businessLevelCode?: string
  treeSort?: number
  departmentCode?: string
  templateRevisionId?: LongId
  templateSelectionReason?: string
  scopes: SplitScope[]
}

export interface SplitInput {
  parentProjectId: number
  items: SplitItem[]
}

export interface SplitDraft {
  id: LongId
  parentProjectId: number
  status: string
  draftVersion: number
  parentVersion: number
  scopeVersion: number
  treeVersion: number
  items: Array<Omit<SplitItem, 'scopes'> & {
    itemStatus?: string
    scopes: Array<{
      orderLineId: number
      allocatedQty: number
      departmentCode?: string
      serialNo?: string
    }>
  }>
}

export interface SplitResult {
  requestId: LongId
  projects: Array<{ clientItemKey: string; projectId: LongId; projectCode: string }>
  treeVersion: number
  scopeVersion: number
  replayed: boolean
}

export interface SplitScopeOption {
  orderLineId: number
  label: string
  quantity: number
  serialNumbers: string[]
}

const baseUrl = '/pms/project-split-requests'
const headers = (key: string, version: number) => ({ 'Idempotency-Key': key, 'If-Match': String(version) })

export const getDraft = (id: LongId) => request.get<SplitDraft>({ url: `${baseUrl}/${id}` })
export const createDraft = (data: SplitInput, key: string) =>
  request.post<SplitDraft>({ url: baseUrl, data, headers: headers(key, 0) })
export const updateDraft = (draft: SplitDraft, data: SplitInput, key: string) =>
  request.put<SplitDraft>({ url: `${baseUrl}/${draft.id}`, data: { ...data, expectedDraftVersion: draft.draftVersion }, headers: headers(key, draft.draftVersion) })
export const previewDraft = (draft: SplitDraft, key: string) =>
  request.post<ProjectSplitPreviewVO>({ url: `${baseUrl}/${draft.id}/actions/preview`, params: { expectedDraftVersion: draft.draftVersion }, headers: headers(key, draft.draftVersion) })
export const applyDraft = (draft: SplitDraft, key: string) =>
  request.post<SplitResult>({ url: `${baseUrl}/${draft.id}/actions/apply`, data: {
    expectedParentVersion: draft.parentVersion,
    expectedScopeVersion: draft.scopeVersion,
    expectedTreeVersion: draft.treeVersion
  }, headers: headers(key, draft.draftVersion) })

export const getChildTemplateOptions = (params: {
  parentProjectId: number; pageNo: number; pageSize: number; name?: string;
  projectName?: string; businessLevelCode?: string; departmentCode?: string
}) => request.get<{ list: ChildTemplateOption[]; total: number }>({ url: '/api/v1/pms/project-child-template-options', params })

/** 父项目的有效范围按订单行合并；完整分页，最终可拆分性仍由服务端预览确认。 */
export const getScopeOptions = async (projectId: number): Promise<SplitScopeOption[]> => {
  const rows: DeliveryScopeRespVO[] = []
  let pageNo = 1
  while (true) {
    const page = await getDeliveryScopePage({ projectId, pageNo, pageSize: 200, includeHistory: false })
    rows.push(...page.list)
    if (rows.length >= page.total || page.list.length === 0) break
    pageNo++
  }
  const options = new Map<number, SplitScopeOption>()
  for (const row of rows) {
    if (row.scopeStatus !== 'ACTIVE' || Number(row.allocatedQuantity) <= 0) continue
    const option = options.get(row.orderLineId) || {
      orderLineId: row.orderLineId,
      label: [row.orderNo, `行 ${row.lineNo}`, row.productCode, row.productDesc].filter(Boolean).join(' · '),
      quantity: 0,
      serialNumbers: []
    }
    option.quantity += Number(row.allocatedQuantity)
    option.serialNumbers = [...new Set([...option.serialNumbers, ...row.details
      .filter(detail => detail.status === 'ACTIVE' && detail.serialNo)
      .map(detail => detail.serialNo!)])]
    options.set(row.orderLineId, option)
  }
  return [...options.values()]
}

export const restoreItems = (draft: SplitDraft): SplitItem[] => draft.items.map(item => {
  const scopes: SplitScope[] = []
  for (const scope of item.scopes) {
    const departmentCode = scope.departmentCode || undefined
    // 服务端把 SN 拆成每条数量为 1 的记录，重开草稿时还原为原组合。
    const existing = scope.serialNo && scopes.find(value => value.orderLineId === scope.orderLineId
      && value.departmentCode === departmentCode && value.serialNumbers.length > 0)
    if (existing) {
      existing.quantity += Number(scope.allocatedQty)
      existing.serialNumbers.push(scope.serialNo!)
    } else scopes.push({ orderLineId: scope.orderLineId, quantity: Number(scope.allocatedQty),
      departmentCode, serialNumbers: scope.serialNo ? [scope.serialNo] : [] })
  }
  return { clientItemKey: item.clientItemKey, projectName: item.projectName,
    businessLevelCode: item.businessLevelCode, treeSort: item.treeSort,
    departmentCode: item.departmentCode || undefined, templateRevisionId: item.templateRevisionId,
    templateSelectionReason: item.templateSelectionReason, scopes }
})
