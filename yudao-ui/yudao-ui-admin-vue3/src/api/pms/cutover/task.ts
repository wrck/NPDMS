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
