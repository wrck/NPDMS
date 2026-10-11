import request from '@/config/axios'
import type { ParseResultEnvelope } from '@/components/DeviceCollection/semantic/types'

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
export interface ParseState {
  state: string
  taskId?: string
  parserVersion?: string
  conpVersion?: string
  message?: string
  deviceProjection?: { status: string; versionStatus: string; message: string }
}
export const parseState = (id: Identifier) =>
  request.get<ParseState>({ url: `${base}/${id}/parse` })
export interface ParseResult {
  state: string
  taskId?: string
  parserVersion?: string
  message?: string
  result?: ParseResultEnvelope
}
export const parseResult = (id: Identifier) =>
  request.get<ParseResult>({ url: `${base}/${id}/parse-result` })
export const upload = (projectId: Identifier, operationId: string, file: File) => {
  return request.post<ImportResult>({
    url: `${base}/imports`,
    params: { projectId, operationId, fileName: file.name },
    data: file,
    headersType: 'application/octet-stream',
    timeout: 60000
  })
}
