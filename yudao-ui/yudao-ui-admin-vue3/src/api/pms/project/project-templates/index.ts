import request from '@/config/axios'
import type { ValidationResult } from './definitions'

export type JsonValue = string | number | boolean | null | JsonObject | JsonValue[]
export interface JsonObject { [key: string]: JsonValue | undefined }

// -----------------------------------------------------------------------------
// Template Designer V2 — the only writable template content contract.
// -----------------------------------------------------------------------------
export interface TemplateMatch {
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
}

export interface TemplateSourcePin {
  definitionRevisionId?: number
  workBindingRevisionId?: number
  permissionPolicyRevisionId?: number
  completionRuleRevisionId?: number
  transitionId?: number
  transitionRevisionNo?: number
}

export interface WorkBindingSpec {
  type: string
  targetContextCode?: string
  targetObjectType?: string
  targetObjectKey?: string
  componentKey?: string
  dynamicFormRevisionId?: number | string
  approvalDefinitionKey?: string
  parameters?: JsonObject
  businessViewSnapshot?: JsonObject
  sourceRevisionId?: number
}

export interface PermissionRequirement {
  policyRef?: string
  policySnapshot?: JsonObject
  sourceRevisionId?: number
}

export interface RuleSpec {
  expression: JsonObject
  sourceRevisionId?: number
}

export interface DesignerStageNode {
  nodeKey: string
  code: string
  name: string
  sortOrder?: number
  start: boolean
  terminal: boolean
  entryCriteria?: string
  exitCriteria?: string
  workBinding: WorkBindingSpec
  permission: PermissionRequirement
  completionRule: RuleSpec
  source?: TemplateSourcePin
}

export interface DesignerTaskNode {
  nodeKey: string
  code: string
  name: string
  parentTaskCode?: string
  stageCode: string
  priority?: number
  sortOrder?: number
  estimatedHours?: number
  satisfactionTiming?: string
  description?: string
  workBinding: WorkBindingSpec
  permission: PermissionRequirement
  completionRule: RuleSpec
  gateRef?: string
  source?: TemplateSourcePin
}

export interface DesignerMilestoneNode {
  nodeKey: string
  code: string
  name: string
  stageCode?: string
  timing?: string
  criteria?: string
  configuration?: JsonObject
  source?: TemplateSourcePin
}

export interface DesignerDeliverableNode {
  nodeKey: string
  code: string
  name: string
  stageCode?: string
  taskCode?: string
  required?: boolean
  configuration?: JsonObject
  confirmationRule?: RuleSpec
  source?: TemplateSourcePin
}

export interface GateRef { refType: string; refCode: string; refVersion?: string }
export interface DesignerGateNode {
  nodeKey: string
  code: string
  name: string
  gateType: string
  stageCode?: string
  description?: string
  references: GateRef[]
  source?: TemplateSourcePin
}

export interface DesignerTransitionNode {
  edgeKey: string
  code: string
  fromStageCode: string
  toStageCode: string
  condition?: RuleSpec
  priority: number
  defaultBranch: boolean
  source?: TemplateSourcePin
}

export interface DesignerRuleAsset {
  key: string
  name?: string
  rule: RuleSpec
}

export interface DesignerLayout {
  nodes?: Record<string, { x?: number; y?: number }>
  viewport?: { x?: number; y?: number; zoom?: number }
}

export interface TemplateDesignerDocument {
  schemaVersion: 2
  match: TemplateMatch
  processDefinitionKey?: string
  closurePolicy?: TemplateClosurePolicy | null
  stages: DesignerStageNode[]
  tasks: DesignerTaskNode[]
  milestones: DesignerMilestoneNode[]
  deliverables: DesignerDeliverableNode[]
  gates: DesignerGateNode[]
  transitions: DesignerTransitionNode[]
  ruleAssets: DesignerRuleAsset[]
  layout?: DesignerLayout
  /** Server-owned provenance for explicit legacy import. Never drives V2 runtime semantics. */
  sourceEvidence?: JsonObject
}

