import request, { writeHeaders } from './request'
import type { WireLong } from './request'
export type { WireLong } from './request'

const base = '/api/v1/pms/inspection-tasks'

export interface AttachmentView {
  artifactId: WireLong
  versionNo: number
  referenceKey: string
  name: string
  sizeBytes?: WireLong
  availabilityStatus: string
}

export const attachments = (taskId: WireLong) =>
  request.get<AttachmentView[]>({ url: `${base}/${taskId}/attachments` })

export interface TaskSummary {
  id: WireLong
  version: WireLong
  taskCode: string
  taskName: string
  projectId: WireLong
  projectCode?: string
  projectName: string
  customerId?: WireLong
  customerName: string
  ownerUserId: WireLong
  ownerName?: string
  creatorName?: string
  background: string
  plannedAt?: number
  mode: 'ONLINE' | 'OFFLINE'
  statusCode: string
  currentAttemptId?: WireLong
  currentReportId?: WireLong
  archiveRequestedAt?: number
  archiveRequestedBy?: WireLong
  archivedAt?: number
  archivedBy?: WireLong
  customerArchiveId?: WireLong
  cancellationReason?: string
  createTime?: number
}

export interface DeviceView {
  id: WireLong
  deviceId: WireLong
  serialNumber?: string
  deviceName?: string
  productTypeCode?: string
  productTypeName?: string
  productModel?: string
  contractNo?: string
}

export interface SelectionView {
  deviceId: WireLong
  ruleRevisionId?: WireLong
}

export interface AttemptView {
  id: WireLong
  attemptNo?: number
  mode: 'ONLINE' | 'OFFLINE'
  statusCode: string
  reason?: string
  startedBy?: WireLong
  startedAt?: number
  finishedAt?: number
}

export interface ExecutionView {
  id: WireLong
  version: WireLong
  attemptId?: WireLong
  deviceId: WireLong
  purposeCode?: string
  statusCode: string
  fileName?: string
  artifactId?: WireLong
  fileVersionNo?: number
  fileReferenceKey?: string
  softwareVersion?: string
  parserTaskId?: string
  parserRelease?: string
  recognizedSerial?: string
  failureCode?: string
  failureMessage?: string
  collectionExecutionId?: WireLong
  collectionTaskId?: string
  collectionResultVersion?: number
  collectionStatus?: string
  collectionFailureCategory?: string
  submittedBy?: WireLong
  protocol?: string
  host?: string
  port?: number
  username?: string
  credentialId?: WireLong
  precheckExecutionId?: WireLong
  submittedAt?: number
  completedAt?: number
  precheckExpiresAt?: number
  deviceStartedAt?: number
  deviceCompletedAt?: number
}

export interface ResultView {
  id: WireLong
  attemptId?: WireLong
  executionId?: WireLong
  deviceId: WireLong
  ruleRevisionId?: WireLong
  ruleName?: string
  categoryCode?: string
  categoryName?: string
  severityCode?: string
  severityName?: string
  outcome?: string
  measuredValue?: number | string
  measuredUnit?: string
  regexMatched?: boolean
  outputSummary?: string
  detailCode?: string
  detailMessage?: string
}

export interface ReportFileView {
  id: WireLong
  formatCode?: string
  fileName?: string
  artifactId?: WireLong
  fileVersionNo?: number
  referenceKey?: string
}

export interface ReportView {
  id: WireLong
  attemptId?: WireLong
  revisionNo?: number
  sourceCode?: string
  statusCode: string
  templateVersion?: string
  explanation?: string
  failureMessage?: string
  generatedAt?: number
  generatedBy?: WireLong
  files?: ReportFileView[]
  resultIds?: WireLong[]
}

export interface AnnotationView {
  id: WireLong
  reportId?: WireLong
  resultId?: WireLong
  revisionNo?: number
  kind?: string
  reason?: string
  changeReason?: string
  responsibleUserId?: WireLong
  dueAt?: number
  annotatedBy?: WireLong
  annotatedAt?: number
}

