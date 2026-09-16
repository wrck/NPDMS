/**
 * PunchList 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-project/punchlist/entity/PunchList.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@NotNull/@Size 等）的前端镜像。
 *
 * 字段命名以后端为准：projectId / severity / title / walkdownStage 等。
 *
 * 历史问题：前端 `api/punch-list.ts` 的 `PunchList` interface 部分字段使用
 * `createdAt/createdBy`（驼峰风格），与后端 BaseEntity 字段名
 * `createTime/createBy` 不一致；且 `attachmentIds` 类型为 `number[]`，
 * 但后端实体声明为 `String`（逗号分隔的 id 列表）。
 * 本 validator 通过 `fieldMapping` 提供旧字段名→新字段名的自动映射，
 * 在不破坏现有 view 代码的前提下保证出站请求体字段名正确。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  maxLen,
  number,
  optional,
  required,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ PunchList Schema ============
//
// 与后端 PunchList.java 字段一一对应：
// - projectId:      @NotNull Long
// - milestoneId:    Long
// - severity:       @NotBlank @Size(max=20) [SAFETY/FUNCTIONAL/COSMETIC]
// - title:          @NotBlank @Size(max=200)
// - description:    @Size(max=2000)
// - walkdownStage:  @Size(max=20) [PRE_PUNCH/FORMAL]
// - assigneeId:     Long
// - assigneeName:   @Size(max=50)
// - deadline:       LocalDate
// - status:         @Size(max=50) [OPEN/RESOLVED/VERIFIED]
// - resolvedAt:     LocalDateTime
// - verifiedAt:     LocalDateTime
// - verifiedBy:     Long
// - verifiedByName: @Size(max=50)
// - attachmentIds:  String（逗号分隔的附件 id 列表）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy）
//     在前端 schema 中以可选形式声明，便于响应数据校验。

const PUNCH_LIST_SEVERITY_ENUM = ['SAFETY', 'FUNCTIONAL', 'COSMETIC'] as const
const PUNCH_LIST_WALKDOWN_STAGE_ENUM = ['PRE_PUNCH', 'FORMAL'] as const
const PUNCH_LIST_STATUS_ENUM = ['OPEN', 'RESOLVED', 'VERIFIED'] as const

export const punchListSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],

  // ============ PunchList 自身字段 ============
  projectId: [required(), number()],
  milestoneId: [optional(), number()],
  severity: [required(), string(), maxLen(20)],
  title: [required(), string(), maxLen(200)],
  description: [optional(), string(), maxLen(2000)],
  walkdownStage: [optional(), string(), maxLen(20)],
  assigneeId: [optional(), number()],
  assigneeName: [optional(), string(), maxLen(50)],
  deadline: [optional(), string()],
  status: [optional(), string(), maxLen(50)],
  resolvedAt: [optional(), string()],
  verifiedAt: [optional(), string()],
  verifiedBy: [optional(), number()],
  verifiedByName: [optional(), string(), maxLen(50)],
  attachmentIds: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `createdAt/createdBy`（驼峰）时，
 * validator 会自动映射到 `createTime/createBy`（后端 BaseEntity 字段名）。
 *
 * 注：前端 `api/punch-list.ts` 的 `PunchList` interface 中 `attachmentIds` 类型
 * 为 `number[]`，但后端实体声明为 `String`（逗号分隔）。本 schema 以后端为准
 * 声明为 string 类型；前端代码如传递数组，应在调用前自行 `.join(',')`。
 */
export const punchListFieldMapping = {
  createdAt: 'createTime',
  createdBy: 'createBy'
} as const

/** PunchList 请求体 validator（带旧字段名兼容映射） */
export const punchListRequestValidator = createValidatorWithMapping(
  punchListSchema,
  punchListFieldMapping
)

/** PunchList 响应数据 validator（无映射，直接按后端字段名校验） */
export function validatePunchListResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(punchListSchema, {})(input)
}

/**
 * PunchList 类型定义——与后端 PunchList 实体字段名严格一致。
 *
 * 替代 `api/punch-list.ts` 中的旧 `PunchList` interface（含字段名/类型差异）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface PunchListDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string

  // PunchList 自身字段
  projectId: number
  milestoneId?: number
  severity: typeof PUNCH_LIST_SEVERITY_ENUM[number]
  title: string
  description?: string
  walkdownStage?: typeof PUNCH_LIST_WALKDOWN_STAGE_ENUM[number]
  assigneeId?: number
  assigneeName?: string
  deadline?: string
  status?: typeof PUNCH_LIST_STATUS_ENUM[number]
  resolvedAt?: string
  verifiedAt?: string
  verifiedBy?: number
  verifiedByName?: string
  /** 逗号分隔的附件 id 列表（后端 String 类型） */
  attachmentIds?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 punchListRequestValidator 校验以下 URL：
// - POST /api/project/punch-list （创建 punch list 项）
// - PUT  /api/project/punch-list （更新 punch list 项）
//
// 校验失败 → ElMessage.error + 阻止请求发送
registerValidator({
  method: 'POST',
  urlPattern: '/api/project/punch-list',
  requestValidator: punchListRequestValidator,
  description: '创建 punch list 项 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/project/punch-list',
  requestValidator: punchListRequestValidator,
  description: '更新 punch list 项 - 校验请求体字段名'
})