export const emptyDesignerDocument = (): TemplateDesignerDocument => ({
  schemaVersion: 2,
  match: {},
  stages: [],
  tasks: [],
  milestones: [],
  deliverables: [],
  gates: [],
  transitions: [],
  ruleAssets: [],
  layout: {}
})

export const cloneDesignerDocument = (
  document?: TemplateDesignerDocument
): TemplateDesignerDocument => ({
  ...emptyDesignerDocument(),
  ...JSON.parse(JSON.stringify(document ?? {})),
  match: { ...(document?.match ?? {}) }
})

// -----------------------------------------------------------------------------
// Legacy content DTOs remain exported only for old revision readers/components.
// New designer code must use TemplateDesignerDocument and /{id}/draft.
// -----------------------------------------------------------------------------
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
  reviewerUserId: number | string
}
export interface TemplateDefinitionContent {
  closurePolicy?: TemplateClosurePolicy | null
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
  processDefinitionKey?: string
  processDefinitionVersion?: string
  stages: StageDef[]
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
  designerSchemaVersion?: number
  executionSchemaVersion?: number
  compilerVersion?: string
  snapshotHash?: string
  validationSummary?: string
  publishedBy?: string
  publishedTime?: Date
}
export interface ProjectTemplateDetailVO extends ProjectTemplateVO {
  /** Legacy compatibility projection. New editor loads /draft explicitly. */
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
  /** Legacy compatibility only. */
  content?: TemplateDefinitionContent
}
export interface TemplateCopy { code: string; name: string; sourceRevisionNo?: number }
export interface MatchPreviewReqVO extends TemplateMatch {}
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
export interface CompletionFactCatalogVO {
  ownerContext: string
  objectType: string
  factCode: string
  label: string
}

const baseUrl = '/api/v1/pms/project-templates'
export const getProjectTemplatePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectTemplate = (id: number) =>
  request.get<ProjectTemplateDetailVO>({ url: `${baseUrl}/${id}` })
export const createProjectTemplate = (data: ProjectTemplateVO) =>
  request.post<number>({ url: baseUrl, data })
export const updateProjectTemplate = (id: number, data: ProjectTemplateUpdateReqVO) =>
  request.put({ url: `${baseUrl}/${id}`, data })

/** V2 authoring truth. */
export const getProjectTemplateDraft = (id: number) =>
  request.get<TemplateDesignerDocument>({ url: `${baseUrl}/${id}/draft` })
export const updateProjectTemplateDraft = (id: number, data: TemplateDesignerDocument) =>
  request.put({ url: `${baseUrl}/${id}/draft`, data: cloneDesignerDocument(data) })

export const deleteProjectTemplate = (id: number) =>
  request.delete({ url: `${baseUrl}/${id}` })
export const publishProjectTemplate = (id: number) =>
  request.post({ url: `${baseUrl}/${id}/actions/publish` })
export const disableProjectTemplate = (id: number) =>
  request.post({ url: `${baseUrl}/${id}/actions/disable` })
export const validateProjectTemplate = (id: number) =>
  request.post<ValidationResult>({ url: `${baseUrl}/${id}/actions/validate` })
export const copyProjectTemplate = (id: number, version: number, data: TemplateCopy, key: string) =>
  request.post<number>({
    url: `${baseUrl}/${id}/actions/copy`,
    data,
    headers: { 'If-Match': String(version), 'Idempotency-Key': key }
  })
export const getProjectTemplateRevision = (id: number, revisionNo: number) =>
  request.get<ProjectTemplateRevisionDetailVO>({ url: `${baseUrl}/${id}/revisions/${revisionNo}` })
export const matchPreview = (data: MatchPreviewReqVO) =>
  request.post<MatchRespVO>({ url: `${baseUrl}/actions/match-preview`, data })
export const getCompletionFactCatalog = () =>
  request.get<CompletionFactCatalogVO[]>({ url: `${baseUrl}/actions/completion-fact-catalog` })
