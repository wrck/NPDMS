import type { FileId } from '@/api/pms/platform/file'
import request from '@/config/axios'

// P06R 统一交付件承接后的 ACC 兼容契约：存储在平台 plt_delivery_*，
// FileSelection 只带 referenceId（上传返回引用，与统一材料登记一致），历史行渲染统一材料。
export interface ResultType { ownerContext: string; entityType: string; resultType: string }
export interface ResultTypeDescriptor { type: ResultType; currentLookup: boolean; exactLookup: boolean; historicalLookup: boolean }
export interface BusinessResult {
  tenantId: number
  projectId: number
  type: ResultType
  objectId: string
  resultId: string
  validity: string
  businessRevision?: string
  observationVersion?: string
  formedAt: string
}
export interface FileSelection { referenceId: FileId }
export interface MaterialLine {
  id: number
  materialKind: string
  referenceId?: FileId
  artifactId?: FileId
  versionNo?: number
  sha256?: string
  fileName?: string
  businessObjectType?: string
  businessObjectId?: string
}
export interface HistoryLine {
  id: number
  planVersionId?: number
  sourceType: string
  materials: MaterialLine[]
  submittedAt: string
}
export interface DeliverableDetail {
  id: number
  projectId: number
  code: string
  name: string
  status: string
  version: number
  planVersionId: number
  writable: boolean
  automaticSource?: string
  configuration: { minimumQuantity?: number; allowedSources?: string[]; outputType?: string; automaticSources?: string[] }
  history: HistoryLine[]
}
export interface Submission {
  planVersionId: number
  expectedVersion: number
  sourceType: string
  files: FileSelection[]
  businessResult?: Pick<BusinessResult, 'tenantId' | 'projectId' | 'type' | 'objectId' | 'resultId'>
}
export interface Submitted {
  submissionId: number
  sourceVersionId?: number
  status: string
  version?: number
  evaluation: { satisfied: boolean; reason: string; evidence?: string }
}
export const getDocumentSources = (): Promise<{ code: string; name: string }[]> => request.get({ url: '/api/v1/pms/project-document-sources' })
const base = (projectId: number, id: number) => `/api/v1/pms/projects/${projectId}/deliverables/${id}`
export const getDetail = (projectId: number, id: number): Promise<DeliverableDetail> => request.get({ url: base(projectId, id) })
export const getTypes = (projectId: number, id: number): Promise<ResultTypeDescriptor[]> => request.get({ url: `${base(projectId, id)}/result-types` })
export const getCandidates = (projectId: number, id: number, type: ResultType, after?: string): Promise<{
  nextCursor?: string; complete: boolean; observations: { result?: BusinessResult }[]
}> => request.get({ url: `${base(projectId, id)}/result-candidates`, params: { ...type, after } })
export const submit = (projectId: number, id: number, data: Submission, key: string): Promise<Submitted> =>
  request.post({ url: `${base(projectId, id)}/submissions`, data, headers: { 'Idempotency-Key': key } })
export const evaluate = (projectId: number, id: number): Promise<{ satisfied: boolean; reason: string; evidence?: string }> =>
  request.post({ url: `${base(projectId, id)}/evaluate` })
