import { FieldType, type FormFieldConfig } from '@/api/lowcode'

/**
 * V2 对 V1 表单协议的兼容适配层。
 *
 * 这里仅负责纯数据/组件类型转换，不引入 FormCreate 实例，方便用单测锁定
 * “V1 无回归 + V2 可替换”的协议边界。
 */

/**
 * 自定义组件名兼容两代配置：
 * - V1 历史配置主要从 props.componentName 读取；
 * - 当前设计器将 componentName 写在字段顶层。
 */
export function resolveCustomComponentName(field: FormFieldConfig): string {
  return (field.props?.componentName as string) || field.componentName || ''
}

/**
 * 将 NPDMS FieldType 映射为 FormCreate Element Plus 内置组件名。
 * radio / checkbox 必须使用 FormCreate 的聚合组件别名，而不是 Element Plus 的 *Group 名称。
 */
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

/** V1 required + rules 合并语义。 */
export function buildFormCreateValidate(field: FormFieldConfig): Array<Record<string, unknown>> {
  const list: Array<Record<string, unknown>> = []
  if (field.required) {
    list.push({
      required: true,
      message: field.placeholder || `请填写${field.label}`,
      trigger: ['blur', 'change']
    })
  }
  if (field.rules && Array.isArray(field.rules)) {
    for (const rule of field.rules) {
      list.push({ ...rule })
    }
  }
  return list
}

/**
 * 外部 modelValue → 内部 formData 保持 V1 的 merge 语义：只覆盖传入 key，
 * 不删除未传入 key。部分业务消费方会按字段增量回填数据，不能改成 replace。
 */
export function mergeExternalModelValue(
  target: Record<string, unknown>,
  source?: Record<string, unknown>
): void {
  if (!source) return
  for (const key of Object.keys(source)) {
    if (target[key] !== source[key]) {
      target[key] = source[key]
    }
  }
}

/** V1 默认值初始化语义。 */
export function initMissingFieldDefaults(
  target: Record<string, unknown>,
  fields: FormFieldConfig[]
): void {
  for (const field of fields) {
    if (field.prop in target) continue
    if (field.defaultValue !== undefined && field.defaultValue !== null) {
      target[field.prop] = field.defaultValue
    } else if (field.type === FieldType.CHECKBOX) {
      target[field.prop] = []
    } else {
      target[field.prop] = ''
    }
  }
}

/** V1 resetFields 后的默认值语义。 */
export function resetFieldDefaults(
  target: Record<string, unknown>,
  fields: FormFieldConfig[]
): void {
  for (const field of fields) {
    if (field.defaultValue !== undefined) {
      target[field.prop] = field.defaultValue
    } else {
      target[field.prop] = field.type === FieldType.CHECKBOX ? [] : ''
    }
  }
}
