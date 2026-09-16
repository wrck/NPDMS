/**
 * Notification / NotificationTemplate 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-notification/.../entity/Notification.java` 和
 * `NotificationTemplate.java` 严格对齐，作为后端契约的前端镜像。
 *
 * 字段命名以后端为准。Notification 不继承 BaseEntity（使用独立的
 * `createdAt/createdBy` 审计字段）；NotificationTemplate 同样不继承
 * BaseEntity（使用独立的 `createdAt/updatedAt`）。
 *
 * 前端 `api/notification.ts` 的 `Notification` interface 字段名已与后端一致，
 * 故两个 `fieldMapping` 均为空对象。前端未定义 `NotificationTemplate`
 * interface，本文件同时提供 `NotificationTemplateDTO`。
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

// ============ Notification Schema ============
//
// 与后端 Notification.java 字段一一对应：
// - id:          Long（@TableId AUTO）
// - userId:      Long（接收人）
// - title:       String
// - content:     String
// - category:    String（MILESTONE/TASK/APPROVAL/PUNCH_LIST/WARRANTY/RMA/SETTLEMENT）
// - bizType:     String（如 TASK_ASSIGNED、WARRANTY_EXPIRE_30）
// - bizId:       Long
// - readStatus:  String（UNREAD/READ）
// - channel:     String（IN_APP/WS/EMAIL/OA）
// - createdAt:   LocalDateTime
// - createdBy:   Long
//
// 注：Notification 不继承 BaseEntity，无 createTime/updateTime/createBy/updateBy/deleted。
export const notificationSchema: Schema = defineSchema({
  id: [optional(), number()],
  userId: [optional(), number()],
  title: [optional(), string()],
  content: [optional(), string()],
  category: [optional(), string()],
  bizType: [optional(), string()],
  bizId: [optional(), number()],
  readStatus: [optional(), string()],
  channel: [optional(), string()],
  createdAt: [optional(), string()],
  createdBy: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/notification.ts` 的 `Notification` interface 字段名已与后端一致，
 * 故为空对象。
 */
export const notificationFieldMapping = {} as const

/** Notification 请求体 validator（带旧字段名兼容映射） */
export const notificationRequestValidator = createValidatorWithMapping(
  notificationSchema,
  notificationFieldMapping
)

/** Notification 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateNotificationResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(notificationSchema, {})(input)
}

/**
 * Notification 类型定义——与后端 Notification 实体字段名严格一致。
 *
 * 与 `api/notification.ts` 中的 `Notification` interface 等价，可直接替换。
 */
export interface NotificationDTO {
  id?: number
  userId?: number
  title?: string
  content?: string
  category?: string
  bizType?: string
  bizId?: number
  readStatus?: string
  channel?: string
  createdAt?: string
  createdBy?: number
}

// ============ NotificationTemplate Schema ============
//
// 与后端 NotificationTemplate.java 字段一一对应：
// - id:            Long（@TableId AUTO）
// - templateCode:  String（唯一，如 TASK_ASSIGNED、WARRANTY_EXPIRE_30）
// - subject:       String（通知标题模板）
// - body:          String（通知正文模板，含 ${var} 占位符）
// - variables:     String（变量定义 JSON）
// - description:   String
// - createdAt:     LocalDateTime
// - updatedAt:     LocalDateTime
//
// 注：NotificationTemplate 不继承 BaseEntity，使用独立的 createdAt/updatedAt。
export const notificationTemplateSchema: Schema = defineSchema({
  id: [optional(), number()],
  templateCode: [optional(), string()],
  subject: [optional(), string()],
  body: [optional(), string()],
  variables: [optional(), string()],
  description: [optional(), string()],
  createdAt: [optional(), string()],
  updatedAt: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/notification.ts` 暂未定义 NotificationTemplate interface，字段名
 * 与后端一致，故为空对象。
 */
export const notificationTemplateFieldMapping = {} as const

/** NotificationTemplate 请求体 validator（带旧字段名兼容映射） */
export const notificationTemplateRequestValidator = createValidatorWithMapping(
  notificationTemplateSchema,
  notificationTemplateFieldMapping
)

/** NotificationTemplate 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateNotificationTemplateResponse(
  input: unknown
): ValidationResult {
  return createValidatorWithMapping(notificationTemplateSchema, {})(input)
}

/**
 * NotificationTemplate 类型定义——与后端 NotificationTemplate 实体字段名严格一致。
 */
export interface NotificationTemplateDTO {
  id?: number
  templateCode?: string
  subject?: string
  body?: string
  variables?: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用对应 validator 校验以下 URL：
// - POST   /api/notification/send        （管理员手动发送通知，校验 Notification）
// - POST   /api/notification/template    （创建通知模板，校验 NotificationTemplate）
// - PUT    /api/notification/template/{id}（更新通知模板，校验 NotificationTemplate）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/notification/send',
  requestValidator: notificationRequestValidator,
  description: '发送通知 - 校验请求体字段名'
})

registerValidator({
  method: 'POST',
  urlPattern: '/api/notification/template',
  requestValidator: notificationTemplateRequestValidator,
  description: '创建通知模板 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: /^\/api\/notification\/template\/\d+$/,
  requestValidator: notificationTemplateRequestValidator,
  description: '更新通知模板 - 校验请求体字段名'
})
