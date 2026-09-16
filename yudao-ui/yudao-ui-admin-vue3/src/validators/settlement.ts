/**
 * Settlement 实体 Schema 与 Validator
 * ===========================================================================
 *
 * 与后端 `pms-implementation/.../entity/Settlement.java` 及其明细
 * `SettlementDetail.java`、请求包装 `SettlementCreateRequest.java` 严格对齐，
 * 作为后端 Jakarta Validation 注解（@NotNull/@NotBlank/@DecimalMin/@Size）
 * 的前端镜像。
 *
 * 请求体形态为 `SettlementCreateRequest`：
 * ```
 * {
 *   settlement: { ...Settlement 字段... },   // @NotNull @Valid
 *   details:     [ ...SettlementDetail 字段... ]  // @NotEmpty @Valid
 * }
 * ```
 *
 * 字段命名以后端为准：settlementNo / totalAmount / taxRate / taxAmount /
 * totalWithTax / applyUserName / approveUserName / approveOpinion /
 * pushStatus / pushResponse / processInstanceId 等。前端
 * `api/implementation.ts` 的 `Settlement` / `SettlementDetail` interface
 * 字段名已与后端一致，故 `fieldMapping` 为空对象。前端额外的 `agentName` /
 * `projectName` / `remark` 为展示字段（后端无对应列），校验时会被白名单过滤。
 * ===========================================================================
 */
import {
  array,
  createValidatorWithMapping,
  defineSchema,
  maxLen,
  min,
  number,
  object,
  optional,
  required,
  string,
  type Schema,
  type ValidationResult
} from './index'
import { registerValidator } from './registry'

/** 结算单状态枚举：PENDING / APPROVED / REJECTED / PUSHED */
const SETTLEMENT_STATUS_ENUM = ['PENDING', 'APPROVED', 'REJECTED', 'PUSHED'] as const

/** 推送状态枚举：NULL / SUCCESS / FAILED */
const PUSH_STATUS_ENUM = ['SUCCESS', 'FAILED'] as const

// ============ SettlementDetail Schema ============
//
// 与后端 SettlementDetail.java 字段一一对应：
// - settlementId:   Long（无注解）
// - itemName:       @NotBlank @Size(max=200) String
// - workQuantity:   @NotNull @DecimalMin("0") BigDecimal
// - unit:           @NotBlank @Size(max=20) String
// - unitPrice:      @NotNull @DecimalMin("0") BigDecimal
// - amount:         @NotNull @DecimalMin("0") BigDecimal
// - remarks:        @Size(max=500) String
export const settlementDetailSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ SettlementDetail 自身字段 ============
  settlementId: [optional(), number()],
  itemName: [required(), string(), maxLen(200)],
  workQuantity: [required(), number(), min(0)],
  unit: [required(), string(), maxLen(20)],
  unitPrice: [required(), number(), min(0)],
  amount: [required(), number(), min(0)],
  remarks: [optional(), string(), maxLen(500)]
})

// ============ Settlement Schema ============
//
// 与后端 Settlement.java 字段一一对应：
// - taskId:            @NotNull Long
// - agentId:           @NotNull Long
// - projectId:         @NotNull Long
// - settlementNo:      @NotBlank @Size(max=50) String
// - totalAmount:       @NotNull @DecimalMin("0") BigDecimal
// - taxRate:           @DecimalMin("0") BigDecimal
// - taxAmount:         @DecimalMin("0") BigDecimal
// - totalWithTax:      @DecimalMin("0") BigDecimal
// - status:            @Size(max=50) String
// - applyUserId:       Long
// - applyUserName:     @Size(max=50) String
// - applyTime:         LocalDateTime
// - approveUserId:     Long
// - approveUserName:   @Size(max=50) String
// - approveTime:       LocalDateTime
// - approveOpinion:    @Size(max=500) String
// - pushStatus:        @Size(max=20) String
// - pushTime:          LocalDateTime
// - pushResponse:      String
// - processInstanceId: String
// - version:           @Version Integer（乐观锁）
export const settlementSchema: Schema = defineSchema({
  // ============ BaseEntity 字段 ============
  id: [optional(), number()],
  createTime: [optional(), string()],
  updateTime: [optional(), string()],
  createBy: [optional(), string()],
  updateBy: [optional(), string()],
  deleted: [optional(), number()],

  // ============ Settlement 自身字段 ============
  taskId: [required(), number()],
  agentId: [required(), number()],
  projectId: [required(), number()],
  settlementNo: [required(), string(), maxLen(50)],
  totalAmount: [required(), number(), min(0)],
  taxRate: [optional(), number(), min(0)],
  taxAmount: [optional(), number(), min(0)],
  totalWithTax: [optional(), number(), min(0)],
  status: [optional(), string(), maxLen(50)],
  applyUserId: [optional(), number()],
  applyUserName: [optional(), string(), maxLen(50)],
  applyTime: [optional(), string()],
  approveUserId: [optional(), number()],
  approveUserName: [optional(), string(), maxLen(50)],
  approveTime: [optional(), string()],
  approveOpinion: [optional(), string(), maxLen(500)],
  pushStatus: [optional(), string(), maxLen(20)],
  pushTime: [optional(), string()],
  pushResponse: [optional(), string()],
  processInstanceId: [optional(), string()],
  version: [optional(), number()]
})

