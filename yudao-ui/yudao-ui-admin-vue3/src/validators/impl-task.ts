/**
 * ImplTask 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-implementation/.../entity/ImplTask.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@NotNull/@Size/@Min/@Max 等）的前端镜像。
 *
 * 字段命名以后端为准：projectId / taskName / taskType / progress 等。
 *
 * 历史问题：前端 `api/implementation.ts` 的 `ImplTask` interface 部分字段使用短名
 * （`actualStart/actualEnd/description`），与后端字段名
 * （`actualStartDate/actualEndDate/workDescription`）不一致，导致
 * `POST /api/implementation/task/oem/assign` 触发 400 校验失败。
 * 本 validator 通过 `fieldMapping` 提供旧字段名→新字段名的自动映射，
 * 在不破坏现有 view 代码的前提下保证出站请求体字段名正确。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  max,
  maxLen,
  min,
  number,
  optional,
  required,
  string,
  boolean,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ ImplTask Schema ============
//
// 与后端 ImplTask.java 字段一一对应：
// - projectId:        @NotNull Long
// - milestoneId:      Long
// - taskName:         @NotBlank @Size(max=200)
// - taskType:         @NotBlank @Size(max=20) [OEM/AGENT]
// - agentId:          Long
// - engineerId:       Long
// - engineerName:     @Size(max=50)
// - planStartDate / planEndDate: LocalDate
// - actualStartDate / actualEndDate: LocalDate
// - status:           @Size(max=50) [PENDING/ACCEPTED/IN_PROGRESS/COMPLETED/CONFIRMED/REJECTED]
// - progress:         @Min(0) @Max(100)
// - workDescription:  @Size(max=2000)
// - acceptOpinion:    String
// - acceptUserId:     Long
// - acceptUserName:   @Size(max=50)
// - acceptTime:       LocalDateTime
// - customerContact:  @Size(max=100)
// - serviceAddress:   @Size(max=500)
// - serviceType:      @Size(max=50) [SITE_SURVEY/INSTALL/DEBUG/MAINTENANCE]
// - sopSteps:         String
// - materialList:     String
// - plannedHours:     Integer
// - skillLevel:       @Size(max=20) [JUNIOR/SENIOR/EXPERT]
// - safetyPpe:        String
// - evidenceCheckpoints: String
// - signOffRequired:  Boolean (default true)
// - parentTaskId:     Long
// - taskPath:         @Size(max=500) (default "/")
// - depth:            Integer (default 0)
// - priority:         @Size(max=16) (default "MEDIUM") [LOW/MEDIUM/HIGH/CRITICAL]
// - actualHours:      BigDecimal
// - remainingHours:   BigDecimal
// - phaseId:          Long
// - taskWeight:       BigDecimal (default 1)
// - version:          @Version Integer
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy）
//     在前端 schema 中以可选形式声明，便于响应数据校验。

const IMPL_TASK_TYPE_ENUM = ['OEM', 'AGENT'] as const
const IMPL_TASK_STATUS_ENUM = [
  'PENDING',
  'ACCEPTED',
  'IN_PROGRESS',
  'COMPLETED',
  'CONFIRMED',
  'REJECTED'
] as const
const IMPL_TASK_SERVICE_TYPE_ENUM = [
  'SITE_SURVEY',
  'INSTALL',
  'DEBUG',
  'MAINTENANCE'
] as const
const IMPL_TASK_SKILL_LEVEL_ENUM = ['JUNIOR', 'SENIOR', 'EXPERT'] as const
const IMPL_TASK_PRIORITY_ENUM = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const

export const implTaskSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],

  // ============ ImplTask 自身字段 ============
  projectId: [required(), number()],
  milestoneId: [optional(), number()],
  taskName: [required(), string(), maxLen(200)],
  taskType: [required(), string(), maxLen(20)],
  agentId: [optional(), number()],
  engineerId: [optional(), number()],
  engineerName: [optional(), string(), maxLen(50)],
  planStartDate: [optional(), string()],
  planEndDate: [optional(), string()],
  actualStartDate: [optional(), string()],
  actualEndDate: [optional(), string()],
  status: [optional(), string(), maxLen(50)],
  progress: [optional(), number(), min(0), max(100)],
  workDescription: [optional(), string(), maxLen(2000)],
  acceptOpinion: [optional(), string()],
  acceptUserId: [optional(), number()],
  acceptUserName: [optional(), string(), maxLen(50)],
  acceptTime: [optional(), string()],
  customerContact: [optional(), string(), maxLen(100)],
  serviceAddress: [optional(), string(), maxLen(500)],
  serviceType: [optional(), string(), maxLen(50)],
  sopSteps: [optional(), string()],
  materialList: [optional(), string()],
  plannedHours: [optional(), number()],
  skillLevel: [optional(), string(), maxLen(20)],
  safetyPpe: [optional(), string()],
  evidenceCheckpoints: [optional(), string()],
  signOffRequired: [optional(), boolean()],
  parentTaskId: [optional(), number()],
  taskPath: [optional(), string(), maxLen(500)],
  depth: [optional(), number()],
  priority: [optional(), string(), maxLen(16)],
  actualHours: [optional(), number()],
  remainingHours: [optional(), number()],
  phaseId: [optional(), number()],
  taskWeight: [optional(), number()],
  version: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `actualStart/actualEnd/description` 时，
 * validator 会自动映射到 `actualStartDate/actualEndDate/workDescription`。
 *
 * 注：前端 `api/implementation.ts` 的 `ImplTask` interface 还包含
 * `projectName/agentName/remark` 字段，这些字段后端 `ImplTask` 实体未定义，
 * 不会出现在 schema 中，normalize 时会被白名单过滤掉。
 */
