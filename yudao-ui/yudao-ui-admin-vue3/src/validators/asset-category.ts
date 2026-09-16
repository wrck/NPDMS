/**
 * AssetCategory 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-asset/.../entity/AssetCategory.java` 严格对齐。
 *
 * 注：后端 AssetCategory 实体无 Jakarta Validation 注解，本 schema 仅建立
 * 字段白名单与类型校验。
 *
 * 字段命名以后端为准：categoryName / categoryCode / sortOrder 等。
 *
 * 历史问题：前端 `api/asset.ts` 的 `AssetCategory` interface 曾使用短名
 * `code/name/sort`，与后端不一致，导致 `POST /api/asset/category` 触发
 * 字段丢失。本 validator 通过 `fieldMapping` 提供旧字段名→新字段名的
 * 自动映射。
 * ===========================================================================
 */
import {
  array,
  createValidatorWithMapping,
  defineSchema,
  number,
  optional,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ AssetCategory Schema ============
//
// 与后端 AssetCategory.java 字段一一对应（无 Jakarta Validation 注解）：
// - parentId:     Long
// - categoryName: String
// - categoryCode: String
// - sortOrder:    Integer
// - status:       Integer (1=active, 0=disabled)
// - children:     List<AssetCategory> (@TableField(exist=false)，仅响应使用)
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const assetCategorySchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ AssetCategory 自身字段 ============
  parentId: [optional(), number()],
  categoryName: [optional(), string()],
  categoryCode: [optional(), string()],
  sortOrder: [optional(), number()],
  status: [optional(), number()],
  children: [optional(), array()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `code/name/sort` 时，
 * validator 会自动映射到 `categoryCode/categoryName/sortOrder`。
 */
export const assetCategoryFieldMapping = {
  code: 'categoryCode',
  name: 'categoryName',
  sort: 'sortOrder'
} as const

/** AssetCategory 请求体 validator（带旧字段名兼容映射） */
export const assetCategoryRequestValidator = createValidatorWithMapping(
  assetCategorySchema,
  assetCategoryFieldMapping
)

/** AssetCategory 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateAssetCategoryResponse(input: unknown): ValidationResult {
  // 响应数据已经是后端字段名，无需映射；用同 schema 做白名单过滤
  return createValidatorWithMapping(assetCategorySchema, {})(input)
}

/**
 * AssetCategory 类型定义——与后端 AssetCategory 实体字段名严格一致。
 *
 * 替代 `api/asset.ts` 中的旧 `AssetCategory` interface（短名版）。
 */
export interface AssetCategoryDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // AssetCategory 自身字段
  parentId?: number
  categoryName?: string
  categoryCode?: string
  sortOrder?: number
  status?: number
  children?: AssetCategoryDTO[]
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 assetCategoryRequestValidator 校验以下 URL：
// - POST   /api/asset/category    （创建分类）
// - PUT    /api/asset/category     （更新分类）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/asset/category',
  requestValidator: assetCategoryRequestValidator,
  description: '创建设备分类 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/asset/category',
  requestValidator: assetCategoryRequestValidator,
  description: '更新设备分类 - 校验请求体字段名'
})
