import request from '@/config/axios'

export interface QuestionnaireOption {
  code: string
  label: string
}

export interface QuestionnaireQuestion {
  code: string
  title: string
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'RATING' | 'TEXT'
  required: boolean
  options?: QuestionnaireOption[]
  minSelections?: number
  maxSelections?: number
  minLength?: number
  maxLength?: number
}

export interface QuestionnaireDefinition {
  schemaVersion: number
  questions: QuestionnaireQuestion[]
}

export interface TemplateRevision {
  id: number
  revisionNo: number
  projectType: string
  signingMode: string
  implementationMode: string
  businessPurposeCode: string
  applicableTimingCode: string
  priority: number
  questionnaireJson: string
  threshold: number
  ruleVersion: string
  status: string
  version: number
}

export interface TemplateView {
  id: number
  templateCode: string
  name: string
  status: string
  currentRevisionId?: number
  version: number
  revisions: TemplateRevision[]
}

export interface TaskView {
  id: number
  projectId: number
  projectTaskId: number
  collectionKey: string
  revisionNo: number
  priorTaskId?: number
  assignedToUserId?: number
  status: string
  questionnaireId: number
  resultId?: number
  version: number
  questionnaireStatus: string
  templateRevisionId: number
}

export interface ResultView {
  resultId: number
  projectId: number
  projectTaskId: number
  taskId: number
  taskRevisionNo: number
  questionnaireId: number
  responseId: number
  resultVersion: number
  factVersion: number
  score: number
  threshold: number
  passed: boolean
  ruleVersion: string
  resultStatus: string
  archiveStatus: string
  effectiveFrom: string
  effectiveTo?: string
}

export interface PublicQuestionnaire {
  questionnaireId: number
  version: number
  frozenQuestions: string
  expiresAt: string
}

export interface GrantFileFact {
  policyKey: string
  fileSlotKey: string
  fileSequence: number
  fileFact: {
    artifactId: number
    versionNo: number
    referenceKey: string
    fileFactVersion: {
      artifactVersion: number
      referenceVersion: number
      availabilityVersion: number
    }
    scopeVersion: number
    sha256: string
  }
}

export interface AssistedResponseReservation {
  responseId: number
  taskId: number
  questionnaireId: number
  actorUserId: number
  replayed: boolean
}

export interface AssistedUploadInitialized {
  responseId: number
  fileSlotKey: string
  fileSequence: number
  artifactId: number
  sessionId: number
  scopeVersion: number
  expiresAt: string
}

export type AssistedFileFact = GrantFileFact

export interface SubmissionOutcome {
  responseId: number
  resultId: number
  score: number
  threshold: number
  passed: boolean
  responseReplayed: boolean
  resultReplayed: boolean
}

export interface ExportTask {
  taskId: number
  status: string
  failureRetryable: boolean
  retryCount: number
  version: number
  resultCount?: number
  artifactId?: number
  fileVersionNo?: number
  expiresAt?: string
}

export interface ResultDownloadFact {
  sourceSequence: number
  role: 'RESULT_DOCUMENT' | 'SIGNATURE' | 'ATTACHMENT'
  roleSequence: number
  file: {
    artifactId: number
    versionNo: number
    referenceKey: string
    fileFactVersion: {
      artifactVersion: number
      referenceVersion: number
      availabilityVersion: number
    }
    scopeVersion: number
    sha256: string
  }
}

const tenantHeaders = (tenantId: string | number) => ({ 'tenant-id': String(tenantId) })

export const listTemplates = () =>
  request.get<TemplateView[]>({ url: '/api/v1/pms/satisfaction-questionnaire-templates' })

export const createTemplate = (data: { templateCode: string; name: string }) =>
  request.post<TemplateView>({ url: '/api/v1/pms/satisfaction-questionnaire-templates', data })

export const createRevision = (
  templateId: number,
  data: Omit<TemplateRevision, 'id' | 'revisionNo' | 'status' | 'version'>
) =>
  request.post<TemplateRevision>({
    url: `/api/v1/pms/satisfaction-questionnaire-templates/${templateId}/revisions`,
    data
  })

export const publishRevision = (templateId: number, revision: TemplateRevision) =>
  request.post({
    url: `/api/v1/pms/satisfaction-questionnaire-templates/${templateId}/revisions/${revision.id}/actions/publish`,
    data: { expectedRevisionVersion: revision.version },
    headers: { 'Idempotency-Key': crypto.randomUUID() }
  })

export const listTasks = (projectId?: number) =>
  request.get<TaskView[]>({ url: '/api/v1/pms/satisfaction-tasks', params: { projectId } })

export const assignTask = (task: TaskView, assignedToUserId: number) =>
  request.post({
    url: `/api/v1/pms/satisfaction-tasks/${task.id}/actions/assign`,
    data: { assignedToUserId, expectedTaskVersion: task.version },
    headers: { 'Idempotency-Key': crypto.randomUUID() }
  })

