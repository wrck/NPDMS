import request from '@/config/axios'

const base = '/api/v1/pms/device-configuration-log-archives'
export type Identifier = string | number
export interface LogFile {
  id: Identifier
  deviceId?: Identifier
  serialNumber?: string
  deviceVersion?: number
  sourceType: 'MANUAL_UPLOAD' | 'COLLECTION_CALLBACK'
  fileName: string
  importId: Identifier
  materialId: Identifier
  archivedAt: number
  available: boolean
}
export interface DeviceLog {
  deviceId: Identifier
  serialNumber: string
  deviceName: string
  latest?: LogFile
}
export interface ImportResult {
  id: Identifier
  outcome: 'ARCHIVED' | 'REJECTED'
  rejectionReason?: string
  deviceCount: number
  redactedValueCount: number
  files: LogFile[]
}
export const latest = (projectId: Identifier) =>
  request.get<DeviceLog[]>({ url: `${base}/latest`, params: { projectId } })
export const history = (projectId: Identifier, deviceId: Identifier) =>
  request.get<LogFile[]>({ url: `${base}/history`, params: { projectId, deviceId } })
export const getImport = (id: Identifier) =>
  request.get<ImportResult>({ url: `${base}/imports/${id}` })
export const download = (id: Identifier) => request.get<string>({ url: `${base}/${id}/download` })
export const upload = (projectId: Identifier, operationId: string, file: File) => {
  return request.post<ImportResult>({
    url: `${base}/imports`,
    params: { projectId, operationId, fileName: file.name },
    data: file,
    headersType: 'application/octet-stream',
    timeout: 60000
  })
}
