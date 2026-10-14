import request, { baseUrl } from './request'
import type { WireLong, CutoverApprovalDetail, CutoverApprovalReassignmentView, CutoverApprovalView, CutoverApprovalDecisionRequest, CutoverApprovalReassignmentCandidatePage, CutoverApprovalTodoPage } from './types'

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

export const approveCutoverApproval = (command: {
  approvalId: WireLong; approvalVersion: WireLong; taskVersion: WireLong
  data: CutoverApprovalDecisionRequest; idempotencyKey: string
}) => request.post<CutoverApprovalDetail>({
  url: `/api/v1/pms/cutover-approvals/${command.approvalId}/actions/approve`,
  data: command.data,
  headers: { 'If-Match': String(command.approvalVersion), 'X-Task-Version': String(command.taskVersion), 'Idempotency-Key': command.idempotencyKey }
})

export const rejectCutoverApproval = (command: {
  approvalId: WireLong; approvalVersion: WireLong; taskVersion: WireLong
  data: CutoverApprovalDecisionRequest; idempotencyKey: string
}) => request.post<CutoverApprovalDetail>({
  url: `/api/v1/pms/cutover-approvals/${command.approvalId}/actions/reject`,
  data: command.data,
  headers: { 'If-Match': String(command.approvalVersion), 'X-Task-Version': String(command.taskVersion), 'Idempotency-Key': command.idempotencyKey }
})

export const reassignCutoverApproval = (command: {
  approvalId: WireLong; approvalVersion: WireLong
  data: { nodeNo: number; newApproverUserId: WireLong; reason: string }; idempotencyKey: string
}) => request.post<CutoverApprovalReassignmentView>({
  url: `/api/v1/pms/cutover-approvals/${command.approvalId}/actions/reassign`, data: command.data,
  headers: { 'If-Match': String(command.approvalVersion), 'Idempotency-Key': command.idempotencyKey }
})

export const getCutoverApprovalInstance = (approvalId: WireLong) => request.get<CutoverApprovalView>({ url: `/api/v1/pms/cutover-approvals/${approvalId}` })
