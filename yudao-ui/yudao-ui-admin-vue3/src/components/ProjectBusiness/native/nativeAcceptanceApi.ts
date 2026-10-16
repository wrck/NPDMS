import type * as AcceptanceApi from '@/api/pms/acceptance/acceptance-report'
import type { NativeBusinessBridge } from './nativeBusinessBridge'
import request from '@/config/axios'

export const nativeAcceptanceApi = (bridge: NativeBusinessBridge, original: typeof AcceptanceApi) => {
  const result = async (id: number, version: number, reportId?: number): Promise<AcceptanceApi.ReportCommandResult> => {
    const versions = await original.getReportVersions(id)
    const matches = reportId == null ? versions.filter(item => item.reportStatus === 'DRAFT') : versions.filter(item => String(item.id) === String(reportId))
    if (matches.length !== 1) throw new Error('验收报告身份尚未确认，请刷新后继续')
    const report = matches[0]
    return { acceptanceId: id, reportVersionId: report.id, reportVersionNo: report.reportVersionNo,
      reportStatus: report.reportStatus, activityVersion: version, replayed: false }
  }
  return {
    getReportVersions: async (id: number) => {
      const versions = await original.getReportVersions(id)
      return Promise.all(versions.map(async version => {
        if (version.reportStatus !== 'DRAFT') return version
        const files = await request.get<unknown[]>({ url: `${bridge.api.base}/${id}/reports/${version.id}/files` })
        return { ...version, draftAttachmentCount: files.length }
      }))
    },
    getActivities: async (projectId: number) => {
      const rows: any[] = []
      for (let pageNo = 1; ; pageNo++) {
        const page = await bridge.page({ projectId, pageNo, pageSize: 100 }); rows.push(...page.list)
        if (rows.length >= page.total || !page.list.length) return rows
      }
    },
    getActivity: async (id: number) => (await bridge.read(id)).value,
    createIndependent: async (data: AcceptanceApi.IndependentAcceptanceCreate, key: string) => {
      const id = await bridge.create({ ...data, $business: {
        expectedProjectVersion: data.expectedProjectVersion, expectedTreeVersion: data.expectedTreeVersion } }, key)
      return { acceptanceId: id, projectId: data.projectId, acceptanceType: data.acceptanceType, created: true }
    },
    createDraft: async (id: number, _content: AcceptanceApi.DraftContent, version: number) => {
      const { receipt } = await bridge.command('report-create', id, {}, version)
      return result(id, receipt.newConcurrencyBasis!)
    },
    updateDraft: async (id: number, reportId: number, content: AcceptanceApi.DraftContent, version: number) => {
      const { receipt } = await bridge.command('report-save', id, { reportId, reportNo: content.expectedReportVersionNo }, version)
      return result(id, receipt.newConcurrencyBasis!, reportId)
    },
    publishVersion: async (activity: AcceptanceApi.AcceptanceActivityVO, report: Pick<AcceptanceApi.AcceptanceReportVersionVO, 'id' | 'reportVersionNo'>, key: string) => {
      const { receipt } = await bridge.command('report-publish', activity.id, { reportId: report.id, reportNo: report.reportVersionNo,
        expectedCurrent: activity.currentReportVersionId }, activity.version, key)
      return result(activity.id, receipt.newConcurrencyBasis!, report.id)
    },
    revokeCurrentVersion: async (activity: AcceptanceApi.AcceptanceActivityVO, report: Pick<AcceptanceApi.AcceptanceReportVersionVO, 'id' | 'reportVersionNo'>, key: string) => {
      const { receipt } = await bridge.command('report-revoke', activity.id, { reportId: report.id, reportNo: report.reportVersionNo }, activity.version, key)
      return result(activity.id, receipt.newConcurrencyBasis!, report.id)
    }
  }
}
