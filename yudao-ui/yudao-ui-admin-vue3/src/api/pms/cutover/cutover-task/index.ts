import request from '@/config/axios'

export type WireLong = number | string
export type WireDateTime = WireLong
export type CutoverStage = 'P2' | 'P3' | 'P4'
export type CutoverStatus = 'GRADE_CONFIRMING' | 'SURVEYING' | 'PLAN_DRAFTING' | 'LEGACY_UNKNOWN'
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
  unmetCodes: string[]
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
  allowedActions: Array<'SAVE_ASSESSMENT' | 'SUBMIT_ASSESSMENT'>
}

export interface CutoverTaskPage {
  list: CutoverTaskSummary[]
  total: WireLong
}

export interface CreateCutoverTaskRequest {
  projectId: WireLong
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

const baseUrl = '/api/v1/pms/cutover-tasks'

export const resolveCreateContext = (serialNumbers: string[]) =>
  request.post<CreateContextData>({ url: `${baseUrl}/actions/resolve-create-context`, data: { serialNumbers } })

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
