import request from '@/config/axios'
import type { RuleResult } from '@/api/pms/project/project-templates/rules'
import type { TaskExecutionContext } from '@/api/pms/project/task-business'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'

export interface ProjectBusinessExecutionSelection {
  task?: TaskExecutionContext
  stage?: StageExecutionContext
}

export interface NodeExecution {
  id: string | number
  planVersionId: string | number
  nodeKey: string
  nodeKind: 'STAGE' | 'TASK'
  nodeCode: string
  name: string
  roundNo: number
  status: 'PENDING' | 'ACTIVE' | 'DONE' | 'TERMINATED'
  version: number
  canSubmit: boolean
}

export const getNodeExecutions = (projectId: number): Promise<NodeExecution[]> =>
  request.get({ url: `/api/v1/pms/projects/${projectId}/node-executions` })

export interface ExecutionHistory {
  plans: {
    id: string | number
    revisionNo: number
    status: string
    effectiveAt: string | number
    closedAt?: string | number
    closure?: Extract<RuleResult, { kind: 'CONDITION' }>
  }[]
  rounds: {
    id: string | number
    planVersionId: string | number
    planRevisionNo?: number
    nodeKey: string
    nodeKind: 'STAGE' | 'TASK'
    nodeCode: string
    name: string
    roundNo: number
    current: boolean
    status: string
    admittedAt?: string | number
    startedAt?: string | number
    startedPlanVersionId?: string | number
    submittedAt?: string | number
    submittedBy?: string | number
    submissionNote?: string
    canViewSubmissionNote: boolean
    endedAt?: string | number
    evaluations: {
      purpose: string
      name: string
      result: Extract<RuleResult, { kind: 'CONDITION' }>
    }[]
  }[]
}

export const getExecutionHistory = (projectId: number): Promise<ExecutionHistory> =>
  request.get({ url: `/api/v1/pms/projects/${projectId}/node-executions/history` })

export const submitStageExecution = (
  projectId: number,
  execution: NodeExecution,
  note: string,
  key: string
) =>
  request.post({
    url: `/api/v1/pms/projects/${projectId}/node-executions/${execution.id}/submission`,
    data: { expectedVersion: execution.version, note },
    headers: { 'Idempotency-Key': key }
  })
