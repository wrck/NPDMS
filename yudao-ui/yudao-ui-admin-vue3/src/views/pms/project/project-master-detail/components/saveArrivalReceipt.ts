import { generateUUID } from '@/utils'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import type { ArrivalVO } from '@/api/pms/engineering/arrival'
import { initializeUpload, completeUpload } from '@/api/pms/platform/file'

export interface ArrivalUploadAttempt {
  file: File
  initKey: string
  completeKey: string
  initialized?: Awaited<ReturnType<typeof initializeUpload>>
  completed?: Awaited<ReturnType<typeof completeUpload>>
  completedAt?: number
}
export const createArrivalUploadAttempt = (file: File): ArrivalUploadAttempt => ({
  file, initKey: generateUUID(), completeKey: generateUUID()
})

// Keep the saved owner and upload attempt on failure: retry completes the same
// receipt and file session rather than creating another receipt or file version.
export const saveArrivalReceipt = async (form: ArrivalVO, attempt?: ArrivalUploadAttempt,
  api: typeof ArrivalApi = ArrivalApi, additionalValues: Record<string, unknown> = {}) => {
  const creating = !form.id
  if (!form.id) {
    form.id = await api.createArrival({ ...form, ...additionalValues, attachmentUrl: '',
      arrivalTime: form.arrivalTime ?? Math.floor(Date.now() / 1000) * 1000 })
    Object.assign(form, await api.getArrival(form.id!))
  }
  if (attempt && !attempt.completed) {
    const mime: Record<string, string> = {
      pdf: 'application/pdf', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg',
      doc: 'application/msword', docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', txt: 'text/plain'
    }
    attempt.initialized ||= await initializeUpload({
      modeCode: form.signDocumentArtifactId ? 'ADD_VERSION' : 'CREATE_ARTIFACT',
      artifactId: form.signDocumentArtifactId ?? undefined,
      expectedReferenceVersion: form.signDocumentReferenceVersion ?? undefined,
      ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: String(form.id),
      purposeCode: 'ARRIVAL_SIGN_DOCUMENT', referenceKey: 'arrival-sign-document', categoryCode: 'ARRIVAL_SIGN_DOCUMENT',
      fileName: attempt.file.name, declaredSizeBytes: attempt.file.size,
      declaredMediaType: attempt.file.type || mime[attempt.file.name.split('.').pop()!.toLowerCase()]
    }, attempt.initKey)
    attempt.completed = await completeUpload(attempt.initialized.artifactId, attempt.initialized.sessionId, attempt.file, attempt.completeKey)
    attempt.completedAt = Math.floor(Date.now() / 1000) * 1000
  }
  // A retry can observe the already completed sign command after a lost response.
  const current: ArrivalVO = await api.getArrival(form.id!)
  form.signDocumentArtifactId = current.signDocumentArtifactId
  form.signDocumentVersionNo = current.signDocumentVersionNo
  form.signDocumentReferenceVersion = current.signDocumentReferenceVersion
  if (current.status === 1) { Object.assign(form, current); return }
  if (!current.signDocumentArtifactId) throw new Error('签收单上传尚未完成，请重试')
  if (attempt?.completedAt) form.arrivalTime = attempt.completedAt
  // A newly created receipt has already saved its extension form in the create transaction.
  await api.updateArrival({ ...form, ...(creating ? {} : additionalValues), version: current.version })
  const updated: ArrivalVO = await api.getArrival(form.id!)
  if (updated.status === 0) await api.signArrival(form.id!)
  Object.assign(form, await api.getArrival(form.id!))
}
