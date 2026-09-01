import request from '@/config/axios'

export type WireLong = number | string
export type WireDateTime = WireLong
export type CutoverStage = 'P2' | 'P3' | 'P4' | 'P5' | 'P6'
export type CutoverStatus =
  | 'GRADE_CONFIRMING'
  | 'SURVEYING'
  | 'PLAN_DRAFTING'
  | 'APPROVING'
  | 'CLOSURE_IN_PROGRESS'
  | 'ARCHIVED'
  | 'LEGACY_UNKNOWN'
export type ManualGrade = 'A' | 'B' | 'C' | 'D'

export interface DeviceScopeEntry {
  deviceId: WireLong
  serialNumber: string
  projectAssignmentVersion: WireLong
}

export interface ProjectContext {
  projectId: WireLong
  projectVersion: number
  projectCode: string
  projectName: string
  customerId: WireLong
  customerCode: string
  customerName: string
  officeDepartmentId: WireLong
  officeCode: string
  officeName: string
  projectScopeVersion: WireLong
}

export interface CustomerServiceLevelContext {
  status: 'AVAILABLE' | 'NOT_CONFIGURED'
  customerId: WireLong
  customerCode: string
  customerName: string
  serviceLevelRevisionId: WireLong | null
  serviceLevelCode: string | null
  factVersion: WireLong
  effectiveFrom: WireDateTime | null
  effectiveTo: WireDateTime | null
}

export interface ReadinessContext {
  snapshotId: WireLong
  snapshotVersion: WireLong
  decision: 'READY' | 'NOT_READY'
  projectId: WireLong
  deviceIds: WireLong[]
  sourceWatermark: unknown
  unmetCodes: string[]
}

export interface ConfigurationChoice {
  configurationCode: string
  configurationName: string
  revisionId: WireLong
  revisionNo: number
  effectiveFrom: WireDateTime
  effectiveTo: WireDateTime | null
}

export interface CreateContextCandidate {
  project: ProjectContext
  devices: DeviceScopeEntry[]
  customerServiceLevel: CustomerServiceLevelContext
  implementationReadiness: ReadinessContext
  createAllowed: boolean
}

export interface CreateContextData {
  candidates: CreateContextCandidate[]
  selectionRequired: boolean
  configurationChoices: ConfigurationChoice[]
  configurationSelectionRequired: boolean
}

export interface AssessmentAnswers {
  businessImportanceLevel: string | null
  operationComplexityLevel: string | null
  hiddenRiskLevel: string | null
  sparePartApplied: boolean | null
}

export interface AssessmentView {
  id: WireLong
  assessmentVersion: number
  rowVersion: number
  status: 'DRAFT' | 'SUBMITTED' | 'INVALIDATED'
  questionnaireTemplateCode: string
  questionnaireTemplateVersion: WireLong
  answers: AssessmentAnswers
  customerServiceLevel: CustomerServiceLevelContext
  manualGrade: ManualGrade | null
  simpleFlow: boolean
  submittedBy: WireLong | null
  submittedAt: WireDateTime | null
  invalidatedAt: WireDateTime | null
  invalidationReason: string | null
}

export interface CutoverTaskSummary {
  id: WireLong
  taskNo: string
  taskName: string
  taskOrigin: 'NEW_PLATFORM' | 'LEGACY_FORWARD'
  intakeSourceType: 'SELF_CREATED' | 'ITR' | 'PROJECT_EVENT' | 'LEGACY_FORWARD'
  configurationRevisionId: WireLong | null
  configurationCode: string | null
  configurationRevisionNo: number | null
  projectId: WireLong
  projectName: string
  officeCode: string | null
  officeName: string | null
  ownerUserId: WireLong | null
  currentStage: CutoverStage | null
  taskStatus: CutoverStatus
  manualGrade: ManualGrade | null
  scheduledTime: WireDateTime | null
  generatedAt: WireDateTime
  version: number
}

export interface CutoverTaskCore {
  id: WireLong
  taskNo: string
  taskName: string
  background: string | null
  taskOrigin: 'NEW_PLATFORM' | 'LEGACY_FORWARD'
  cutoverType: string | null
  networkMode: string | null
  configurationRevisionId: WireLong | null
  configurationCode: string | null
  configurationRevisionNo: number | null
  projectId: WireLong
  projectName: string
  ownerUserId: WireLong | null
  currentStage: CutoverStage | null
  taskStatus: CutoverStatus
  manualGrade: ManualGrade | null
  scheduledTime: WireDateTime | null
  createTime: WireDateTime
  version: number
}