export interface IssueView {
  id: WireLong
  version: WireLong
  taskId: WireLong
  reportId?: WireLong
  resultId?: WireLong
  responsibleUserId?: WireLong
  responsibleName?: string
  dueAt?: number
  description?: string
  severityCode?: string
  statusCode: string
  closureDescription?: string
  closureRequestedBy?: WireLong
  closureRequestedAt?: number
  closedBy?: WireLong
  closedAt?: number
}

export interface RemediationView {
  id: WireLong
  issueId?: WireLong
  actionCode?: string
  content?: string
  actorId?: WireLong
  actorName?: string
  actionAt?: number
}

export interface EventView {
  id: WireLong
  actionCode?: string
  fromStatus?: string
  toStatus?: string
  actorId?: WireLong
  actorName?: string
  description?: string
  occurredAt?: number
}

export interface TaskDetail extends TaskSummary {
  devices: DeviceView[]
  selections: SelectionView[]
  attempts: AttemptView[]
  executions: ExecutionView[]
  collectionReceipts: {
    id: WireLong
    executionId: WireLong
    collectionExecutionId: WireLong
    collectionTaskId: string
    resultVersion: WireLong
    fileVersionId: WireLong
    externalStatus: string
    failureCategory?: string
    receivedAt: number
  }[]
  results: ResultView[]
  reports: ReportView[]
  annotations: AnnotationView[]
  issues: IssueView[]
  remediations: RemediationView[]
  evidence: EvidenceView[]
  events: EventView[]
  ruleFeedback: {
    id: WireLong
    deviceId?: WireLong
    description: string
    reportedBy: WireLong
    reportedName?: string
    reportedAt: number
  }[]
  manager: boolean
  owner: boolean
}
export interface CreateTaskInput {
  taskName: string
  projectId: WireLong
  deviceIds: WireLong[]
  background: string
  plannedAt?: number
  mode: 'ONLINE' | 'OFFLINE'
}
export interface PreparationInput {
  taskName: string
  background: string
  plannedAt?: number
  mode: 'ONLINE' | 'OFFLINE'
  rules: { deviceId: WireLong; ruleRevisionId: WireLong }[]
}
export const getTaskPage = (params: {
  pageNo: number
  pageSize: number
  keyword?: string
  projectId?: WireLong
  mode?: 'ONLINE' | 'OFFLINE'
  statusCode?: string
}) => request.get<{ list: TaskSummary[]; total: number }>({ url: base, params })
export const getTask = (id: WireLong) =>
  request.get<TaskDetail>({ url: `${base}/${id}`, silentError: true })
export const createTask = (data: CreateTaskInput, key: string) =>
  request.post<TaskSummary>({ url: base, data, headers: writeHeaders(key) })
export const savePreparation = (
  id: WireLong,
  data: PreparationInput,
  version: WireLong,
  key: string
) =>
  request.put<TaskSummary>({
    url: `${base}/${id}/preparation`,
    data,
    headers: writeHeaders(key, version)
  })
export const getProjectOptions = (keyword: string) =>
  request.get<{ id: WireLong; code: string; name: string; customerName: string }[]>({
    url: `${base}/project-options`,
    params: { keyword }
  })
export const getDeviceOptions = (projectId: WireLong) =>
  request.get<{ id: WireLong; serialNumber: string; name: string; productModel: string }[]>({
    url: `${base}/device-options`,
    params: { projectId }
  })
export const taskAction = (
  id: WireLong,
  action: string,
  data: unknown,
  version: WireLong,
  key: string
) =>
  request.post<TaskSummary>({
    url: `${base}/${id}/actions/${action}`,
    data,
    headers: writeHeaders(key, version)
  })
export const reportMissingRule = (
  id: WireLong,
  data: { deviceId?: WireLong; description: string },
  version: WireLong,
  key: string
) =>
  request.post<TaskSummary>({
    url: `${base}/${id}/rule-feedback`,
    data,
    headers: writeHeaders(key, version)
  })

