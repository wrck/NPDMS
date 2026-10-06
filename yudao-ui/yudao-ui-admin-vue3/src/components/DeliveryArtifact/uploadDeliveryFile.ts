import { initializeUpload, completeUpload } from '@/api/pms/platform/file'
import { registerMaterial, type EntityOwnerParam, type DeliveryMaterialVO, type DeliveryTypeVO } from '@/api/pms/platform/delivery'

/** One recoverable attempt shared by manual uploads and generated business documents. */
export interface DeliveryUploadAttempt {
  owner: EntityOwnerParam
  type: DeliveryTypeVO
  file: File
  title?: string
  sourceKind: 'UPLOAD' | 'GENERATED'
  referenceKey: string
  initKey: string
  completeKey: string
  initialized?: Awaited<ReturnType<typeof initializeUpload>>
  referenceId?: number
  material?: DeliveryMaterialVO
}
export const createDeliveryUploadAttempt = (owner: EntityOwnerParam, type: DeliveryTypeVO,
  file: File, title?: string, sourceKind: 'UPLOAD' | 'GENERATED' = 'UPLOAD'): DeliveryUploadAttempt => ({
  owner: { ...owner }, type: { ...type }, file, title, sourceKind,
  referenceKey: crypto.randomUUID(), initKey: crypto.randomUUID(), completeKey: crypto.randomUUID()
})

export const uploadDeliveryFile = async (attempt: DeliveryUploadAttempt): Promise<DeliveryMaterialVO> => {
  if (attempt.material) return attempt.material
  if (!attempt.referenceId) {
    const { file, owner, type } = attempt
    if (file.size <= 0 || file.size > type.maxSizeBytes) throw new Error('文件大小不符合所选交付件类型限制')
    attempt.initialized ||= await initializeUpload({
      modeCode: 'CREATE_ARTIFACT', ownerContext: 'PLT', objectType: 'DELIVERY_MATERIAL',
      objectId: `${owner.ownerModule}:${owner.entityType}:${owner.entityId}`,
      purposeCode: type.typeCode, referenceKey: attempt.referenceKey,
      fileName: file.name, categoryCode: type.category, declaredSizeBytes: file.size,
      declaredMediaType: file.type || 'application/octet-stream'
    }, attempt.initKey)
    const completed = await completeUpload(attempt.initialized.artifactId, attempt.initialized.sessionId,
      file, attempt.completeKey)
    attempt.referenceId = completed.referenceId
  }
  attempt.material = await registerMaterial({ ...attempt.owner, typeCode: attempt.type.typeCode,
    fileReferenceId: attempt.referenceId, title: attempt.title, sourceKind: attempt.sourceKind })
  return attempt.material
}
