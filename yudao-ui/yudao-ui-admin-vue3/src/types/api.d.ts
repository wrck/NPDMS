/**
 * 全局 API 响应类型定义。
 *
 * <p>本文件集中声明后端统一信封 {@link Result}、分页结果 {@link PageResult}
 * 与各业务领域 DTO 的「契约视图」。各 api/*.ts 模块在自身文件内仍可定义
 * 更精确的局部接口（例如带可选字段的实体表单），二者互为补充：
 * <ul>
 *   <li>本文件提供「公共契约」供跨模块复用；</li>
 *   <li>各 api/*.ts 提供「模块本地类型」供单文件消费。</li>
 * </ul>
 * </p>
 *
 * <p>本文件为 ambient declaration（.d.ts），仅导出类型，不产生运行时代码。</p>
 */

/** 统一响应包装（与后端 Result.java 对应） */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
  success: boolean
  timestamp?: number
}

/** 分页响应（与后端 IPage 序列化结构对应） */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// ===================== 用户相关 =====================

export interface UserInfo {
  id: number
  username: string
  realName: string
  email?: string
  phone?: string
  status: string
  deptId?: number
  companyId?: number
  roles?: string[]
  permissions?: string[]
}

export interface LoginRequest {
  username: string
  password: string
  captcha?: string
  captchaKey?: string
}

export interface LoginResponse {
  token: string
  refreshToken?: string
  userId: number
  username: string
  realName: string
  expiresAt?: number
}

// ===================== 项目相关 =====================

export interface Project {
  id: number
  projectCode: string
  projectName: string
  projectType: string
  status: string
  customerId: number
  customerName?: string
  startDate: string
  endDate: string
  budget?: number
  description?: string
  pmId?: number
  pmName?: string
  createdAt: string
  updatedAt: string
}

export interface ProjectQuery {
  page?: number
  size?: number
  projectCode?: string
  projectName?: string
  status?: string
  projectType?: string
  customerId?: number
}

// ===================== 资产相关 =====================

export interface Asset {
  id: number
  assetNo: string
  serialNo: string
  model?: string
  projectId: number
  status: string
  warehouse?: string
  location?: string
  warrantyId?: number
  macAddress?: string
  managementIp?: string
  hostname?: string
  createdAt: string
  updatedAt: string
}

// ===================== 里程碑 =====================

export interface Milestone {
  id: number
  projectId: number
  milestoneType: string
  ppdiooPhase?: string
  plannedDate: string
  actualDate?: string
  status: string
  remark?: string
}

// ===================== 实施任务 =====================

export interface ImplTask {
  id: number
  taskNo: string
  projectId: number
  assigneeId?: number
  serviceType?: string
  status: string
  startTime?: string
  endTime?: string
  description?: string
}

// ===================== 结算 =====================

export interface Settlement {
  id: number
  settlementNo: string
  projectId: number
  type: string
  amount: number
  status: string
  paymentStatus?: string
  invoiceNo?: string
  createdAt: string
}

// ===================== 变更请求 =====================

export interface ChangeRequest {
  id: number
  crNo: string
  projectId: number
  title: string
  description?: string
  priority: string
  status: string
  impactScope?: string
  impactSchedule?: number
  impactCost?: number
  impactQuality?: string
}

// ===================== 风险 =====================

export interface Risk {
  id: number
  riskNo: string
  projectId: number
  description: string
  category: string
  likelihood: number
  impact: number
  score: number
  priority: string
  status: string
  ownerId?: number
}

// ===================== 问题 =====================

export interface Issue {
  id: number
  issueNo: string
  projectId: number
  description: string
  priority: string
  status: string
  assigneeId?: number
  sourceRiskId?: number
  sourceChangeId?: number
}

// ===================== RMA =====================

export interface Rma {
  id: number
  rmaNo: string
  assetId: number
  ticketStatus: string
  warrantyStatus: string
  faultDescription?: string
  createdAt: string
}

// ===================== 质保 =====================

export interface Warranty {
  id: number
  assetId: number
  startDate: string
  endDate: string
  durationMonths: number
  slaLevel: string
  contractNo?: string
}

// ===================== Punch List =====================

export interface PunchList {
  id: number
  projectId: number
  milestoneId?: number
  severity: string
  title: string
  description?: string
  walkdownStage?: string
  assigneeId?: number
  deadline?: string
  status: string
}

// ===================== 通知 =====================

export interface Notification {
  id: number
  userId: number
  title: string
  content: string
  category: string
  bizType?: string
  bizId?: number
  readStatus: string
  createdAt: string
}

// ===================== 附件 =====================

export interface Attachment {
  id: number
  bizType: string
  bizId: number
  fileName: string
  fileSize: number
  mimeType: string
  storagePath: string
  storageType: string
  uploadUserId: number
  uploadTime: string
}

// ===================== 字典 =====================

export interface SysDict {
  id: number
  dictCode: string
  dictName: string
  description?: string
  status: string
}

export interface SysDictItem {
  id: number
  dictCode: string
  itemValue: string
  itemLabel: string
  sort: number
  status: string
}

// ===================== 集成健康状态 =====================

export interface IntegrationHealth {
  system: string
  status: string
  tokenValid: boolean
  lastPushAt?: string
  lastPushStatus?: string
  failureCount: number
  totalPush: number
}
