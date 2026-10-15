import request, { writeHeaders } from './request'
import type { WireLong } from './request'

const base = '/api/v1/pms/inspection-rules/feedback'
export interface FeedbackFilter {
  pageNo?: number
  pageSize?: number
  ruleRevisionId?: WireLong
  detectionId?: string
  categoryCode?: string
  severityCode?: string
  productModel?: string
  fromAt?: string
  toAt?: string
  currentOnly?: boolean
  confirmation?: 'PENDING' | 'VALID' | 'INVALID'
}
export interface FeedbackRow {
  annotationId: WireLong
  taskId: WireLong
  resultId: WireLong
  ruleRevisionId: WireLong
  ruleId: WireLong
  revisionNo: number
  detectionId: string
  inspectionItem: string
  categoryName: string
  severityName: string
  taskCode: string
  reason: string
  annotatedBy: WireLong
  annotatedAt: number
  detectedAt: number
  deviceSerial?: string
  productModel?: string
  outputSummary?: string
  commands?: string
  confirmationVersion: number
  validFlag?: boolean
  confirmationReason?: string
  confirmedBy?: WireLong
  confirmedAt?: number
  activeFlag: boolean
}
export interface FeedbackStatistic {
  ruleRevisionId: WireLong
  ruleId: WireLong
  revisionNo: number
  detectionId: string
  inspectionItem: string
  detectionCount: number | string
  confirmedFalsePositiveCount: number | string
  falsePositiveRate?: number | string
}
export interface FeedbackPage {
  page: { list: FeedbackRow[]; total: number }
  statistics: FeedbackStatistic[]
  dataAsOf: number
  fromAt?: number
  toAt?: number
  administrator: boolean
  details: boolean
}
export interface ExportView {
  id: WireLong
  version: WireLong
  exportedBy: WireLong
  formatCode: 'CSV' | 'XLSX'
  statusCode: string
  resultCount: number
  dataAsOf: number
  generatedAt?: number
  fromAt?: number
  toAt?: number
  artifactId?: WireLong
  fileVersionNo?: number
  referenceKey?: string
  fileName?: string
  failureMessage?: string
}
export const getFeedback = (params: FeedbackFilter) => request.get<FeedbackPage>({ url: base, params, silentError: true })
export const confirmFeedback = (id: WireLong, data: { valid: boolean; reason: string }, version: WireLong, key: string) =>
  request.post({ url: `${base}/${id}/actions/confirm`, data, headers: writeHeaders(key, version), silentError: true })
export const createRuleDraft = (id: WireLong, data: { reason: string }, version: WireLong, key: string) =>
  request.post<import('./rules').DraftResult>({ url: `${base}/${id}/actions/create-rule-draft`, data, headers: writeHeaders(key, version), silentError: true })
export const requestExport = (format: 'CSV' | 'XLSX', filter: FeedbackFilter, key: string) =>
  request.post<ExportView>({ url: `${base}/exports`, data: { format, filter }, headers: writeHeaders(key), silentError: true })
export const generateExport = (id: WireLong, version: WireLong, key: string) =>
  request.post<ExportView>({ url: `${base}/exports/${id}/actions/generate`, headers: writeHeaders(key, version), timeout: 180000, silentError: true })
export const getExport = (id: WireLong) => request.get<ExportView>({ url: `${base}/exports/${id}`, silentError: true })
export const getExports = (params: { pageNo: number; pageSize: number }) =>
  request.get<{ list: ExportView[]; total: number }>({ url: `${base}/exports`, params, silentError: true })
