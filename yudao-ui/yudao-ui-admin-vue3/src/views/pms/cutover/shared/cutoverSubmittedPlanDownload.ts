import * as CutoverApi from '@/api/pms/cutover'
import * as FileApi from '@/api/pms/platform/file/version-reference'
import fileDownload from '@/utils/download'
import type { CutoverPlanContent, WireLong } from '@/api/pms/cutover'

export const downloadSubmittedPlanFile = async (query: { taskId: WireLong; planRevisionId: WireLong; content: CutoverPlanContent }) => {
  const target = query.content.editMode === 'FULL_FILE_UPLOAD' ? window.open('about:blank', '_blank') : null
  if (query.content.editMode === 'FULL_FILE_UPLOAD' && !target) throw new Error('请允许浏览器下载窗口后重试')
  if (target) target.opener = null
  try {
    const plan = await CutoverApi.getCutoverPlan(query.taskId)
    if (plan.status !== 'SUBMITTED' || !plan.content || plan.planVersion == null
      || String(plan.planRevisionId) !== String(query.planRevisionId) || plan.content.editMode !== query.content.editMode) {
      throw new Error('方案已变化，请刷新后下载')
    }
    if (plan.content.editMode === 'FULL_FILE_UPLOAD' && target) {
      const fact = plan.content.fileArtifactFact
      const ticket = await FileApi.createAccessTicket(fact.artifactId, fact.versionNo, 'DOWNLOAD', {
        ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: String(query.taskId), purposeCode: 'FULL_PLAN', referenceKey: fact.referenceKey
      })
      target.location.replace(ticket.shortLivedUrl)
    } else {
      const content = await CutoverApi.downloadSubmittedCutoverPlan({ taskId: query.taskId, planVersion: plan.planVersion })
      fileDownload.file(content, `割接方案-v${plan.revisionNo}.docx`)
    }
  } catch (error) { target?.close(); throw error }
}
