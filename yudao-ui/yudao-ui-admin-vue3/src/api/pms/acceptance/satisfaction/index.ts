import { generateUUID } from '@/utils'
import request from '@/config/axios'
import { customerConfirmationBaseUrl } from '@/api/pms/customerConfirmation'

export interface QuestionnaireOption {
  code: string
  label: string
  score?: number | string
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

export interface ResultView {
  resultId: number
  projectId: number
  projectTaskId: number | null
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
  archiveStatus: string | null
  effectiveFrom: string
  effectiveTo?: string
}

export interface PublicQuestionnaire {
  submitted?: boolean
  answerSnapshot?: string
  signatureImageDataUrl?: string
  signedAt?: string
  questionnaireId: number
  version: number
  frozenQuestions: string
  expiresAt: string
  completionCertificate?: CompletionCertificateSnapshot
}

export interface CompletionCertificateSnapshot {
  id: number
  version: number
  projectId: number
  code?: string
  name?: string
  certificateNo?: string
  customerUnit?: string
  contractNo?: string
  serviceType?: string
  engineerName?: string
  engineerContact?: string
  itemArrival?: string
  itemInstall?: string
  itemCutover?: string
  itemTraining?: string
  itemDocs?: string
  arrivalDate?: string | null
  completionDate?: string
  devices?: Array<{ deviceType?: string; deviceModel?: string; quantity: number }>
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

export interface SubmissionOutcome {
  responseId: number
  resultId: number
  score: number
  threshold: number
  passed: boolean
  responseReplayed: boolean
  resultReplayed: boolean
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
    headers: { 'Idempotency-Key': generateUUID() }
  })

export const listResults = (projectId?: number) =>
  request.get<ResultView[]>({ url: '/api/v1/pms/satisfaction-results', params: { projectId } })

export const getResultDownload = (resultId: number, sequence: number) =>
  request.get<ResultDownloadFact>({
    url: `/api/v1/pms/satisfaction-results/${resultId}/files/${sequence}/download`
  })

export const inspectPublicQuestionnaire = (token: string, tenantId: string | number) =>
  request.get<PublicQuestionnaire>({
    url: `/api/v1/pms/satisfaction-questionnaires/${encodeURIComponent(token)}`,
    baseURL: customerConfirmationBaseUrl(),
    headers: { ...tenantHeaders(tenantId), isToken: false }
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
    baseURL: customerConfirmationBaseUrl(),
    headers: { ...tenantHeaders(tenantId), isToken: false }
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
    baseURL: customerConfirmationBaseUrl(),
    headers: { ...tenantHeaders(tenantId), isToken: false }
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
    baseURL: customerConfirmationBaseUrl(),
    headers: { ...tenantHeaders(tenantId), isToken: false }
  })
