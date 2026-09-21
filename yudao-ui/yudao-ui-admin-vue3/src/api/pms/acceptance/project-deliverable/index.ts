import request from '@/config/axios'

export interface ResultType { ownerContext: string; entityType: string; resultType: string }
export interface BusinessResult {
  tenantId: number
  projectId: number
  type: ResultType
  objectId: string
  resultId: string
  validity: string
  businessRevision?: string
  formedAt: string
}
export interface FileSelection { artifactId: number; versionNo: number; referenceKey: string }
export interface BusinessDocument extends FileSelection { referenceId: number; ownerContext: string; objectType: string; objectId: string; purposeCode: string; name: string }
export const getDocumentSources = (): Promise<{ code: string; name: string }[]> => request.get({ url: '/api/v1/pms/project-document-sources' })
export interface DeliverableDetail {
  id: number
  projectId: number
  code: string
  name: string
  status: string
  version: number
  planVersionId: number
  writable: boolean
  automaticSource?: 'ACCEPTANCE_REPORT' | 'SATISFACTION_RESULT'
  configuration: { minimumQuantity: number; allowedSources: string[]; outputType: string; automaticSources?: string[] }
  history: { id: number; sourceVersionId: number; sourceType: string; submittedAt: string;
    source: { files: (FileSelection & { name: string })[]; businessResult?: BusinessResult; businessFiles?: BusinessDocument[] } }[]
}
export interface Submission {
  planVersionId: number
  expectedVersion: number
  sourceType: string
  files: FileSelection[]
  businessResult?: Pick<BusinessResult, 'tenantId' | 'projectId' | 'type' | 'objectId' | 'resultId'>
}
const base = (projectId: number, id: number) => `/api/v1/pms/projects/${projectId}/deliverables/${id}`
export const getDetail = (projectId: number, id: number): Promise<DeliverableDetail> => request.get({ url: base(projectId, id) })
export const getTypes = (projectId: number, id: number): Promise<{ type: ResultType }[]> => request.get({ url: `${base(projectId, id)}/result-types` })
export const getCandidates = (projectId: number, id: number, type: ResultType, after?: string): Promise<{
  nextCursor?: string; complete: boolean; observations: { result?: BusinessResult }[]
}> => request.get({ url: `${base(projectId, id)}/result-candidates`, params: { ...type, after } })
export const submit = (projectId: number, id: number, data: Submission, key: string): Promise<{
  status: string; evaluation: { satisfied: boolean; reason: string }
}> => request.post({ url: `${base(projectId, id)}/submissions`, data, headers: { 'Idempotency-Key': key } })
export const evaluate = (projectId: number, id: number): Promise<{ satisfied: boolean; reason: string }> =>
  request.post({ url: `${base(projectId, id)}/evaluate` })
