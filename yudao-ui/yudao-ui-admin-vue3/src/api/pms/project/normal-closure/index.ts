import request from '@/config/axios'
import { businessViewIdKey, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
export interface ClosureCheck {
  code: string
  passed: boolean
  reason?: string
  subjectId?: BusinessViewId
}
export interface ClosureSnapshot {
  id: BusinessViewId
  projectVersion: number
  treeVersion: number
  fromStage: string
  passed: boolean
  checkedAt: string
  checkedBy: BusinessViewId
  sourceDigest: string
}
export interface ClosureApplication {
  id: BusinessViewId
  projectId: BusinessViewId
  snapshotId: BusinessViewId
  status: 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  processInstanceId: string
  processDefinitionId: string
  submittedAt: string
  decidedAt?: string
  serviceManagerUserId: BusinessViewId
  reviewerUserId: BusinessViewId
}
export interface ClosureOverview {
  projectId: BusinessViewId
  version: number
  treeVersion: number
  currentStage: string
  lifecycleStatus: string
  policyAvailable: boolean
  checks: ClosureCheck[]
  latestSnapshot?: ClosureSnapshot
  latestApplication?: ClosureApplication
  allowedActions: string[]
}
export interface ClosureReview {
  id: BusinessViewId
  taskId: string
  taskDefinitionKey: string
  reviewerUserId: BusinessViewId
  outcome: string
  reason?: string
  reviewedAt: string
}
export interface ApplicationDetail {
  projectId: BusinessViewId
  application: ClosureApplication & {
    fromStage: string
    closureType: string
    applicantUserId: BusinessViewId
    businessKey: string
    processDefinitionKey: string
    processEvidence: string
  }
  snapshot?: ClosureSnapshot & { evidence: string }
  reviews: ClosureReview[]
}
export const getNormalClosureApplication = (applicationId: string) =>
  request.get<ApplicationDetail>({
    url: `/api/v1/pms/normal-closure-applications/${applicationId}`
  })

const base = (id: BusinessViewId) => `/api/v1/pms/projects/${businessViewIdKey(id)}/normal-closure`
export const getNormalClosure = (id: BusinessViewId) =>
  request.get<ClosureOverview>({ url: base(id) })
export const checkNormalClosure = (overview: ClosureOverview, key: string) =>
  request.post<ClosureSnapshot>({
    url: `${base(overview.projectId)}/actions/check`,
    data: { expectedProjectVersion: overview.version, expectedTreeVersion: overview.treeVersion },
    headers: { 'Idempotency-Key': key }
  })
export const submitNormalClosure = (
  overview: ClosureOverview,
  snapshotId: BusinessViewId,
  key: string
) =>
  request.post<ClosureApplication>({
    url: `${base(overview.projectId)}/actions/submit`,
    data: {
      snapshotId,
      expectedProjectVersion: overview.version,
      expectedTreeVersion: overview.treeVersion
    },
    headers: { 'Idempotency-Key': key }
  })