export interface CutoverTaskDetail {
  task: CutoverTaskCore
  source: {
    intakeSourceType: CutoverTaskSummary['intakeSourceType']
    sourceSystem: string | null
    sourceBusinessNo: string | null
    businessEventId: string | null
    legacyTaskId: WireLong | null
  }
  project: {
    projectId: WireLong
    projectCode: string | null
    projectName: string | null
    projectScopeVersion: WireLong | null
  }
  devices: DeviceScopeEntry[]
  customerServiceLevel: CustomerServiceLevelContext | null
  implementationReadiness: ReadinessContext | null
  assessment: AssessmentView | null
  workbenchSteps: Array<{
    stage: 'P2' | 'P3' | 'P4' | 'P5' | 'P6'
    label: string
    state: 'CURRENT' | 'COMPLETED' | 'FUTURE'
    isCurrent: boolean
    isAccessible: boolean
  }>
  allowedActions: Array<
    | 'SAVE_ASSESSMENT'
    | 'SUBMIT_ASSESSMENT'
    | 'GENERATE_CHECKLIST'
    | 'SAVE_CHECKLIST'
    | 'REQUEST_COLLECTION'
    | 'SUBMIT_CHECKLIST'
  >
}

export interface ChecklistFileFactVersion {
  artifactVersion: number
  referenceVersion: number
  availabilityVersion: number
}

export interface ChecklistFileHandle {
  artifactId: WireLong
  versionNo: number
  referenceKey: string
  fileFactVersion: ChecklistFileFactVersion
  scopeVersion: WireLong
}

export interface CutoverChecklistResult {
  resultVersion: number
  resultSourceCode: 'DIRECT' | 'MANUAL' | 'COLLECTION' | 'EXTERNAL'
  answerSnapshot: string
  factDescription: string | null
  manualEvidenceFileReference: string | null
  collectionTaskId: WireLong | null
  collectionResultReferenceId: WireLong | null
  collectionResultVersion: WireLong | null
  loadFailureCode: string | null
}

export interface CutoverChecklistItem {
  itemId: WireLong
  stableItemKey: string
  itemTypeCode: string
  itemName: string
  itemDescription: string | null
  interfaceFormatCode: string
  interfaceSchemaSnapshot: string | null
  workModeCode: 'DIRECT' | 'COLLECTION' | 'EXTERNAL'
  required: boolean
  sourceCode: 'SYSTEM_MATCHED' | 'CUSTOM'
  applicable: boolean
  sortOrder: number
  currentResult: CutoverChecklistResult | null
}

export interface CutoverChecklistView {
  taskId: WireLong
  taskStage: string
  taskVersion: number
  projectScopeVersion: WireLong
  checklistId: WireLong
  checklistVersion: number
  checklistFactVersion: number
  status: 'DRAFT' | 'SUBMITTED' | 'INVALIDATED'
  inputSnapshotHash: string
  configRevisionSnapshot: string
  matchTrace: string
  configGapSnapshot: string
  items: CutoverChecklistItem[]
}

export interface CutoverPlanFileFactVersion {
  artifactVersion: number
  referenceVersion: number
  availabilityVersion: number
}

export interface CutoverPlanFileFact {
  artifactId: WireLong
  versionNo: number
  referenceKey: string
  fileFactVersion: CutoverPlanFileFactVersion
  scopeVersion: WireLong
  sha256: string
}

export interface CutoverPlanDeviceSnapshot {
  deviceId: WireLong
  serialNumber: string
  projectAssignmentVersion: WireLong
  deviceTypeCode: string
  deviceTypeSourceVersion: string
}

export interface CutoverPlanRiskFact {
  checklistItemId: WireLong
  stableItemKey: string
  itemResultVersion: number
  itemName: string
  resultCode: 'FAILED' | 'NO' | 'NOT_PASSED'
  factDescription: string
}

export interface CutoverPlanSourceSnapshot {
  snapshotVersion: number
  taskId: WireLong
  taskVersion: number
  assessmentId: WireLong
  assessmentVersion: number
  grade: ManualGrade
  checklistId: WireLong | null
  checklistVersion: number | null
  projectId: WireLong
  projectVersion: number
  projectScopeVersion: WireLong
  devices: CutoverPlanDeviceSnapshot[]
  configurationRevisionId: WireLong
  configurationCode: string
  configurationRevisionNo: number
  templateSections: Array<{
    stableSectionKey: string
    title: string
    sortOrder: number
    cutoverTypeCodes: string[]
    levelCodes: ManualGrade[]
    required: boolean
  }>
  failedRiskFacts: CutoverPlanRiskFact[]
}

export interface LegacyCutoverPlanSourceSnapshot {
  sourceTable: 'pms_cut_plan'
  sourceId: WireLong
  sourceTenantId: WireLong
  sourceTaskId: WireLong
  sourceVersion: number
  sourceStatusRaw: number
  mappingVersion: 'FCUT004_LEGACY_V1'
  code: string
  name: string
  level: ManualGrade
  remark: string | null
}

