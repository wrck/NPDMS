/**
 * ApprovalFieldPermission 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-workflow/.../entity/ApprovalFieldPermission.java` 严格对齐，
 * 作为后端 Jakarta Validation 注解（@NotNull/@NotBlank/@Size）的前端镜像。
 *
 * 字段命名以后端为准：approvalNodeId / entityType / fieldName / permission /
 * maskPattern / customPattern / version。前端
 * `api/approval-field-perm.ts` 的 `ApprovalFieldPermission` interface 字段名
 * 已与后端一致，故 `fieldMapping` 为空对象。
 *
 * 权限取值：VISIBLE / MASKED / HIDDEN；
 * maskPattern 取值：phone-mask / amount-mask / email-mask / custom。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  enumOf,
  maxLen,
  number,
  optional,
  required,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

/** 权限枚举：VISIBLE / MASKED / HIDDEN */
const PERMISSION_ENUM = ['VISIBLE', 'MASKED', 'HIDDEN'] as const

/** 脱敏规则枚举：phone-mask / amount-mask / email-mask / custom */
const MASK_PATTERN_ENUM = [
  'phone-mask',
  'amount-mask',
  'email-mask',
  'custom'
] as const

// ============ ApprovalFieldPermission Schema ============
//
// 与后端 ApprovalFieldPermission.java 字段一一对应：
// - approvalNodeId:  @NotNull Long
// - entityType:      @NotBlank @Size(max=128) String
// - fieldName:       @NotBlank @Size(max=64) String
// - permission:      String（默认 "VISIBLE"）
// - maskPattern:     @Size(max=64) String
// - customPattern:   @Size(max=128) String
// - version:         @Version Integer（乐观锁）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const approvalFieldPermissionSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ ApprovalFieldPermission 自身字段 ============
  approvalNodeId: [required(), number()],
  entityType: [required(), string(), maxLen(128)],
  fieldName: [required(), string(), maxLen(64)],
  permission: [optional(), string(), maxLen(64), enumOf(PERMISSION_ENUM)],
  maskPattern: [optional(), string(), maxLen(64), enumOf(MASK_PATTERN_ENUM)],
  customPattern: [optional(), string(), maxLen(128)],
  version: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/approval-field-perm.ts` 的 `ApprovalFieldPermission` 字段名与
 * 后端一致，无需映射。
 */
export const approvalFieldPermissionFieldMapping = {} as const

/** ApprovalFieldPermission 请求体 validator（带旧字段名兼容映射） */
export const approvalFieldPermissionRequestValidator = createValidatorWithMapping(
  approvalFieldPermissionSchema,
  approvalFieldPermissionFieldMapping
)

/** ApprovalFieldPermission 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateApprovalFieldPermissionResponse(
  input: unknown
): ValidationResult {
  return createValidatorWithMapping(approvalFieldPermissionSchema, {})(input)
}

/**
 * ApprovalFieldPermission 类型定义——与后端 ApprovalFieldPermission 实体
 * 字段名严格一致。
 *
 * 与 `api/approval-field-perm.ts` 中的 `ApprovalFieldPermission` interface
 * 字段名一致。
 */
export interface ApprovalFieldPermissionDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // ApprovalFieldPermission 自身字段
  /** 关联审批节点ID（或节点模板） */
  approvalNodeId: number
  /** 业务实体类名（如 Deliverable） */
  entityType: string
  /** 字段名 */
  fieldName: string
  /** 权限：VISIBLE / MASKED / HIDDEN */
  permission?: typeof PERMISSION_ENUM[number]
  /** 脱敏规则：phone-mask / amount-mask / email-mask / custom */
  maskPattern?: typeof MASK_PATTERN_ENUM[number]
  /** 自定义正则（当 maskPattern=custom 时使用） */
  customPattern?: string
  /** 乐观锁版本号 */
  version?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 approvalFieldPermissionRequestValidator
// 校验以下 URL：
// - POST   /api/workflow/field-perm   （新增字段权限）
// - PUT    /api/workflow/field-perm   （更新字段权限）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/workflow/field-perm',
  requestValidator: approvalFieldPermissionRequestValidator,
  description: '新增审批字段权限 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/workflow/field-perm',
  requestValidator: approvalFieldPermissionRequestValidator,
  description: '更新审批字段权限 - 校验请求体字段名'
})