export interface FileReference {
  artifactId: WireLong
  versionNo: number
  referenceKey: string
}
export interface EvidenceView {
  id: WireLong
  remediationId: WireLong
  artifactId: WireLong
  fileVersionNo: number
  referenceKey: string
  fileName: string
}
export interface IssueDetail extends IssueView {
  taskCode: string
  taskName: string
  taskStatus: string
  remediations: RemediationView[]
  evidence: EvidenceView[]
  responsible: boolean
  canConfirm: boolean
}
export interface IssueSummary extends IssueView {
  taskCode: string
  taskName: string
}
export interface TaskWorkbench {
  taskCounts: { statusCode: string; count: number }[]
  pendingTasks: TaskSummary[]
  createdTasks: TaskSummary[]
  myIssues: IssueSummary[]
}
export interface CommandView {
  sequenceNo: number
  ruleRevisionId: WireLong
  detectionId: string
  inspectionItem: string
  revisionNo: number
  stableCommandKey: string
  content: string
  executionOrder: number
  timeoutSeconds: number
  continueOnTimeout: boolean
  expectedRegex: string
  expectedThreshold: string
}
export interface AnnotationInput {
  reportId: WireLong
  resultId: WireLong
  kind: 'FALSE_POSITIVE' | 'IGNORE' | 'TRACK'
  reason: string
  changeReason?: string
  responsibleUserId?: WireLong
  dueAt?: number
}

export const getTaskWorkbench = () => request.get<TaskWorkbench>({ url: `${base}/workbench` })
export const getCommands = (taskId: WireLong, deviceId: WireLong) =>
  request.get<CommandView[]>({ url: `${base}/${taskId}/commands`, params: { deviceId } })
export const uploadOfflineLog = (
  taskId: WireLong,
  data: {
    attemptId: WireLong
    deviceId: WireLong
    file: File
    commandHint?: string
  },
  version: WireLong,
  key: string
) => {
  const form = new FormData()
  form.append('attemptId', String(data.attemptId))
  form.append('deviceId', String(data.deviceId))
  form.append('file', data.file)
  if (data.commandHint) form.append('commandHint', data.commandHint)
  return request.post<TaskSummary>({
    url: `${base}/${taskId}/offline-logs`,
    data: form,
    headers: { ...writeHeaders(key, version), 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}
export const generateReport = (
  taskId: WireLong,
  explanation: string,
  version: WireLong,
  key: string
) =>
  request.post<TaskSummary>({
    url: '/api/v1/pms/inspection-reports/actions/generate',
    data: { taskId, explanation },
    headers: writeHeaders(key, version),
    timeout: 180000
  })
export const submitManualReport = (
  taskId: WireLong,
  data: {
    explanation: string
    files: { formatCode: 'PDF' | 'DOCX' | 'DOC' | 'XML'; file: FileReference }[]
  },
  version: WireLong,
  key: string
) =>
  request.post<TaskSummary>({
    url: '/api/v1/pms/inspection-reports/actions/submit-manual',
    data: { taskId, ...data },
    headers: writeHeaders(key, version)
  })
export const annotateResult = (
  taskId: WireLong,
  data: AnnotationInput,
  version: WireLong,
  key: string
) =>
  request.post<TaskSummary>({
    url: `${base}/${taskId}/annotations`,
    data,
    headers: writeHeaders(key, version)
  })
export const getIssue = (issueId: WireLong) =>
  request.get<IssueDetail>({ url: `/api/v1/pms/service-issues/${issueId}` })
export const issueAction = (
  issueId: WireLong,
  action: 'record-process' | 'request-closure' | 'confirm-closure' | 'return-closure',
  data: { content?: string; description?: string; evidence?: FileReference[] },
  version: WireLong,
  key: string
) =>
  request.post<IssueView>({
    url: `/api/v1/pms/service-issues/${issueId}/actions/${action}`,
    data,
    headers: writeHeaders(key, version)
  })
