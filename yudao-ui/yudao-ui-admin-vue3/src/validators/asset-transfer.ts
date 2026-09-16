/**
 * AssetTransfer 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-asset/.../entity/AssetTransfer.java` 严格对齐。
 *
 * 注：后端 AssetTransfer 实体无 Jakarta Validation 注解，本 schema 仅建立
 * 字段白名单与类型校验。
 *
 * 字段命名以后端为准：applyUserId / applyUserName / approveOpinion /
 * processInstanceId 等。
 *
 * 历史问题：前端 `api/asset.ts` 的 `AssetTransfer` interface 曾使用短名
 * `applicantId/applicantName/opinion`，与后端不一致，导致
 * `POST /api/asset/transfer/apply` 触发字段丢失。本 validator 通过
 * `fieldMapping` 提供旧字段名→新字段名的自动映射。
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

const TRANSFER_STATUS_ENUM = ['PENDING', 'APPROVED', 'REJECTED'] as const

// ============ AssetTransfer Schema ============
//
// 与后端 AssetTransfer.java 字段一一对应（无 Jakarta Validation 注解）：
// - assetId:           Long
// - fromProjectId:     Long
// - toProjectId:       Long
// - transferReason:    String
// - status:            String (PENDING/APPROVED/REJECTED)
// - applyUserId:       Long
// - applyUserName:     String
// - applyTime:         LocalDateTime
// - approveUserId:     Long
// - approveUserName:   String
// - approveTime:       LocalDateTime
// - approveOpinion:    String
// - processInstanceId: String (工作流实例 ID)
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const assetTransferSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ AssetTransfer 自身字段 ============
  assetId: [optional(), number()],
  fromProjectId: [optional(), number()],
  toProjectId: [optional(), number()],
  transferReason: [optional(), string()],
  status: [optional(), string()],
  applyUserId: [optional(), number()],
  applyUserName: [optional(), string()],
  applyTime: [optional(), string()],
  approveUserId: [optional(), number()],
  approveUserName: [optional(), string()],
  approveTime: [optional(), string()],
  approveOpinion: [optional(), string()],
  processInstanceId: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `applicantId/applicantName/opinion` 时，
 * validator 会自动映射到 `applyUserId/applyUserName/approveOpinion`。
 */
export const assetTransferFieldMapping = {
  applicantId: 'applyUserId',
  applicantName: 'applyUserName',
  opinion: 'approveOpinion'
} as const

/** AssetTransfer 请求体 validator（带旧字段名兼容映射） */
export const assetTransferRequestValidator = createValidatorWithMapping(
  assetTransferSchema,
  assetTransferFieldMapping
)

/** AssetTransfer 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateAssetTransferResponse(input: unknown): ValidationResult {
  // 响应数据已经是后端字段名，无需映射；用同 schema 做白名单过滤
  return createValidatorWithMapping(assetTransferSchema, {})(input)
}

/**
 * AssetTransfer 类型定义——与后端 AssetTransfer 实体字段名严格一致。
 *
 * 替代 `api/asset.ts` 中的旧 `AssetTransfer` interface（短名版）。
 */
export interface AssetTransferDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // AssetTransfer 自身字段
  assetId?: number
  fromProjectId?: number
  toProjectId?: number
  transferReason?: string
  status?: typeof TRANSFER_STATUS_ENUM[number]
  applyUserId?: number
  applyUserName?: string
  applyTime?: string
  approveUserId?: number
  approveUserName?: string
  approveTime?: string
  approveOpinion?: string
  processInstanceId?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 assetTransferRequestValidator 校验以下 URL：
// - POST   /api/asset/transfer/apply    （申请设备调拨）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/asset/transfer/apply',
  requestValidator: assetTransferRequestValidator,
  description: '申请设备调拨 - 校验请求体字段名'
})
