/**
 * TaskDependency 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-baseline/.../entity/TaskDependency.java` 严格对齐，作为后端
 * Jakarta Validation 注解（@NotNull/@NotBlank/@Size）的前端镜像。
 *
 * 字段命名以后端为准：projectId / predecessorTaskId / successorTaskId /
 * dependencyType / lagDays / version。前端 `api/task-dependency.ts` 的
 * `TaskDependency` interface 字段名已与后端一致，故 `fieldMapping` 为空对象。
 *
 * 依赖类型：FS（完成-开始）/ FF（完成-完成）/ SS（开始-开始）/ SF（开始-完成）。
 * lagDays 滞后天数（可负，表示提前）。保存时后端执行 DFS 循环依赖检测。
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

/** 依赖类型枚举：FS / FF / SS / SF */
const DEPENDENCY_TYPE_ENUM = ['FS', 'FF', 'SS', 'SF'] as const

// ============ TaskDependency Schema ============
//
// 与后端 TaskDependency.java 字段一一对应：
// - projectId:          @NotNull Long
// - predecessorTaskId:  @NotNull Long
// - successorTaskId:    @NotNull Long
// - dependencyType:     @NotBlank @Size(max=4) String（默认 "FS"）
// - lagDays:            Integer（默认 0，可负）
// - version:            @Version Integer（乐观锁）
//
// 注：BaseEntity 字段（id/createTime/updateTime/createBy/updateBy/deleted）
//     在前端 schema 中以可选形式声明，便于响应数据校验。
export const taskDependencySchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ TaskDependency 自身字段 ============
  projectId: [required(), number()],
  predecessorTaskId: [required(), number()],
  successorTaskId: [required(), number()],
  dependencyType: [required(), string(), maxLen(4), enumOf(DEPENDENCY_TYPE_ENUM)],
  lagDays: [optional(), number()],
  version: [optional(), number()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/task-dependency.ts` 的 `TaskDependency` 字段名与后端一致，无需映射。
 */
export const taskDependencyFieldMapping = {} as const

/** TaskDependency 请求体 validator（带旧字段名兼容映射） */
export const taskDependencyRequestValidator = createValidatorWithMapping(
  taskDependencySchema,
  taskDependencyFieldMapping
)

/** TaskDependency 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateTaskDependencyResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(taskDependencySchema, {})(input)
}

/**
 * TaskDependency 类型定义——与后端 TaskDependency 实体字段名严格一致。
 *
 * 与 `api/task-dependency.ts` 中的 `TaskDependency` interface 字段名一致。
 */
export interface TaskDependencyDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // TaskDependency 自身字段
  projectId: number
  predecessorTaskId: number
  successorTaskId: number
  /** FS / FF / SS / SF，默认 FS */
  dependencyType?: typeof DEPENDENCY_TYPE_ENUM[number]
  /** 滞后天数（可负，表示提前），默认 0 */
  lagDays?: number
  /** 乐观锁版本号 */
  version?: number
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 taskDependencyRequestValidator 校验以下 URL：
// - POST   /api/implementation/task/dependency   （保存任务依赖，含 DFS 循环检测）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/implementation/task/dependency',
  requestValidator: taskDependencyRequestValidator,
  description: '保存任务依赖 - 校验请求体字段名与必填'
})