export type CutoverPlanSectionCode =
  | 'PRE_OPERATION'
  | 'OPERATION'
  | 'CLOSING_COLLECTION'
  | 'POST_BUSINESS_TEST'
  | 'ROLLBACK'
  | 'POST_CUTOVER_SUPPORT'

export interface CutoverPlanStep {
  sectionCode: CutoverPlanSectionCode
  stepNo: number
  content: string
}

export interface CutoverPlanScheduleRow {
  sequenceNo: number
  plannedAt: WireDateTime
  content: string
}

export interface CutoverPlanSupportArrangement {
  arrangementId: WireLong | null
  roleCode: 'CUSTOMER' | 'DP_FIRST_LINE' | 'DP_SECOND_LINE' | 'DP_RND'
  personName: string
  dutyDescription: string
  phone: string
  arrivalTime: WireDateTime
}

export interface StandardCutoverPlanContent {
  editMode: 'ONLINE_TEMPLATE_STANDARD'
  overview: {
    projectDescription: string
    scheduleTable: CutoverPlanScheduleRow[]
    preTopologyFile: CutoverPlanFileFact | null
    postTopologyFile: CutoverPlanFileFact | null
    deviceSummary: CutoverPlanDeviceSnapshot[]
    networkConfigurationFile: CutoverPlanFileFact | null
  }
  steps: CutoverPlanStep[]
  riskMitigations: Array<{ riskFact: CutoverPlanRiskFact; mitigation: string }>
  supportArrangements: CutoverPlanSupportArrangement[]
}

export interface SimpleCutoverPlanContent {
  editMode: 'ONLINE_TEMPLATE_SIMPLE_D'
  steps: CutoverPlanStep[]
}

export interface UploadedCutoverPlanContent {
  editMode: 'FULL_FILE_UPLOAD'
  fileArtifactFact: CutoverPlanFileFact
  ownershipConfirmed: true
}

export interface LegacyCutoverPlanContent {
  editMode: 'LEGACY_READ_ONLY'
  steps: CutoverPlanStep[]
}

export type WritableCutoverPlanContent =
  | StandardCutoverPlanContent
  | SimpleCutoverPlanContent
  | UploadedCutoverPlanContent

export type CutoverPlanContent = WritableCutoverPlanContent | LegacyCutoverPlanContent
export type CutoverPlanAllowedAction =
  | 'CREATE_DRAFT'
  | 'SAVE_DRAFT'
  | 'DOWNLOAD_DRAFT'
  | 'SUBMIT_PLAN'
  | 'REVISE_PLAN'
  | 'UPDATE_APPROVED_CONTACTS'

export interface CutoverPlanView {
  taskId: WireLong
  taskStage: 'P4' | 'P5' | 'P6' | null
  taskVersion: number
  planRevisionId: WireLong | null
  revisionNo: number | null
  planVersion: number | null
  originCode: 'NEW_PLATFORM' | 'LEGACY_FORWARD' | null
  status: 'DRAFT' | 'SUBMITTED' | 'INVALIDATED' | 'LEGACY_READ_ONLY' | null
  legacyPlanId: WireLong | null
  legacyStatusRaw: number | null
  sourcePlanRevisionId: WireLong | null
  revisionReason: 'INITIAL' | 'APPROVAL_REJECTED' | 'DUTY_CHANGED' | 'SOURCE_REPLACED' | null
  sourceSnapshot: CutoverPlanSourceSnapshot | LegacyCutoverPlanSourceSnapshot | null
  content: CutoverPlanContent | null
  approvalFact: {
    approvalInstanceId: WireLong
    approvalVersion: number
    status: 'PENDING' | 'PAUSED_SOURCE_INVALIDATED' | 'APPROVED' | 'REJECTED'
    decisionAt: WireDateTime | null
    rejectionReason: string | null
  } | null
  allowedActions: CutoverPlanAllowedAction[]
}

export type CreateCutoverPlanDraftRequest =
  | { editMode: 'ONLINE_TEMPLATE_STANDARD' }
  | { editMode: 'ONLINE_TEMPLATE_SIMPLE_D' }
  | UploadedCutoverPlanContent

export interface DownloadCutoverPlanDraftResult {
  planRevisionId: WireLong
  planVersion: number
  fileArtifactFact: CutoverPlanFileFact
  downloadedAt: WireDateTime
}

export interface SubmitCutoverPlanResult {
  taskId: WireLong
  taskStage: 'P5'
  taskVersion: number
  planRevisionId: WireLong
  revisionNo: number
  planVersion: number
  approvalInstanceId: WireLong
  approvalVersion: number
  approvalStatus: 'PENDING'
}

