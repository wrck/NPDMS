import request from '@/config/axios'

export interface ArchiveDocumentVO {
  id?: number
  projectId: number
  code?: string
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

// The business version and optimistic lock have different backend fields.
interface ArchiveDocumentResponse extends Omit<ArchiveDocumentVO, 'version'> {
  documentUrl?: string
  versionNo?: string
  version?: number
}
export const fromArchiveDocumentResponse = (row: ArchiveDocumentResponse): ArchiveDocumentVO => ({
  ...row, fileUrl: row.documentUrl, version: row.versionNo, versionNum: row.version
})
export const toArchiveDocumentRequest = (row: ArchiveDocumentVO) => ({
  id: row.id, projectId: row.projectId, name: row.name, documentType: row.documentType,
  documentUrl: row.fileUrl, versionNo: row.version, version: row.versionNum, remark: row.remark
})
const baseUrl = '/pms/acc-archive-document'

export const getArchiveDocumentPage = async (params: PmsProjectPageParam) => {
  const page = await request.get({ url: `${baseUrl}/page`, params })
  return { ...page, list: page.list.map(fromArchiveDocumentResponse) }
}
export const getArchiveDocument = async (id: number) =>
  fromArchiveDocumentResponse(await request.get({ url: `${baseUrl}/get`, params: { id } }))
export const createArchiveDocument = (data: ArchiveDocumentVO) =>
  request.post({ url: `${baseUrl}/create`, data: toArchiveDocumentRequest(data) })
export const updateArchiveDocument = (data: ArchiveDocumentVO) =>
  request.put({ url: `${baseUrl}/update`, data: toArchiveDocumentRequest(data) })
export const deleteArchiveDocument = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1待归档 2已归档
export const submitArchiveDocument = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const archiveArchiveDocument = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })

export const getArchiveDocumentFileTicket = (id: number, materialId: number) =>
  request.get<string>({ url: `/api/v1/pms/archive-documents/${id}/files/${materialId}` })
