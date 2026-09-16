/**
 * SysMenu 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/SysMenu.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：menuName / menuType / perms / orderNum 等。
 *
 * 历史问题：前端 `api/system.ts` 的 `SysMenu` interface 曾使用短名
 * `name/type/permission/sort`，与后端 `menuName/menuType/perms/orderNum`
 * 不一致，导致 `POST /api/system/menu` 触发 400 校验失败。本 validator 通过
 * `fieldMapping` 提供旧字段名→新字段名的自动映射，在不破坏现有 view 代码的
 * 前提下保证出站请求体字段名正确。
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

// ============ SysMenu Schema ============
//
// 与后端 SysMenu.java 字段一一对应：
// - parentId:   Long
// - menuName:   String
// - menuType:   String（M=directory, C=menu, F=button, L=lowcode page）
// - path:       String
// - component:  String
// - perms:      String
// - icon:       String
// - orderNum:   Integer
// - visible:    String（0=visible, 1=hidden）
// - children:   List<SysMenu>（@TableField(exist=false)，树形构造用瞬态字段）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const sysMenuSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ SysMenu 自身字段 ============
  parentId: [optional(), number()],
  menuName: [optional(), string()],
  menuType: [optional(), string()],
  path: [optional(), string()],
  component: [optional(), string()],
  perms: [optional(), string()],
  icon: [optional(), string()],
  orderNum: [optional(), number()],
  visible: [optional(), string()],
  // children 为瞬态字段，不持久化，但响应数据中会携带（树形结构）；
  // 此处仅做类型声明，不递归校验元素，避免循环引用。
  children: [optional(), array()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `name/type/permission/sort` 时，
 * validator 会自动映射到 `menuName/menuType/perms/orderNum`。
 *
 * 注：前端 `pageType/pageCode/status` 为前端独有字段，后端 SysMenu 未定义，
 *     会被 schema 白名单过滤剥离，不参与映射。
 */
export const sysMenuFieldMapping = {
  name: 'menuName',
  type: 'menuType',
  permission: 'perms',
  sort: 'orderNum'
} as const

/** SysMenu 请求体 validator（带旧字段名兼容映射） */
export const sysMenuRequestValidator = createValidatorWithMapping(
  sysMenuSchema,
  sysMenuFieldMapping
)

/** SysMenu 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateSysMenuResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(sysMenuSchema, {})(input)
}

/**
 * SysMenu 类型定义——与后端 SysMenu 实体字段名严格一致。
 *
 * 替代 `api/system.ts` 中的旧 `SysMenu` interface（含 name/type 等短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface SysMenuDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // SysMenu 自身字段
  parentId?: number
  menuName?: string
  menuType?: string
  path?: string
  component?: string
  perms?: string
  icon?: string
  orderNum?: number
  visible?: string
  children?: SysMenuDTO[]
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 sysMenuRequestValidator 校验以下 URL：
// - POST   /api/system/menu   （创建菜单）
// - PUT    /api/system/menu    （更新菜单）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/menu',
  requestValidator: sysMenuRequestValidator,
  description: '创建菜单 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/system/menu',
  requestValidator: sysMenuRequestValidator,
  description: '更新菜单 - 校验请求体字段名'
})
