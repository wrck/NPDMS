import request from '@/config/axios'
import type { FileArtifactVO as OriginalArtifact, FileReferenceVO, FileBusinessKey, FileAccessOperation, FileAccessTicketVO, CursorPage, FileVersionVO } from './index'

export type FileWireId = string | number
export type FileArtifactVO = Omit<OriginalArtifact, 'artifactId' | 'reference'> & {
  artifactId: FileWireId
  reference: Omit<FileReferenceVO, 'referenceId' | 'artifactId'> & { referenceId: FileWireId; artifactId: FileWireId }
}
export type FileCurrentReferenceVO = Omit<FileReferenceVO, 'referenceId' | 'artifactId'> & {
  referenceId: FileWireId
  artifactId: FileWireId
}
export const getCurrentReference = (params: FileBusinessKey) =>
  request.get<FileCurrentReferenceVO | null>({ url: '/api/v1/pms/file-references/current', params })
// 冻结业务引用原样传输 Long 身份，避免 Number 转换损坏平台工件编号。
export const getArtifact = (artifactId: FileWireId, params: FileBusinessKey) =>
  request.get<FileArtifactVO>({ url: `/api/v1/pms/files/${artifactId}`, params })
export const getVersions = (artifactId: FileWireId, params: FileBusinessKey & { cursor?: string; pageSize?: number }) =>
  request.get<CursorPage<FileVersionVO>>({ url: `/api/v1/pms/files/${artifactId}/versions`, params })
export const createAccessTicket = (artifactId: FileWireId, versionNo: number, operationCode: FileAccessOperation, key: FileBusinessKey) =>
  request.post<FileAccessTicketVO>({ url: `/api/v1/pms/files/${artifactId}/access-tickets`, data: { versionNo, operationCode, ...key } })