export type CutoverApprovalAction = 'APPROVE' | 'REJECT' | 'REASSIGN'
export type CutoverApprovalNodeCode = 'INITIATOR' | 'SERVICE_MANAGER' | 'SECOND_LINE' | 'RND'
export type CutoverApprovalNodeStatus =
  | 'WAITING'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
export type CutoverApprovalStatus =
  | 'PENDING'
  | 'PAUSED_SOURCE_INVALIDATED'
  | 'APPROVED'
  | 'REJECTED'

export interface CutoverApprovalReviewItem {
  itemCode: 'PREPARATION' | 'BUSINESS_TEST' | 'EXECUTION' | 'ROLLBACK' | 'OTHER'
  decision: 'YES' | 'NO'
  unreasonableReason: string | null
}

export interface CutoverApprovalAssessmentReview {
  decision: 'CONFIRMED' | 'NOT_REASONABLE'
  reason: string | null
}

export interface CutoverApprovalChecklistResultSnapshot {
  checklistItemId: WireLong
  stableItemKey: string
  itemDefinitionId: WireLong | null
  itemDefinitionVersion: number | null
  itemTypeCode: 'RISK' | 'DUAL_MACHINE_CHECK' | 'BUSINESS_SURVEY'
  itemName: string
  required: boolean
  itemResultVersion: number
  resultSourceCode: 'DIRECT' | 'COLLECTION' | 'EXTERNAL' | 'MANUAL'
  answerSnapshot: string
  factDescription: string | null
  collectionTaskId: WireLong | null
  collectionResultReferenceId: WireLong | null
  collectionResultVersion: WireLong | null
  externalSourceCode: string | null
  manualEvidenceFileReference: string | null
}

export interface CutoverApprovalNode {
  nodeId: WireLong
  nodeNo: number
  nodeCode: CutoverApprovalNodeCode
  status: CutoverApprovalNodeStatus
  originalApproverUserId: WireLong | null
  currentApproverUserId: WireLong | null
  decisionAt: WireDateTime | null
  feedback: string | null
  reviewItems: CutoverApprovalReviewItem[]
  assessmentReview: CutoverApprovalAssessmentReview | null
}

export interface CutoverApprovalSourceSnapshot {
  snapshotVersion: number
  taskId: WireLong
  taskVersion: number
  checklistId: WireLong | null
  checklistVersion: number | null
  project: ProjectContext
  collectionAnalysis: {
    cutoverType: string
    networkMode: string | null
    scheduledTime: WireDateTime
  }
  riskItems: CutoverApprovalChecklistResultSnapshot[]
  businessSurveyItems: CutoverApprovalChecklistResultSnapshot[]
  assessment: {
    assessmentId: WireLong
    assessmentVersion: number
    questionnaireTemplateCode: 'CUT_P2_MANUAL_ASSESSMENT'
    questionnaireTemplateVersion: WireLong
    businessImportanceLevel: string
    operationComplexityLevel: string
    hiddenRiskLevel: string
    sparePartApplied: boolean
    customerServiceLevelCode: string
    manualGrade: ManualGrade
    submittedBy: WireLong
    submittedAt: WireDateTime
  }
  plan: {
    planRevisionId: WireLong
    planRevisionNo: number
    planVersion: number
    originCode: 'NEW_PLATFORM'
    sourceSnapshot: CutoverPlanSourceSnapshot
    content: CutoverPlanContent
  }
}

export interface CutoverApprovalDetail {
  viewMode: 'FULL'
  approvalInstanceId: WireLong
  approvalVersion: number
  taskId: WireLong
  taskVersion: number
  planRevisionId: WireLong
  planRevisionNo: number
  grade: ManualGrade
  status: CutoverApprovalStatus
  holdReason: 'ROUTE_CANDIDATE_NOT_UNIQUE' | 'APPROVER_UNAVAILABLE' | null
  currentNodeNo: number | null
  nodes: CutoverApprovalNode[]
  sourceSnapshot: CutoverApprovalSourceSnapshot
  decisionAt: WireDateTime | null
  rejectionReason: string | null
  allowedActions: CutoverApprovalAction[]
}

export interface CutoverApprovalFinalResult {
  viewMode: 'FINAL_RESULT_ONLY'
  approvalInstanceId: WireLong
  taskId: WireLong
  planRevisionId: WireLong
  grade: ManualGrade
  status: 'APPROVED' | 'REJECTED'
  decisionAt: WireDateTime
  rejectionReason: string | null
  allowedActions: []
}

