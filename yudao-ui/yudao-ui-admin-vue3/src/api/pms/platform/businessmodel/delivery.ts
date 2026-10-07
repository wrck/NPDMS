import request from '@/config/axios'
import { createAccessTicket, type FileBusinessKey } from '@/api/pms/platform/file'
export interface DeliveryScope {
  projectId: string | number
  businessType: string
  businessEntityKey: string | number
  deliverableType: string
}
export interface DeliveryRecord extends DeliveryScope {
  id: string; title: string; fileName: string; fileReferenceId: string; fileArtifactId: string
  fileVersionNo: number; uploadedAt: string | number; version: number; status: string; ownerModule: string; entityType: string
}
const base = '/api/v1/pms/business-models'
export const deliveryContext = (owner: string, type: string, entityId: string | number) =>
  request.get<Omit<DeliveryScope, 'deliverableType'>>({ url: `${base}/${owner}/${type}/deliverables/context`, params: { entityId }, silentError: true })
export const uploadDelivery = (scope: DeliveryScope, file: File, key: string) => {
  const data = new FormData()
  Object.entries(scope).forEach(([name, value]) => data.append(name, String(value)))
  data.append('file', file)
  return request.post<DeliveryRecord>({ url: `${base}/deliverables/upload`, data, headersType: 'multipart/form-data', headers: { 'Idempotency-Key': key }, silentError: true })
}
export const listDeliveries = (params: { projectId: string | number; deliverableType?: string; businessType?: string; businessEntityKey?: string | number; pageNo?: number; pageSize?: number }) =>
  request.get<{ list: DeliveryRecord[]; total: number }>({ url: `${base}/deliverables`, params, silentError: true })
export const deliveryCompletion = (params: DeliveryScope) =>
  request.get<{ completed: boolean; latest?: DeliveryRecord }>({ url: `${base}/deliverables/completion`, params, silentError: true })
export const getDelivery = (id: string) => request.get<DeliveryRecord>({ url: `${base}/deliverables/${id}`, silentError: true })
export const editDelivery = (row: DeliveryRecord, title: string) =>
  request.put<DeliveryRecord>({ url: `${base}/deliverables/${row.id}`, data: { version: row.version, title }, silentError: true })
export const deleteDelivery = (row: DeliveryRecord) =>
  request.delete<boolean>({ url: `${base}/deliverables/${row.id}`, params: { version: row.version }, silentError: true })
export const downloadDelivery = async (row: DeliveryRecord) => {
  const key = await request.get<FileBusinessKey>({ url: `${base}/deliverables/${row.id}/file`, silentError: true })
  return createAccessTicket(row.fileArtifactId, row.fileVersionNo, 'DOWNLOAD', { ownerContext: key.ownerContext, objectType: key.objectType, objectId: key.objectId, purposeCode: key.purposeCode, referenceKey: key.referenceKey })
}
