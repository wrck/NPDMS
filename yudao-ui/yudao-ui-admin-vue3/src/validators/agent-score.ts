/**
 * AgentScore 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-implementation/.../entity/AgentScore.java` 严格对齐，作为后端
 * Jakarta Validation 注解（本实体未声明）的前端镜像。
 *
 * 字段命名以后端为准：agentId / taskId / responseSpeedScore /
 * constructionQualityScore / documentCompletenessScore / overallScore /
 * comment / evaluatorId / evaluatorName / evaluateTime。
 *
 * 历史问题：前端 `api/implementation.ts` 的 `AgentScore` interface 使用
 * `scorer` 表示评分人姓名、`scoreTime` 表示评分时间，与后端 `evaluatorName`
 * / `evaluateTime` 不一致，导致 `POST /api/impl/agent/score/evaluate` 字段名
 * 不匹配。本 validator 通过 `fieldMapping` 提供旧字段名→新字段名的自动映射，
 * 在不破坏现有 view 代码的前提下保证出站请求体字段名正确。
 *
 * 请求体可为 `EvaluatePayload`（评分提交，子集字段）或完整 `AgentScore`
 * （更新场景），两种形态均能通过校验。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  max,
  min,
  number,
  optional,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ AgentScore Schema ============
//
// 与后端 AgentScore.java 字段一一对应：
// - agentId:                       Long（无注解）
// - taskId:                        Long
// - responseSpeedScore:            Integer（0-10）
// - constructionQualityScore:      Integer（0-10）
// - documentCompletenessScore:     Integer（0-10）
// - overallScore:                  BigDecimal
// - comment:                       String
// - evaluatorId:                   Long
// - evaluatorName:                 String
// - evaluateTime:                  LocalDateTime
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const agentScoreSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ AgentScore 自身字段 ============
  agentId: [optional(), number()],
  taskId: [optional(), number()],
  responseSpeedScore: [optional(), number(), min(0), max(10)],
  constructionQualityScore: [optional(), number(), min(0), max(10)],
  documentCompletenessScore: [optional(), number(), min(0), max(10)],
  overallScore: [optional(), number()],
  comment: [optional(), string()],
  evaluatorId: [optional(), number()],
  evaluatorName: [optional(), string()],
  evaluateTime: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `scorer` / `scoreTime` 时，validator
 * 会自动映射到 `evaluatorName` / `evaluateTime`。
 */
export const agentScoreFieldMapping = {
  scorer: 'evaluatorName',
  scoreTime: 'evaluateTime'
} as const

/** AgentScore 请求体 validator（带旧字段名兼容映射） */
export const agentScoreRequestValidator = createValidatorWithMapping(
  agentScoreSchema,
  agentScoreFieldMapping
)

/** AgentScore 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateAgentScoreResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(agentScoreSchema, {})(input)
}

/**
 * AgentScore 类型定义——与后端 AgentScore 实体字段名严格一致。
 *
 * 替代 `api/implementation.ts` 中的旧 `AgentScore` interface（含 `scorer` /
 * `scoreTime` 短名）。现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface AgentScoreDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // AgentScore 自身字段
  agentId?: number
  taskId?: number
  /** 响应速度评分 0-10 */
  responseSpeedScore?: number
  /** 施工质量评分 0-10 */
  constructionQualityScore?: number
  /** 文档完整性评分 0-10 */
  documentCompletenessScore?: number
  /** 本次评价的综合评分 */
  overallScore?: number
  comment?: string
  evaluatorId?: number
  evaluatorName?: string
  evaluateTime?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 agentScoreRequestValidator 校验以下 URL：
// - POST   /api/impl/agent/score/evaluate   （提交代理商评价）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/impl/agent/score/evaluate',
  requestValidator: agentScoreRequestValidator,
  description: '提交代理商评价 - 校验请求体字段名'
})
