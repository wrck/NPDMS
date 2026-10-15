import request, { baseUrl } from './request'
import type { WireLong, CutoverDashboardKpiData, ManualGrade, CreateContextData, AssessmentAnswers, CutoverTaskListQuery, CutoverTaskDetail, CutoverTaskPage, CreateCutoverTaskRequest, IntakeOption } from './types'

export const getCutoverDashboardKpis = () =>
  request.get<CutoverDashboardKpiData>({ url: '/api/v1/pms/cutover-tasks/statistics' })

export const resolveCreateContext = (serialNumbers: string[]) =>
  request.post<CreateContextData>({
    url: `${baseUrl}/actions/resolve-create-context`,
    data: { serialNumbers }
  })

export const getCutoverTaskPage = (params: CutoverTaskListQuery) => request.get<CutoverTaskPage>({ url: baseUrl, params })

export const getCutoverTaskDetail = (id: WireLong) =>
  request.get<CutoverTaskDetail>({ url: `${baseUrl}/${id}` })

export const getCutoverRevisionOptions = (id: WireLong) =>
  request.get<IntakeOption[]>({ url: `${baseUrl}/${id}/revision-options` })

export const reviseCutoverTask = (command: {
  taskId: WireLong; taskVersion: WireLong; targetStage: 'P2' | 'P3' | 'P4';
  data: { expectedProjectScopeVersion: WireLong; reason: string; configurationRevisionId?: WireLong }; idempotencyKey: string
}) => request.post({
  url: `${baseUrl}/${command.taskId}/actions/${({ P2: 'revise-assessment', P3: 'revise-checklist', P4: 'withdraw-plan' })[command.targetStage]}`,
  data: command.data, headers: { 'If-Match': String(command.taskVersion), 'Idempotency-Key': command.idempotencyKey }
})

export const createCutoverTask = (command: { data: CreateCutoverTaskRequest; idempotencyKey: string }) =>
  request.post({ url: baseUrl, data: command.data, silentError: true, headers: { 'Idempotency-Key': command.idempotencyKey } })

export const saveCutoverAssessment = (command: {
  taskId: WireLong
  taskVersion: WireLong
  assessmentVersion: WireLong
  data: { answers: AssessmentAnswers; manualGrade: ManualGrade | null }
  idempotencyKey: string
}) =>
  request.put({
    url: `${baseUrl}/${command.taskId}/assessment`,
    data: command.data,
    headers: { 'If-Match': String(command.taskVersion), 'Assessment-If-Match': String(command.assessmentVersion), 'Idempotency-Key': command.idempotencyKey }
  })

export const submitCutoverAssessment = (command: {
  taskId: WireLong
  taskVersion: WireLong
  assessmentVersion: WireLong
  idempotencyKey: string
}) =>
  request.post({
    url: `${baseUrl}/${command.taskId}/assessment/actions/submit`,
    data: {},
    headers: {
      'Idempotency-Key': command.idempotencyKey,
      'If-Match': String(command.taskVersion),
      'Assessment-If-Match': String(command.assessmentVersion)
    }
  })

export const getCreationOptions = () => request.get<IntakeOption[]>({ url: '/api/v1/pms/cutover-tasks/creation-options' })

export interface CreationProjectOption { id: WireLong; code: string; name: string; customerName?: string }
export interface CreationProjectPresentation {
  projectId: WireLong; projectCode: string; projectName: string; customerName?: string
  projectManagerName?: string; serviceManagerNames?: string; salesNames?: string
  departmentName?: string; industryName?: string; majorProjectLevel?: string
}
export interface CreationDevice {
  deviceId: WireLong; serialNumber: string; productCode?: string; productModel?: string
}
export interface CreationProjectContext { project: CreationProjectPresentation; customerServiceLevel?: string }
export interface ResolvedCreationDevices { context: CreateContextData; devices: CreationDevice[] }

export const searchCreationProjects = (keyword = '') =>
  request.get<CreationProjectOption[]>({ url: `${baseUrl}/creation-projects`, params: { keyword } })
export const getCreationProject = (projectId: WireLong) =>
  request.get<CreationProjectContext>({ url: `${baseUrl}/creation-projects/${projectId}` })
export const getCreationProjectDevices = (projectId: WireLong, params: { keyword?: string; pageNo: number; pageSize: number }) =>
  request.get<{ list: CreationDevice[]; total: number }>({ url: `${baseUrl}/creation-projects/${projectId}/devices`, params })
export const resolveCreationDevices = (serialNumbers: string[]) =>
  request.post<ResolvedCreationDevices>({ url: `${baseUrl}/creation-devices/resolve`, data: { serialNumbers } })
