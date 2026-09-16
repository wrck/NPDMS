/**
 * HelpContent 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/HelpContent.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：category / title / content / sortOrder / status /
 * viewCount 等。前端 `api/help.ts` 的 `HelpContent` interface 字段名已与
 * 后端一致，故 `fieldMapping` 为空对象。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  maxLen,
  number,
  optional,
  pattern,
  required,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ HelpContent Schema ============
//
// 与后端 HelpContent.java 字段一一对应：
// - category:   @NotBlank @Pattern("^(QUICK_START|FAQ|VIDEO|ADVANCED)$")
// - title:      @NotBlank @Size(max=200)
// - content:    @NotBlank
// - sortOrder:  Integer
// - status:     @Pattern("^[01]$")（0=启用，1=禁用）
// - viewCount:  Integer
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
const HELP_CATEGORY_PATTERN = /^(QUICK_START|FAQ|VIDEO|ADVANCED)$/
const HELP_STATUS_PATTERN = /^[01]$/

export const helpContentSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ HelpContent 自身字段 ============
  category: [required(), string(), pattern(HELP_CATEGORY_PATTERN)],
  title: [required(), string(), maxLen(200)],
  content: [required(), string()],
  sortOrder: [optional(), number()],
  status: [optional(), string(), pattern(HELP_STATUS_PATTERN)],
  viewCount: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/help.ts` 的 `HelpContent` interface 字段名已与后端一致，故为空对象。
 */
export const helpContentFieldMapping = {} as const

/** HelpContent 请求体 validator（带旧字段名兼容映射） */
export const helpContentRequestValidator = createValidatorWithMapping(
  helpContentSchema,
  helpContentFieldMapping
)

/** HelpContent 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateHelpContentResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(helpContentSchema, {})(input)
}

/**
 * HelpContent 类型定义——与后端 HelpContent 实体字段名严格一致。
 *
 * 与 `api/help.ts` 中的 `HelpContent` interface 等价，可直接替换。
 */
export interface HelpContentDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // HelpContent 自身字段
  category: string
  title: string
  content: string
  sortOrder?: number
  status?: string
  viewCount?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 helpContentRequestValidator 校验以下 URL：
// - POST   /api/system/help-content   （创建帮助内容）
// - PUT    /api/system/help-content    （更新帮助内容）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/help-content',
  requestValidator: helpContentRequestValidator,
  description: '创建帮助内容 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/system/help-content',
  requestValidator: helpContentRequestValidator,
  description: '更新帮助内容 - 校验请求体字段名'
})
