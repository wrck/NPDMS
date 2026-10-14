import request, { baseUrl } from './request'
import type { WireLong, WireDateTime, CustomerContactReference, ProjectContactChoice, WritableCutoverPlanContent, CutoverPlanView, CreateCutoverPlanDraftRequest, DownloadCutoverPlanDraftResult, SubmitCutoverPlanResult } from './types'

export const getCutoverPlan = (taskId: WireLong) =>
  request.get<CutoverPlanView>({ url: `${baseUrl}/${taskId}/plan` })

export const createCutoverPlanDraft = (command: { taskId: WireLong; taskVersion: WireLong; data: CreateCutoverPlanDraftRequest; idempotencyKey: string }) =>
  request.post<CutoverPlanView>({
    url: `${baseUrl}/${command.taskId}/plan/actions/create-draft`,
    data: command.data,
    headers: { 'X-Task-Version': String(command.taskVersion), 'Idempotency-Key': command.idempotencyKey }
  })

export const saveCutoverPlanDraft = (command: { taskId: WireLong; taskVersion: WireLong; planVersion: WireLong; data: WritableCutoverPlanContent; idempotencyKey: string }) =>
  request.put<CutoverPlanView>({
    url: `${baseUrl}/${command.taskId}/plan`,
    data: command.data,
    headers: {
      'If-Match': String(command.planVersion),
      'X-Task-Version': String(command.taskVersion),
      'Idempotency-Key': command.idempotencyKey
    }
  })

export const downloadCutoverPlanDraft = (command: { taskId: WireLong; planVersion: WireLong; idempotencyKey: string }) =>
  request.post<DownloadCutoverPlanDraftResult>({
    url: `${baseUrl}/${command.taskId}/plan/actions/download-draft`,
    data: {},
    headers: { 'If-Match': String(command.planVersion), 'Idempotency-Key': command.idempotencyKey }
  })

export const submitCutoverPlan = (command: { taskId: WireLong; taskVersion: WireLong; planVersion: WireLong; idempotencyKey: string }) =>
  request.post<SubmitCutoverPlanResult>({
    url: `${baseUrl}/${command.taskId}/plan/actions/submit`,
    data: {},
    headers: {
      'If-Match': String(command.planVersion),
      'X-Task-Version': String(command.taskVersion),
      'Idempotency-Key': command.idempotencyKey
    }
  })

export const patchApprovedCutoverPlanContact = (command: { taskId: WireLong; arrangementId: WireLong; planVersion: WireLong; data: { personName: string; phone: string; arrivalTime: WireDateTime; reason: string; customerContactReference: CustomerContactReference | null }; idempotencyKey: string }) =>
  request.put<CutoverPlanView>({
    method: 'PATCH',
    url: `${baseUrl}/${command.taskId}/plan/support-arrangements/${command.arrangementId}`,
    data: command.data,
    headers: { 'If-Match': String(command.planVersion), 'Idempotency-Key': command.idempotencyKey }
  })

export const reviseCutoverPlan = (command: { taskId: WireLong; taskVersion: WireLong; data: {
    sourcePlanRevisionId: WireLong
    reason: 'APPROVAL_REJECTED' | 'SOURCE_REPLACED'
  }; idempotencyKey: string }) =>
  request.post<CutoverPlanView>({
    url: `${baseUrl}/${command.taskId}/plan/actions/revise`,
    data: command.data,
    headers: { 'X-Task-Version': String(command.taskVersion), 'Idempotency-Key': command.idempotencyKey }
  })

export const getProjectContactChoices = (taskId: WireLong) => request.get<ProjectContactChoice[]>({ url: `/api/v1/pms/cutover-tasks/${taskId}/project-contacts` })
