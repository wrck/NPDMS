/**
 * Feedback 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/Feedback.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：category / title / content / status / reply 等。
 * 前端 `api/feedback.ts` 的 `Feedback` interface 字段名已与后端一致，
 * 故 `fieldMapping` 为空对象。
 *
 * 另含回复端点专用 schema `feedbackReplySchema`，对应后端
 * `FeedbackController.FeedbackReplyRequest` 内部 DTO：
 * - reply: @NotBlank @Size(max=4000)
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

// ============ Feedback Schema ============
//
// 与后端 Feedback.java 字段一一对应：
// - userId:     Long
// - username:   @Size(max=50)
// - category:   @NotBlank @Pattern("^(BUG|SUGGESTION|QUESTION|OTHER)$")
// - title:      @NotBlank @Size(max=200)
// - content:    @NotBlank @Size(max=4000)
// - contact:    @Size(max=100)
// - status:     @Pattern("^(PENDING|PROCESSING|RESOLVED|CLOSED)$")
// - reply:      @Size(max=4000)
// - replyBy:    @Size(max=50)
// - replyAt:    LocalDateTime
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
const FEEDBACK_CATEGORY_PATTERN = /^(BUG|SUGGESTION|QUESTION|OTHER)$/
const FEEDBACK_STATUS_PATTERN = /^(PENDING|PROCESSING|RESOLVED|CLOSED)$/

export const feedbackSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ Feedback 自身字段 ============
  userId: [optional(), number()],
  username: [optional(), string(), maxLen(50)],
  category: [required(), string(), pattern(FEEDBACK_CATEGORY_PATTERN)],
  title: [required(), string(), maxLen(200)],
  content: [required(), string(), maxLen(4000)],
  contact: [optional(), string(), maxLen(100)],
  status: [optional(), string(), pattern(FEEDBACK_STATUS_PATTERN)],
  reply: [optional(), string(), maxLen(4000)],
  replyBy: [optional(), string(), maxLen(50)],
  replyAt: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/feedback.ts` 的 `Feedback` interface 字段名已与后端一致，故为空对象。
 */
export const feedbackFieldMapping = {} as const

/** Feedback 请求体 validator（带旧字段名兼容映射） */
export const feedbackRequestValidator = createValidatorWithMapping(
  feedbackSchema,
  feedbackFieldMapping
)

/** Feedback 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateFeedbackResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(feedbackSchema, {})(input)
}

/**
 * Feedback 类型定义——与后端 Feedback 实体字段名严格一致。
 *
 * 与 `api/feedback.ts` 中的 `Feedback` interface 等价，可直接替换。
 */
export interface FeedbackDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // Feedback 自身字段
  userId?: number
  username?: string
  category: string
  title: string
  content: string
  contact?: string
  status?: string
  reply?: string
  replyBy?: string
  replyAt?: string
}

// ============ Feedback Reply Schema ============
//
// 对应后端 `FeedbackController.FeedbackReplyRequest` 内部 DTO：
// - reply: @NotBlank @Size(max=4000)
//
// 用于 `PUT /api/system/feedback/{id}/reply` 端点（管理员回复反馈）。
export const feedbackReplySchema: Schema = defineSchema({
  reply: [required(), string(), maxLen(4000)]
})

/** Feedback 回复请求体 validator */
export const feedbackReplyRequestValidator = createValidatorWithMapping(
  feedbackReplySchema,
  {}
)

/** Feedback 回复请求 DTO——与后端 FeedbackReplyRequest 字段名严格一致。 */
export interface FeedbackReplyRequestDTO {
  reply: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用对应 validator 校验以下 URL：
// - POST   /api/system/feedback              （提交反馈，校验完整 Feedback）
// - PUT    /api/system/feedback/{id}/reply   （管理员回复，校验 reply 字段）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/feedback',
  requestValidator: feedbackRequestValidator,
  description: '提交反馈 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: /^\/api\/system\/feedback\/\d+\/reply$/,
  requestValidator: feedbackReplyRequestValidator,
  description: '回复反馈 - 校验 reply 字段必填与长度'
})
