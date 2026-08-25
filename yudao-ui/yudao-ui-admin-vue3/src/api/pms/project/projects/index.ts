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
  companyId?: number
  companyCode?: string
  companyName?: string
  departmentId?: number
  departmentCode?: string
  departmentName?: string
  signingMethod?: string
  projectCategory?: string
  implementationMode?: string
  majorProjectLevel?: string | null
  contractNo?: string
  implementationLocation?: string
  locationResolutionStatus?: 'RESOLVED' | 'UNRESOLVED'
  creationReason?: string
  lifecycleTemplateId?: number
  lifecycleTemplateRevisionNo?: number
  templateLoadMethod?: string
  processDefinitionKey?: string
  processDefinitionVersion?: string
  sourceType?: string
  status?: string
  lifecycleStatus?: string
  currentStage?: string
  assignmentStatus?: string
  version?: number
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
  orderOfficeCompanyId: number
  orderOfficeDepartmentId: number
  sites?: ProjectSiteReqVO[]
  implementationLocation?: string
  signingMethod?: string
  projectCategory?: string
  implementationMode?: string
  majorProjectLevel?: string | null
  creationReason: string
  templateRevisionId?: number | null
  candidateWatermark?: string
}

export interface ProjectSiteReqVO {
  siteId: number
  siteVersion: number
  primarySite: boolean
}

export interface ProjectSiteVO {
  id: number
  projectId: number
  siteId: number
  siteVersionSnapshot: number
  primarySite: boolean
  scopeStatus: string
  siteCodeSnapshot?: string
  siteNameSnapshot?: string
  addressSnapshot?: string
}

/** 创建响应（含实例化摘要） */
export interface ProjectCreateRespVO extends ProjectMasterVO {
  stageCount?: number
  taskCount?: number
  milestoneCount?: number
  deliverableCount?: number
  gateCount?: number
  serviceManagerAssigned?: boolean
  matchResult?: string
  matchDecisionMode?: string
  matchOperationId?: string
}

export interface ProjectAttributeClassifyReqVO {
  signingMethod: string
  projectCategory: string
  implementationMode: string
  adjustmentReason: string
}

export interface ProjectAttributeClassifyRespVO {
  projectId: number
  version: number
  matchResult: string
  impactResult: string
  operationId: string
}

export interface ProjectTemplateMatchHistoryVO {
  id: number
  projectId: number
  triggerType: string
  recordPurpose: string
  inputOrigin: string
  beforeAttributeSnapshot?: string | null
  attributeSnapshot: string
  attributeOwnerSnapshot: string
  sourceSystem?: string | null
  sourceVersion?: string | null
  matcherVersion: string
  matchResult: string
  candidateDigest: string
  decisionMode?: string | null
  matchedTemplateId?: number | null
  matchedTemplateRevisionId?: number | null
  frozenTemplateRevisionId?: number | null
  impactResult: string
  operatorId: number
  changeReason: string
  occurredAt: string
  recordedAt: string
  operationId: string
  traceId?: string | null
  auditLogId?: number | null
}

export type ProjectTemplateMatchHistoryPageParams = PageParam & {
  triggerType?: string
  matchResult?: string
  impactResult?: string
  occurredAtBegin?: string
  occurredAtEnd?: string
  orderBy?: 'occurredAt' | 'recordedAt' | 'id'
  ascending?: boolean
}

