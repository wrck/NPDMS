/**
 * TaskChecklist 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-implementation/.../entity/TaskChecklist.java` 严格对齐，作为
 * 后端 Jakarta Validation 注解（@NotNull/@NotBlank/@Size）的前端镜像。
 *
 * 字段命名以后端为准：taskId / title / description / mandatory / checked /
 * checkedBy / checkedAt / sortOrder / version。前端 `api/task-checklist.ts`
 * 的 `TaskChecklistItem` interface 字段名已与后端一致，故 `fieldMapping`
 * 为空对象。
 *
 * 强制检查项（mandatory=true）在提交评审前必须勾选，否则后端会抛出
 * TaskChecklistRequiredException。
 * ===========================================================================
 */
import {
  boolean,
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

// ============ TaskChecklist Schema ============
//
// 与后端 TaskChecklist.java 字段一一对应：
// - taskId:      @NotNull Long
// - title:       @NotBlank @Size(max=128) String
// - description: @Size(max=500) String
// - mandatory:   Boolean（默认 false）
// - checked:     Boolean（默认 false）
// - checkedBy:   Long
// - checkedAt:   LocalDateTime
// - sortOrder:   Integer（默认 0）
// - version:     @Version Integer（乐观锁）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const taskChecklistSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ TaskChecklist 自身字段 ============
  taskId: [required(), number()],
  title: [required(), string(), maxLen(128)],
  description: [optional(), string(), maxLen(500)],
  mandatory: [optional(), boolean()],
  checked: [optional(), boolean()],
  checkedBy: [optional(), number()],
  checkedAt: [optional(), string()],
  sortOrder: [optional(), number()],
  version: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/task-checklist.ts` 的 `TaskChecklistItem` 字段名与后端一致，无需映射。
 */
export const taskChecklistFieldMapping = {} as const

/** TaskChecklist 请求体 validator（带旧字段名兼容映射） */
export const taskChecklistRequestValidator = createValidatorWithMapping(
  taskChecklistSchema,
  taskChecklistFieldMapping
)

/** TaskChecklist 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateTaskChecklistResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(taskChecklistSchema, {})(input)
}

/**
 * TaskChecklist 类型定义——与后端 TaskChecklist 实体字段名严格一致。
 *
 * 与 `api/task-checklist.ts` 中的 `TaskChecklistItem` interface 字段名一致。
 */
export interface TaskChecklistDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // TaskChecklist 自身字段
  taskId: number
  /** 检查项标题（最长 128 字符） */
  title: string
  /** 检查项描述（最长 500 字符，可选） */
  description?: string
  /** 是否强制检查项（提交评审前必须勾选） */
  mandatory?: boolean
  /** 是否已勾选 */
  checked?: boolean
  /** 勾选人ID */
  checkedBy?: number
  /** 勾选时间 */
  checkedAt?: string
  /** 排序序号 */
  sortOrder?: number
  /** 乐观锁版本号 */
  version?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 taskChecklistRequestValidator 校验以下 URL：
// - POST   /api/implementation/task/checklist   （新增检查项）
// - PUT    /api/implementation/task/checklist   （更新检查项）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/implementation/task/checklist',
  requestValidator: taskChecklistRequestValidator,
  description: '新增任务检查项 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'PUT',
  urlPattern: '/api/implementation/task/checklist',
  requestValidator: taskChecklistRequestValidator,
  description: '更新任务检查项 - 校验请求体字段名'
})
