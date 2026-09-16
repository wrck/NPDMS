<script setup lang="ts">
/**
 * 低代码表单渲染引擎 V2（FormCreate 实现）。
 *
 * V2 复用 V1 的 FormConfig、props、emits 与公开方法契约，只替换渲染底层。
 * V1 保持原样；V2 负责将 FormConfig 翻译为 @form-create/element-ui Rule[]。
 */
import { computed, reactive, ref, watch, type Component } from 'vue'
import {
  FieldType,
  LayoutType,
  type FormConfig,
  type FormFieldConfig,
  type ResponsiveSpan
} from '@/api/lowcode'
import formCreate, { type Api as FormCreateApi, type Rule } from '@form-create/element-ui'

const props = withDefaults(
  defineProps<{
    config: FormConfig
    modelValue?: Record<string, unknown>
    disabled?: boolean
    componentRegistry?: Record<string, Component>
    eventHandlers?: Record<string, (...args: unknown[]) => void>
  }>(),
  {
    disabled: false,
    componentRegistry: () => ({}),
    eventHandlers: () => ({})
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'submit', value: Record<string, unknown>): void
  (e: 'validate-fail', errors: unknown): void
  (e: 'field-change', field: FormFieldConfig, value: unknown): void
}>()

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const formData = reactive<Record<string, any>>({ ...(props.modelValue || {}) })
const formApi = ref<FormCreateApi>()
/** V1 兼容别名。底层对象在 V2 中仍是 FormCreate Api。 */
const formRef = formApi

const values = computed<Record<string, unknown>>({
  get: () => formData,
  set: (val) => {
    if (!val) return
    for (const key of Object.keys(val)) {
      if (formData[key] !== val[key]) {
        formData[key] = val[key]
      }
    }
  }
})

function initDefaults() {
  for (const field of props.config.fields || []) {
    if (!(field.prop in formData)) {
      if (field.defaultValue !== undefined && field.defaultValue !== null) {
        formData[field.prop] = field.defaultValue
      } else if (field.type === FieldType.CHECKBOX) {
        formData[field.prop] = []
      } else {
        formData[field.prop] = ''
      }
    }
  }
}

watch(
  () => props.config,
  () => initDefaults(),
  { immediate: true, deep: false }
)

watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    for (const key of Object.keys(val)) {
      if (formData[key] !== val[key]) {
        formData[key] = val[key]
      }
    }
  },
  { deep: true }
)

watch(
  formData,
  (val) => {
    emit('update:modelValue', { ...val })
  },
  { deep: true }
)

const visibleFields = computed(() => (props.config.fields || []).filter((f) => !f.hidden))

/**
 * 记录已注册组件实例。Registry 被替换或同名组件更新时允许重新注册，
 * 与 V1 每次从 componentRegistry 解析组件的动态语义保持一致。
 */
const registeredCustomComponents = new Map<string, Component>()

function customComponentName(field: FormFieldConfig): string {
  return (field.props?.componentName as string) || field.componentName || ''
}

function registerCustomComponents() {
  for (const field of props.config.fields || []) {
    if (field.type !== FieldType.CUSTOM) continue
    const name = customComponentName(field)
    if (!name) continue
    const comp = props.componentRegistry[name]
    if (!comp) {
      console.warn(`[LowCodeFormRendererV2] 未注册的自定义组件: ${name}`)
      continue
    }
    if (registeredCustomComponents.get(name) === comp) continue
    formCreate.component(name, comp)
    registeredCustomComponents.set(name, comp)
  }
}

watch(
  () => props.config,
  () => registerCustomComponents(),
  { immediate: true, deep: false }
)
watch(
  () => props.componentRegistry,
  () => registerCustomComponents(),
  { immediate: true, deep: true }
)

function handleFieldChange(field: FormFieldConfig, value: unknown) {
  emit('field-change', field, value)
  const handlerName = field.events?.change
  if (handlerName && props.eventHandlers[handlerName]) {
    props.eventHandlers[handlerName](value, field, formData)
  }
}

