import { getDeliveryTypes } from '@/api/pms/platform/delivery'
import { initializeUpload, completeUpload, createAccessTicket } from '@/api/pms/platform/file'
import { createDeliveryUploadAttempt, type DeliveryUploadAttempt } from '@/components/DeliveryArtifact/uploadDeliveryFile'

export type ScriptSection = 'network' | 'businessModule' | 'ha' | 'full'
export interface PendingScript { section: ScriptSection; file: File; attempt?: DeliveryUploadAttempt; completed?: ScriptReference }
export interface ScriptReference {
  section: ScriptSection; name: string; referenceId: number; artifactId: number
  versionNo: number; referenceKey: string; sha256: string
}
export const downloadSolutionScript = async (input: { solutionId: number; reference: ScriptReference }) => {
  const ticket = await createAccessTicket(input.reference.artifactId, input.reference.versionNo, 'DOWNLOAD', {
    ownerContext: 'PLT', objectType: 'DELIVERY_MATERIAL', objectId: `SOL:solution:${input.solutionId}`,
    purposeCode: 'IMPLEMENTATION_PLAN', referenceKey: input.reference.referenceKey
  })
  window.open(ticket.shortLivedUrl, '_blank', 'noopener')
}
export const saveSolutionScripts = async (input: { solutionId: number; pending: PendingScript[] }): Promise<ScriptReference[]> => {
  if (!input.solutionId) throw new Error('请先保存真实方案草稿')
  const type = (await getDeliveryTypes(true)).find(item => item.typeCode === 'IMPLEMENTATION_PLAN')
  if (!type) throw new Error('实施方案文件类型不可用')
  const references: ScriptReference[] = []
  for (const item of input.pending) {
    if (!/\.txt$/i.test(item.file.name) || !item.file.size || item.file.size > type.maxSizeBytes)
      throw new Error('请上传大小符合限制的非空 TXT 脚本文件')
    if (!item.attempt || item.attempt.owner.entityId !== input.solutionId) {
      item.attempt = createDeliveryUploadAttempt({ ownerModule: 'SOL', entityType: 'solution', entityId: input.solutionId }, type, item.file)
      item.attempt.referenceKey = `solution-script-${item.section}-${item.attempt.referenceKey}`
      item.completed = undefined
    }
    const attempt = item.attempt
    attempt.initialized ||= await initializeUpload({ modeCode: 'CREATE_ARTIFACT', ownerContext: 'PLT', objectType: 'DELIVERY_MATERIAL',
      objectId: `SOL:solution:${input.solutionId}`, purposeCode: 'IMPLEMENTATION_PLAN', referenceKey: attempt.referenceKey,
      fileName: item.file.name, categoryCode: type.category, declaredSizeBytes: item.file.size, declaredMediaType: 'text/plain' }, attempt.initKey)
    if (!item.completed) {
      const completed = await completeUpload(attempt.initialized.artifactId, attempt.initialized.sessionId, item.file, attempt.completeKey)
      item.completed = { ...completed, section: item.section, name: item.file.name }
    }
    references.push(item.completed)
  }
  return references
}
