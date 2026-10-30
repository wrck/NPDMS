import request from '@/config/axios'
import type { LongId } from './workspace'

export interface ContractScopeVersions {
  expectedProjectVersion: number
  expectedTreeVersion: number
  expectedScopeVersion: number
}
export interface ContractScopeLine {
  orderLineId: LongId
  orderNo: string
  lineNo: string
  productCode: string
  productDesc?: string
  sourceVersion: string
  orderQuantity: number | null
  allocatedElsewhere: number
  currentQuantity: number
  proposedQuantity: number | null
  changeType: 'NO_CHANGE' | 'ADD' | 'UPDATE' | 'RELEASE' | 'CONFLICT'
  errors: string[]
}
export interface ContractScopePreview {
  projectId: LongId
  scopeVersion: number
  sourceDigest: string
  lines: ContractScopeLine[]
  errors: string[]
  changedLineCount: number
  valid: boolean
}
export const previewContractScope = (projectId: number, data: ContractScopeVersions) =>
  request.post<ContractScopePreview>({ url: `/api/v1/pms/project-contract-scopes/${projectId}/actions/preview`, data })

export const synchronizeContractScope = (projectId: number, data: ContractScopeVersions & { sourceDigest: string }, idempotencyKey: string) =>
  request.post<{ projectId: LongId; scopeVersion: number; changedLineCount: number }>({
    url: `/api/v1/pms/project-contract-scopes/${projectId}/actions/synchronize`, data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

const messages: Record<string, string> = {
  PROJECT_CONTRACT_REQUIRED: '当前项目尚未关联合同，请先核对合同关系。',
  CONTRACT_ORDER_RELATION_CHANGED: '该订单与项目合同的关系已变化，请核对原范围。',
  ORDER_LINE_AUTHORITY_INVALID: '订单行尚无有效的 ERP 权威数量，请先完成来源核对。',
  ALLOCATED_SCOPE_EXCEEDS_ORDER: '已分给其他项目的数量超过订单有效数量，需要先处理占用冲突。',
  CHILD_SCOPE_CONFLICT_FROZEN: '其他项目承接的范围已冲突冻结，请先处理该范围。',
  SERIAL_SCOPE_REQUIRES_REVIEW: '变更涉及已有设备序列号，需要先核对设备范围。',
  ACCEPTANCE_SCOPE_LOCKED: '当前范围已被验收业务锁定，不能自动减少。',
  ACCEPTANCE_SCOPE_UNKNOWN: '暂时无法确认验收占用情况，请稍后重试。',
  UNIT_PRECISION_INVALID: '数量精度不符合订单计量单位，请核对来源数据。',
  ORDER_PRODUCT_REQUIRED: '订单行缺少产品编码，请先补齐来源数据。',
  CONTRACT_SCOPE_SOURCE_CHANGED: '合同订单或已分配范围在预览后发生变化，请重新预览。',
  DELIVERY_SCOPE_PROJECT_VERSION_CONFLICT: '项目订单范围已更新，请重新预览。',
  PROJECT_TREE_VERSION_CONFLICT: '主子项目关系已更新，请重新预览。',
  PROJECT_OFFICE_FACT_INVALID: '项目状态、版本或办事处信息已变化，请刷新后重新预览。',
  ROOT_PROJECT_REQUIRED: '子项目范围由父项目拆分确定，不能同步整份合同。'
}
export const contractScopeError = (value: string) => messages[value] ||
  Object.entries(messages).find(([code]) => value.includes(code))?.[1] || value
export const contractScopeChangeLabel = (value: string) => ({ ADD: '新增', UPDATE: '更新', RELEASE: '释放', NO_CHANGE: '无变化', CONFLICT: '待处理' })[value] || value
