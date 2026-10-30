// 统一审批关联公共客户端：发起/撤回/重提/决定走公共操作，生效命令走 P03 操作分发。
// 响应体由 axios 客户端统一解包 CommonResult，这里直接接收业务载荷。
import request from '@/config/axios'

const baseUrl = '/api/v1/pms/approvals'

export interface AttemptVO {
  id: number
  ownerModule: string
  entityType: string
  entityId: number
  purpose: string
  attemptId: string
  submissionBasis: string
  neutralProcessRef: string
  backendId: string
  instanceRef: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'
  conclusionBasis?: string
  previousAttemptId?: number
  batchGroupRef?: string
}

export interface OpinionVO {
  id: number
  attemptRowId: number
  action: 'SUBMIT' | 'APPROVE' | 'REJECT' | 'WITHDRAW'
  comment?: string
  actorUserId?: number
  createTime?: string
}

export interface EffectVO {
  id: number
  attemptRowId: number
  operationCode: string
  idempotencyKey: string
  status: 'SUCCESS' | 'PENDING_RECOVERY' | 'FAILED'
  receiptOutcome?: string
  concurrencyBasis?: number
  detail?: string
}

export interface AttemptDetailVO {
  attempt: AttemptVO
  opinions: OpinionVO[]
  effects: EffectVO[]
}

export interface SubjectParam {
  ownerModule: string
  entityType: string
  entityId: number
}

export interface SubmitParam {
  purpose: string
  attemptId: string
  submissionBasis: string
  neutralProcessRef: string
  subjects?: SubjectParam[]
  ownerModule?: string
  entityType?: string
  entityId?: number
}

export const submitApproval = (data: SubmitParam) =>
  request.post<AttemptVO[]>({ url: `${baseUrl}/submissions`, data, silentError: true })

export const listAttempts = (ownerModule: string, entityType: string, entityId: number, purpose?: string) =>
  request.get<AttemptVO[]>({
    url: `${baseUrl}/attempts`,
    params: { ownerModule, entityType, entityId, purpose },
    silentError: true
  })

export const getAttemptDetail = (id: number) =>
  request.get<AttemptDetailVO>({ url: `${baseUrl}/attempts/${id}`, silentError: true })

export const withdrawAttempt = (id: number, reason: string) =>
  request.post<AttemptVO>({ url: `${baseUrl}/attempts/${id}/withdraw`, data: { reason }, silentError: true })

export const resubmitAttempt = (id: number, attemptId: string, submissionBasis: string) =>
  request.post<AttemptVO>({
    url: `${baseUrl}/attempts/${id}/resubmit`,
    data: { attemptId, submissionBasis },
    silentError: true
  })

export const decideAttempt = (id: number, approved: boolean, comment: string) =>
  request.post<AttemptVO>({
    url: `${baseUrl}/attempts/${id}/decide`,
    data: { approved, comment },
    silentError: true
  })

export const executeEffect = (
  id: number,
  data: { operationCode: string; idempotencyKey: string; concurrencyBasis?: number; input?: Record<string, unknown> }
) => request.post<EffectVO>({ url: `${baseUrl}/attempts/${id}/effects`, data, silentError: true })
