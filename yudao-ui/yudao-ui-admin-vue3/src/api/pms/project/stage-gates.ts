import request from '@/config/axios'
import type { RuleResult } from './project-templates/rules'

export interface StageGateWorkbench {
  projectId: number | string
  projectVersion: number
  planVersionId: number | string | null
  stageId: number | string | null
  stageCode: string
  executionId: number | string | null
  executionRound: number | null
  recoverableError: string | null
  gates: {
    gateId: number | string
    gateCode: string
    name: string
    gateType: string
    persistedStatus: string
    evaluation: Extract<RuleResult, { kind: 'CONDITION' }>
    references: StageGateReference[]
  }[]
}

export interface StageGateReference {
  gateReferenceId: number | string
  refType: string
  refCode: string
  refVersion?: string | null
  canStart: boolean
  process: null | {
    processInstanceId: string | null
    status: 'NOT_STARTED' | 'RUNNING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'UNKNOWN'
    outcome: 'SATISFIED' | 'UNSATISFIED' | 'DEPENDENCY_UNAVAILABLE' | 'VERSION_CONFLICT'
    reasonCode: string | null
  }
}

/** Read-only current-plan evaluation, not the old adjacent-stage advance guard. */
export const getStageGateWorkbench = (projectId: number, stageCode: string): Promise<StageGateWorkbench> =>
  request.get({ url: `/api/v1/pms/projects/${projectId}/stages/${encodeURIComponent(stageCode)}/gate-workbench` })
