/**
 * SysUser 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/SysUser.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：username / realName / deptId / companyId 等。
 *
 * 历史问题：前端 `api/system.ts` 的 `SysUser` interface 曾使用短名
 * `nickname`，与后端 `realName` 不一致，导致 `POST /api/system/user` 触发
 * 400 校验失败。本 validator 通过 `fieldMapping` 提供旧字段名→新字段名的
 * 自动映射，在不破坏现有 view 代码的前提下保证出站请求体字段名正确。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  maxLen,
  minLen,
  number,
  optional,
  pattern,
  required,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ SysUser Schema ============
//
// 与后端 SysUser.java 字段一一对应：
// - username:   @NotBlank @Size(min=3, max=50) @Pattern("^[a-zA-Z0-9_]+$")
// - password:   （仅写入，无校验注解）
// - realName:   @Size(max=50)
// - email:      （字段级加密，无校验注解）
// - phone:      （字段级加密，无校验注解）
// - status:     @Pattern("^[01]$")
// - deptId:     Long
// - companyId:  Long
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
const USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/
const USER_STATUS_PATTERN = /^[01]$/

export const sysUserSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ SysUser 自身字段 ============
  username: [required(), string(), minLen(3), maxLen(50), pattern(USERNAME_PATTERN)],
  password: [optional(), string()],
  realName: [optional(), string(), maxLen(50)],
  email: [optional(), string()],
  phone: [optional(), string()],
  status: [optional(), string(), pattern(USER_STATUS_PATTERN)],
  deptId: [optional(), number()],
  companyId: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `nickname` 时，
 * validator 会自动映射到 `realName`。
 *
 * 注：前端 `roleIds/deptName/remark` 为前端独有字段，后端 SysUser 未定义，
 *     会被 schema 白名单过滤剥离，不参与映射。
 */
export const sysUserFieldMapping = {
  nickname: 'realName'
} as const

/** SysUser 请求体 validator（带旧字段名兼容映射） */
export const sysUserRequestValidator = createValidatorWithMapping(
  sysUserSchema,
  sysUserFieldMapping
)

/** SysUser 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateSysUserResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(sysUserSchema, {})(input)
}

/**
 * SysUser 类型定义——与后端 SysUser 实体字段名严格一致。
 *
 * 替代 `api/system.ts` 中的旧 `SysUser` interface（含 nickname 等短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface SysUserDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // SysUser 自身字段
  username: string
  password?: string
  realName?: string
  email?: string
  phone?: string
  status?: string
  deptId?: number
  companyId?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 sysUserRequestValidator 校验以下 URL：
// - POST   /api/system/user   （创建用户）
// - PUT    /api/system/user    （更新用户）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/user',
  requestValidator: sysUserRequestValidator,
  description: '创建用户 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/system/user',
  requestValidator: sysUserRequestValidator,
  description: '更新用户 - 校验请求体字段名'
})
