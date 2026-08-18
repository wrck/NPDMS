import request from '@/config/axios'

/**
 * 新链项目 API（F-PM01 / PM-01，复数路由 /pms/projects）
 *
 * 承接全部项目写语义（创建/更新/指派）与主档查询；
 * 旧单数路由 /pms/project 已冻结只读（仅 get/page 供旧页面选择器过渡）。
 */

/** 项目主档（列表行与详情共用） */
export interface ProjectMasterVO {
  id?: number
  projectCode?: string
  codeRootId?: number
  projectSequence?: number
  codeRuleVersion?: string
  projectName?: string
  parentId?: number
  treeDepth?: number
  businessLevelCode?: string
  businessLevelName?: string
  customerCode?: string
  customerName?: string
  managerName?: string
  signingMethod?: string
  projectCategory?: string
  implementationMode?: string
  majorProjectLevel?: string | null
  contractNo?: string
  implementationLocation?: string
  creationReason?: string
  lifecycleTemplateId?: number
  lifecycleTemplateRevisionNo?: number
  templateLoadMethod?: string
  processDefinitionKey?: string
  processDefinitionVersion?: string
  sourceType?: string
  status?: string
  progress?: number
  aggregationWeight?: number
  weightSource?: string
  projectStartTime?: Date
  createTime?: Date
}

/** 手工创建请求（BR-2 必填：名称/三维/创建原因；parentId 非空=下挂子项目） */
export interface ProjectCreateReqVO {
  projectName: string
  parentId?: number
  customerCode?: string
  customerName?: string
  contractNo?: string
  orderOfficeCompanyCode?: string
  orderOfficeDepartmentCode?: string
  implementationLocation?: string
  signingMethod?: string
  projectCategory?: string
  implementationMode?: string
  majorProjectLevel?: string | null
  creationReason: string
  templateId?: number | null
  serviceManagerUserId?: number | null
}

/** 创建响应（含实例化摘要） */
export interface ProjectCreateRespVO extends ProjectMasterVO {
  stageCount?: number
  taskCount?: number
  milestoneCount?: number
  deliverableCount?: number
  gateCount?: number
  serviceManagerAssigned?: boolean
}

/** 模板匹配候选 */
export interface TemplateCandidateVO {
  templateId: number
  code: string
  name: string
  matchPriority: number
  latestRevisionNo: number
  signingMethod: string | null
  projectCategory: string | null
  implementationMethod: string | null
  majorProjectLevel: string | null
}

/** 模板匹配响应：MATCHED 唯一命中 / NO_MATCH 无匹配 / MULTI_MATCH 同优先级多匹配 */
export interface ProjectMatchTemplatesRespVO {
  outcome: 'MATCHED' | 'NO_MATCH' | 'MULTI_MATCH'
  candidates: TemplateCandidateVO[]
  conflicts: string[]
}

/** 成员区间（当前有效+历史） */
export interface ProjectMemberAssignmentVO {
  id: number
  projectId: number
  userId: number
  employeeNo?: string
  memberName?: string
  memberRole: string
  effectiveFrom: Date
  effectiveTo?: Date | null
  status?: string
  createTime?: Date
}

/** 实例视图：门禁引用行 */
export interface GateReferenceItem {
  refType: string
  refCode: string
  refVersion?: string
}

/** 实例视图（阶段→任务/里程碑/交付件/门禁，按冻结版本只读） */
export interface ProjectInstancesVO {
  projectId: number
  lifecycleTemplateId?: number
  lifecycleTemplateRevisionNo?: number
  stages: {
    stageCode: string
    name: string
    sortOrder: number
    entryCriteria?: string
    exitCriteria?: string
    status: string
  }[]
  tasks: {
    taskCode: string
    name: string
    parentTaskCode?: string | null
    stageCode: string
    priority?: number
    sortOrder?: number
    estimatedHours?: number
    satisfactionTiming?: string
    status: string
  }[]
  milestones: {
    milestoneCode: string
    name: string
    stageCode: string
    timing?: string
    criteria?: string
    status: string
  }[]
  deliverables: {
    deliverableCode: string
    name: string
    stageCode: string
    taskCode?: string | null
    required?: boolean
    status: string
  }[]
  gates: {
    gateCode: string
    name: string
    gateType: string
    stageCode: string
    validationSummary?: string
    status: string
    references: GateReferenceItem[]
  }[]
}