export interface CutoverApprovalReassignmentNode {
  nodeId: WireLong
  nodeNo: number
  nodeCode: CutoverApprovalNodeCode
  nodeStatus: 'WAITING' | 'PENDING'
  currentApproverUserId: WireLong | null
  nodeVersion: number
}

export interface CutoverApprovalReassignmentView {
  viewMode: 'REASSIGNMENT_ONLY'
  approvalInstanceId: WireLong
  approvalVersion: number
  taskId: WireLong
  projectId: WireLong
  taskCode: string
  taskName: string
  grade: ManualGrade
  status: 'PENDING'
  holdReason: 'ROUTE_CANDIDATE_NOT_UNIQUE' | 'APPROVER_UNAVAILABLE' | null
  nodes: CutoverApprovalReassignmentNode[]
  allowedActions: ['REASSIGN']
}

export type CutoverApprovalView =
  | CutoverApprovalDetail
  | CutoverApprovalFinalResult
  | CutoverApprovalReassignmentView

export interface CutoverApprovalDecisionRequest {
  action: 'APPROVE' | 'REJECT'
  reviewItems: CutoverApprovalReviewItem[]
  assessmentReview: CutoverApprovalAssessmentReview | null
  feedback: string
}

export interface CutoverApprovalReassignmentCandidate {
  approvalInstanceId: WireLong
  approvalVersion: number
  taskId: WireLong
  projectId: WireLong
  taskCode: string
  taskName: string
  grade: ManualGrade
  status: 'PENDING'
  holdReason: 'ROUTE_CANDIDATE_NOT_UNIQUE' | 'APPROVER_UNAVAILABLE' | null
  nodeId: WireLong
  nodeNo: number
  nodeCode: CutoverApprovalNodeCode
  nodeStatus: 'WAITING' | 'PENDING'
  currentApproverUserId: WireLong | null
  nodeVersion: number
  createdAt: WireDateTime
}

export interface CutoverApprovalReassignmentCandidatePage {
  list: CutoverApprovalReassignmentCandidate[]
  total: WireLong
  pageNo: number
  pageSize: number
}

export interface CutoverApprovalTodoItem {
  approvalInstanceId: WireLong
  approvalVersion: number
  taskId: WireLong
  projectId: WireLong
  taskCode: string
  taskName: string
  grade: ManualGrade
  nodeNo: number
  nodeCode: CutoverApprovalNodeCode
  createdAt: WireDateTime
}

export interface CutoverApprovalTodoPage {
  list: CutoverApprovalTodoItem[]
  total: WireLong
  pageNo: number
  pageSize: number
}

export interface CutoverTaskPage {
  list: CutoverTaskSummary[]
  total: WireLong
}

export interface CreateCutoverTaskRequest {
  projectId: WireLong
  configurationCode: string
  serialNumbers: string[]
  taskName: string
  background: string
  cutoverType: string
  networkMode: string | null
  scheduledTime: WireDateTime
  expectedProjectContext: Omit<ProjectContext, 'projectScopeVersion'>
  expectedProjectScopeVersion: WireLong
  expectedDeviceScopeWatermark: DeviceScopeEntry[]
  expectedReadinessSnapshotId: WireLong
  expectedReadinessSnapshotVersion: WireLong
  expectedCustomerServiceLevelStatus: CustomerServiceLevelContext['status']
  expectedCustomerServiceLevelRevisionId: WireLong | null
  expectedCustomerServiceLevelCode: string | null
  expectedCustomerServiceLevelFactVersion: WireLong
  expectedCustomerServiceLevelEffectiveFrom: WireDateTime | null
  expectedCustomerServiceLevelEffectiveTo: WireDateTime | null
}

export type CutoverClosureAction =
  | 'CREATE_CLOSURE'
  | 'SAVE_CLOSURE'
  | 'REQUEST_COLLECTION'
  | 'LINK_MANUAL_RESULT'
  | 'SUBMIT_CLOSURE'
export type CutoverClosureStage = 'PRE_CHECK' | 'EXECUTION' | 'TEST' | 'ROLLBACK' | 'POST_COLLECTION'
export type CutoverClosureFilePurpose =
  | 'POST_COLLECTION_CHECKLIST'
  | 'IMPLEMENTATION_COMMITMENT'
  | 'OTHER_EVIDENCE'
  | 'MANUAL_COLLECTION_RESULT'

export interface CutoverClosureFileFact {
  purposeCode: CutoverClosureFilePurpose
  artifactId: WireLong
  versionNo: number
  referenceKey: string
  fileFactVersion: { artifactVersion: number; referenceVersion: number; availabilityVersion: number }
  scopeVersion: WireLong
  sha256: string
}

