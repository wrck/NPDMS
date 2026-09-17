import request from '@/config/axios'
import { service } from '@/config/axios/service'
import type { OperationClient } from '@/components/BusinessView/operationClient'
export type AcceptanceType = 'PRELIMINARY' | 'FINAL'
export type ReportStatus = 'DRAFT' | 'EFFECTIVE' | 'SUPERSEDED' | 'REVOKED'
export interface AcceptanceActivityVO {
  id: number; projectId: number; projectTaskId: number; executionContractId: number
  deliverableId?: number | string | null
  acceptanceType: AcceptanceType; activityStatus: string; currentReportVersionId?: number; version: number
}
export interface ReportAttachmentVO {
  sequence: number; artifactId: number; versionNo: number; referenceKey: string; artifactVersion: number
  referenceVersion: number; availabilityVersion: number; scopeVersion: number; fileHash: string
}
export interface AcceptanceReportVersionVO {
  id: number; acceptanceId: number; reportVersionNo: number; reportStatus: ReportStatus
  acceptanceTime?: string; conclusionCode?: string; conclusionText?: string; acceptorName?: string
  previousVersionId?: number; effectiveFrom?: string; effectiveTo?: string; uploaderUserId: number
  publisherUserId?: number; archiveStatus?: string; archiveFailureCode?: string; archiveRetryCount?: number
  attachments: ReportAttachmentVO[]
}
export interface DraftContent {
  expectedReportVersionNo?: number; acceptanceTime?: string; conclusionCode?: string; conclusionText?: string; acceptorName?: string
}
export interface ReportCommandResult {
  acceptanceId: number; reportVersionId: number; reportVersionNo: number; reportStatus: ReportStatus
  changeType?: string; replayed: boolean
  /** Present on controlled responses; refreshes the editor's concurrency vector after its own write. */
  activityVersion?: number
}
const baseUrl = '/api/v1/pms/acceptances'
export const getActivities = (projectId?: number) => request.get<AcceptanceActivityVO[]>({ url: baseUrl, params: { projectId } })
export const getActivity = (acceptanceId: number) => request.get<AcceptanceActivityVO>({ url: `${baseUrl}/${acceptanceId}` })
export const getReportVersions = (acceptanceId: number) => request.get<AcceptanceReportVersionVO[]>({ url: `${baseUrl}/${acceptanceId}/report-versions` })
async function controlled(client: OperationClient, code: string, id: number, version: number, input: Record<string, unknown>, key?: string): Promise<ReportCommandResult> {
  const result = await client.execute({ operationCode: `ACC.ACCEPTANCE_REPORT.${code}`, objectId: id, expectedBusinessVersion: version, input, key })
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
