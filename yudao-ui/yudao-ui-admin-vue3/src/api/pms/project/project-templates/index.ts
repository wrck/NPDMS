import request from '@/config/axios'

// ========== F-PM03 项目模板基座（/pms/project-templates，SDS 10-api 契约） ==========

/** 阶段定义行（S0～S6） */
export interface StageDef {
  stageCode: string
  name: string
  sortOrder?: number
  entryCriteria?: string
  exitCriteria?: string
}

/** 任务定义行（版本内唯一，可父子） */
export interface TaskDef {
  taskCode: string
  name: string
  parentTaskCode?: string
  stageCode?: string
  priority?: number
  sortOrder?: number
  estimatedHours?: number
  satisfactionTiming?: string
  description?: string
}

/** 里程碑定义行 */
export interface MilestoneDef {
  milestoneCode: string
  name: string
  stageCode?: string
  timing?: string
  criteria?: string
}

/** 交付件定义行 */
export interface DeliverableDef {
  deliverableCode: string
  name: string
  stageCode?: string
  taskCode?: string
  required?: boolean
}

/** 门禁结构化引用行（任务/交付件/状态/流程） */
export interface GateRef {
  refType: string // TASK / DELIVERABLE / STATE / PROCESS
  refCode: string
  refVersion?: string
}

/** 门禁定义行（ENTRY 准入 / EXIT 准出） */
export interface GateDef {
  gateCode: string
  name: string
  gateType: string
  stageCode?: string
  description?: string
  references: GateRef[]
}

/** 草稿/版本定义内容（四维条件+流程引用+六类定义行） */
export interface TemplateDefinitionContent {
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
  processDefinitionKey?: string
  processDefinitionVersion?: string
  stages: StageDef[]
  tasks: TaskDef[]
  milestones: MilestoneDef[]
  deliverables: DeliverableDef[]
  gates: GateDef[]
}

/** 模板身份 */
export interface ProjectTemplateVO {
  id?: number
  code: string
  name: string
  status?: string // DRAFT / ACTIVE / RETIRED
  matchPriority?: number
  description?: string
  systemReserved?: boolean
  createTime?: Date
}

/** 版本头 */
export interface ProjectTemplateRevisionVO {
  id: number
  templateId: number
  revisionNo: number
  status: string // DRAFT / PUBLISHED
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

/** 模板详情（身份+草稿内容+版本清单） */
export interface ProjectTemplateDetailVO extends ProjectTemplateVO {
  draftContent?: TemplateDefinitionContent
  revisions: ProjectTemplateRevisionVO[]
}

/** 已发布版本详情（只读快照） */
export interface ProjectTemplateRevisionDetailVO extends ProjectTemplateRevisionVO {
  content: TemplateDefinitionContent
}

/** 编辑入参：身份字段 + 可选草稿内容整体替换 */
export interface ProjectTemplateUpdateReqVO {
  name?: string
  matchPriority?: number
  description?: string
  content?: TemplateDefinitionContent
}

/** 四维匹配预演入参 */
export interface MatchPreviewReqVO {
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
}

/** 匹配候选 */
export interface MatchCandidateVO {
  templateId: number
  code: string
  name: string
  matchPriority?: number
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
}

/** 匹配预演结果：唯一命中或冲突清单（不静默选模） */
export interface MatchRespVO {
  outcome: 'MATCHED' | 'NO_MATCH' | 'MULTI_MATCH'
  matched?: MatchCandidateVO
  conflicts: string[]
}

const baseUrl = '/pms/project-templates'

export const getProjectTemplatePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectTemplate = (id: number) =>
  request.get<ProjectTemplateDetailVO>({ url: `${baseUrl}/${id}` })
export const createProjectTemplate = (data: ProjectTemplateVO) =>
  request.post({ url: baseUrl, data })
export const updateProjectTemplate = (id: number, data: ProjectTemplateUpdateReqVO) =>
  request.put({ url: `${baseUrl}/${id}`, data })
export const deleteProjectTemplate = (id: number) =>
  request.delete({ url: `${baseUrl}/${id}` })
export const publishProjectTemplate = (id: number) =>
  request.post({ url: `${baseUrl}/${id}/actions/publish` })
export const disableProjectTemplate = (id: number) =>
  request.post({ url: `${baseUrl}/${id}/actions/disable` })
export const getProjectTemplateRevision = (id: number, revisionNo: number) =>
  request.get<ProjectTemplateRevisionDetailVO>({
    url: `${baseUrl}/${id}/revisions/${revisionNo}`
  })
export const matchPreview = (data: MatchPreviewReqVO) =>
  request.post<MatchRespVO>({ url: `${baseUrl}/actions/match-preview`, data })
