/**
 * Workflow 请求 DTO Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-workflow/.../dto/StartProcessRequest.java`、
 * `CompleteTaskRequest.java` 严格对齐，作为后端 Jakarta Validation 注解
 * （@NotBlank/@Size）的前端镜像。
 *
 * 包含两个独立的 schema + validator：
 * - `startProcessRequestValidator`：POST /api/workflow/start
 * - `completeTaskRequestValidator`：POST /api/workflow/task/complete
 *
 * 前端 `api/workflow.ts` 的 `StartProcessPayload` / `CompleteTaskPayload`
 * interface 字段名已与后端一致，故 `fieldMapping` 为空对象。
 * ===========================================================================
 */
import {
  createValidatorWithMapping,
  defineSchema,
  maxLen,
  object,
  optional,
  required,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

// ============ StartProcessRequest Schema ============
//
// 与后端 StartProcessRequest.java 字段一一对应：
// - processDefinitionKey: @NotBlank @Size(max=100) String
// - businessKey:          @Size(max=100) String
// - variables:            Map<String, Object>
export const startProcessSchema: Schema = defineSchema({
  processDefinitionKey: [required(), string(), maxLen(100)],
  businessKey: [optional(), string(), maxLen(100)],
  variables: [optional(), object()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/workflow.ts` 的 `StartProcessPayload` 字段名与后端一致，无需映射。
 */
export const startProcessFieldMapping = {} as const

/** StartProcessRequest 请求体 validator（带旧字段名兼容映射） */
export const startProcessRequestValidator = createValidatorWithMapping(
  startProcessSchema,
  startProcessFieldMapping
)

/** StartProcessRequest 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateStartProcessResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(startProcessSchema, {})(input)
}

// ============ CompleteTaskRequest Schema ============
//
// 与后端 CompleteTaskRequest.java 字段一一对应：
// - taskId:   String（无注解，但 Swagger 标注为 REQUIRED）
// - variables: Map<String, Object>
// - comment:  String
export const completeTaskSchema: Schema = defineSchema({
  taskId: [required(), string()],
  variables: [optional(), object()],
  comment: [optional(), string()]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/workflow.ts` 的 `CompleteTaskPayload` 字段名与后端一致，无需映射。
 */
export const completeTaskFieldMapping = {} as const

/** CompleteTaskRequest 请求体 validator（带旧字段名兼容映射） */
export const completeTaskRequestValidator = createValidatorWithMapping(
  completeTaskSchema,
  completeTaskFieldMapping
)

/** CompleteTaskRequest 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateCompleteTaskResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(completeTaskSchema, {})(input)
}

/**
 * StartProcessRequest 类型定义——与后端 StartProcessRequest 严格一致。
 *
 * 与 `api/workflow.ts` 中的 `StartProcessPayload` interface 字段名一致。
 */
export interface StartProcessRequestDTO {
  /** 流程定义Key */
  processDefinitionKey: string
  /** 业务Key */
  businessKey?: string
  /** 流程变量 */
  variables?: Record<string, unknown>
}

/**
 * CompleteTaskRequest 类型定义——与后端 CompleteTaskRequest 严格一致。
 *
 * 与 `api/workflow.ts` 中的 `CompleteTaskPayload` interface 字段名一致。
 */
export interface CompleteTaskRequestDTO {
  /** 任务ID */
  taskId: string
  /** 流程变量 */
  variables?: Record<string, unknown>
  /** 审批意见 */
  comment?: string
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用对应 validator 校验以下 URL：
// - POST   /api/workflow/start          （启动流程实例）
// - POST   /api/workflow/task/complete  （完成待办任务）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/workflow/start',
  requestValidator: startProcessRequestValidator,
  description: '启动流程实例 - 校验请求体字段名与必填'
})

registerValidator({
  method: 'POST',
  urlPattern: '/api/workflow/task/complete',
  requestValidator: completeTaskRequestValidator,
  description: '完成待办任务 - 校验请求体字段名'
})
