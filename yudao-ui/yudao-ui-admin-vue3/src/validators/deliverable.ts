/**
 * Deliverable 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-deliverable/.../entity/Deliverable.java` 严格对齐，作为后端
 * 数据契约的前端镜像。
 *
 * 注：后端 Deliverable 实体未声明 Jakarta Validation 注解（无 @NotBlank/@NotNull
 * 等约束），但本 schema 仍按字段一一对应建立规则，用于：
 * 1. 出站请求体字段白名单过滤（防止前端误传未定义字段污染后端）；
 * 2. 入站响应数据字段白名单过滤（防止后端多余字段污染前端状态）。
 *
 * 字段命名以后端为准：projectId / deliverableName / deliverableType / status 等。
 *
 * 历史问题：前端 `api/deliverable.ts` 的 `Deliverable` interface 字段名已与
 * 后端基本一致，故 `fieldMapping` 为空对象，仅做白名单过滤。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  number,
  optional,
  string,
  boolean,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ Deliverable Schema ============
//
// 与后端 Deliverable.java 字段一一对应（实体本身无 Jakarta Validation 注解）：
// - projectId:        Long
// - deliverableName:  String
// - deliverableType:  String [DOCUMENT/CONFIG/REPORT/OTHER]
// - filePath:         String
// - status:           String [DRAFT/SUBMITTED/REVIEWED/SIGNED/PUBLISHED/REFERENCED/ARCHIVED]
// - phaseId:          Long
// - currentVersion:   Integer
// - mandatory:        Boolean
// - approverRole:     String
// - publishedAt:      LocalDateTime
// - archivedAt:       LocalDateTime
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy）
//     在前端 schema 中以可选形式声明，便于响应数据校验。

const DELIVERABLE_TYPE_ENUM = ['DOCUMENT', 'CONFIG', 'REPORT', 'OTHER'] as const

const DELIVERABLE_STATUS_ENUM = [
  'DRAFT',
  'SUBMITTED',
  'REVIEWED',
  'SIGNED',
  'PUBLISHED',
  'REFERENCED',
  'ARCHIVED'
] as const

export const deliverableSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],

  // ============ Deliverable 自身字段 ============
  projectId: [optional(), number()],
  deliverableName: [optional(), string()],
  deliverableType: [optional(), string()],
  filePath: [optional(), string()],
  status: [optional(), string()],
  phaseId: [optional(), number()],
  currentVersion: [optional(), number()],
  mandatory: [optional(), boolean()],
  approverRole: [optional(), string()],
  publishedAt: [optional(), string()],
  archivedAt: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/deliverable.ts` 的 `Deliverable` interface 字段名已与后端一致，
 * 无需映射，置为空对象。
 */
export const deliverableFieldMapping = {} as const

/** Deliverable 请求体 validator（字段名已一致，无映射） */
export const deliverableRequestValidator = createValidatorWithMapping(
  deliverableSchema,
  deliverableFieldMapping
)

/** Deliverable 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateDeliverableResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(deliverableSchema, {})(input)
}

/**
 * Deliverable 类型定义——与后端 Deliverable 实体字段名严格一致。
 *
 * 替代 `api/deliverable.ts` 中的旧 `Deliverable` interface。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface DeliverableDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string

  // Deliverable 自身字段
  projectId?: number
  deliverableName?: string
  deliverableType?: typeof DELIVERABLE_TYPE_ENUM[number]
  filePath?: string
  status?: typeof DELIVERABLE_STATUS_ENUM[number]
  phaseId?: number
  currentVersion?: number
  mandatory?: boolean
  approverRole?: string
  publishedAt?: string
  archivedAt?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 deliverableRequestValidator 校验以下 URL：
// - POST /api/deliverable      （创建交付件）
// - PUT  /api/deliverable/{id} （更新交付件基础信息）
//
// 校验失败 → ElMessage.error + 阻止请求发送
registerValidator({
  method: 'POST',
  urlPattern: '/api/deliverable',
  requestValidator: deliverableRequestValidator,
  description: '创建交付件 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: /^\/api\/deliverable\/\d+$/,
  requestValidator: deliverableRequestValidator,
  description: '更新交付件 - 校验请求体字段名'
})
