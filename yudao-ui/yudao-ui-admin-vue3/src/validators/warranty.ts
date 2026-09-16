/**
 * Warranty 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-asset/.../warranty/entity/Warranty.java` 严格对齐，作为后端
 * Jakarta Validation 注解（@NotNull/@Min/@Size 等）的前端镜像。
 *
 * 字段命名以后端为准：startDate / endDate / durationMonths / slaLevel /
 * contractNo / notes 等。
 *
 * 历史问题：前端 `api/warranty.ts` 的 `Warranty` interface 曾使用短名
 * `remark`，与后端的 `notes` 不一致，导致 `POST /api/asset/warranty`
 * 触发字段丢失。本 validator 通过 `fieldMapping` 提供旧字段名→新字段名
 * 的自动映射。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
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

const SLA_LEVEL_ENUM = ['BASIC', 'PREMIUM', 'PLATINUM'] as const

// ============ Warranty Schema ============
//
// 与后端 Warranty.java 字段一一对应：
// - assetId:        @NotNull
// - startDate:      @NotNull LocalDate
// - endDate:        @NotNull LocalDate
// - durationMonths: @NotNull @Min(1) Integer
// - slaLevel:       @Size(max=20) String (BASIC/PREMIUM/PLATINUM)
// - contractNo:     @Size(max=100) String
// - projectId:      Long
// - notes:          @Size(max=2000) String
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const warrantySchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ Warranty 自身字段 ============
  assetId: [required(), number()],
  startDate: [required(), string()],
  endDate: [required(), string()],
  durationMonths: [required(), number(), min(1)],
  slaLevel: [optional(), string(), maxLen(20)],
  contractNo: [optional(), string(), maxLen(100)],
  projectId: [optional(), number()],
  notes: [optional(), string(), maxLen(2000)]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `remark` 时，
 * validator 会自动映射到 `notes`。
 */
export const warrantyFieldMapping = {
  remark: 'notes'
} as const

/** Warranty 请求体 validator（带旧字段名兼容映射） */
export const warrantyRequestValidator = createValidatorWithMapping(
  warrantySchema,
  warrantyFieldMapping
)

/** Warranty 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateWarrantyResponse(input: unknown): ValidationResult {
  // 响应数据已经是后端字段名，无需映射；用同 schema 做白名单过滤
  return createValidatorWithMapping(warrantySchema, {})(input)
}

/**
 * Warranty 类型定义——与后端 Warranty 实体字段名严格一致。
 *
 * 替代 `api/warranty.ts` 中的旧 `Warranty` interface（短名版）。
 */
export interface WarrantyDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // Warranty 自身字段
  assetId: number
  startDate: string
  endDate: string
  durationMonths: number
  slaLevel?: typeof SLA_LEVEL_ENUM[number]
  contractNo?: string
  projectId?: number
  notes?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 warrantyRequestValidator 校验以下 URL：
// - POST   /api/asset/warranty    （创建质保）
// - PUT    /api/asset/warranty     （更新质保）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/asset/warranty',
  requestValidator: warrantyRequestValidator,
  description: '创建质保 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/asset/warranty',
  requestValidator: warrantyRequestValidator,
  description: '更新质保 - 校验请求体字段名'
})
