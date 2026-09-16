/**
 * Agent 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-implementation/.../entity/Agent.java` 严格对齐，作为后端
 * Jakarta Validation 注解（本实体未声明）的前端镜像。
 *
 * 字段命名以后端为准：agentName / agentCode / contactPerson / contactPhone /
 * contactEmail / address / qualification / status / overallScore / certLevel /
 * ccieCount / specializations / certExpiryDate。前端
 * `api/implementation.ts` 的 `Agent` interface 字段名已与后端一致，故
 * `fieldMapping` 为空对象。前端额外的 `remark` 为展示字段（后端无对应列），
 * 校验时会被白名单过滤。
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

/** 代理商状态：1=enabled, 0=disabled */
const AGENT_STATUS_ENUM = [0, 1] as const

/** 认证等级：SELECT / PREMIER / SILVER / GOLD */
const CERT_LEVEL_ENUM = ['SELECT', 'PREMIER', 'SILVER', 'GOLD'] as const

// ============ Agent Schema ============
//
// 与后端 Agent.java 字段一一对应：
// - agentName:        String（无注解）
// - agentCode:        String
// - contactPerson:    String
// - contactPhone:     String
// - contactEmail:     String
// - address:          String
// - qualification:    String
// - status:           Integer（1=enabled, 0=disabled）
// - overallScore:     BigDecimal（0-10 平均分）
// - certLevel:        String（SELECT/PREMIER/SILVER/GOLD）
// - ccieCount:        Integer
// - specializations:  String（JSON 数组存为 text）
// - certExpiryDate:   LocalDate
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const agentSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ Agent 自身字段 ============
  agentName: [optional(), string()],
  agentCode: [optional(), string()],
  contactPerson: [optional(), string()],
  contactPhone: [optional(), string()],
  contactEmail: [optional(), string()],
  address: [optional(), string()],
  qualification: [optional(), string()],
  status: [optional(), number()],
  overallScore: [optional(), number()],
  certLevel: [optional(), string()],
  ccieCount: [optional(), number()],
  specializations: [optional(), string()],
  certExpiryDate: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/implementation.ts` 的 `Agent` 字段名与后端一致，无需映射。
 */
export const agentFieldMapping = {} as const

/** Agent 请求体 validator（带旧字段名兼容映射） */
export const agentRequestValidator = createValidatorWithMapping(
  agentSchema,
  agentFieldMapping
)

/** Agent 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateAgentResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(agentSchema, {})(input)
}

/**
 * Agent 类型定义——与后端 Agent 实体字段名严格一致。
 *
 * 与 `api/implementation.ts` 中的 `Agent` interface 字段名一致（前端缺少
 * certLevel/ccieCount/specializations/certExpiryDate，本 DTO 补齐）。
 */
export interface AgentDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // Agent 自身字段
  agentName?: string
  agentCode?: string
  contactPerson?: string
  contactPhone?: string
  contactEmail?: string
  address?: string
  qualification?: string
  /** 1=enabled, 0=disabled */
  status?: typeof AGENT_STATUS_ENUM[number]
  /** 综合评分 0-10（所有评价的平均值） */
  overallScore?: number
  /** 认证等级：SELECT / PREMIER / SILVER / GOLD */
  certLevel?: typeof CERT_LEVEL_ENUM[number]
  /** CCIE 认证工程师数量 */
  ccieCount?: number
  /** 专长领域（JSON 数组存为 text） */
  specializations?: string
  /** 认证到期日期 */
  certExpiryDate?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 agentRequestValidator 校验以下 URL：
// - POST   /api/impl/agent   （新增代理商）
// - PUT    /api/impl/agent   （更新代理商）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/impl/agent',
  requestValidator: agentRequestValidator,
  description: '新增代理商 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/impl/agent',
  requestValidator: agentRequestValidator,
  description: '更新代理商 - 校验请求体字段名'
})
