import request from '@/config/axios'
import type { BusinessViewId, BusinessViewRegistrationVO } from '@/api/pms/platform/business-view'

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
}
export const getStageBusinessContext = (projectId: number, stageCode: string) =>
  request.get<StageBusinessContext>({
    url: `/api/v1/pms/projects/${projectId}/stages/${encodeURIComponent(stageCode)}/business/context`
  })
