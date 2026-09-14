import request from '@/config/axios'
import type { BusinessViewId, BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'
import type { TaskApprovalView } from '@/api/pms/project/task-workbench'

export interface StageExecutionContext {
  projectId: BusinessViewId
  projectVersion: number
  stageId: BusinessViewId
  stageVersion: number
  executionContractId: BusinessViewId
  contractVersion: number
  planVersionId: BusinessViewId
  executionId: BusinessViewId
  executionVersion: number
  roundNo: number
  writable: boolean
}

export interface StageBusinessContext {
  projectId: BusinessViewId
  stageId: BusinessViewId
  stageCode: string
  executionContractId?: BusinessViewId
  contractVersion?: number
  bindingType?: string
  instanceResolutionStrategy?: string
  businessView?: BusinessViewRegistrationVO
  ownerActions: string[]
  readonly: boolean
  recoverableError?: string
  execution?: StageExecutionContext
  approval?: TaskApprovalView
}
export const getStageBusinessContext = (projectId: number, stageCode: string) =>
  request.get<StageBusinessContext>({
    url: `/api/v1/pms/projects/${projectId}/stages/${encodeURIComponent(stageCode)}/business/context`
  })