export interface CutoverClosureContent {
  preCheckNormal: boolean | null
  preCheckDetail: string | null
  executionNormal: boolean | null
  executionDetail: string | null
  testNormal: boolean | null
  testDetail: string | null
  rollbackOccurred: boolean | null
  rollbackSuccessful: boolean | null
  rollbackReason: string | null
  legacyItems: string | null
  finalResult: 'SUCCESS' | 'FAILED' | null
  attachments: CutoverClosureFileFact[]
}

export interface CutoverClosureEvidence {
  evidenceId: WireLong
  deviceId: WireLong
  collectionStage: CutoverClosureStage
  evidenceType: 'DISPATCH_ACCEPTED' | 'DISPATCH_FAILED' | 'CALLBACK_SUCCEEDED' | 'CALLBACK_FAILED' | 'MANUAL_UPLOAD'
  collectionTaskId: string
  callbackEventId: string | null
  resultRef: string | null
  resultVersion: string | null
  originalFailedCollectionTaskId: string | null
  manualFile: CutoverClosureFileFact | null
  occurredAt: WireDateTime
}

export interface CutoverClosureView {
  taskId: WireLong
  taskStage: 'P6'
  taskStatus: 'CLOSURE_IN_PROGRESS' | 'ARCHIVED'
  taskVersion: number
  closureId: WireLong | null
  closureVersion: number | null
  closureStatus: 'DRAFT' | 'SUBMITTED' | null
  approvalInstanceId: WireLong
  approvalVersion: number
  planRevisionId: WireLong
  planRevisionNo: number
  planVersion: number
  content: CutoverClosureContent | null
  collectionEvidence: CutoverClosureEvidence[]
  resultRef: string | null
  submittedBy: WireLong | null
  submittedAt: WireDateTime | null
  archivedAt: WireDateTime | null
  allowedActions: CutoverClosureAction[]
}

export type CutoverClosureCollectionRequest =
  | {
      authenticationMode: 'SAVED_CREDENTIAL'
      deviceId: WireLong
      collectionStage: CutoverClosureStage
      credentialId: WireLong
      credentialVersion: WireLong
      templateCode: string
      templateVersion: WireLong
    }
  | {
      authenticationMode: 'TRANSIENT_CREDENTIAL'
      deviceId: WireLong
      collectionStage: CutoverClosureStage
      loginName: string
      transientSecret: string
      saveAsCredential: boolean
      templateCode: string
      templateVersion: WireLong
    }

export interface LinkCutoverClosureManualResultRequest {
  originalFailedCollectionTaskId: string
  deviceId: WireLong
  collectionStage: CutoverClosureStage
  file: CutoverClosureFileFact
}

const baseUrl = '/api/v1/pms/cutover-tasks'

export const resolveCreateContext = (serialNumbers: string[]) =>
  request.post<CreateContextData>({
    url: `${baseUrl}/actions/resolve-create-context`,
    data: { serialNumbers }
  })

export const getCutoverTaskPage = (params: {
  projectId?: WireLong
  taskStatus?: CutoverStatus
  currentStage?: CutoverStage
  pageNo: number
  pageSize: number
}) => request.get<CutoverTaskPage>({ url: baseUrl, params })

export const getCutoverTaskDetail = (id: WireLong) =>
  request.get<CutoverTaskDetail>({ url: `${baseUrl}/${id}` })

export const createCutoverTask = (data: CreateCutoverTaskRequest, idempotencyKey: string) =>
  request.post({ url: baseUrl, data, headers: { 'Idempotency-Key': idempotencyKey } })

export const saveCutoverAssessment = (
  id: WireLong,
  taskVersion: number,
  assessmentVersion: number,
  data: { answers: AssessmentAnswers; manualGrade: ManualGrade | null }
) =>
  request.put({
    url: `${baseUrl}/${id}/assessment`,
    data,
    headers: { 'If-Match': String(taskVersion), 'Assessment-If-Match': String(assessmentVersion) }
  })

