/**
 * 数据集成校验对象（Data Integration Validator）
 * ===========================================================================
 *
 * 设计目标
 * --------
 * 作为前端 ↔ 后端数据交互的统一守门员，确保：
 * 1. 出站请求体（POST/PUT/PATCH）字段名与后端实体契约严格一致；
 * 2. 入站响应数据（Result.data）通过字段白名单过滤，避免未知字段污染前端状态；
 * 3. 必填 / 类型 / 范围 / 长度等基础校验在前端先于网络请求触发，给开发者即时反馈；
 * 4. 所有校验规则与后端 Jakarta Validation 注解（@NotBlank/@Size/@Min/@Max 等）
 *    一一对应，作为后端契约的前端镜像。
 *
 * 核心概念
 * --------
 * - {@link FieldSpec}：单字段规则。包含 type、required、maxLen、min、max、enum、regex 等。
 * - {@link Schema}：字段规则的集合，`{ fieldName: FieldSpec }`。
 * - {@link Validator}：校验器函数，`(input: unknown) => ValidationResult<T>`。
 * - {@link ValidationResult}：校验结果。valid=false 时携带 errors 数组；valid=true 时
 *   携带 normalize 后的 data（仅包含 schema 中声明的字段）。
 *
 * 使用方式
 * --------
 * ```ts
 * import { defineSchema, validate, required, string, optional, maxLen, range, enumOf } from '@/validators'
 *
 * const projectSchema = defineSchema({
 *   projectCode: [required(), string(), maxLen(50)],
 *   projectName: [required(), string(), maxLen(200)],
 *   projectType: [required(), string(), enumOf(['NETWORK_DEVICE', 'SECURITY', 'DATACENTER'])],
 *   progress: [optional(), number(), range(0, 100)],
 * })
 *
 * const result = validate(projectSchema, input)
 * if (!result.valid) {
 *   console.error(result.errors)  // ['projectName: 字段必填', 'progress: 必须是数字']
 * } else {
 *   console.log(result.data)      // 仅含 projectCode/projectName/projectType/progress
 * }
 * ```
 *
 * 与 request.ts 集成
 * -------------------
 * - 请求拦截器：根据 method + url 从 {@link registry} 查找 validator，校验请求体。
 *   校验失败 → ElMessage.error + reject，不发请求。
 * - 响应拦截器：可选对响应 data 调用 validator，做字段白名单过滤。
 * ===========================================================================
 */

// ============ 类型定义 ============

/** 字段类型 */
export type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'array' | 'object'

/** 单条校验错误 */
export interface ValidationError {
  /** 字段路径，如 "projectName" 或 "items[0].taskName" */
  field: string
  /** 错误消息（中文，可直接展示给用户） */
  message: string
}

/** 校验结果 */
export interface ValidationResult<T = Record<string, unknown>> {
  /** 是否通过校验 */
  valid: boolean
  /** 错误列表（valid=false 时有值） */
  errors: ValidationError[]
  /** 规范化后的数据（仅包含 schema 中声明的字段；valid=true 时有值） */
  data?: T
}

/** 字段规则——所有字段均为可选，按需声明 */
export interface FieldSpec {
  /** 字段类型（用于类型检查） */
  type?: FieldType
  /** 是否必填（默认 false） */
  required?: boolean
  /** 字符串最大长度 */
  maxLen?: number
  /** 字符串最小长度 */
  minLen?: number
  /** 数字最小值 */
  min?: number
  /** 数字最大值 */
  max?: number
  /** 枚举值列表（字段值必须在此列表中） */
  enum?: Array<string | number>
  /** 正则校验（仅对 string 类型生效） */
  pattern?: RegExp
  /** 嵌套对象 schema（仅 type='object' 时生效） */
  schema?: Schema
  /** 数组元素 schema（仅 type='array' 时生效） */
  items?: FieldSpec
}

/** Schema：字段名 → 字段规则 */
export type Schema = Record<string, FieldSpec>

// ============ 规则构造函数（链式 API） ============

/** 必填 */
export function required(): Pick<FieldSpec, 'required'> {
  return { required: true }
}

/** 可选（显式声明，默认即为可选） */
export function optional(): Pick<FieldSpec, 'required'> {
  return { required: false }
}

/** 字符串类型 */
export function string(): Pick<FieldSpec, 'type'> {
  return { type: 'string' }
}

/** 数字类型 */
export function number(): Pick<FieldSpec, 'type'> {
  return { type: 'number' }
}

/** 布尔类型 */
export function boolean(): Pick<FieldSpec, 'type'> {
  return { type: 'boolean' }
}

/** 日期类型（字符串形式 YYYY-MM-DD 或 ISO 8601） */
export function date(): Pick<FieldSpec, 'type'> {
  return { type: 'date' }
}

/** 数组类型 */
export function array(): Pick<FieldSpec, 'type'> {
  return { type: 'array' }
}

/** 对象类型 */
export function object(): Pick<FieldSpec, 'type'> {
  return { type: 'object' }
}

