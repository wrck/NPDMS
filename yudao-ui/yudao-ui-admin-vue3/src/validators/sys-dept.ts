/**
 * SysDept 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/SysDept.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：parentId / deptName / orderNum / status 等。
 *
 * 注：前端 `api/system.ts` 暂未定义 SysDept interface，故 `fieldMapping`
 *     为空对象。如后续前端引入短名（如 `name/sort`），可在
 *     `sysDeptFieldMapping` 中补充旧→新映射。
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

// ============ SysDept Schema ============
//
// 与后端 SysDept.java 字段一一对应：
// - parentId:   Long
// - deptName:   String
// - orderNum:   Integer
// - status:     String（0=normal, 1=disabled）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const sysDeptSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ SysDept 自身字段 ============
  parentId: [optional(), number()],
  deptName: [optional(), string()],
  orderNum: [optional(), number()],
  status: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/system.ts` 暂未定义 SysDept interface，字段名与后端一致，故为空对象。
 */
export const sysDeptFieldMapping = {} as const

/** SysDept 请求体 validator（带旧字段名兼容映射） */
export const sysDeptRequestValidator = createValidatorWithMapping(
  sysDeptSchema,
  sysDeptFieldMapping
)

/** SysDept 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateSysDeptResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(sysDeptSchema, {})(input)
}

/**
 * SysDept 类型定义——与后端 SysDept 实体字段名严格一致。
 */
export interface SysDeptDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // SysDept 自身字段
  parentId?: number
  deptName?: string
  orderNum?: number
  status?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 sysDeptRequestValidator 校验以下 URL：
// - POST   /api/system/dept   （创建部门）
// - PUT    /api/system/dept    （更新部门）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/dept',
  requestValidator: sysDeptRequestValidator,
  description: '创建部门 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/system/dept',
  requestValidator: sysDeptRequestValidator,
  description: '更新部门 - 校验请求体字段名'
})
