/**
 * Risk 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-governance/.../risk/entity/Risk.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@NotNull/@Size/@Min/@Max 等）的前端镜像。
 *
 * 字段命名以后端为准：riskNo / projectId / description / likelihood / impact / score 等。
 *
 * 历史问题：前端 `api/risk.ts` 的 `Risk` interface 使用 `createdAt`（驼峰风格），
 * 与后端 BaseEntity 字段名 `createTime` 不一致。本 validator 通过 `fieldMapping`
 * 提供旧字段名→新字段名的自动映射，在不破坏现有 view 代码的前提下保证出站请求体
 * 字段名正确。
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
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ Risk Schema ============
//
// 与后端 Risk.java 字段一一对应：
// - riskNo:           @NotBlank @Size(max=50)
// - projectId:        @NotNull Long
// - description:      @NotBlank @Size(max=2000)
// - category:         @Size(max=50) [TECHNICAL/EXTERNAL/ORGANIZATIONAL/PM]
// - likelihood:       @NotNull @Min(1) @Max(5) Integer
// - impact:           @NotNull @Min(1) @Max(5) Integer
// - score:            @Min(1) @Max(25) Integer（= likelihood * impact）
// - priority:         @Size(max=20) [LOW/MEDIUM/HIGH]
// - mitigation:       @Size(max=50) [AVOID/MITIGATE/TRANSFER/ACCEPT]
// - contingencyPlan:  @Size(max=2000)
// - ownerId:          Long
// - ownerName:        @Size(max=50)
// - status:           @Size(max=50) [OPEN/IN_PROGRESS/CLOSED/ESCALATED]
// - reviewDate:       LocalDate
// - sourceIssueId:    Long
// - identifiedAt:     LocalDateTime
// - closedAt:         LocalDateTime
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy）
//     在前端 schema 中以可选形式声明，便于响应数据校验。

const RISK_CATEGORY_ENUM = ['TECHNICAL', 'EXTERNAL', 'ORGANIZATIONAL', 'PM'] as const
const RISK_PRIORITY_ENUM = ['LOW', 'MEDIUM', 'HIGH'] as const
const RISK_MITIGATION_ENUM = ['AVOID', 'MITIGATE', 'TRANSFER', 'ACCEPT'] as const
const RISK_STATUS_ENUM = ['OPEN', 'IN_PROGRESS', 'CLOSED', 'ESCALATED'] as const

export const riskSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],

  // ============ Risk 自身字段 ============
  riskNo: [required(), string(), maxLen(50)],
  projectId: [required(), number()],
  description: [required(), string(), maxLen(2000)],
  category: [optional(), string(), maxLen(50)],
  likelihood: [required(), number(), min(1), max(5)],
  impact: [required(), number(), min(1), max(5)],
  score: [optional(), number(), min(1), max(25)],
  priority: [optional(), string(), maxLen(20)],
  mitigation: [optional(), string(), maxLen(50)],
  contingencyPlan: [optional(), string(), maxLen(2000)],
  ownerId: [optional(), number()],
  ownerName: [optional(), string(), maxLen(50)],
  status: [optional(), string(), maxLen(50)],
  reviewDate: [optional(), string()],
  sourceIssueId: [optional(), number()],
  identifiedAt: [optional(), string()],
  closedAt: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `createdAt`（驼峰）时，
 * validator 会自动映射到 `createTime`（后端 BaseEntity 字段名）。
 */
export const riskFieldMapping = {
  createdAt: 'createTime'
} as const

/** Risk 请求体 validator（带旧字段名兼容映射） */
export const riskRequestValidator = createValidatorWithMapping(
  riskSchema,
  riskFieldMapping
)

/** Risk 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateRiskResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(riskSchema, {})(input)
}

/**
 * Risk 类型定义——与后端 Risk 实体字段名严格一致。
 *
 * 替代 `api/risk.ts` 中的旧 `Risk` interface（含旧短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface RiskDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string

  // Risk 自身字段
  riskNo: string
  projectId: number
  description: string
  category?: typeof RISK_CATEGORY_ENUM[number]
  likelihood: number
  impact: number
  score?: number
  priority?: typeof RISK_PRIORITY_ENUM[number]
  mitigation?: typeof RISK_MITIGATION_ENUM[number]
  contingencyPlan?: string
  ownerId?: number
  ownerName?: string
  status?: typeof RISK_STATUS_ENUM[number]
  reviewDate?: string
  sourceIssueId?: number
  identifiedAt?: string
  closedAt?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 riskRequestValidator 校验以下 URL：
// - POST /api/governance/risk （创建风险）
// - PUT  /api/governance/risk （更新风险）
//
// 校验失败 → ElMessage.error + 阻止请求发送
registerValidator({
  method: 'POST',
  urlPattern: '/api/governance/risk',
  requestValidator: riskRequestValidator,
  description: '创建风险 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/governance/risk',
  requestValidator: riskRequestValidator,
  description: '更新风险 - 校验请求体字段名'
})