export const implTaskFieldMapping = {
  actualStart: 'actualStartDate',
  actualEnd: 'actualEndDate',
  description: 'workDescription'
} as const

/** ImplTask 请求体 validator（带旧字段名兼容映射） */
export const implTaskRequestValidator = createValidatorWithMapping(
  implTaskSchema,
  implTaskFieldMapping
)

/** ImplTask 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateImplTaskResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(implTaskSchema, {})(input)
}

/**
 * ImplTask 类型定义——与后端 ImplTask 实体字段名严格一致。
 *
 * 替代 `api/implementation.ts` 中的旧 `ImplTask` interface（含旧短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface ImplTaskDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string

  // ImplTask 自身字段
  projectId: number
  milestoneId?: number
  taskName: string
  taskType: typeof IMPL_TASK_TYPE_ENUM[number]
  agentId?: number
  engineerId?: number
  engineerName?: string
  planStartDate?: string
  planEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  status?: typeof IMPL_TASK_STATUS_ENUM[number]
  progress?: number
  workDescription?: string
  acceptOpinion?: string
  acceptUserId?: number
  acceptUserName?: string
  acceptTime?: string
  customerContact?: string
  serviceAddress?: string
  serviceType?: typeof IMPL_TASK_SERVICE_TYPE_ENUM[number]
  sopSteps?: string
  materialList?: string
  plannedHours?: number
  skillLevel?: typeof IMPL_TASK_SKILL_LEVEL_ENUM[number]
  safetyPpe?: string
  evidenceCheckpoints?: string
  signOffRequired?: boolean
  parentTaskId?: number
  taskPath?: string
  depth?: number
  priority?: typeof IMPL_TASK_PRIORITY_ENUM[number]
  actualHours?: number
  remainingHours?: number
  phaseId?: number
  taskWeight?: number
  version?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 implTaskRequestValidator 校验以下 URL：
// - POST /api/implementation/task/oem/assign  （OEM 任务分派）
// - POST /api/implementation/task/agent/assign（代理商任务分派）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/implementation/task/oem/assign',
  requestValidator: implTaskRequestValidator,
  description: 'OEM 任务分派 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'POST',
  urlPattern: '/api/implementation/task/agent/assign',
  requestValidator: implTaskRequestValidator,
  description: '代理商任务分派 - 校验请求体字段名与必填'
})
