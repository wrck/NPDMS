import request from '@/config/axios'
import type { BusinessViewId, BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'
import type { TaskExecutionContext } from '@/api/pms/project/task-business'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'

export interface ExecutionSelection {
  task: TaskExecutionContext | null
  stage: StageExecutionContext | null
}
export interface RuleObservation {
  outcome: 'MATCHED' | 'NOT_MATCHED' | 'UNKNOWN' | 'NO_ADDITIONAL_RULE' | 'NOT_EVALUATED'
  reason?: string | null
}
export interface OperationCapability {
  operationCode: string
  operationVersion: number
  label: string
  ownerPermitted: boolean
  executionPermitted: boolean
  runtimeAvailable: boolean
  pre: RuleObservation
  post: RuleObservation
  allowed: boolean
  reason?: string | null
}
export interface OperationCapabilities {
  node: { projectId: BusinessViewId; kind: 'TASK' | 'STAGE'; id: BusinessViewId; code: string; name: string; status: string }
  execution?: ExecutionSelection | null
  actions: OperationCapability[]
  presentation: { registration?: BusinessViewRegistrationVO | null; status: 'AVAILABLE' | 'READ_ONLY' | 'UNAVAILABLE'; reason?: string | null; pageUrl?: string; query?: Record<string, string> | null }
  ownerFactVersion?: string | null
  reason?: string | null
}
export interface CapabilityQuery {
  projectId: BusinessViewId
  nodeKind: 'TASK' | 'STAGE'
  nodeId: BusinessViewId
  objectId?: BusinessViewId
  expected?: ExecutionSelection
}
/** Observation only; a later command must not send allowed=true as an authorization claim. */
export const inspectOperationCapabilities = (data: CapabilityQuery) =>
  request.post<OperationCapabilities>({ url: '/api/v1/pms/project-execution/capabilities/inspect', data })
