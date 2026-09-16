/**
 * Issue 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-governance/.../issue/entity/Issue.java` 严格对齐，作为后端 Jakarta
 * Validation 注解（@NotBlank/@NotNull/@Size 等）的前端镜像。
 *
 * 字段命名以后端为准：issueNo / projectId / description / priority / sourceCrNo 等。
 *
 * 历史问题：前端 `api/issue.ts` 的 `Issue` interface 部分字段使用
 * `sourceChangeNo/createdAt`（驼峰风格），与后端字段名
 * `sourceCrNo/createTime` 不一致。本 validator 通过 `fieldMapping` 提供
 * 旧字段名→新字段名的自动映射，在不破坏现有 view 代码的前提下保证出站请求体
 * 字段名正确。
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

// ============ Issue Schema ============
//
// 与后端 Issue.java 字段一一对应：
// - issueNo:           @NotBlank @Size(max=50)
// - projectId:         @NotNull Long
// - description:       @NotBlank @Size(max=2000)
// - raisedBy:          Long
// - raisedByName:      @Size(max=50)
// - assigneeId:        Long
// - assigneeName:      @Size(max=50)
// - priority:          @NotBlank @Size(max=20) [LOW/MEDIUM/HIGH/CRITICAL]
// - targetResolveDate: LocalDate
// - status:            @Size(max=50) [OPEN/IN_PROGRESS/RESOLVED/CLOSED]
// - sourceRiskId:      Long
// - sourceRiskNo:      @Size(max=50)
// - sourceChangeId:    Long
// - sourceCrNo:        @Size(max=50)
// - resolvedAt:        LocalDateTime
// - closedAt:          LocalDateTime
// - resolution:        @Size(max=2000)
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy）
//     在前端 schema 中以可选形式声明，便于响应数据校验。

const ISSUE_PRIORITY_ENUM = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const
const ISSUE_STATUS_ENUM = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as const

export const issueSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],

  // ============ Issue 自身字段 ============
  issueNo: [required(), string(), maxLen(50)],
  projectId: [required(), number()],
  description: [required(), string(), maxLen(2000)],
  raisedBy: [optional(), number()],
  raisedByName: [optional(), string(), maxLen(50)],
  assigneeId: [optional(), number()],
  assigneeName: [optional(), string(), maxLen(50)],
  priority: [required(), string(), maxLen(20)],
  targetResolveDate: [optional(), string()],
  status: [optional(), string(), maxLen(50)],
  sourceRiskId: [optional(), number()],
  sourceRiskNo: [optional(), string(), maxLen(50)],
  sourceChangeId: [optional(), number()],
  sourceCrNo: [optional(), string(), maxLen(50)],
  resolvedAt: [optional(), string()],
  closedAt: [optional(), string()],
  resolution: [optional(), string(), maxLen(2000)]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 用于兼容历史代码：当前端代码仍使用 `sourceChangeNo/createdAt`（驼峰风格）时，
 * validator 会自动映射到 `sourceCrNo/createTime`（后端字段名）。
 *
 * 注：后端 Issue 实体字段为 `sourceCrNo`（变更单号 CrNo 的来源），前端
 * `api/issue.ts` 误写为 `sourceChangeNo`，需要映射纠正。
 */
export const issueFieldMapping = {
  sourceChangeNo: 'sourceCrNo',
  createdAt: 'createTime'
} as const

/** Issue 请求体 validator（带旧字段名兼容映射） */
export const issueRequestValidator = createValidatorWithMapping(
  issueSchema,
  issueFieldMapping
)

/** Issue 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateIssueResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(issueSchema, {})(input)
}

/**
 * Issue 类型定义——与后端 Issue 实体字段名严格一致。
 *
 * 替代 `api/issue.ts` 中的旧 `Issue` interface（含旧短名）。
 * 现有 view 代码迁移到新 interface 后，可删除旧定义。
 */
export interface IssueDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string

  // Issue 自身字段
  issueNo: string
  projectId: number
  description: string
  raisedBy?: number
  raisedByName?: string
  assigneeId?: number
  assigneeName?: string
  priority: typeof ISSUE_PRIORITY_ENUM[number]
  targetResolveDate?: string
  status?: typeof ISSUE_STATUS_ENUM[number]
  sourceRiskId?: number
  sourceRiskNo?: string
  sourceChangeId?: number
  sourceCrNo?: string
  resolvedAt?: string
  closedAt?: string
  resolution?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 issueRequestValidator 校验以下 URL：
// - POST /api/governance/issue （创建问题）
// - PUT  /api/governance/issue （更新问题）
//
// 校验失败 → ElMessage.error + 阻止请求发送
registerValidator({
  method: 'POST',
  urlPattern: '/api/governance/issue',
  requestValidator: issueRequestValidator,
  description: '创建问题 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/governance/issue',
  requestValidator: issueRequestValidator,
  description: '更新问题 - 校验请求体字段名'
})