/** 最大长度（字符串/数组） */
export function maxLen(n: number): Pick<FieldSpec, 'maxLen'> {
  return { maxLen: n }
}

/** 最小长度（字符串/数组） */
export function minLen(n: number): Pick<FieldSpec, 'minLen'> {
  return { minLen: n }
}

/** 最小值（数字） */
export function min(n: number): Pick<FieldSpec, 'min'> {
  return { min: n }
}

/** 最大值（数字） */
export function max(n: number): Pick<FieldSpec, 'max'> {
  return { max: n }
}

/** 范围校验（min + max 的语法糖，仅对数字生效） */
export function range(minVal: number, maxVal: number): Pick<FieldSpec, 'min' | 'max'> {
  return { min: minVal, max: maxVal }
}

/** 枚举值 */
export function enumOf<T extends string | number>(values: readonly T[]): Pick<FieldSpec, 'enum'> {
  return { enum: [...values] }
}

/** 正则校验 */
export function pattern(re: RegExp): Pick<FieldSpec, 'pattern'> {
  return { pattern: re }
}

// ============ Schema 工厂 ============

/**
 * 定义一个 schema。规则数组按顺序合并为一个 FieldSpec。
 *
 * @example
 * ```ts
 * const schema = defineSchema({
 *   projectCode: [required(), string(), maxLen(50)],
 *   projectName: [required(), string(), maxLen(200)],
 * })
 * ```
 */
export function defineSchema(specs: Record<string, Array<Pick<FieldSpec, keyof FieldSpec>>>): Schema {
  const schema: Schema = {}
  for (const [field, rules] of Object.entries(specs)) {
    schema[field] = Object.assign({}, ...rules) as FieldSpec
  }
  return schema
}

// ============ 校验核心 ============

/** 判断值是否为 null/undefined/空字符串（按"空"处理） */
function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === ''
}

/** 判断值是否为数字（排除 NaN） */
function isNumber(value: unknown): value is number {
  return typeof value === 'number' && !Number.isNaN(value)
}

/** 判断值是否为字符串 */
function isString(value: unknown): value is string {
  return typeof value === 'string'
}

/** 判断值是否为日期字符串（YYYY-MM-DD 或 ISO 8601） */
function isDateString(value: unknown): boolean {
  if (typeof value !== 'string') return false
  // YYYY-MM-DD 或 YYYY-MM-DDTHH:mm:ss
  return /^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2})?/.test(value)
}

/**
 * 校验单个字段。
 *
 * @returns 错误消息数组（空数组表示通过）
 */
function validateField(field: string, value: unknown, spec: FieldSpec): string[] {
  const errors: string[] = []

  // 1. 必填校验
  if (isEmpty(value)) {
    if (spec.required) {
      errors.push(`${field}: 字段必填`)
    }
    return errors // 可选字段为空时，跳过后续校验
  }

  // 2. 类型校验
  if (spec.type) {
    switch (spec.type) {
      case 'string':
        if (!isString(value)) errors.push(`${field}: 必须是字符串`)
        break
      case 'number':
        if (!isNumber(value)) errors.push(`${field}: 必须是数字`)
        break
      case 'boolean':
        if (typeof value !== 'boolean') errors.push(`${field}: 必须是布尔值`)
        break
      case 'date':
        if (!isDateString(value)) errors.push(`${field}: 必须是日期字符串 (YYYY-MM-DD)`)
        break
      case 'array':
        if (!Array.isArray(value)) errors.push(`${field}: 必须是数组`)
        break
      case 'object':
        if (typeof value !== 'object' || Array.isArray(value) || value === null) {
          errors.push(`${field}: 必须是对象`)
        }
        break
    }
  }

  // 3. 长度校验（字符串/数组）
  if (spec.maxLen != null && (isString(value) || Array.isArray(value))) {
    if (value.length > spec.maxLen) {
      errors.push(`${field}: 长度不能超过 ${spec.maxLen}`)
    }
  }
  if (spec.minLen != null && (isString(value) || Array.isArray(value))) {
    if (value.length < spec.minLen) {
      errors.push(`${field}: 长度不能小于 ${spec.minLen}`)
    }
  }

  // 4. 范围校验（数字）
  if (spec.min != null && isNumber(value) && value < spec.min) {
    errors.push(`${field}: 不能小于 ${spec.min}`)
  }
  if (spec.max != null && isNumber(value) && value > spec.max) {
    errors.push(`${field}: 不能大于 ${spec.max}`)
  }

  // 5. 枚举校验
  if (spec.enum && !isEmpty(value)) {
    if (!spec.enum.includes(value as string | number)) {
      errors.push(`${field}: 必须是 [${spec.enum.join(', ')}] 之一`)
    }
  }

  // 6. 正则校验（字符串）
  if (spec.pattern && isString(value)) {
    if (!spec.pattern.test(value)) {
      errors.push(`${field}: 格式不正确`)
    }
  }

  // 7. 嵌套对象校验
  if (spec.type === 'object' && spec.schema && typeof value === 'object' && value !== null && !Array.isArray(value)) {
    const nested = validate(spec.schema, value as Record<string, unknown>)
    if (!nested.valid) {
      for (const e of nested.errors) {
        errors.push(`${field}.${e.field}: ${e.message}`)
      }
    }
  }

  // 8. 数组元素校验
  if (spec.type === 'array' && spec.items && Array.isArray(value)) {
    value.forEach((item, idx) => {
      const itemErrors = validateField(`${field}[${idx}]`, item, spec.items!)
      errors.push(...itemErrors)
    })
  }

  return errors
}

