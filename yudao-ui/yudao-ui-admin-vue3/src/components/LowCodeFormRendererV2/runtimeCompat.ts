import type { Rule } from '@form-create/element-ui'
import { FieldType, type FormFieldConfig } from '@/api/lowcode'

/**
 * V2 与 V1 共用的运行时语义适配。
 *
 * 这里不改变持久化 FormConfig，只把 V1 已经支持的字段类型、默认值和校验描述
 * 翻译成 FormCreate 能消费的结构，便于用单测锁住“V2 可替换”的边界。
 */
export function resolveCustomComponentName(field: FormFieldConfig): string {
  const legacyName = field.props?.componentName
  if (typeof legacyName === 'string' && legacyName) return legacyName
  return field.componentName || ''
}

export function resolveFormCreateComponentType(field: FormFieldConfig): string {
  switch (field.type) {
    case FieldType.INPUT:
    case FieldType.TEXTAREA:
    case FieldType.PASSWORD:
      return 'input'
    case FieldType.NUMBER:
      return 'InputNumber'
    case FieldType.SELECT:
      return 'select'
    case FieldType.RADIO:
      return 'radio'
    case FieldType.CHECKBOX:
      return 'checkbox'
    case FieldType.DATE:
    case FieldType.DATETIME:
    case FieldType.DATERANGE:
      return 'DatePicker'
    case FieldType.SWITCH:
      return 'switch'
    case FieldType.RATE:
      return 'rate'
    case FieldType.SLIDER:
      return 'slider'
    case FieldType.CASCADER:
      return 'cascader'
    case FieldType.UPLOAD:
      return 'upload'
    case FieldType.DIVIDER:
      return 'el-divider'
    case FieldType.TITLE:
      return 'h3'
    case FieldType.CUSTOM:
      return resolveCustomComponentName(field) || 'input'
    default:
      return 'input'
  }
}

type RendererValidation = NonNullable<Rule['validate']>

export function buildFormCreateValidate(field: FormFieldConfig): RendererValidation {
  const validate: RendererValidation = []
  if (field.required) {
    validate.push({
      required: true,
      message: field.placeholder || `请输入${field.label}`,
      trigger: ['blur', 'change']
    })
  }
  if (field.rules?.length) {
    // Persisted custom rules use the existing Element Plus validation grammar.
    // Narrow only at this adapter boundary; never write engine metadata into Schema.
    validate.push(...field.rules.map((rule) => ({ ...rule }) as RendererValidation[number]))
  }
  return validate
}

/** V1 外部回填是字段合并，而不是替换整个 model。 */
export function mergeExternalModelValue(
  target: Record<string, unknown>,
  value: Record<string, unknown> | undefined
): void {
  if (!value) return
  Object.keys(value).forEach((key) => {
    if (target[key] !== value[key]) target[key] = value[key]
  })
}

/** V1 初始化时只补齐缺失字段，保留外部已传入的值。 */
export function initMissingFieldDefaults(
  target: Record<string, unknown>,
  fields: FormFieldConfig[]
): void {
  fields.forEach((field) => {
    if (target[field.prop] !== undefined) return
    target[field.prop] =
      field.defaultValue !== undefined && field.defaultValue !== null
        ? field.defaultValue
        : field.type === FieldType.CHECKBOX
          ? []
          : ''
  })
}

/** V1 resetFields 允许显式 null 默认值，并保留不在 fields 中的业务字段。 */
export function resetFieldDefaults(
  target: Record<string, unknown>,
  fields: FormFieldConfig[]
): void {
  fields.forEach((field) => {
    target[field.prop] =
      field.defaultValue !== undefined
        ? field.defaultValue
        : field.type === FieldType.CHECKBOX
          ? []
          : ''
  })
}