function buildValidate(field: FormFieldConfig): Array<Record<string, unknown>> {
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

function dateType(field: FormFieldConfig): 'date' | 'datetime' | 'daterange' {
  if (field.type === FieldType.DATETIME) return 'datetime'
  if (field.type === FieldType.DATERANGE) return 'daterange'
  return 'date'
}

function toRule(field: FormFieldConfig): Rule {
  if (field.type === FieldType.DIVIDER) {
    return {
      type: 'el-divider',
      props: {
        contentPosition:
          (field.props?.contentPosition as 'left' | 'center' | 'right') || 'center',
        borderStyle: (field.props?.borderStyle as string) || 'solid'
      },
      children: [field.label]
    }
  }
  if (field.type === FieldType.TITLE) {
    return {
      type: 'h3',
      class: ['form-title'],
      style: { fontSize: '16px' },
      children: [field.label]
    }
  }

  const common = {
    field: field.prop,
    title: field.label,
    props: {
      placeholder: field.placeholder,
      disabled: field.disabled,
      readonly: field.readonly,
      clearable: field.clearable,
      ...(field.props || {})
    } as Record<string, unknown>,
    validate: buildValidate(field),
    emit: ['change'],
    on: {
      change: (value: unknown) => handleFieldChange(field, value)
    }
  } as Rule

  switch (field.type) {
    case FieldType.INPUT:
      return { ...common, type: 'input' }
    case FieldType.TEXTAREA:
      return {
        ...common,
        type: 'input',
        props: {
          ...common.props,
          type: 'textarea',
          rows: (field.props?.rows as number) ?? 3
        }
      }
    case FieldType.PASSWORD:
      return {
        ...common,
        type: 'input',
        props: { ...common.props, type: 'password' }
      }
    case FieldType.NUMBER:
      return { ...common, type: 'inputNumber' }
    case FieldType.SELECT: {
      const options = (field.props?.options as Array<{ label: string; value: unknown }>) || []
      return {
        ...common,
        type: 'select',
        options: options.map((option) => ({ label: option.label, value: option.value }))
      }
    }
    case FieldType.RADIO: {
      const options = (field.props?.options as Array<{ label: string; value: unknown }>) || []
      return {
        ...common,
        type: 'radioGroup',
        options: options.map((option) => ({ label: option.label, value: option.value }))
      }
    }
    case FieldType.CHECKBOX: {
      const options = (field.props?.options as Array<{ label: string; value: unknown }>) || []
      return {
        ...common,
        type: 'checkboxGroup',
        options: options.map((option) => ({ label: option.label, value: option.value }))
      }
    }
    case FieldType.DATE:
    case FieldType.DATETIME:
    case FieldType.DATERANGE:
      return {
        ...common,
        type: 'datePicker',
        props: {
          ...common.props,
          type: dateType(field),
          format: (field.props?.format as string) || undefined,
          valueFormat: (field.props?.valueFormat as string) || undefined
        }
      }
    case FieldType.SWITCH:
      return { ...common, type: 'switch' }
    case FieldType.RATE:
      return { ...common, type: 'rate' }
    case FieldType.SLIDER:
      return { ...common, type: 'slider' }
    case FieldType.CASCADER:
      return { ...common, type: 'cascader' }
    case FieldType.UPLOAD:
      return {
        ...common,
        type: 'upload',
        props: {
          ...common.props,
          action: (field.props?.action as string) || '/api/file/upload',
          limit: (field.props?.limit as number) || 5,
          accept: (field.props?.accept as string) || '',
          multiple: (field.props?.multiple as boolean) ?? false,
          listType: (field.props?.listType as string) || 'text'
        }
      }
    case FieldType.CUSTOM: {
      const name = customComponentName(field)
      const comp = name ? props.componentRegistry[name] : undefined
      if (!name || !comp) {
        return { ...common, type: 'input' }
      }
      return { ...common, type: name, field: field.prop }
    }
    default:
      return { ...common, type: 'input' }
  }
}

function colProps(span: number | ResponsiveSpan | undefined): Record<string, number> {
  if (span === undefined || typeof span === 'number') {
    return { span: span ?? 24 }
  }
  const result: Record<string, number> = {}
  if (span.xs !== undefined) result.xs = span.xs
  if (span.sm !== undefined) result.sm = span.sm
  if (span.md !== undefined) result.md = span.md
  if (span.lg !== undefined) result.lg = span.lg
  if (span.xl !== undefined) result.xl = span.xl
  return result
}

const layout = computed(() => props.config.layout || { type: LayoutType.GRID, gutter: 16 })
const activeTab = ref<string>('')
const activeCollapse = ref<string[]>([])

watch(
  layout,
  (val) => {
    if (val.type === LayoutType.TABS && val.tabs && val.tabs.length > 0 && !activeTab.value) {
      activeTab.value = val.tabs[0].name || val.tabs[0].title
    }
    if (val.type === LayoutType.COLLAPSE && val.collapse && val.collapse.length > 0) {
      activeCollapse.value = val.collapse.map((item, index) => item.name || String(index))
    }
  },
  { immediate: true }
)

function resolveFields(ids: string[]): FormFieldConfig[] {
  const map = new Map<string, FormFieldConfig>()
  for (const field of props.config.fields || []) {
    map.set(field.id, field)
  }
  return ids
    .map((id) => map.get(id))
    .filter((field): field is FormFieldConfig => !!field && !field.hidden)
}

/**
 * V1 的 grid/tabs/collapse 都先用 el-col 包裹字段；布局字段也遵循 span。
 * V2 保持同一结构，避免 divider/title 在切换渲染器后改变宽度语义。
 */
function buildFieldRules(fields: FormFieldConfig[]): Rule[] {
  const children: Rule[] = fields.map((field) => ({
    type: 'col',
    props: colProps(field.span) as unknown as Record<string, unknown>,
    children: [toRule(field)]
  }))
  return [
    {
      type: 'row',
      props: { gutter: layout.value.gutter ?? 16 } as unknown as Record<string, unknown>,
      children
    }
  ]
}

const gridRule = computed<Rule[]>(() => {
  if (!layout.value.type || layout.value.type === LayoutType.GRID) {
    return buildFieldRules(visibleFields.value)
  }
  return []
})

const option = computed<Record<string, unknown>>(() => ({
  form: {
    labelWidth: props.config.labelWidth ?? 100,
    labelPosition: props.config.labelPosition ?? 'right',
    size: props.config.size ?? 'default'
  },
  submitBtn: false,
  resetBtn: false
}))

const tabsRule = computed<Rule[]>(() => {
  if (layout.value.type !== LayoutType.TABS) return []
  return [
    {
      type: 'el-tabs',
      props: { modelValue: activeTab.value } as unknown as Record<string, unknown>,
      on: {
        'update:modelValue': (value: string) => {
          activeTab.value = value
        }
      },
      children: (layout.value.tabs || []).map((tab) => ({
        type: 'el-tab-pane',
        props: {
          label: tab.title,
          name: tab.name || tab.title
        } as unknown as Record<string, unknown>,
        children: buildFieldRules(resolveFields(tab.fields))
      }))
    }
  ]
})

const collapseRule = computed<Rule[]>(() => {
  if (layout.value.type !== LayoutType.COLLAPSE) return []
  return [
    {
      type: 'el-collapse',
      props: { modelValue: activeCollapse.value } as unknown as Record<string, unknown>,
      on: {
        'update:modelValue': (value: string[]) => {
          activeCollapse.value = value
        }
      },
      children: (layout.value.collapse || []).map((group, index) => ({
        type: 'el-collapse-item',
        props: {
          title: group.title,
          name: group.name || String(index)
        } as unknown as Record<string, unknown>,
        children: buildFieldRules(resolveFields(group.fields))
      }))
    }
  ]
})

const finalRule = computed<Rule[]>(() => {
  if (layout.value.type === LayoutType.TABS) return tabsRule.value
  if (layout.value.type === LayoutType.COLLAPSE) return collapseRule.value
  return gridRule.value
})

async function validate(): Promise<boolean> {
  if (!formApi.value) return false
  try {
    await formApi.value.validate()
    return true
  } catch (errors) {
    emit('validate-fail', errors)
    return false
  }
}

async function submit(): Promise<void> {
  const ok = await validate()
  if (ok) {
    emit('submit', { ...formData })
  }
}

function resetFields(): void {
  formApi.value?.resetFields()
  for (const field of props.config.fields || []) {
    if (field.defaultValue !== undefined) {
      formData[field.prop] = field.defaultValue
    } else {
      formData[field.prop] = field.type === FieldType.CHECKBOX ? [] : ''
    }
  }
}

function clearValidate(): void {
  formApi.value?.clearValidateState()
}

defineExpose({
  validate,
  submit,
  resetFields,
  clearValidate,
  getFormData: () => ({ ...formData }),
  formRef,
  formApi
})
</script>

<template>
  <form-create
    v-model="values"
    v-model:api="formApi"
    :option="option"
    :rule="finalRule"
    :disabled="disabled"
    class="low-code-form-renderer-v2"
  />
</template>

<style scoped>
.low-code-form-renderer-v2 {
  width: 100%;
}
</style>