/** 模板匹配候选 */
export interface TemplateCandidateVO {
  templateId: number
  templateRevisionId: number
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
  candidateWatermark: string
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

export type ProjectTreeQueryType =
  | 'CHILDREN'
  | 'DESCENDANTS'
  | 'ANCESTORS'
  | 'BUSINESS_LEVEL'
  | 'LOCATE'

export interface ProjectTreeNodeVO {
  projectId: number
  projectName?: string
  lifecycleStatus?: string
  currentStage?: string
  milestoneProgress?: number
  visibility: 'FULL' | 'ROOT_SUMMARY' | 'PATH_PLACEHOLDER'
}

export interface ProjectTreeQueryVO {
  treeVersion: number
  items: ProjectTreeNodeVO[]
  nextCursor?: string
  updating: boolean
}

export interface ProjectProgressVO {
  projectId: number
  policyRevisionId?: number
  treeVersion: number
  sourceWatermark?: string
  status: 'READY' | 'PENDING'
  progress?: number
  items: {
    childProjectId: number
    factVersion?: number
    childProgress?: number
    normalizedWeight?: number
    contribution?: number
    missingReason?: string
  }[]
}

export interface ProjectProgressPolicyVO {
  id: number
  parentProjectId: number
  revisionNo: number
  status: string
  policyType: 'SYSTEM_EQUAL' | 'MANUAL'
  processInstanceId?: string
  effectiveFrom?: string
  effectiveTo?: string
  approvedBy?: number
  approvedAt?: string
  version: number
  items: { childProjectId: number; weight: number; includeStatuses?: string[] }[]
}

export interface ProjectClosureGuardVO {
  allowed: boolean
  treeVersion: number
  blockers: {
    projectId: number
    projectCode?: string
    projectName?: string
    blockerType: 'EXECUTING' | 'PAUSED' | 'CLOSURE_APPROVING'
  }[]
  pendingProgressProjects: number[]
}

export type ProjectAuthorizationAction = 'PROJECT_VIEW' | 'PROJECT_MANAGE'
export type ProjectAuthorizationScope = 'CURRENT_PROJECT' | 'PROJECT_AND_DESCENDANTS'
export type ProjectAuthorizationStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface ProjectAuthorizationVO {
  id: number
  subjectUserId: number
  projectId: number
  actionCode: ProjectAuthorizationAction
  scopeCode: ProjectAuthorizationScope
  effectiveFrom: string
  effectiveTo?: string | null
  statusCode: ProjectAuthorizationStatus
  grantedBy: number
  grantedAt: string
  revokedBy?: number | null
  revokedAt?: string | null
  revokeReason?: string | null
  version: number
}

export interface ProjectAuthorizationCreateReqVO {
  subjectUserId: number
  actionCode: ProjectAuthorizationAction
  scopeCode: ProjectAuthorizationScope
  effectiveFrom?: string
  effectiveTo?: string
  reason?: string
}

export type ProjectAuthorizationPageParams = PageParam & {
  subjectUserId?: number
  actionCode?: ProjectAuthorizationAction
  scopeCode?: ProjectAuthorizationScope
  statusCode?: ProjectAuthorizationStatus
  effectiveAt?: string
}

/** 手工创建项目（Idempotency-Key 幂等：同键同摘要重放返回原资源） */
export const createProject = (data: ProjectCreateReqVO, idempotencyKey: string) =>
  request.post<ProjectCreateRespVO>({
    url: baseUrl,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

/** 按三维+级别实时匹配生效模板（创建向导第②步） */
export const matchTemplates = (params: {
  signingMethod?: string
  projectCategory?: string
  implementationMode?: string
  majorProjectLevel?: string
}) =>
  request.get<ProjectMatchTemplatesRespVO>({ url: `${baseUrl}/actions/match-templates`, params })

/** 分页查询（名称/编码/状态/三维过滤） */
export const getProjectPage = (params: PageParam) =>
  request.get<{ list: ProjectMasterVO[]; total: number }>({ url: `${baseUrl}/page`, params })

/** 项目详情（基本信息+四维+模板绑定） */
export const getProject = (id: number) => request.get<ProjectMasterVO>({ url: `${baseUrl}/${id}` })

export const classifyProject = (
  id: number,
  data: ProjectAttributeClassifyReqVO,
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post<ProjectAttributeClassifyRespVO>({
    url: `${baseUrl}/${id}/actions/classify`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedVersion) }
  })

export const getProjectTemplateMatchHistoryPage = (
  id: number,
  params: ProjectTemplateMatchHistoryPageParams
) =>
  request.get<{ list: ProjectTemplateMatchHistoryVO[]; total: number }>({
    url: `${baseUrl}/${id}/template-match-history`,
    params
  })

export const getProjectSites = (id: number) =>
  request.get<ProjectSiteVO[]>({ url: `${baseUrl}/${id}/sites` })

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

export interface ServiceManagerCandidateVO {
  userId: number
  username: string
  nickname: string
  employeeNo?: string
  companyId: number
  departmentId: number
  departmentCode: string
  departmentName: string
}

export interface ServiceManagerResponsibilityVO {
  projectId: number
  projectCode: string
  projectName: string
  parentId?: number
  treeDepth: number
  assignmentStatus: string
  responsibilities: Array<{
    levelCode: 'L1' | 'L2'
    siteId?: number
    departmentId: number
    departmentCode: string
    departmentName: string
    primaryManager?: ServiceManagerResponsibilityMemberVO
    collaborators: ServiceManagerResponsibilityMemberVO[]
  }>
}

export interface ServiceManagerResponsibilityMemberVO {
  assignmentId: number
  userId: number
  employeeNo?: string
  memberName: string
  effectiveFrom: string
  changeReason: string
}

export const getServiceManagerCandidates = (
  id: number,
  params: {
    siteId?: number
    departmentId: number
    departmentCode: string
    keyword?: string
    pageNo: number
    pageSize: number
  }
) =>
  request.get<PageResult<ServiceManagerCandidateVO[]>>({
    url: `${baseUrl}/${id}/service-manager-candidates`,
    params
  })

export const getServiceManagerResponsibilities = (
  rootId: number,
  params: { projectId?: number; pageNo: number; pageSize: number }
) =>
  request.get<PageResult<ServiceManagerResponsibilityVO[]>>({
    url: `${baseUrl}/${rootId}/service-manager-responsibilities`,
    params
  })

/** 人工指派或改派主责/协同服务经理 */
export const assignManager = (
  id: number,
  data: {
    levelCode: 'L1' | 'L2'
    managerId: number
    siteId?: number
    assignmentType: 'PRIMARY' | 'COLLABORATOR'
    departmentId: number
    departmentCode: string
    changeReason: string
  },
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post<{
    projectId: number
    assignmentId: number
    version: number
    assignmentStatus: string
    effectiveFrom: string
  }>({
    url: `${baseUrl}/${id}/actions/assign-manager`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedVersion) }
  })

export const queryTree = (
  id: number,
  params: {
    queryType: ProjectTreeQueryType
    businessLevelCode?: string
    pageSize?: number
    cursor?: string
  }
) => request.get<ProjectTreeQueryVO>({ url: `${baseUrl}/${id}/tree`, params })

export const moveSubtree = (
  id: number,
  data: { newParentId: number; reason?: string },
  expectedTreeVersion: number,
  idempotencyKey: string
) =>
  request.post<{ treeVersion: number }>({
    url: `${baseUrl}/${id}/actions/move`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedTreeVersion) }
  })