export const createGrant = (taskId: number, expiresAt: number) =>
  request.post<{ grantId: number; grantVersion: number; token: string; expiresAt: string }>({
    url: `/api/v1/pms/satisfaction-tasks/${taskId}/access-grants`,
    data: { expiresAt }
  })

export const recollect = (taskId: number, data: Record<string, unknown>) =>
  request.post({ url: `/api/v1/pms/satisfaction-tasks/${taskId}/actions/recollect`, data })

export const submitAssisted = (taskId: number, data: Record<string, unknown>) =>
  request.post({ url: `/api/v1/pms/satisfaction-tasks/${taskId}/assisted-responses`, data })

export const reserveAssistedResponse = (taskId: number, requestId: string) =>
  request.post<AssistedResponseReservation>({
    url: `/api/v1/pms/satisfaction-tasks/${taskId}/assisted-response-reservations`,
    data: { requestId }
  })

export const initializeAssistedFile = (
  taskId: number,
  data: Record<string, unknown>
) =>
  request.post<AssistedUploadInitialized>({
    url: `/api/v1/pms/satisfaction-tasks/${taskId}/assisted-files`,
    data
  })

export const completeAssistedFile = (
  taskId: number,
  sessionId: number,
  metadata: Record<string, unknown>,
  file: File
) => {
  const data = new FormData()
  data.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
  data.append('file', file)
  return request.post<AssistedFileFact>({
    url: `/api/v1/pms/satisfaction-tasks/${taskId}/assisted-files/${sessionId}/complete`,
    data,
    headersType: 'multipart/form-data'
  })
}

export const listResults = (projectId?: number) =>
  request.get<ResultView[]>({ url: '/api/v1/pms/satisfaction-results', params: { projectId } })

export const invalidateResult = (result: ResultView, reasonCode: string, reasonSummary?: string) =>
  request.post({
    url: `/api/v1/pms/satisfaction-results/${result.resultId}/actions/invalidate`,
    data: { expectedResultVersion: result.factVersion, reasonCode, reasonSummary },
    headers: { 'Idempotency-Key': crypto.randomUUID() }
  })

export const getResultDownload = (resultId: number, sequence: number) =>
  request.get<ResultDownloadFact>({
    url: `/api/v1/pms/satisfaction-results/${resultId}/files/${sequence}/download`
  })

export const requestResultExport = (projectId: number, fields: string[], includeFiles: boolean) =>
  request.post<ExportTask>({
    url: '/api/v1/pms/satisfaction-results/exports',
    data: { projectId, fields, includeFiles },
    headers: { 'Idempotency-Key': crypto.randomUUID() }
  })

export const getExportTask = (taskId: number) =>
  request.get<ExportTask>({ url: `/api/v1/pms/export-tasks/${taskId}` })

export const retryExportTask = (task: ExportTask) =>
  request.post<ExportTask>({
    url: `/api/v1/pms/export-tasks/${task.taskId}/actions/retry`,
    data: { expectedVersion: task.version }
  })

export const getExportAccessTicket = (taskId: number) =>
  request.post<{ shortLivedUrl: string }>({
    url: `/api/v1/pms/export-tasks/${taskId}/access-ticket`
  })

export const inspectPublicQuestionnaire = (token: string, tenantId: string | number) =>
  request.get<PublicQuestionnaire>({
    url: `/api/v1/pms/satisfaction-questionnaires/${encodeURIComponent(token)}`,
    headers: tenantHeaders(tenantId)
  })

export const initializeGrantFile = (
  token: string,
  tenantId: string | number,
  data: Record<string, unknown>
) =>
  request.post<{
    responseId: number
    fileSlotKey: string
    fileSequence: number
    artifactId: number
    sessionId: number
    scopeVersion: number
  }>({
    url: `/api/v1/pms/satisfaction-questionnaires/${encodeURIComponent(token)}/files/initialize`,
    data,
    headers: tenantHeaders(tenantId)
  })

export const completeGrantFile = (
  token: string,
  tenantId: string | number,
  sessionId: number,
  metadata: Record<string, unknown>,
  file: File
) => {
  const data = new FormData()
  data.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
  data.append('file', file)
  return request.post<GrantFileFact>({
    url: `/api/v1/pms/satisfaction-questionnaires/${encodeURIComponent(token)}/files/${sessionId}/complete`,
    data,
    headersType: 'multipart/form-data',
    headers: tenantHeaders(tenantId)
  })
}

export const submitPublicResponse = (
  token: string,
  tenantId: string | number,
  data: Record<string, unknown>
) =>
  request.post<SubmissionOutcome>({
    url: `/api/v1/pms/satisfaction-questionnaires/${encodeURIComponent(token)}/responses`,
    data,
    headers: tenantHeaders(tenantId)
  })
