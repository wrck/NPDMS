import request from '@/config/axios'
import { service } from '@/config/axios/service'
import type { OperationClient } from '@/components/BusinessView/operationClient'
import { acceptanceReportResult } from './operationResults'
export type AcceptanceType = 'PRELIMINARY' | 'FINAL'
export type ReportStatus = 'DRAFT' | 'EFFECTIVE' | 'SUPERSEDED' | 'REVOKED'
export interface AcceptanceActivityVO {
  id: number; projectId: number; projectTaskId: number | null; executionContractId: number | null
  originKind?: 'DIRECT' | 'LEGACY_TASK'
  deliverableId?: number | string | null
  acceptanceType: AcceptanceType; activityStatus: string; currentReportVersionId?: number; version: number
}
export interface ReportAttachmentVO {
  sequence: number; artifactId: number; versionNo: number; referenceKey: string; artifactVersion: number
  referenceVersion: number; availabilityVersion: number; scopeVersion: number; fileHash: string
}
export interface AcceptanceReportVersionVO {
  id: number; acceptanceId: number; reportVersionNo: number; reportStatus: ReportStatus
  acceptanceTime?: number; conclusionCode?: string; conclusionText?: string; acceptorName?: string
  previousVersionId?: number; effectiveFrom?: number; effectiveTo?: number; uploaderUserId: number
  publisherUserId?: number; archiveStatus?: string; archiveFailureCode?: string; archiveRetryCount?: number
  attachments: ReportAttachmentVO[]
}
export interface DraftContent {
  expectedReportVersionNo?: number; acceptanceTime?: number | string; conclusionCode?: string; conclusionText?: string; acceptorName?: string
}
export interface ReportCommandResult {
  acceptanceId: number; reportVersionId: number; reportVersionNo: number; reportStatus: ReportStatus
  changeType?: string; replayed: boolean
  /** Present on controlled responses; refreshes the editor's concurrency vector after its own write. */
  activityVersion?: number
}
const baseUrl = '/api/v1/pms/acceptances'
export interface IndependentAcceptanceContext { projectId: number; projectVersion: number; treeVersion: number; lifecycleStatus: string }
export interface IndependentAcceptanceCreate { projectId: number; acceptanceType: AcceptanceType; expectedProjectVersion: number; expectedTreeVersion: number }
export const getIndependentContext = (projectId: number) => request.get<IndependentAcceptanceContext>({ url: `${baseUrl}/independent/context`, params: { projectId } })
export const createIndependent = (data: IndependentAcceptanceCreate, key: string) => request.post<{ acceptanceId: number; projectId: number; acceptanceType: AcceptanceType; created: boolean }>({ url: `${baseUrl}/independent`, data, headers: { 'Idempotency-Key': key } })
export const getActivities = (projectId?: number) => request.get<AcceptanceActivityVO[]>({ url: baseUrl, params: { projectId } })
export const getActivity = (acceptanceId: number) => request.get<AcceptanceActivityVO>({ url: `${baseUrl}/${acceptanceId}` })
export const getReportVersions = (acceptanceId: number) => request.get<AcceptanceReportVersionVO[]>({ url: `${baseUrl}/${acceptanceId}/report-versions` })
async function controlled(client: OperationClient, code: 'CREATE_DRAFT' | 'UPDATE_DRAFT' | 'PUBLISH' | 'REVOKE', id: number, version: number, input: Record<string, unknown>, key?: string): Promise<ReportCommandResult> {
  const result = await client.execute({ operationCode: `ACC.ACCEPTANCE_REPORT.${code}`, objectId: id, expectedBusinessVersion: version, input, key, validateResult: acceptanceReportResult(code) })
  return { ...(result.response as ReportCommandResult), activityVersion: result.objectVersion, replayed: result.replayed }
}
export const createDraft = (acceptanceId: number, data: DraftContent, activityVersion: number, client?: OperationClient) => client
  ? controlled(client, 'CREATE_DRAFT', acceptanceId, activityVersion, { ...data })
  : request.post<ReportCommandResult>({ url: `${baseUrl}/${acceptanceId}/report-versions`, data, headers: { 'If-Match': String(activityVersion) } })
export const updateDraft = (acceptanceId: number, reportVersionId: number, data: DraftContent, activityVersion: number, client?: OperationClient) => client
  ? controlled(client, 'UPDATE_DRAFT', acceptanceId, activityVersion, { ...data, reportVersionId })
  : service({ url: `${baseUrl}/${acceptanceId}/report-versions/${reportVersionId}`, method: 'PATCH', data,
      headers: { 'If-Match': String(activityVersion) } }).then(response => response.data as ReportCommandResult)
export const publishVersion = (activity: AcceptanceActivityVO, report: Pick<AcceptanceReportVersionVO, 'id' | 'reportVersionNo'>,
  idempotencyKey: string, client?: OperationClient) => client
  ? controlled(client, 'PUBLISH', activity.id, activity.version, { reportVersionId: report.id, expectedReportVersionNo: report.reportVersionNo,
      expectedCurrentReportVersionId: activity.currentReportVersionId }, idempotencyKey)
  : request.post<ReportCommandResult>({ url: `${baseUrl}/${activity.id}/report-versions/${report.id}/actions/publish`,
      data: { expectedReportVersionNo: report.reportVersionNo, expectedCurrentReportVersionId: activity.currentReportVersionId },
      headers: { 'If-Match': String(activity.version), 'Idempotency-Key': idempotencyKey } })
export const revokeCurrentVersion = (activity: AcceptanceActivityVO, current: Pick<AcceptanceReportVersionVO, 'id' | 'reportVersionNo'>,
  idempotencyKey: string, client?: OperationClient) => client
  ? controlled(client, 'REVOKE', activity.id, activity.version, { expectedCurrentReportVersionId: current.id,
      expectedCurrentReportVersionNo: current.reportVersionNo }, idempotencyKey)
  : request.post<ReportCommandResult>({ url: `${baseUrl}/${activity.id}/actions/revoke-current-version`,
      data: { expectedCurrentReportVersionId: current.id, expectedCurrentReportVersionNo: current.reportVersionNo },
      headers: { 'If-Match': String(activity.version), 'Idempotency-Key': idempotencyKey } })
export const downloadAttachment = (acceptanceId: number, reportVersionId: number, sequence: number) => request.get<ReportAttachmentVO>({
  url: `${baseUrl}/${acceptanceId}/report-versions/${reportVersionId}/attachments/${sequence}/download` })
