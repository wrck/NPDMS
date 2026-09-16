/**
 * Rma 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-asset/.../rma/entity/Rma.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@NotNull/@Size 等）的前端镜像。
 *
 * 字段命名以后端为准：rmaNo / faultDescription / ticketStatus /
 * warrantyStatus / registerUserId / registerUserName / resolution /
 * inspectorNotes / registeredAt / closedAt 等。
 *
 * 历史问题：前端 `api/rma.ts` 的 `Rma` interface 曾使用短名
 * `createdAt/inspectionResult/remark`，与后端不一致，导致
 * `POST /api/asset/rma` 触发字段丢失或 400。本 validator 通过
 * `fieldMapping` 提供旧字段名→新字段名的自动映射。
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

const RMA_TICKET_STATUS_ENUM = [
  'REGISTERED',
  'WARRANTY_CHECKED',
  'RMA_ISSUED',
  'RETURNING',
  'INSPECTED',
  'CLOSED'
] as const

const RMA_WARRANTY_STATUS_ENUM = ['IN_WARRANTY', 'OUT_OF_WARRANTY'] as const

// ============ Rma Schema ============
//
// 与后端 Rma.java 字段一一对应：
// - rmaNo:             @NotBlank @Size(max=50)  (格式 RMA-YYYY-XXXX)
// - assetId:           @NotNull
// - sn:                @Size(max=100)  (登记时快照序列号)
// - faultDescription:  @NotBlank @Size(max=2000)
// - faultPhotos:       String (逗号分隔附件 ID，预留)
// - ticketStatus:      @Size(max=50)  (REGISTERED/WARRANTY_CHECKED/RMA_ISSUED/RETURNING/INSPECTED/CLOSED)
// - warrantyStatus:    @Size(max=50)  (IN_WARRANTY/OUT_OF_WARRANTY)
// - projectId:         Long
// - registeredAt:      LocalDateTime
// - warrantyCheckedAt: LocalDateTime
// - rmaIssuedAt:       LocalDateTime
// - returningAt:       LocalDateTime
// - inspectedAt:       LocalDateTime
// - closedAt:          LocalDateTime
// - registerUserId:    Long
// - registerUserName:  @Size(max=50) String
// - resolution:        @Size(max=2000) String  (处理结果)
// - inspectorNotes:    @Size(max=2000) String  (检验备注)
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const rmaSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ Rma 自身字段 ============
  rmaNo: [required(), string(), maxLen(50)],
  assetId: [required(), number()],
  sn: [optional(), string(), maxLen(100)],
  faultDescription: [required(), string(), maxLen(2000)],
  faultPhotos: [optional(), string()],
  ticketStatus: [optional(), string(), maxLen(50)],
  warrantyStatus: [optional(), string(), maxLen(50)],
  projectId: [optional(), number()],
  registeredAt: [optional(), string()],
  warrantyCheckedAt: [optional(), string()],
  rmaIssuedAt: [optional(), string()],
  returningAt: [optional(), string()],
  inspectedAt: [optional(), string()],
  closedAt: [optional(), string()],
  registerUserId: [optional(), number()],
  registerUserName: [optional(), string(), maxLen(50)],
  resolution: [optional(), string(), maxLen(2000)],
  inspectorNotes: [optional(), string(), maxLen(2000)]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `createdAt/inspectionResult/remark` 时，
 * validator 会自动映射到 `registeredAt/resolution/inspectorNotes`。
 */
export const rmaFieldMapping = {
  createdAt: 'registeredAt',
  inspectionResult: 'resolution',
  remark: 'inspectorNotes'
} as const

/** Rma 请求体 validator（带旧字段名兼容映射） */
export const rmaRequestValidator = createValidatorWithMapping(
  rmaSchema,
  rmaFieldMapping
)

/** Rma 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateRmaResponse(input: unknown): ValidationResult {
  // 响应数据已经是后端字段名，无需映射；用同 schema 做白名单过滤
  return createValidatorWithMapping(rmaSchema, {})(input)
}

/**
 * Rma 类型定义——与后端 Rma 实体字段名严格一致。
 *
 * 替代 `api/rma.ts` 中的旧 `Rma` interface（短名版）。
 */
export interface RmaDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // Rma 自身字段
  rmaNo: string
  assetId: number
  sn?: string
  faultDescription: string
  faultPhotos?: string
  ticketStatus?: typeof RMA_TICKET_STATUS_ENUM[number]
  warrantyStatus?: typeof RMA_WARRANTY_STATUS_ENUM[number]
  projectId?: number
  registeredAt?: string
  warrantyCheckedAt?: string
  rmaIssuedAt?: string
  returningAt?: string
  inspectedAt?: string
  closedAt?: string
  registerUserId?: number
  registerUserName?: string
  resolution?: string
  inspectorNotes?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 rmaRequestValidator 校验以下 URL：
// - POST   /api/asset/rma    （登记 RMA 工单）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/asset/rma',
  requestValidator: rmaRequestValidator,
  description: '登记 RMA 工单 - 校验请求体字段名与必填'
})
