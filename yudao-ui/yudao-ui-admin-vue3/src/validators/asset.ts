/**
 * Asset 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-asset/.../entity/Asset.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@NotNull/@Size/@Min 等）的前端镜像。
 *
 * 字段命名以后端为准：serialNo / assetName / modelId / categoryId /
 * startU / endU / remarks / inboundTime 等。
 *
 * 历史问题：前端 `api/asset.ts` 的 `Asset` interface 曾使用短名
 * `name/inboundDate/remark`，与后端不一致，导致 `POST /api/asset/inbound`
 * 触发 400 校验失败。本 validator 通过 `fieldMapping` 提供旧字段名→新字段名
 * 的自动映射，在不破坏现有 view 代码的前提下保证出站请求体字段名正确。
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

// ============ Asset Schema ============
//
// 与后端 Asset.java 字段一一对应：
// - serialNo:           @NotBlank @Size(max=100)
// - modelId:            @NotNull
// - categoryId:         @NotNull
// - assetName:          @NotBlank @Size(max=200)
// - status:             @NotBlank @Size(max=30)  (IN_STOCK/ALLOCATED/IN_TRANSIT/SCRAPPED)
// - warehouse:          @Size(max=100)
// - location:           @Size(max=200)
// - projectId:          Long (nullable)
// - inboundTime:        LocalDateTime
// - outboundTime:       LocalDateTime
// - remarks:            @Size(max=500)
// - macAddress:         @Size(max=100)
// - managementIp:       @Size(max=50)
// - hostname:           @Size(max=100)
// - dataCenter:         @Size(max=100)
// - rack:               @Size(max=50)
// - startU:             @Min(1) Integer
// - endU:               @Min(1) Integer
// - imei:               @Size(max=50)
// - poNo:               @Size(max=100)
// - invoiceNo:          @Size(max=100)
// - warrantyContractNo: @Size(max=100)
// - version:            @Version (MyBatis-Plus 乐观锁)
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
const ASSET_STATUS_ENUM = ['IN_STOCK', 'ALLOCATED', 'IN_TRANSIT', 'SCRAPPED'] as const

export const assetSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ Asset 自身字段 ============
  serialNo: [required(), string(), maxLen(100)],
  modelId: [required(), number()],
  categoryId: [required(), number()],
  assetName: [required(), string(), maxLen(200)],
  status: [required(), string(), maxLen(30)],
  warehouse: [optional(), string(), maxLen(100)],
  location: [optional(), string(), maxLen(200)],
  projectId: [optional(), number()],
  inboundTime: [optional(), string()],
  outboundTime: [optional(), string()],
  remarks: [optional(), string(), maxLen(500)],
  macAddress: [optional(), string(), maxLen(100)],
  managementIp: [optional(), string(), maxLen(50)],
  hostname: [optional(), string(), maxLen(100)],
  dataCenter: [optional(), string(), maxLen(100)],
  rack: [optional(), string(), maxLen(50)],
  startU: [optional(), number(), min(1)],
  endU: [optional(), number(), min(1)],
  imei: [optional(), string(), maxLen(50)],
  poNo: [optional(), string(), maxLen(100)],
  invoiceNo: [optional(), string(), maxLen(100)],
  warrantyContractNo: [optional(), string(), maxLen(100)],
  version: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `name/inboundDate/remark` 时，
 * validator 会自动映射到 `assetName/inboundTime/remarks`。
 */
export const assetFieldMapping = {
  name: 'assetName',
  inboundDate: 'inboundTime',
  remark: 'remarks'
} as const

/** Asset 请求体 validator（带旧字段名兼容映射） */
export const assetRequestValidator = createValidatorWithMapping(
  assetSchema,
  assetFieldMapping
)

/** Asset 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateAssetResponse(input: unknown): ValidationResult {
  // 响应数据已经是后端字段名，无需映射；用同 schema 做白名单过滤
  return createValidatorWithMapping(assetSchema, {})(input)
}

/**
 * Asset 类型定义——与后端 Asset 实体字段名严格一致。
 *
 * 替代 `api/asset.ts` 中的旧 `Asset` interface（短名版）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface AssetDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // Asset 自身字段
  serialNo: string
  modelId: number
  categoryId: number
  assetName: string
  status: typeof ASSET_STATUS_ENUM[number]
  warehouse?: string
  location?: string
  projectId?: number
  inboundTime?: string
  outboundTime?: string
  remarks?: string
  macAddress?: string
  managementIp?: string
  hostname?: string
  dataCenter?: string
  rack?: string
  startU?: number
  endU?: number
  imei?: string
  poNo?: string
  invoiceNo?: string
  warrantyContractNo?: string
  version?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 assetRequestValidator 校验以下 URL：
// - POST   /api/asset/inbound    （设备入库）
// - PUT    /api/asset             （更新设备）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/asset/inbound',
  requestValidator: assetRequestValidator,
  description: '设备入库 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/asset',
  requestValidator: assetRequestValidator,
  description: '更新设备 - 校验请求体字段名'
})