export const getProgress = (id: number) =>
  request.get<ProjectProgressVO>({ url: `${baseUrl}/${id}/progress` })

export const getProgressPolicies = (id: number) =>
  request.get<ProjectProgressPolicyVO[]>({ url: `${baseUrl}/${id}/progress-policies` })

export const createProgressPolicy = (
  id: number,
  data: {
    policyType: 'SYSTEM_EQUAL' | 'MANUAL'
    items: { childProjectId: number; weight: number; includeStatuses?: string[] }[]
  },
  idempotencyKey: string,
  expectedTreeVersion: number
) =>
  request.post<number>({
    url: `${baseUrl}/${id}/progress-policies`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedTreeVersion) }
  })

export const submitProgressPolicy = (
  revisionId: number,
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post<string>({
    url: `/pms/progress-policies/${revisionId}/actions/submit`,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedVersion) }
  })

export const getClosureGuard = (projectId: number, treeVersion: number) =>
  request.get<ProjectClosureGuardVO>({
    url: `/pms/closure-gates/${projectId}`,
    params: { treeVersion }
  })

export const getProjectAuthorizationPage = (
  projectId: number,
  params: ProjectAuthorizationPageParams
) =>
  request.get<{ list: ProjectAuthorizationVO[]; total: number }>({
    url: `${baseUrl}/${projectId}/authorization-grants`,
    params
  })

export const createProjectAuthorization = (
  projectId: number,
  data: ProjectAuthorizationCreateReqVO,
  idempotencyKey: string
) =>
  request.post<ProjectAuthorizationVO>({
    url: `${baseUrl}/${projectId}/authorization-grants`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const getProjectAuthorization = (grantId: number) =>
  request.get<ProjectAuthorizationVO>({ url: `/pms/project-authorization-grants/${grantId}` })

export const revokeProjectAuthorization = (
  grantId: number,
  expectedVersion: number,
  reason: string,
  idempotencyKey: string
) =>
  request.post<ProjectAuthorizationVO>({
    url: `/pms/project-authorization-grants/${grantId}/actions/revoke`,
    data: { reason },
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedVersion) }
  })
