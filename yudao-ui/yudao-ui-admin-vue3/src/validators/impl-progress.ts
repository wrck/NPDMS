/**
 * ImplProgress 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-implementation/.../entity/ImplProgress.java` 严格对齐，作为后端
 * Jakarta Validation 注解（本实体未声明）的前端镜像。
 *
 * 字段命名以后端为准：taskId / progressPercent / workLog / photoUrls /
 * reportUserId / reportUserName / reportTime 等。
 *
 * 历史问题：前端 `api/implementation.ts` 的 `TaskProgress` interface 使用
 * `reporter` 表示上报人姓名，与后端 `reportUserName` 不一致，导致
 * `POST /api/impl/progress` 字段名不匹配。本 validator 通过 `fieldMapping`
 * 提供旧字段名→新字段名的自动映射，在不破坏现有 view 代码的前提下保证
 * 出站请求体字段名正确。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  number,
  optional,
  range,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ ImplProgress Schema ============
//
// 与后端 ImplProgress.java 字段一一对应：
// - taskId:           Long（无注解）
// - progressPercent:  Integer（0-100，无注解）
// - workLog:          String
// - photoUrls:        String（逗号分隔的图片 URL）
// - reportUserId:     Long
// - reportUserName:   String
// - reportTime:       LocalDateTime
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const implProgressSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ ImplProgress 自身字段 ============
  taskId: [optional(), number()],
  progressPercent: [optional(), number(), range(0, 100)],
  workLog: [optional(), string()],
  photoUrls: [optional(), string()],
  reportUserId: [optional(), number()],
  reportUserName: [optional(), string()],
  reportTime: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `reporter` 时，validator 会自动
 * 映射到 `reportUserName`。
 */
export const implProgressFieldMapping = {
  reporter: 'reportUserName'
} as const

/** ImplProgress 请求体 validator（带旧字段名兼容映射） */
export const implProgressRequestValidator = createValidatorWithMapping(
  implProgressSchema,
  implProgressFieldMapping
)

/** ImplProgress 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateImplProgressResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(implProgressSchema, {})(input)
}

/**
 * ImplProgress 类型定义——与后端 ImplProgress 实体字段名严格一致。
 *
 * 替代 `api/implementation.ts` 中的旧 `TaskProgress` interface（含 `reporter`
 * 短名）。现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface ImplProgressDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // ImplProgress 自身字段
  taskId?: number
  progressPercent?: number
  workLog?: string
  photoUrls?: string
  reportUserId?: number
  reportUserName?: string
  reportTime?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 implProgressRequestValidator 校验以下 URL：
// - POST   /api/impl/progress       （新增进度日志）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/impl/progress',
  requestValidator: implProgressRequestValidator,
  description: '新增实施进度日志 - 校验请求体字段名'
})
