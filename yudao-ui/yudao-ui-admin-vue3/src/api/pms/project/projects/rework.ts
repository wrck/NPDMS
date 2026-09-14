import request from '@/config/axios'
import type { BusinessViewId } from '@/api/pms/platform/business-view/ids'

export interface ReworkNode {
  nodeKey: string
  nodeKind: 'STAGE' | 'TASK'
  code: string
  name: string
  stageCode: string
}
export interface ReworkCandidate {
  node: ReworkNode
  executionId: BusinessViewId
  executionVersion: number
  roundNo: number
  status: string
}
export interface ReworkState {
  planVersionId: BusinessViewId
  projectVersion: number
  nodes: ReworkCandidate[]
}
export interface ReworkPreview {
  planVersionId: BusinessViewId
  projectVersion: number
  plan: {
    targets: (Omit<ReworkCandidate, 'status'> & { selected: boolean })[]
    affectedNodeKeys: string[]
    blockers: { nodeKey: string; code: string; message: string }[]
  }
}
export interface ReworkApply {
  planVersionId: BusinessViewId
  expectedProjectVersion: number
  selectedNodeKeys: string[]
  expectedExecutions: { nodeKey: string; executionId: BusinessViewId; version: number }[]
  reason: string
}
export interface ReworkResult {
  projectId: BusinessViewId
  planVersionId: BusinessViewId
  projectVersion: number
  executions: {
    nodeKey: string
    previousExecutionId: BusinessViewId
    executionId: BusinessViewId
    roundNo: number
  }[]
}
const path = (projectId: BusinessViewId) => `/api/v1/pms/projects/${projectId}/rework`
export const getReworkState = (projectId: BusinessViewId): Promise<ReworkState> =>
  request.get({ url: path(projectId) })
export const previewRework = (
  projectId: BusinessViewId,
  selectedNodeKeys: string[]
): Promise<ReworkPreview> =>
  request.post({ url: `${path(projectId)}/preview`, data: { selectedNodeKeys } })
export const applyRework = (
  projectId: BusinessViewId,
  data: ReworkApply,
  key: string
): Promise<ReworkResult> =>
  request.post({ url: path(projectId), data, headers: { 'Idempotency-Key': key } })
