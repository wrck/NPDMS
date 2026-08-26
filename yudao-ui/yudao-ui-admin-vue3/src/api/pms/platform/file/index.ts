import request from '@/config/axios'

export type FileUploadMode = 'CREATE_ARTIFACT' | 'ADD_VERSION'
export type FileAccessOperation = 'DOWNLOAD' | 'PREVIEW'

export interface FileBusinessKey {
  ownerContext: string
  objectType: string
  objectId: string
  purposeCode: string
  referenceKey: string
}

export interface FileUploadInitReqVO extends FileBusinessKey {
  modeCode: FileUploadMode
  artifactId?: number
  expectedReferenceVersion?: number
  fileName: string
  categoryCode: string
  declaredSizeBytes: number
  declaredMediaType: string
}

export interface FileUploadInitRespVO {
  artifactId: number
  sessionId: number
  expiresAt: string
}

export interface FileUploadCompleteRespVO {
  artifactId: number
  versionNo: number
  referenceId: number
  referenceKey: string
  sha256: string
}

export interface FileReferenceVO extends FileBusinessKey {
  referenceId: number
  artifactId: number
  versionNo: number
  sensitivityCode: string
  status: string
  scopeVersion: number
  referenceVersion: number
  createdAt: string
  updatedAt: string
}

export interface FileArtifactVO {
  artifactId: number
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
  id: number
  versionNo: number
  sha256: string
  sizeBytes: number
  mediaType: string
  scanStatus: string
  availabilityStatus: string
  availabilityVersion: number
  unavailableReasonCode?: string
  versionNote?: string
  createdBy: number
  createdAt: string
}

export interface CursorPage<T> {
  items: T[]
  nextCursor?: string
  hasMore: boolean
}

export interface FileAccessTicketVO {
  grantId: number
  shortLivedUrl: string
  expiresAt: string
}

export interface FileLifecycleResultVO {
  artifactId: number
  versionNo?: number
  referenceId?: number
  factVersion: number
  status: string
}

const baseUrl = '/api/v1/pms'

export const initializeUpload = (data: FileUploadInitReqVO, idempotencyKey: string) =>
  request.post<FileUploadInitRespVO>({
    url: `${baseUrl}/files:init-upload`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const completeUpload = (
  artifactId: number,
  sessionId: number,
  file: File,
  idempotencyKey: string,
  onUploadProgress?: (progress: number) => void
) => {
  const data = new FormData()
  data.append('sessionId', String(sessionId))
  data.append('file', file)
  return request.post<FileUploadCompleteRespVO>({
    url: `${baseUrl}/files/${artifactId}:complete-upload`,
    data,
    headersType: 'multipart/form-data',
    headers: { 'Idempotency-Key': idempotencyKey },
    onUploadProgress: (event: { loaded: number; total?: number }) => {
      if (event.total) onUploadProgress?.(Math.round((event.loaded / event.total) * 100))
    }
  })
}

export const getArtifact = (artifactId: number, params: FileBusinessKey) =>
  request.get<FileArtifactVO>({ url: `${baseUrl}/files/${artifactId}`, params })

export const getVersions = (
  artifactId: number,
  params: FileBusinessKey & { cursor?: string; pageSize?: number }
) =>
  request.get<CursorPage<FileVersionVO>>({ url: `${baseUrl}/files/${artifactId}/versions`, params })

export const createAccessTicket = (
  artifactId: number,
  versionNo: number,
  operationCode: FileAccessOperation,
  key: FileBusinessKey
) =>
  request.post<FileAccessTicketVO>({
    url: `${baseUrl}/files/${artifactId}/access-tickets`,
    data: { versionNo, operationCode, ...key }
  })

export const detachReference = (
  referenceId: number,
  referenceVersion: number,
  key: FileBusinessKey,
  reason: string,
  idempotencyKey: string
) =>
  request.delete<FileLifecycleResultVO>({
    url: `${baseUrl}/file-references/${referenceId}`,
    data: { ...key, reason },
    headers: { 'If-Match': String(referenceVersion), 'Idempotency-Key': idempotencyKey }
  })
