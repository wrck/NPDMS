/**
 * TaskComment 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-implementation/.../entity/TaskComment.java` 严格对齐，作为后端
 * Jakarta Validation 注解（@NotNull/@NotBlank/@Size）的前端镜像。
 *
 * 字段命名以后端为准：taskId / userId / userName / content / parentCommentId /
 * version。前端 `api/task-comment.ts` 的 `TaskCommentItem` interface 字段名
 * 已与后端一致，故 `fieldMapping` 为空对象。
 *
 * 支持二级回复（parentCommentId 非 NULL 时为二级回复）。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  maxLen,
  number,
  optional,
  required,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ TaskComment Schema ============
//
// 与后端 TaskComment.java 字段一一对应：
// - taskId:          @NotNull Long
// - userId:          @NotNull Long
// - userName:        @Size(max=64) String
// - content:         @NotBlank String
// - parentCommentId: Long（NULL=顶级评论，非 NULL=二级回复）
// - version:         @Version Integer（乐观锁）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const taskCommentSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ TaskComment 自身字段 ============
  taskId: [required(), number()],
  userId: [required(), number()],
  userName: [optional(), string(), maxLen(64)],
  content: [required(), string()],
  parentCommentId: [optional(), number()],
  version: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/task-comment.ts` 的 `TaskCommentItem` 字段名与后端一致，无需映射。
 */
export const taskCommentFieldMapping = {} as const

/** TaskComment 请求体 validator（带旧字段名兼容映射） */
export const taskCommentRequestValidator = createValidatorWithMapping(
  taskCommentSchema,
  taskCommentFieldMapping
)

/** TaskComment 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateTaskCommentResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(taskCommentSchema, {})(input)
}

/**
 * TaskComment 类型定义——与后端 TaskComment 实体字段名严格一致。
 *
 * 与 `api/task-comment.ts` 中的 `TaskCommentItem` interface 字段名一致。
 */
export interface TaskCommentDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // TaskComment 自身字段
  taskId: number
  userId: number
  userName?: string
  content: string
  /** 父评论ID（NULL=顶级评论，非 NULL=二级回复） */
  parentCommentId?: number
  /** 乐观锁版本号 */
  version?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 taskCommentRequestValidator 校验以下 URL：
// - POST   /api/implementation/task/comment   （新增评论 / 二级回复）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/implementation/task/comment',
  requestValidator: taskCommentRequestValidator,
  description: '新增任务评论 - 校验请求体字段名与必填'
})
