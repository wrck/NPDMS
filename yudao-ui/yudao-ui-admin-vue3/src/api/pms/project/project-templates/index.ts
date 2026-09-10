import request from '@/config/axios'
import type { ValidationResult } from './definitions'

// PM-03 / F-PROJ-009 — one template identity and explicit graph, server-owned snapshots.
export interface DefinitionLink {
  definitionRevisionId?: number
  definitionSnapshot?: Record<string, unknown>
}
export interface ExecutionLinks extends DefinitionLink {
  workBindingRevisionId?: number
  permissionPolicyRevisionId?: number
  completionRuleRevisionId?: number
}
export interface StageTransition {
  transitionCode: string
  fromStageCode: string
  toStageCode: string
  conditionRuleRevisionId?: number
  priority: number
  default: boolean
  revisionNo: number
}
export interface StageDef extends ExecutionLinks {
  stageCode: string
  name: string
  sortOrder?: number
  entryCriteria?: string
  exitCriteria?: string
  start?: boolean
  terminal?: boolean
}
export interface TaskDef extends ExecutionLinks {
  taskCode: string
  name: string
  parentTaskCode?: string
  stageCode?: string
  priority?: number
  sortOrder?: number
  estimatedHours?: number
  satisfactionTiming?: string
  description?: string
  // Existing inline contracts remain readable; new nodes select reusable revisions.
  workBindingTypeCode?: string
  targetContextCode?: string
  targetObjectType?: string
  targetObjectKey?: string
  componentKey?: string
  dynamicFormRevisionId?: number
  approvalDefinitionKey?: string
  bindingConfig?: string
  permissionPolicyRef?: string
  completionRuleTypeCode?: string
  completionRuleConfig?: string
  gateRef?: string
  definitionVersion?: number
}
export interface MilestoneDef extends DefinitionLink {
  milestoneCode: string
  name: string
  stageCode?: string
  timing?: string
  criteria?: string
}
export interface DeliverableDef extends DefinitionLink {
  deliverableCode: string
  name: string
  stageCode?: string
  taskCode?: string
  required?: boolean
}
export interface GateRef { refType: string; refCode: string; refVersion?: string }
export interface GateDef extends DefinitionLink {
  gateCode: string
  name: string
  gateType: string
  stageCode?: string
  description?: string
  references: GateRef[]
}
export interface TemplateClosurePolicy {
  closureType: 'NORMAL'
  ruleRevision: 1
  requireTerminalStage: true
  requireAllTasksDone: true
  revalidateBusinessFacts: true
  processDefinitionKey: 'PMS_MINIMAL_NORMAL_CLOSURE'
  /** Decimal strings preserve IDs beyond JavaScript's safe integer range. */
  reviewerUserId: number | string
}
export interface TemplateDefinitionContent {
  closurePolicy?: TemplateClosurePolicy | null
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
  processDefinitionKey?: string
  /** Historical read-only field. Never submitted by the editor. */
  processDefinitionVersion?: string
  stages: StageDef[]
  /** Missing in historical content; absence must not be inferred from sortOrder. */
  transitions?: StageTransition[]
  tasks: TaskDef[]
  milestones: MilestoneDef[]
  deliverables: DeliverableDef[]
  gates: GateDef[]
}
export interface ProjectTemplateVO {
  id?: number
  code: string
  name: string
  status?: string
  version?: number
  matchPriority?: number
  description?: string
  systemReserved?: boolean
  createTime?: Date
}
export interface ProjectTemplateRevisionVO {
  id: number
  templateId: number
  revisionNo: number
  status: string
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
  processDefinitionKey?: string
  processDefinitionVersion?: string
  validationSummary?: string
  publishedBy?: string
  publishedTime?: Date
}
export interface ProjectTemplateDetailVO extends ProjectTemplateVO {
  draftContent?: TemplateDefinitionContent
  revisions: ProjectTemplateRevisionVO[]
}
export interface ProjectTemplateRevisionDetailVO extends ProjectTemplateRevisionVO {
  content: TemplateDefinitionContent
}
export interface ProjectTemplateUpdateReqVO {
  name?: string
  matchPriority?: number
  description?: string
  content?: TemplateDefinitionContent
}
export interface TemplateCopy { code: string; name: string; sourceRevisionNo?: number }
export interface MatchPreviewReqVO {
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
}
export interface MatchCandidateVO extends MatchPreviewReqVO {
  templateId: number
  code: string
  name: string
  matchPriority?: number
}
export interface MatchRespVO {
  outcome: 'MATCHED' | 'NO_MATCH' | 'MULTI_MATCH'
  matched?: MatchCandidateVO
  conflicts: string[]
}

export const templateSaveContent = (content: TemplateDefinitionContent): TemplateDefinitionContent =>
  JSON.parse(JSON.stringify(content, (key, value) =>
    ['definitionSnapshot', 'processDefinitionVersion', 'refVersion'].includes(key) ? undefined : value
  ))
const baseUrl = '/api/v1/pms/project-templates'
export const getProjectTemplatePage = (params: PageParam) => request.get({ url: `${baseUrl}/page`, params })
export const getProjectTemplate = (id: number) => request.get<ProjectTemplateDetailVO>({ url: `${baseUrl}/${id}` })
export const createProjectTemplate = (data: ProjectTemplateVO) => request.post<number>({ url: baseUrl, data })
// Existing template commands retain the Controller's header-free compatibility contract.
export const updateProjectTemplate = (id: number, data: ProjectTemplateUpdateReqVO) =>
  request.put({ url: `${baseUrl}/${id}`, data: { ...data, ...(data.content ? { content: templateSaveContent(data.content) } : {}) } })
export const deleteProjectTemplate = (id: number) => request.delete({ url: `${baseUrl}/${id}` })
export const publishProjectTemplate = (id: number) => request.post({ url: `${baseUrl}/${id}/actions/publish` })
export const disableProjectTemplate = (id: number) => request.post({ url: `${baseUrl}/${id}/actions/disable` })
export const validateProjectTemplate = (id: number) => request.post<ValidationResult>({ url: `${baseUrl}/${id}/actions/validate` })
export const copyProjectTemplate = (id: number, version: number, data: TemplateCopy, key: string) =>
  request.post<number>({ url: `${baseUrl}/${id}/actions/copy`, data,
    headers: { 'If-Match': String(version), 'Idempotency-Key': key } })
export const getProjectTemplateRevision = (id: number, revisionNo: number) =>
  request.get<ProjectTemplateRevisionDetailVO>({ url: `${baseUrl}/${id}/revisions/${revisionNo}` })
export const matchPreview = (data: MatchPreviewReqVO) => request.post<MatchRespVO>({ url: `${baseUrl}/actions/match-preview`, data })
