import type { Component } from 'vue'
import type { Rule } from '@form-create/element-ui'
import { FieldType, type FormFieldConfig } from '@/api/lowcode'
import {
  buildFormCreateValidate,
  resolveCustomComponentName,
  resolveFormCreateComponentType
} from './runtimeCompat'

export interface FieldRuleContext {
  resolveComponent: (name: string) => Component | undefined
  uploadComponent: Component
  onInput: (field: FormFieldConfig, value: unknown) => void
  onChange: (field: FormFieldConfig, value: unknown) => void
}

/** FormConfig 是持久化协议；Rule 仅为本次渲染实例的数据，不写回配置。 */
export function buildFieldRule(field: FormFieldConfig, context: FieldRuleContext): Rule {
  const type = resolveFormCreateComponentType(field)
  if (field.type === FieldType.DIVIDER) {
    return {
      type,
      native: true,
      props: {
        contentPosition: field.props?.contentPosition || 'center',
        borderStyle: field.props?.borderStyle || 'solid'
      },
      children: [field.label]
    }
  }
  if (field.type === FieldType.TITLE) {
    return {
      type,
      native: true,
      class: ['form-title'],
      style: { fontSize: '16px' },
      children: [field.label]
    }
  }

  // V1 的上传字段不在 v-model/change 链中，不能换成 FormCreate 的 URL 数组协议。
  if (field.type === FieldType.UPLOAD) {
    return {
      type: 'npdms-legacy-upload',
      component: context.uploadComponent,
      field: field.prop,
      title: field.label,
      validate: buildFormCreateValidate(field),
      props: { field, disabled: field.disabled }
    }
  }

  const common: Rule = {
    type,
    field: field.prop,
    title: field.label,
    props: {
      placeholder: field.placeholder,
      disabled: field.disabled,
      readonly: field.readonly,
      clearable: field.clearable,
      ...(field.props || {})
    },
    validate: buildFormCreateValidate(field),
    on: {
      // 与 V1 v-model 一样，先同步数据，再处理组件随后发出的 change。
      'update:modelValue': (value: unknown) => context.onInput(field, value),
      change: (value: unknown) => context.onChange(field, value)
    }
  }

  switch (field.type) {
    case FieldType.TEXTAREA:
      return {
        ...common,
        props: { ...common.props, type: 'textarea', rows: 3, ...(field.props || {}) }
      }
    case FieldType.PASSWORD:
      return {
        ...common,
        props: { ...common.props, type: 'password', ...(field.props || {}) }
      }
    case FieldType.SELECT:
    case FieldType.RADIO:
    case FieldType.CHECKBOX: {
      const options = (field.props?.options as Array<{ label: string; value: unknown; disabled?: boolean }>) || []
      return {
        ...common,
        options: options.map((option) => ({ ...option }))
      }
    }
    case FieldType.DATE:
    case FieldType.DATETIME:
    case FieldType.DATERANGE:
      return {
        ...common,
        props: {
          ...common.props,
          type: field.type,
          format: field.props?.format || undefined,
          valueFormat: field.props?.valueFormat || undefined,
          ...(field.props || {})
        }
      }
    case FieldType.CUSTOM: {
      const name = resolveCustomComponentName(field)
      const component = name ? context.resolveComponent(name) : undefined
      if (!component) {
        console.warn(`[LowCodeFormRendererV2] 未注册的自定义组件: ${name}`)
        return { ...common, type: 'input' }
      }
      return {
        ...common,
        type: name,
        // Rule.component 是实例局部定义，不能调用 formCreate.component 污染其他表单。
        component,
        props: { field, ...common.props }
      }
    }
    default:
      return common
  }
}
