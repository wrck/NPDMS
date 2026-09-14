import request from '@/config/axios'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'

export interface PlanDefinition {
  id: string | number
  revisionNo: number
  version: number
  basePlanVersionId?: string | number
  designer: TemplateDesignerDocument
}
export interface ProjectPlanState {
  effective: PlanDefinition
  draft?: PlanDefinition
  editable: boolean
  projectVersion: number
}
export interface PlanElementChange {
  nodeKey: string
  action: 'ADD' | 'UPDATE' | 'REMOVE'
  instanceId: string | number | null
  expectedVersion: number | null
  fromCode: string | null
  toCode: string | null
}
export interface PlanImpact {
  basePlanVersionId: string | number
  draftId: string | number
  draftVersion: number
  projectVersion: number
  changedRuleKeys: string[]
  deliverableChanges: PlanElementChange[]
  milestoneChanges: PlanElementChange[]
  gateChanges: (PlanElementChange & { reevaluationRequired: boolean })[]
  executionChanges: {
    nodeKey: string
    nodeKind: 'STAGE' | 'TASK'
    name: string
    action: 'CREATE' | 'REBASE_CURRENT' | 'PRESERVE_HISTORY' | 'RETIRE_UNSTARTED'
    nodeInstanceId: string | number | null
    executionId: string | number | null
    executionVersion: number | null
    nodeVersion: number | null
    roundNo: number | null
    sourcePlanVersionId: string | number | null
    fromCode: string | null
    toCode: string | null
  }[]
  changes: {
    nodeKey: string
    nodeKind: string
    name: string
    action: string
    started: boolean
    completed: boolean
    effects: string[]
  }[]
  issues: { field: string; code: string; message: string }[]
}
const url = (projectId: number) => `/api/v1/pms/projects/${projectId}/plan`
export const getProjectPlan = (projectId: number): Promise<ProjectPlanState> =>
  request.get({ url: url(projectId) })
export const createProjectPlanDraft = (
  projectId: number,
  expectedPlanVersionId: string | number,
  key: string
): Promise<PlanDefinition> =>
  request.post({
    url: `${url(projectId)}/draft`,
    data: { expectedPlanVersionId },
    headers: { 'Idempotency-Key': key }
  })
export const saveProjectPlanDraft = (
  projectId: number,
  draft: PlanDefinition,
  designer: TemplateDesignerDocument,
  key: string
): Promise<PlanDefinition> =>
  request.put({
    url: `${url(projectId)}/draft/${draft.id}`,
    data: { expectedVersion: draft.version, designer },
    headers: { 'Idempotency-Key': key }
  })
export const previewProjectPlanDraft = (
  projectId: number,
  draft: PlanDefinition
): Promise<PlanImpact> =>
  request.get({
    url: `${url(projectId)}/draft/${draft.id}/preview`,
    params: { expectedVersion: draft.version }
  })

export const applyProjectPlanDraft = (
  projectId: number,
  expectedPreview: PlanImpact,
  key: string
): Promise<{
  projectId: string | number
  planVersionId: string | number
  revisionNo: number
  projectVersion: number
}> =>
  request.post({
    url: `${url(projectId)}/draft/${expectedPreview.draftId}/apply`,
    data: { expectedPreview },
    headers: { 'Idempotency-Key': key }
  })
