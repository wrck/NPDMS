import request from '@/config/axios'

export interface ArchiveDocumentVO {
  id?: number
  projectId: number
  code: string
  name: string
  documentType?: string
  version?: string
  fileUrl?: string
  fileChecksum?: string
  uploadedBy?: number
  uploadedDate?: Date
  status?: number
  remark?: string
  versionNum?: number
  createTime?: Date
}

const baseUrl = '/pms/acc-archive-document'

export const getArchiveDocumentPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getArchiveDocument = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createArchiveDocument = (data: ArchiveDocumentVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateArchiveDocument = (data: ArchiveDocumentVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteArchiveDocument = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1待归档 2已归档
export const submitArchiveDocument = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const archiveArchiveDocument = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
