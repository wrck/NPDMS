import request from '@/config/axios'
import type { BusinessViewId, BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'
import { businessViewIdKey } from '@/api/pms/platform/business-view/ids'

export interface BusinessArtifact {
  artifactId: string
  versionNo: number
  referenceKey: string
  displayName: string
  sourceVersion: string
}
export interface TaskBusinessObject {
  objectId: string
  displayName: string
  factVersion: string
  allowedActions: string[]
  completionFacts: Record<string, boolean>
  artifacts: BusinessArtifact[]
}
export interface TaskBusinessLink extends TaskBusinessObject {
  id: BusinessViewId
}
export interface TaskBusinessContext {
  taskId: BusinessViewId
  projectId: BusinessViewId
  executionContractId: BusinessViewId
  contractVersion: number
  ownerContext: string
  objectType: string
  componentKey: string
  businessViewRevisionId?: BusinessViewId
  instanceResolutionStrategy: string
  links: TaskBusinessLink[]
  allowedActions: string[]
  recoverableError?: string
  factVersion?: string
  ownerActions: string[]
  executionAllowed?: boolean
  businessView?: BusinessViewRegistrationVO
}
const base = (id: BusinessViewId) => `/api/v1/pms/project-tasks/${businessViewIdKey(id)}/business`
export const getTaskBusinessContext = (id: BusinessViewId) =>
  request.get<TaskBusinessContext>({ url: `${base(id)}/context` })
export const getTaskBusinessCandidates = (id: BusinessViewId) =>
  request.get<TaskBusinessObject[]>({ url: `${base(id)}/candidates` })
export const linkTaskBusinessObject = (
  id: BusinessViewId,
  objectId: string,
  taskVersion: number,
  contractVersion: number,
  key: string
) =>
  request.post({
    url: `${base(id)}/links`,
    data: { objectId, expectedTaskVersion: taskVersion, expectedContractVersion: contractVersion },
    headers: { 'If-Match': String(contractVersion), 'Idempotency-Key': key }
  })
export const unlinkTaskBusinessObject = (
  id: BusinessViewId,
  linkId: BusinessViewId,
  taskVersion: number,
  contractVersion: number,
  key: string
) =>
  request.post({
    url: `${base(id)}/links/${businessViewIdKey(linkId)}/actions/unlink`,
    data: { expectedTaskVersion: taskVersion, expectedContractVersion: contractVersion },
    headers: { 'If-Match': String(contractVersion), 'Idempotency-Key': key }
  })
