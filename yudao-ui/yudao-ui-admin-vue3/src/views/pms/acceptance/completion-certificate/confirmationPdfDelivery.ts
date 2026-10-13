import CryptoJS from 'crypto-js'
import { getConfirmationFileTicket, getRegisteredConfirmationPdf, type CompletionCertificateVO } from '@/api/pms/acceptance/completion-certificate'
import type { ConfirmationDocumentKind } from './confirmationPrintForm'

export const pdfDigest = async (content: ArrayBuffer) =>
  CryptoJS.SHA256(CryptoJS.lib.WordArray.create(new Uint8Array(content))).toString(CryptoJS.enc.Hex)

interface StablePdfContext {
  pdf: { setFileId(id: string): unknown; setCreationDate(date: string): unknown }
  layoutKey: string
  certificate: CompletionCertificateVO
}
export const confirmationPdfIdentity = async ({ layout, certificate }: { layout: unknown; certificate: CompletionCertificateVO }) =>
  (await pdfDigest(new TextEncoder().encode(JSON.stringify({ layout, certificate })).buffer)).slice(0, 32)

export const stabilizeConfirmationPdf = async ({ pdf, layoutKey, certificate }: StablePdfContext) => {
  pdf.setFileId(layoutKey)
  // Keep generated-document metadata stable; the actual registration timestamp stays in the server audit.
  const created = new Date(certificate.createTime || certificate.completionDate || 0)
  pdf.setCreationDate(`D:${created.toISOString().replace(/[-:T]/g, '').slice(0, 14)}+00'00'`)
}

export const reuseConfirmationPdf = async ({ certificate, kind, layoutKey }: {
  certificate: CompletionCertificateVO
  kind: ConfirmationDocumentKind
  layoutKey: string
}): Promise<Blob | undefined> => {
  const existing = await getRegisteredConfirmationPdf({ id: certificate.id!, expectedVersion: certificate.version!, kind, layoutKey })
  if (!existing) return
  const ticket = await getConfirmationFileTicket(certificate.id!, existing.materialId)
  const response = await fetch(ticket)
  if (!response.ok) throw new Error('已登记PDF暂不可下载，请重新打开预览后重试')
  return response.blob()
}
