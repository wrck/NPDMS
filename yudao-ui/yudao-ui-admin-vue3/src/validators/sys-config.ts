/**
 * SysConfig 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-system/.../entity/SysConfig.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@Size/@Pattern 等）的前端镜像。
 *
 * 字段命名以后端为准：configName / configKey / configValue / configType /
 * remark 等。
 *
 * 注：前端 `api/system.ts` 暂未定义 SysConfig interface，故 `fieldMapping`
 *     为空对象。如后续前端引入短名（如 `name/key/value`），可在
 *     `sysConfigFieldMapping` 中补充旧→新映射。
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

// ============ SysConfig Schema ============
//
// 与后端 SysConfig.java 字段一一对应：
// - configName:   String
// - configKey:    String
// - configValue:  String
// - configType:   String（0=system built-in, 1=user-defined）
// - remark:       String
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const sysConfigSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ SysConfig 自身字段 ============
  configName: [optional(), string()],
  configKey: [optional(), string()],
  configValue: [optional(), string()],
  configType: [optional(), string()],
  remark: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/system.ts` 暂未定义 SysConfig interface，字段名与后端一致，故为空对象。
 */
export const sysConfigFieldMapping = {} as const

/** SysConfig 请求体 validator（带旧字段名兼容映射） */
export const sysConfigRequestValidator = createValidatorWithMapping(
  sysConfigSchema,
  sysConfigFieldMapping
)

/** SysConfig 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateSysConfigResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(sysConfigSchema, {})(input)
}

/**
 * SysConfig 类型定义——与后端 SysConfig 实体字段名严格一致。
 */
export interface SysConfigDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // SysConfig 自身字段
  configName?: string
  configKey?: string
  configValue?: string
  configType?: string
  remark?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 sysConfigRequestValidator 校验以下 URL：
// - POST   /api/system/config   （创建参数配置）
// - PUT    /api/system/config    （更新参数配置）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/system/config',
  requestValidator: sysConfigRequestValidator,
  description: '创建参数配置 - 校验请求体字段名'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/system/config',
  requestValidator: sysConfigRequestValidator,
  description: '更新参数配置 - 校验请求体字段名'
})
