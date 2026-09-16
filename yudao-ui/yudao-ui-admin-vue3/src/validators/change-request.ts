/**
 * ChangeRequest 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-governance/.../change/entity/ChangeRequest.java` 严格对齐，
 * 作为后端 Jakarta Validation 注解（@NotBlank/@NotNull/@Size 等）的前端镜像。
 *
 * 字段命名以后端为准：crNo / projectId / title / description / priority 等。
 *
 * 历史问题：前端 `api/change-request.ts` 的 `ChangeRequest` interface 部分字段
 * 使用 `approveTime/createdAt`（驼峰风格），与后端字段名
 * `approvedAt/createTime` 不一致。本 validator 通过 `fieldMapping` 提供
 * 旧字段名→新字段名的自动映射，在不破坏现有 view 代码的前提下保证出站请求体
 * 字段名正确。
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
  boolean,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ ChangeRequest Schema ============
//
// 与后端 ChangeRequest.java 字段一一对应：
// - crNo:              @NotBlank @Size(max=50)
// - projectId:         @NotNull Long
// - projectName:       @Size(max=200)（冗余字段，用于展示）
// - title:             @NotBlank @Size(max=200)
// - description:       @NotBlank @Size(max=2000)
// - requesterId:       Long
// - requesterName:     @Size(max=50)
// - requestDate:       LocalDate
// - impactScope:       @Size(max=2000)
// - impactSchedule:    @Size(max=500)
// - impactCost:        @Size(max=500)
// - impactQuality:     @Size(max=500)
// - priority:          @NotBlank @Size(max=20) [LOW/MEDIUM/HIGH/CRITICAL]
// - status:            @Size(max=50) [SUBMITTED/UNDER_REVIEW/CCB_APPROVED/
//                       CCB_REJECTED/IMPLEMENTING/CLOSED]
// - approverId:        Long
// - approverName:      @Size(max=50)
// - processInstanceId: String
// - baselineUpdated:   Boolean (default false)
// - approvedAt:        LocalDateTime
// - closedAt:          LocalDateTime
// - version:           @Version Integer
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy）
//     在前端 schema 中以可选形式声明，便于响应数据校验。

const CHANGE_REQUEST_PRIORITY_ENUM = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const

const CHANGE_REQUEST_STATUS_ENUM = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'CCB_APPROVED',
  'CCB_REJECTED',
  'IMPLEMENTING',
  'CLOSED'
] as const

export const changeRequestSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],

  // ============ ChangeRequest 自身字段 ============
  crNo: [required(), string(), maxLen(50)],
  projectId: [required(), number()],
  projectName: [optional(), string(), maxLen(200)],
  title: [required(), string(), maxLen(200)],
  description: [required(), string(), maxLen(2000)],
  requesterId: [optional(), number()],
  requesterName: [optional(), string(), maxLen(50)],
  requestDate: [optional(), string()],
  impactScope: [optional(), string(), maxLen(2000)],
  impactSchedule: [optional(), string(), maxLen(500)],
  impactCost: [optional(), string(), maxLen(500)],
  impactQuality: [optional(), string(), maxLen(500)],
  priority: [required(), string(), maxLen(20)],
  status: [optional(), string(), maxLen(50)],
  approverId: [optional(), number()],
  approverName: [optional(), string(), maxLen(50)],
  processInstanceId: [optional(), string()],
  baselineUpdated: [optional(), boolean()],
  approvedAt: [optional(), string()],
  closedAt: [optional(), string()],
  version: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `approveTime/createdAt`（驼峰风格）时，
 * validator 会自动映射到 `approvedAt/createTime`（后端字段名）。
 *
 * 注：前端 `api/change-request.ts` 的 `ChangeRequest` interface 还包含
 * `approveOpinion` 字段，但后端 ChangeRequest 实体未定义该字段
 * （审批意见通过单独的 approve/reject 端点参数 `opinion` 传递，不在实体中）。
 * 该字段不会出现在 schema 中，normalize 时会被白名单过滤掉。
 */
export const changeRequestFieldMapping = {
  approveTime: 'approvedAt',
  createdAt: 'createTime'
} as const

/** ChangeRequest 请求体 validator（带旧字段名兼容映射） */
export const changeRequestRequestValidator = createValidatorWithMapping(
  changeRequestSchema,
  changeRequestFieldMapping
)

/** ChangeRequest 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateChangeRequestResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(changeRequestSchema, {})(input)
}

/**
 * ChangeRequest 类型定义——与后端 ChangeRequest 实体字段名严格一致。
 *
 * 替代 `api/change-request.ts` 中的旧 `ChangeRequest` interface（含旧短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface ChangeRequestDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string

  // ChangeRequest 自身字段
  crNo: string
  projectId: number
  projectName?: string
  title: string
  description: string
  requesterId?: number
  requesterName?: string
  requestDate?: string
  impactScope?: string
  impactSchedule?: string
  impactCost?: string
  impactQuality?: string
  priority: typeof CHANGE_REQUEST_PRIORITY_ENUM[number]
  status?: typeof CHANGE_REQUEST_STATUS_ENUM[number]
  approverId?: number
  approverName?: string
  processInstanceId?: string
  baselineUpdated?: boolean
  approvedAt?: string
  closedAt?: string
  version?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 changeRequestRequestValidator 校验以下 URL：
// - POST /api/governance/change-request （创建变更请求）
// - PUT  /api/governance/change-request （更新变更请求）
//
// 校验失败 → ElMessage.error + 阻止请求发送
registerValidator({
  method: 'POST',
  urlPattern: '/api/governance/change-request',
  requestValidator: changeRequestRequestValidator,
  description: '创建变更请求 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/governance/change-request',
  requestValidator: changeRequestRequestValidator,
  description: '更新变更请求 - 校验请求体字段名'
})
