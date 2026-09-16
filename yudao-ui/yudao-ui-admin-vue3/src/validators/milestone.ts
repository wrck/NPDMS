/**
 * Milestone 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-project/.../entity/Milestone.java` 严格对齐，作为后端
 * 数据契约的前端镜像。
 *
 * 注：后端 Milestone 实体未声明 Jakarta Validation 注解（无 @NotBlank/@NotNull
 * 等约束），但本 schema 仍按字段一一对应建立规则，用于：
 * 1. 出站请求体字段白名单过滤（防止前端误传未定义字段污染后端）；
 * 2. 入站响应数据字段白名单过滤（防止后端多余字段污染前端状态）。
 *
 * 字段命名以后端为准：milestoneName / milestoneType / ppdiooPhase 等。
 *
 * 历史问题：前端 `api/project.ts` 的 `Milestone` interface 使用短名
 * `name/type/plannedDate`，与后端字段名
 * `milestoneName/milestoneType/planDate` 不一致，可能导致字段对不上后端。
 * 本 validator 通过 `fieldMapping` 提供旧字段名→新字段名的自动映射。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  number,
  optional,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ Milestone Schema ============
//
// 与后端 Milestone.java 字段一一对应（实体本身无 Jakarta Validation 注解）：
// - projectId:      Long
// - milestoneName:  String
// - milestoneType:  String [SITE_SURVEY/NETWORK_DESIGN/PROCUREMENT/STAGING/FAT/
//                       ARRIVAL/INSTALLATION/TESTING/COMMISSIONING/SAT/UAT/FINAL_ACCEPTANCE]
// - ppdiooPhase:    String [PREPARE/PLAN/DESIGN/IMPLEMENT/OPERATE]
// - planDate:       LocalDate
// - actualDate:     LocalDate
// - status:         String [PENDING/IN_PROGRESS/COMPLETED/OVERDUE/BLOCKED]
// - description:    String
// - sortOrder:      Integer
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy）
//     在前端 schema 中以可选形式声明，便于响应数据校验。

const MILESTONE_TYPE_ENUM = [
  'SITE_SURVEY',
  'NETWORK_DESIGN',
  'PROCUREMENT',
  'STAGING',
  'FAT',
  'ARRIVAL',
  'INSTALLATION',
  'TESTING',
  'COMMISSIONING',
  'SAT',
  'UAT',
  'FINAL_ACCEPTANCE'
] as const

const MILESTONE_PPDIOO_PHASE_ENUM = ['PREPARE', 'PLAN', 'DESIGN', 'IMPLEMENT', 'OPERATE'] as const

const MILESTONE_STATUS_ENUM = [
  'PENDING',
  'IN_PROGRESS',
  'COMPLETED',
  'OVERDUE',
  'BLOCKED'
] as const

export const milestoneSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],

  // ============ Milestone 自身字段 ============
  projectId: [optional(), number()],
  milestoneName: [optional(), string()],
  milestoneType: [optional(), string()],
  ppdiooPhase: [optional(), string()],
  planDate: [optional(), string()],
  actualDate: [optional(), string()],
  status: [optional(), string()],
  description: [optional(), string()],
  sortOrder: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `name/type/plannedDate` 时，
 * validator 会自动映射到 `milestoneName/milestoneType/planDate`。
 */
export const milestoneFieldMapping = {
  name: 'milestoneName',
  type: 'milestoneType',
  plannedDate: 'planDate'
} as const

/** Milestone 请求体 validator（带旧字段名兼容映射） */
export const milestoneRequestValidator = createValidatorWithMapping(
  milestoneSchema,
  milestoneFieldMapping
)

/** Milestone 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateMilestoneResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(milestoneSchema, {})(input)
}

/**
 * Milestone 类型定义——与后端 Milestone 实体字段名严格一致。
 *
 * 替代 `api/project.ts` 中的旧 `Milestone` interface（含旧短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface MilestoneDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string

  // Milestone 自身字段
  projectId?: number
  milestoneName?: string
  milestoneType?: typeof MILESTONE_TYPE_ENUM[number]
  ppdiooPhase?: typeof MILESTONE_PPDIOO_PHASE_ENUM[number]
  planDate?: string
  actualDate?: string
  status?: typeof MILESTONE_STATUS_ENUM[number]
  description?: string
  sortOrder?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 milestoneRequestValidator 校验以下 URL：
// - POST /api/project/milestone （创建里程碑）
// - PUT  /api/project/milestone （更新里程碑）
//
// 校验失败 → ElMessage.error + 阻止请求发送
registerValidator({
  method: 'POST',
  urlPattern: '/api/project/milestone',
  requestValidator: milestoneRequestValidator,
  description: '创建里程碑 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/project/milestone',
  requestValidator: milestoneRequestValidator,
  description: '更新里程碑 - 校验请求体字段名'
})