export const submitCutoverAssessment = (
  id: WireLong,
  taskVersion: number,
  assessmentVersion: number,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${id}/assessment/actions/submit`,
    data: {},
    headers: {
      'Idempotency-Key': idempotencyKey,
      'If-Match': String(taskVersion),
      'Assessment-If-Match': String(assessmentVersion)
    }
  })

export const getCutoverChecklist = (taskId: WireLong) =>
  request.get<CutoverChecklistView>({ url: `${baseUrl}/${taskId}/checklist` })

export const generateCutoverChecklist = (
  taskId: WireLong,
  data: {
    expectedTaskVersion: number
    expectedAssessmentVersion: number
    expectedProjectScopeVersion: WireLong
    selectedConflictDefinitions: Record<
      string,
      { itemDefinitionId: WireLong; itemDefinitionVersion: number }
    >
  },
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${taskId}/checklist/actions/generate`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const saveCutoverChecklist = (
  taskId: WireLong,
  data: {
    expectedTaskVersion: number
    expectedProjectScopeVersion: WireLong
    checklistId: WireLong
    expectedChecklistVersion: number
    answers: Array<{ stableItemKey: string; answerSnapshot: string }>
  }
) => request.put({ url: `${baseUrl}/${taskId}/checklist`, data })

export const addCustomChecklistItem = (
  taskId: WireLong,
  data: {
    expectedTaskVersion: number
    expectedProjectScopeVersion: WireLong
    checklistId: WireLong
    expectedChecklistVersion: number
    itemTypeCode: string
    itemName: string
    itemDescription: string
    interfaceFormatCode: string
    interfaceSchema: string
    required: boolean
    answerSnapshot: string | null
  }
) => request.post({ url: `${baseUrl}/${taskId}/checklist/custom-items`, data })

export const removeCustomChecklistItem = (
  taskId: WireLong,
  stableItemKey: string,
  data: {
    expectedTaskVersion: number
    expectedProjectScopeVersion: WireLong
    checklistId: WireLong
    expectedChecklistVersion: number
  }
) =>
  request.delete({
    url: `${baseUrl}/${taskId}/checklist/custom-items/${encodeURIComponent(stableItemKey)}`,
    data
  })

export const requestChecklistCollection = (
  taskId: WireLong,
  stableItemKey: string,
  data: {
    expectedTaskVersion: number
    expectedProjectScopeVersion: WireLong
    checklistId: WireLong
    expectedChecklistVersion: number
    deviceId: WireLong
    commandTemplateId: WireLong
  },
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${taskId}/checklist/items/${encodeURIComponent(stableItemKey)}/collection-requests`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const saveManualChecklistResult = (
  taskId: WireLong,
  stableItemKey: string,
  data: {
    expectedTaskVersion: number
    expectedProjectScopeVersion: WireLong
    checklistId: WireLong
    expectedChecklistVersion: number
    file: ChecklistFileHandle
    factDescription: string
  }
) =>
  request.post({
    url: `${baseUrl}/${taskId}/checklist/items/${encodeURIComponent(stableItemKey)}/manual-results`,
    data
  })

export const submitCutoverChecklist = (
  taskId: WireLong,
  data: {
    expectedTaskVersion: number
    expectedAssessmentVersion: number
    expectedProjectScopeVersion: WireLong
    checklistId: WireLong
    expectedChecklistVersion: number
  },
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/${taskId}/checklist/actions/submit`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const getCutoverPlan = (taskId: WireLong) =>
  request.get<CutoverPlanView>({ url: `${baseUrl}/${taskId}/plan` })

export const createCutoverPlanDraft = (
  taskId: WireLong,
  taskVersion: number,
  data: CreateCutoverPlanDraftRequest,
  idempotencyKey: string
) =>
  request.post<CutoverPlanView>({
    url: `${baseUrl}/${taskId}/plan/actions/create-draft`,
    data,
    headers: { 'X-Task-Version': String(taskVersion), 'Idempotency-Key': idempotencyKey }
  })

export const saveCutoverPlanDraft = (
  taskId: WireLong,
  taskVersion: number,
  planVersion: number,
  data: WritableCutoverPlanContent,
  idempotencyKey: string
) =>
  request.put<CutoverPlanView>({
    url: `${baseUrl}/${taskId}/plan`,
    data,
    headers: {
      'If-Match': String(planVersion),
      'X-Task-Version': String(taskVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const downloadCutoverPlanDraft = (
  taskId: WireLong,
  planVersion: number,
  idempotencyKey: string
) =>
  request.post<DownloadCutoverPlanDraftResult>({
    url: `${baseUrl}/${taskId}/plan/actions/download-draft`,
    data: {},
    headers: { 'If-Match': String(planVersion), 'Idempotency-Key': idempotencyKey }
  })

export const submitCutoverPlan = (
  taskId: WireLong,
  taskVersion: number,
  planVersion: number,
  idempotencyKey: string
) =>
  request.post<SubmitCutoverPlanResult>({
    url: `${baseUrl}/${taskId}/plan/actions/submit`,
    data: {},
    headers: {
      'If-Match': String(planVersion),
      'X-Task-Version': String(taskVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const patchApprovedCutoverPlanContact = (
  taskId: WireLong,
  arrangementId: WireLong,
  planVersion: number,
  data: { personName: string; phone: string; arrivalTime: WireDateTime },
  idempotencyKey: string
) =>
  request.put<CutoverPlanView>({
    method: 'PATCH',
    url: `${baseUrl}/${taskId}/plan/support-arrangements/${arrangementId}`,
    data,
    headers: { 'If-Match': String(planVersion), 'Idempotency-Key': idempotencyKey }
  })

export const reviseCutoverPlan = (
  taskId: WireLong,
  taskVersion: number,
  data: {
    sourcePlanRevisionId: WireLong
    reason: 'APPROVAL_REJECTED' | 'SOURCE_REPLACED'
  },
  idempotencyKey: string
) =>
  request.post<CutoverPlanView>({
    url: `${baseUrl}/${taskId}/plan/actions/revise`,
    data,
    headers: { 'X-Task-Version': String(taskVersion), 'Idempotency-Key': idempotencyKey }
  })

export const getCutoverClosure = (taskId: WireLong) =>
  request.get<CutoverClosureView>({ url: `${baseUrl}/${taskId}/closure` })

export const saveCutoverClosure = (
  taskId: WireLong,
  taskVersion: number,
  closureVersion: number | null,
  data: CutoverClosureContent,
  idempotencyKey: string
) =>
  request.put<CutoverClosureView>({
    url: `${baseUrl}/${taskId}/closure`,
    data,
    headers: {
      ...(closureVersion === null ? {} : { 'If-Match': String(closureVersion) }),
      'X-Task-Version': String(taskVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const requestCutoverClosureCollection = (
  taskId: WireLong,
  taskVersion: number,
  closureVersion: number,
  data: CutoverClosureCollectionRequest,
  idempotencyKey: string
) =>
  request.post<CutoverClosureView>({
    url: `${baseUrl}/${taskId}/closure/actions/request-collection`, data,
    headers: { 'If-Match': String(closureVersion), 'X-Task-Version': String(taskVersion), 'Idempotency-Key': idempotencyKey }
  })

export const linkCutoverClosureManualResult = (
  taskId: WireLong,
  taskVersion: number,
  closureVersion: number,
  data: LinkCutoverClosureManualResultRequest,
  idempotencyKey: string
) =>
  request.post<CutoverClosureView>({
    url: `${baseUrl}/${taskId}/closure/actions/link-manual-result`, data,
    headers: { 'If-Match': String(closureVersion), 'X-Task-Version': String(taskVersion), 'Idempotency-Key': idempotencyKey }
  })

export const submitCutoverClosure = (
  taskId: WireLong,
  taskVersion: number,
  closureVersion: number,
  finalResult: 'SUCCESS' | 'FAILED',
  idempotencyKey: string
) =>
  request.post<CutoverClosureView>({
    url: `${baseUrl}/${taskId}/closure/actions/submit`, data: { finalResult },
    headers: { 'If-Match': String(closureVersion), 'X-Task-Version': String(taskVersion), 'Idempotency-Key': idempotencyKey }
  })

export const getCutoverApproval = (taskId: WireLong) =>
  request.get<CutoverApprovalView>({ url: `${baseUrl}/${taskId}/approval` })

export const getCutoverApprovalTodos = (params: { pageNo: number; pageSize: number }) =>
  request.get<CutoverApprovalTodoPage>({
    url: '/api/v1/pms/cutover-approvals/todos',
    params
  })

export const getCutoverApprovalReassignmentCandidates = (params: {
  pageNo: number
  pageSize: number
}) =>
  request.get<CutoverApprovalReassignmentCandidatePage>({
    url: '/api/v1/pms/cutover-approvals/reassignment-candidates',
    params
  })

export const approveCutoverApproval = (
  taskId: WireLong,
  approvalVersion: number,
  taskVersion: number,
  data: CutoverApprovalDecisionRequest,
  idempotencyKey: string
) =>
  request.post<CutoverApprovalDetail>({
    url: `${baseUrl}/${taskId}/approval-actions/approve`,
    data,
    headers: {
      'If-Match': String(approvalVersion),
      'X-Task-Version': String(taskVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const rejectCutoverApproval = (
  taskId: WireLong,
  approvalVersion: number,
  taskVersion: number,
  data: CutoverApprovalDecisionRequest,
  idempotencyKey: string
) =>
  request.post<CutoverApprovalDetail>({
    url: `${baseUrl}/${taskId}/approval-actions/reject`,
    data,
    headers: {
      'If-Match': String(approvalVersion),
      'X-Task-Version': String(taskVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const reassignCutoverApproval = (
  taskId: WireLong,
  approvalVersion: number,
  data: { nodeNo: number; newApproverUserId: WireLong; reason: string },
  idempotencyKey: string
) =>
  request.post<CutoverApprovalReassignmentView>({
    url: `${baseUrl}/${taskId}/approval-actions/reassign`,
    data,
    headers: { 'If-Match': String(approvalVersion), 'Idempotency-Key': idempotencyKey }
  })