/**
 * 校验一个输入对象是否符合 schema。
 *
 * - 返回 valid=true 时，data 仅包含 schema 中声明的字段（白名单过滤）。
 * - 返回 valid=false 时，errors 包含所有字段的错误信息。
 *
 * @param schema 字段规则集合
 * @param input 待校验的输入（通常是请求体或响应 data）
 * @param options 选项
 */
export function validate<T = Record<string, unknown>>(
  schema: Schema,
  input: unknown,
  options: { stripUnknown?: boolean } = {}
): ValidationResult<T> {
  const errors: ValidationError[] = []
  const normalized: Record<string, unknown> = {}

  // 非对象输入直接失败
  if (typeof input !== 'object' || input === null || Array.isArray(input)) {
    return {
      valid: false,
      errors: [{ field: '_root', message: '输入必须是对象' }],
    }
  }

  const inputObj = input as Record<string, unknown>
  const stripUnknown = options.stripUnknown ?? true

  // 1. 按 schema 校验每个声明字段
  for (const [field, spec] of Object.entries(schema)) {
    const value = inputObj[field]
    const fieldErrors = validateField(field, value, spec)
    for (const msg of fieldErrors) {
      errors.push({ field, message: msg.replace(`${field}: `, '') })
    }
    // 即使值为空，也写入 normalized（保持字段存在性）
    if (!isEmpty(value)) {
      normalized[field] = value
    } else if (spec.required) {
      normalized[field] = undefined
    }
  }

  // 2. 未知字段检测（仅开发期警告，不阻止通过，避免误伤）
  if (!stripUnknown) {
    for (const key of Object.keys(inputObj)) {
      if (!(key in schema)) {
        // 仅在控制台警告，不计入 errors
        if (typeof console !== 'undefined' && console.warn) {
          console.warn(`[validator] 未知字段: ${key}`)
        }
      }
    }
  }

  if (errors.length > 0) {
    return { valid: false, errors }
  }

  return { valid: true, errors: [], data: normalized as T }
}

// ============ Validator 工厂 ============

/**
 * 根据 schema 创建一个 validator 函数。
 *
 * @example
 * ```ts
 * const projectValidator = createValidator<Project>(projectSchema)
 * const result = projectValidator(input)
 * ```
 */
export function createValidator<T = Record<string, unknown>>(
  schema: Schema
): (input: unknown) => ValidationResult<T> {
  return (input: unknown) => validate<T>(schema, input)
}

// ============ 字段映射（兼容旧字段名） ============

/**
 * 字段映射配置：把旧字段名映射到新字段名。
 *
 * 用于兼容历史代码：当前端代码仍使用旧字段名（如 `name`）时，
 * validator 会自动把 `name` 重命名为 `projectName` 后再校验。
 */
export type FieldMapping = Record<string, string>

/**
 * 应用字段映射：把 input 中的旧字段名重命名为新字段名。
 * - 仅在 input 中存在旧字段名、且新字段名不存在时执行映射
 * - 不修改原对象，返回新对象
 */
export function applyFieldMapping(
  input: unknown,
  mapping: FieldMapping
): Record<string, unknown> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) {
    return input as Record<string, unknown>
  }
  const result: Record<string, unknown> = { ...(input as Record<string, unknown>) }
  for (const [oldKey, newKey] of Object.entries(mapping)) {
    if (oldKey in result && !(newKey in result)) {
      result[newKey] = result[oldKey]
      delete result[oldKey]
    }
  }
  return result
}

/**
 * 创建带字段映射的 validator。
 *
 * @example
 * ```ts
 * const projectValidator = createValidatorWithMapping<Project>(projectSchema, {
 *   name: 'projectName',
 *   code: 'projectCode',
 *   type: 'projectType',
 * })
 * ```
 */
export function createValidatorWithMapping<T = Record<string, unknown>>(
  schema: Schema,
  mapping: FieldMapping
): (input: unknown) => ValidationResult<T> {
  return (input: unknown) => {
    const mapped = applyFieldMapping(input, mapping)
    return validate<T>(schema, mapped)
  }
}

// ============ 工具：把错误列表格式化为单条消息 ============

/** 把 ValidationResult.errors 拼接为单条消息，用于 ElMessage.error */
export function formatErrors(errors: ValidationError[]): string {
  return errors.map((e) => `${e.field}: ${e.message}`).join('; ')
}
