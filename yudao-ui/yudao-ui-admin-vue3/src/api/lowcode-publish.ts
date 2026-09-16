import { get, post } from '@/utils/request'

export interface LowCodePublishRecord {
  id?: number
  configType: string
  configId: number
  configCode?: string
  version?: number
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'PUBLISHED'
  /** 当前审批级别（多级审批链生效时从 1 开始递增；单步审批为 null） */
  currentLevel?: number | null
  /** 审批链 ID（关联 pms_lowcode_approval_chain，无审批链时为 null） */
  approvalChainId?: number | null
  applicantId?: number
  applicant?: string
  approverId?: number
  approver?: string
  changeLog?: string
  rejectReason?: string
  submittedAt?: string
  approvedAt?: string
  publishedAt?: string
}

export function submitForPublish(configType: string, configId: number, changeLog: string, userId: number, userName?: string) {
  return post<LowCodePublishRecord>('/api/lowcode/publish/submit', null, { params: { configType, configId, changeLog, userId, userName } })
}

export function approvePublish(id: number, approverId: number, approver?: string) {
  return post<LowCodePublishRecord>(`/api/lowcode/publish/${id}/approve`, null, { params: { approverId, approver } })
}

export function rejectPublish(id: number, reason: string, approverId: number, approver?: string) {
  return post<LowCodePublishRecord>(`/api/lowcode/publish/${id}/reject`, null, { params: { reason, approverId, approver } })
}

export function rollbackPublish(id: number, userId: number, userName?: string) {
  return post<LowCodePublishRecord>(`/api/lowcode/publish/${id}/rollback`, null, { params: { userId, userName } })
}

export function getPublishList(configType: string, configId: number) {
  return get<LowCodePublishRecord[]>('/api/lowcode/publish', { configType, configId })
}

export function getPendingList() {
  return get<LowCodePublishRecord[]>('/api/lowcode/publish/pending')
}
