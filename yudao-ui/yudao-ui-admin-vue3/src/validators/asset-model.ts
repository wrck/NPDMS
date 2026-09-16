/**
 * AssetModel 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-asset/.../entity/AssetModel.java` 严格对齐。
 *
 * 注：后端 AssetModel 实体无 Jakarta Validation 注解，本 schema 仅建立
 * 字段白名单与类型校验，便于响应数据过滤与基础类型检查。
 *
 * 字段命名以后端为准：modelName / modelCode / specParams / standardPrice 等。
 *
 * 历史问题：前端 `api/asset.ts` 的 `AssetModel` interface 曾使用短名
 * `code/name/spec`，与后端不一致，导致 `POST /api/asset/model` 触发
 * 字段丢失。本 validator 通过 `fieldMapping` 提供旧字段名→新字段名的
 * 自动映射，在不破坏现有 view 代码的前提下保证出站请求体字段名正确。
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

// ============ AssetModel Schema ============
//
// 与后端 AssetModel.java 字段一一对应（无 Jakarta Validation 注解）：
// - categoryId:     Long
// - modelName:      String
// - modelCode:      String
// - brand:          String
// - specParams:     String (JSON string of specifications)
// - standardPrice:  BigDecimal
// - unit:           String (台/套/个)
// - status:         Integer (1=active, 0=disabled)
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const assetModelSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ AssetModel 自身字段 ============
  categoryId: [optional(), number()],
  modelName: [optional(), string()],
  modelCode: [optional(), string()],
  brand: [optional(), string()],
  specParams: [optional(), string()],
  standardPrice: [optional(), number()],
  unit: [optional(), string()],
  status: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `code/name/spec` 时，
 * validator 会自动映射到 `modelCode/modelName/specParams`。
 */
export const assetModelFieldMapping = {
  code: 'modelCode',
  name: 'modelName',
  spec: 'specParams'
} as const

/** AssetModel 请求体 validator（带旧字段名兼容映射） */
export const assetModelRequestValidator = createValidatorWithMapping(
  assetModelSchema,
  assetModelFieldMapping
)

/** AssetModel 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateAssetModelResponse(input: unknown): ValidationResult {
  // 响应数据已经是后端字段名，无需映射；用同 schema 做白名单过滤
  return createValidatorWithMapping(assetModelSchema, {})(input)
}

/**
 * AssetModel 类型定义——与后端 AssetModel 实体字段名严格一致。
 *
 * 替代 `api/asset.ts` 中的旧 `AssetModel` interface（短名版）。
 */
export interface AssetModelDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // AssetModel 自身字段
  categoryId?: number
  modelName?: string
  modelCode?: string
  brand?: string
  specParams?: string
  standardPrice?: number
  unit?: string
  status?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 assetModelRequestValidator 校验以下 URL：
// - POST   /api/asset/model    （创建型号）
// - PUT    /api/asset/model     （更新型号）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/asset/model',
  requestValidator: assetModelRequestValidator,
  description: '创建设备型号 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/asset/model',
  requestValidator: assetModelRequestValidator,
  description: '更新设备型号 - 校验请求体字段名'
})