// ============ SettlementCreateRequest Schema ============
//
// 与后端 SettlementCreateRequest.java 字段一一对应：
// - settlement: @NotNull @Valid Settlement
// - details:    @NotEmpty @Valid List<SettlementDetail>
export const settlementCreateSchema: Schema = defineSchema({
  settlement: [required(), object(), { schema: settlementSchema }],
  details: [
    required(),
    array(),
    { items: { type: 'object', schema: settlementDetailSchema } }
  ]
})

/**
 * 旧字段名 → 后端字段名映射。
 *
 * 前端 `api/implementation.ts` 的 `Settlement` / `SettlementDetail` 字段名与
 * 后端一致，无需映射。
 */
export const settlementFieldMapping = {} as const

/** SettlementCreateRequest 请求体 validator（带旧字段名兼容映射） */
export const settlementRequestValidator = createValidatorWithMapping(
  settlementCreateSchema,
  settlementFieldMapping
)

/** Settlement 响应数据 validator（无映射，直接按后端字段名校验） */
export function validateSettlementResponse(input: unknown): ValidationResult {
  return createValidatorWithMapping(settlementSchema, {})(input)
}

/**
 * Settlement 类型定义——与后端 Settlement 实体字段名严格一致。
 *
 * 与 `api/implementation.ts` 中的 `Settlement` interface 字段名一致。
 */
export interface SettlementDTO {
  // BaseEntity
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  // Settlement 自身字段
  taskId: number
  agentId: number
  projectId: number
  settlementNo: string
  totalAmount: number
  taxRate?: number
  taxAmount?: number
  totalWithTax?: number
  status?: typeof SETTLEMENT_STATUS_ENUM[number]
  applyUserId?: number
  applyUserName?: string
  applyTime?: string
  approveUserId?: number
  approveUserName?: string
  approveTime?: string
  approveOpinion?: string
  pushStatus?: typeof PUSH_STATUS_ENUM[number] | null
  pushTime?: string
  pushResponse?: string
  processInstanceId?: string
  version?: number
}

/**
 * SettlementDetail 类型定义——与后端 SettlementDetail 实体字段名严格一致。
 *
 * 与 `api/implementation.ts` 中的 `SettlementDetail` interface 字段名一致。
 */
export interface SettlementDetailDTO {
  id?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  deleted?: number

  settlementId?: number
  itemName: string
  workQuantity: number
  unit: string
  unitPrice: number
  amount: number
  remarks?: string
}

/**
 * SettlementCreateRequest 类型定义——与后端 SettlementCreateRequest 严格一致。
 *
 * 与 `api/implementation.ts` 中的 `CreateSettlementPayload` interface 字段名一致。
 */
export interface SettlementCreateRequestDTO {
  settlement: SettlementDTO
  details: SettlementDetailDTO[]
}

// ============ 注册到全局 registry ============
//
// 注册后，request.ts 拦截器会自动调用 settlementRequestValidator 校验以下 URL：
// - POST   /api/impl/settlement   （创建结算单 + 明细）
//
// 校验失败 → ElMessage.error + 阻止请求发送（避免触发后端 400）
registerValidator({
  method: 'POST',
  urlPattern: '/api/impl/settlement',
  requestValidator: settlementRequestValidator,
  description: '创建结算单 - 校验请求体字段名、必填、明细非空'
})
