import request from '@/config/axios'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'

export type FileUploadMode = 'CREATE_ARTIFACT' | 'ADD_VERSION'
export type FileAccessOperation = 'DOWNLOAD' | 'PREVIEW'
// Java Long values are serialized as strings; small numeric fixture IDs remain supported.
export type FileId = string | number

const requireFileId = (id: FileId): FileId => {
  if (typeof id === 'number' ? Number.isSafeInteger(id) && id > 0 : /^[1-9]\d*$/.test(id)) return id
  throw new Error('文件 ID 无效，请刷新后重试')
}

export interface FileBusinessKey {
  ownerContext: string
  objectType: string
  objectId: string
  purposeCode: string
  referenceKey: string
}

export interface FileUploadInitReqVO extends FileBusinessKey {
  ownerExecutionContext?: JsonObject
  modeCode: FileUploadMode
  artifactId?: FileId
  expectedReferenceVersion?: number
  fileName: string
  categoryCode: string
  declaredSizeBytes: number
  declaredMediaType: string
}

export interface FileUploadInitRespVO {
  artifactId: FileId
  sessionId: FileId
  expiresAt: string
}

export interface FileUploadCompleteRespVO {
  artifactId: FileId
  versionNo: number
  referenceId: FileId
  referenceKey: string
  sha256: string
}

export interface FileReferenceVO extends FileBusinessKey {
  referenceId: FileId
  artifactId: FileId
  versionNo: number
  sensitivityCode: string
  status: string
  scopeVersion: FileId
  referenceVersion: number
  createdAt: string
  updatedAt: string
}

export interface FileArtifactVO {
  artifactId: FileId
  name: string
  categoryCode: string
  ownerContext: string
  lifecycleStatus: string
  artifactVersion: number
  reference: FileReferenceVO
  allowedActions: string[]
  createdAt: string
}

export interface FileVersionVO {
  id: FileId
  versionNo: number
  sha256: string
  sizeBytes: number
  mediaType: string
  scanStatus: string
  availabilityStatus: string
  availabilityVersion: number
  unavailableReasonCode?: string
  versionNote?: string
  createdBy: FileId
  createdAt: string
}

export interface CursorPage<T> {
  items: T[]
  nextCursor?: string
  hasMore: boolean
}

export interface FileAccessTicketVO {
  grantId: FileId
  shortLivedUrl: string
  expiresAt: string
}

export interface FileLifecycleResultVO {
  artifactId: FileId
  versionNo?: number
  referenceId?: FileId
  factVersion: number
  status: string
}

const baseUrl = '/api/v1/pms'

export const initializeUpload = (data: FileUploadInitReqVO, idempotencyKey: string) =>
  request.post<FileUploadInitRespVO>({
    url: `${baseUrl}/files:init-upload`,
    data: { ...data, ...(data.artifactId !== undefined ? { artifactId: requireFileId(data.artifactId) } : {}) },
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const completeUpload = (
  artifactId: FileId,
  sessionId: FileId,
  file: File,
  idempotencyKey: string,
  onUploadProgress?: (progress: number) => void,
  ownerExecutionContext?: JsonObject
) => {
  const data = new FormData()
  data.append('sessionId', String(requireFileId(sessionId)))
  data.append('file', file)
  // Spring @RequestPart uses the JSON part's Content-Type for its standard message converter.
  // https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html
  if (ownerExecutionContext) data.append('ownerExecutionContext',
    new Blob([JSON.stringify(ownerExecutionContext)], { type: 'application/json' }))
  return request.post<FileUploadCompleteRespVO>({
    url: `${baseUrl}/files/${requireFileId(artifactId)}:complete-upload`,
    data,
    headersType: 'multipart/form-data',
    headers: { 'Idempotency-Key': idempotencyKey },
    onUploadProgress: (event: { loaded: number; total?: number }) => {
      if (event.total) onUploadProgress?.(Math.round((event.loaded / event.total) * 100))
    }
  })
}

export const getReference = (params: FileBusinessKey) =>
  request.get<FileReferenceVO | null>({ url: `${baseUrl}/file-references`, params })

export const getArtifact = (artifactId: FileId, params: FileBusinessKey) =>
  request.get<FileArtifactVO | null>({ url: `${baseUrl}/files/${requireFileId(artifactId)}`, params })

export const getVersions = (
  artifactId: FileId,
  params: FileBusinessKey & { cursor?: string; pageSize?: number }
) =>
  request.get<CursorPage<FileVersionVO>>({ url: `${baseUrl}/files/${requireFileId(artifactId)}/versions`, params })

export const createAccessTicket = (
  artifactId: FileId,
  versionNo: number,
  operationCode: FileAccessOperation,
  key: FileBusinessKey
) =>
  request.post<FileAccessTicketVO>({
    url: `${baseUrl}/files/${requireFileId(artifactId)}/access-tickets`,
    data: { versionNo, operationCode, ...key }
  })

export const detachReference = (
  referenceId: FileId,
  referenceVersion: number,
  key: FileBusinessKey,
  reason: string,
  idempotencyKey: string,
  ownerExecutionContext?: JsonObject
) =>
  request.delete<FileLifecycleResultVO>({
    url: `${baseUrl}/file-references/${requireFileId(referenceId)}`,
    data: { ...key, reason, ...(ownerExecutionContext ? { ownerExecutionContext } : {}) },
    headers: { 'If-Match': String(referenceVersion), 'Idempotency-Key': idempotencyKey }
  })