const baseUrl = '/pms/projects'

/** 进度汇总（F-PM02 / PM-02） */
export interface ProjectProgressVO {
  aggregate?: number
  children?: {
    projectId: number
    projectCode?: string
    projectName?: string
    progress?: number
    normalizedWeight?: number
    weightSource?: string
  }[]
}

/** 手工创建项目（Idempotency-Key 幂等：同键同摘要重放返回原资源） */
export const createProject = (data: ProjectCreateReqVO, idempotencyKey?: string) =>
  request.post<ProjectCreateRespVO>({
    url: baseUrl,
    data,
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  })

/** 按三维+级别实时匹配生效模板（创建向导第②步） */
export const matchTemplates = (params: {
  signingMethod?: string
  projectCategory?: string
  implementationMode?: string
  majorProjectLevel?: string
}) => request.get<ProjectMatchTemplatesRespVO>({ url: `${baseUrl}/actions/match-templates`, params })

/** 分页查询（名称/编码/状态/三维过滤） */
export const getProjectPage = (params: PageParam) =>
  request.get<{ list: ProjectMasterVO[]; total: number }>({ url: `${baseUrl}/page`, params })

/** 项目详情（基本信息+四维+模板绑定） */
export const getProject = (id: number) =>
  request.get<ProjectMasterVO>({ url: `${baseUrl}/${id}` })

/** 更新可编辑属性（BR-7：编码/父节点/来源/模板绑定/状态不可改） */
export const updateProject = (data: {
  id: number
  projectName?: string
  customerCode?: string
  customerName?: string
  contractNo?: string
  implementationLocation?: string
}) => request.put<boolean>({ url: `${baseUrl}/${data.id}`, data })

/** 实例视图（按冻结模板版本只读） */
export const getProjectInstances = (id: number) =>
  request.get<ProjectInstancesVO>({ url: `${baseUrl}/${id}/instances` })

/** 成员区间列表（当前有效+历史） */
export const getProjectMembers = (id: number) =>
  request.get<ProjectMemberAssignmentVO[]>({ url: `${baseUrl}/${id}/members` })

/** 指派一级服务经理（旧区间关闭+新区间开启，留痕前后值） */
export const assignManager = (
  id: number,
  data: { userId: number; employeeNo?: string; memberName?: string; effectiveFrom?: string }
) => request.post<boolean>({ url: `${baseUrl}/${id}/actions/assign-manager`, data })

/** 直接下级项目（按需加载，F-PM02） */
export const getChildren = (id: number) =>
  request.get<ProjectMasterVO[]>({ url: `${baseUrl}/${id}/children` })

/** 全部后代项目（F-PM02） */
export const getDescendants = (id: number) =>
  request.get<ProjectMasterVO[]>({ url: `${baseUrl}/${id}/descendants` })

/** 完整上级链（根→父，F-PM02） */
export const getAncestors = (id: number) =>
  request.get<ProjectMasterVO[]>({ url: `${baseUrl}/${id}/ancestors` })

/** 指定业务层级查询（F-PM02） */
export const getByBusinessLevel = (businessLevelCode: string) =>
  request.get<ProjectMasterVO[]>({ url: `${baseUrl}/actions/by-business-level`, params: { businessLevelCode } })

/** 子树移动（校验无环后重建子树缓存，F-PM02） */
export const moveSubtree = (id: number, newParentId: number) =>
  request.post<boolean>({ url: `${baseUrl}/${id}/actions/move`, data: { newParentId } })

/** 进度汇总（直接子项目进度列表 + 汇总进度，F-PM02） */
export const getProgress = (id: number) =>
  request.get<ProjectProgressVO>({ url: `${baseUrl}/${id}/progress` })
