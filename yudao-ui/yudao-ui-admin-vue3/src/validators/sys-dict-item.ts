/**
 * SysDictItem 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/SysDictItem.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：dictId / itemText / itemValue / sortOrder 等。
 *
 * 历史问题：前端 `api/system.ts` 的 `SysDictItem` interface 曾使用短名
 * `label/value/sort`，与后端 `itemText/itemValue/sortOrder` 不一致，导致
 * `POST /api/system/dict/item` 触发 400 校验失败。本 validator 通过
 * `fieldMapping` 提供旧字段名→新字段名的自动映射，在不破坏现有 view 代码的
 * 前提下保证出站请求体字段名正确。
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

// ============ SysDictItem Schema ============
//
// 与后端 SysDictItem.java 字段一一对应：
// - dictId:     Long
// - itemText:   String
// - itemValue:  String
// - sortOrder:  Integer
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const sysDictItemSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ SysDictItem 自身字段 ============
  dictId: [optional(), number()],
  itemText: [optional(), string()],
  itemValue: [optional(), string()],
  sortOrder: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `label/value/sort` 时，
 * validator 会自动映射到 `itemText/itemValue/sortOrder`。
 *
 * 注：前端 `status/remark` 为前端独有字段，后端 SysDictItem 未定义，
 *     会被 schema 白名单过滤剥离，不参与映射。
 */
export const sysDictItemFieldMapping = {
  label: 'itemText',
  value: 'itemValue',
  sort: 'sortOrder'
} as const

/** SysDictItem 请求体 validator（带旧字段名兼容映射） */
export const sysDictItemRequestValidator = createValidatorWithMapping(
  sysDictItemSchema,
  sysDictItemFieldMapping
)

/** SysDictItem 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateSysDictItemResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(sysDictItemSchema, {})(input)
}

/**
 * SysDictItem 类型定义——与后端 SysDictItem 实体字段名严格一致。
 *
 * 替代 `api/system.ts` 中的旧 `SysDictItem` interface（含 label/value 等短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface SysDictItemDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // SysDictItem 自身字段
  dictId?: number
  itemText?: string
  itemValue?: string
  sortOrder?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 sysDictItemRequestValidator 校验以下 URL：
// - POST   /api/system/dict/item   （创建字典项）
// - PUT    /api/system/dict/item    （更新字典项）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/dict/item',
  requestValidator: sysDictItemRequestValidator,
  description: '创建字典项 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/system/dict/item',
  requestValidator: sysDictItemRequestValidator,
  description: '更新字典项 - 校验请求体字段名'
})
