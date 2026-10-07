import type { SiteSurveyVO, SiteSurveyExecutionSelection, SiteSurveyCommandResult } from '@/api/pms/engineering/site-survey/entity'
import { saveSiteSurveyReceipt, siteSurveyActionReceipt, surveyReceiptResult } from '@/api/pms/engineering/site-survey/entity'
import type { BusinessOperationReceipt } from '@/api/pms/platform/businessmodel'
import { sameBusinessViewId, isBusinessViewId } from '@/api/pms/platform/business-view/ids'

export type SurveyAction = 'delete' | 'confirm' | 'reject' | 'archive'
export type SurveyReceiptIntent = {
  operation: 'save'; key: string; payload: SiteSurveyVO; context: number; title: string
} | {
  operation: SurveyAction; key: string; objectId: number; projectId: number; basis: number;
  execution?: SiteSurveyExecutionSelection; context: number; title: string
}
function freeze<T>(value: T): T {
  if (value && typeof value === 'object') {
    for (const child of Object.values(value)) freeze(child)
    Object.freeze(value)
  }
  return value
}
/** Freeze the wire values; the local routing symbol remains on a shallow execution copy. */
function executionCopy(execution?: SiteSurveyExecutionSelection): SiteSurveyExecutionSelection | undefined {
  if (!execution) return undefined
  return Object.freeze({ ...execution,
    ...(execution.task ? { task: Object.freeze({ ...execution.task }) } : {}),
    ...(execution.stage ? { stage: Object.freeze({ ...execution.stage }) } : {}) })
}
export function pinSurveySave(payload: SiteSurveyVO, key: string, context: number): SurveyReceiptIntent {
  const { execution, ...body } = payload
  const copy = freeze(JSON.parse(JSON.stringify(body))) as SiteSurveyVO
  return Object.freeze({ operation: 'save', key, context, title: '保存',
    payload: Object.freeze({ ...copy, ...(execution ? { execution: executionCopy(execution) } : {}) }) })
}
export function pinSurveyAction(operation: SurveyAction, row: SiteSurveyVO, execution: SiteSurveyExecutionSelection | undefined,
  key: string, context: number): SurveyReceiptIntent {
  if (!isBusinessViewId(row.id) || !isBusinessViewId(row.projectId) || !Number.isSafeInteger(row.version) || row.version! < 0)
    throw new Error('工勘对象或并发版本缺失，请重新加载')
  return Object.freeze({ operation, key, context, objectId: row.id!, projectId: row.projectId, basis: row.version!,
    execution: executionCopy(execution), title: { delete: '删除', confirm: '确认', reject: '驳回', archive: '归档' }[operation] })
}
export function submitSurveyIntent(intent: SurveyReceiptIntent): Promise<BusinessOperationReceipt> {
  return intent.operation === 'save' ? saveSiteSurveyReceipt(intent.payload, intent.key)
    : siteSurveyActionReceipt(intent.operation, intent.objectId, intent.basis, intent.projectId, intent.key, intent.execution)
}
export function intentResult(intent: SurveyReceiptIntent, receipt: BusinessOperationReceipt): SiteSurveyCommandResult {
  return intent.operation === 'save' ? surveyReceiptResult(receipt, intent.payload.id, intent.payload.projectId)
    : surveyReceiptResult(receipt, intent.objectId, intent.projectId)
}
/** SiteSurvey has no immutable content history: a later current version cannot impersonate this receipt. */
export function requireSurveyReceiptRow(result: SiteSurveyCommandResult, row: SiteSurveyVO): SiteSurveyVO {
  if (result.deleted || !row || !sameBusinessViewId(result.id, row.id) || !sameBusinessViewId(result.projectId, row.projectId)
    || row.version !== result.version || String(row.status) !== result.state)
    throw new Error('回执对应工勘对象、项目或版本已不匹配')
  return row
}
