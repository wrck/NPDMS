/**
 * SysRole 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/SysRole.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：roleName / roleCode / description / status 等。
 *
 * 历史问题：前端 `api/system.ts` 的 `SysRole` interface 曾使用短名
 * `name/code`，与后端 `roleName/roleCode` 不一致，导致 `POST /api/system/role`
 * 触发 400 校验失败。本 validator 通过 `fieldMapping` 提供旧字段名→新字段名
 * 的自动映射，在不破坏现有 view 代码的前提下保证出站请求体字段名正确。
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

// ============ SysRole Schema ============
//
// 与后端 SysRole.java 字段一一对应：
// - roleName:     String（无校验注解）
// - roleCode:     String（无校验注解）
// - description:  String（无校验注解）
// - status:       String（0=normal, 1=disabled，无校验注解）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const sysRoleSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ SysRole 自身字段 ============
  roleName: [optional(), string()],
  roleCode: [optional(), string()],
  description: [optional(), string()],
  status: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `name/code` 时，
 * validator 会自动映射到 `roleName/roleCode`。
 *
 * 注：前端 `sort/menuIds/dataScope/remark` 为前端独有字段，后端 SysRole 未定义，
 *     会被 schema 白名单过滤剥离，不参与映射。
 */
export const sysRoleFieldMapping = {
  name: 'roleName',
  code: 'roleCode'
} as const

/** SysRole 请求体 validator（带旧字段名兼容映射） */
export const sysRoleRequestValidator = createValidatorWithMapping(
  sysRoleSchema,
  sysRoleFieldMapping
)

/** SysRole 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateSysRoleResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(sysRoleSchema, {})(input)
}

/**
 * SysRole 类型定义——与后端 SysRole 实体字段名严格一致。
 *
 * 替代 `api/system.ts` 中的旧 `SysRole` interface（含 name/code 等短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface SysRoleDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // SysRole 自身字段
  roleName?: string
  roleCode?: string
  description?: string
  status?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 sysRoleRequestValidator 校验以下 URL：
// - POST   /api/system/role   （创建角色）
// - PUT    /api/system/role    （更新角色）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/role',
  requestValidator: sysRoleRequestValidator,
  description: '创建角色 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/system/role',
  requestValidator: sysRoleRequestValidator,
  description: '更新角色 - 校验请求体字段名'
})
