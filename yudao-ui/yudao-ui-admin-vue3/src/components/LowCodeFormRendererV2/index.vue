<script setup lang="ts">
/** FormCreate V2：保留 FormConfig、props、emits 与公开方法；V1 独立不变。 */
import { computed, markRaw, reactive, ref, shallowRef, watch, type Component } from 'vue'
import { LayoutType, type FormConfig, type FormFieldConfig, type ResponsiveSpan } from '@/api/lowcode'
import formCreate, { type Api as FormCreateApi, type Options, type Rule } from '@form-create/element-ui'
import { initMissingFieldDefaults, mergeExternalModelValue, resetFieldDefaults } from './runtimeCompat'
import { buildFieldRule } from './fieldRules'
import LegacyUploadField from './LegacyUploadField.vue'

const props = withDefaults(
  defineProps<{
    config: FormConfig
    modelValue?: Record<string, unknown>
    disabled?: boolean
    componentRegistry?: Record<string, Component>
    eventHandlers?: Record<string, (...args: unknown[]) => void>
  }>(),
  { disabled: false, componentRegistry: () => ({}), eventHandlers: () => ({}) }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'submit', value: Record<string, unknown>): void
  (e: 'validate-fail', errors: unknown): void
  (e: 'field-change', field: FormFieldConfig, value: unknown): void
}>()

const formData = reactive<Record<string, unknown>>({ ...(props.modelValue || {}) })
const formApi = shallowRef<FormCreateApi>()
/** V1 兼容别名。底层对象在 V2 中仍是 FormCreate Api。 */
const formRef = formApi
const values = computed<Record<string, unknown>>({
  get: () => formData,
  set: (value) => mergeExternalModelValue(formData, value)
})

watch(
  () => props.config,
  () => initMissingFieldDefaults(formData, props.config.fields || []),
  { immediate: true, deep: false }
)
watch(() => props.modelValue, (value) => mergeExternalModelValue(formData, value), { deep: true })
watch(formData, (value) => emit('update:modelValue', { ...value }), { deep: true })

function handleFieldChange(field: FormFieldConfig, value: unknown): void {
  emit('field-change', field, value)
  const handlerName = field.events?.change
  if (handlerName && props.eventHandlers[handlerName]) {
    props.eventHandlers[handlerName](value, field, formData)
  }
}

function toRule(field: FormFieldConfig): Rule {
  return buildFieldRule(field, {
    resolveComponent: (name) => {
      const component = props.componentRegistry[name]
      return component ? markRaw(component) : undefined
    },
    uploadComponent: markRaw(LegacyUploadField),
    onInput: (changedField, value) => { formData[changedField.prop] = value },
    onChange: handleFieldChange
  })
}

function colProps(span: number | ResponsiveSpan | undefined): ResponsiveSpan & { span?: number } {
  if (span === undefined || typeof span === 'number') return { span: span ?? 24 }
  const result: ResponsiveSpan = {}
  if (span.xs !== undefined) result.xs = span.xs
  if (span.sm !== undefined) result.sm = span.sm
  if (span.md !== undefined) result.md = span.md
  if (span.lg !== undefined) result.lg = span.lg
  if (span.xl !== undefined) result.xl = span.xl
  return result
}

const layout = computed(() => props.config.layout || { type: LayoutType.GRID, gutter: 16 })
const activeTab = ref('')
const activeCollapse = ref<string[]>([])

watch(layout, (value) => {
  if (value.type === LayoutType.TABS && value.tabs?.length && !activeTab.value) {
    activeTab.value = value.tabs[0].name || value.tabs[0].title
  }
  if (value.type === LayoutType.COLLAPSE && value.collapse?.length) {
    activeCollapse.value = value.collapse.map((item, index) => item.name || String(index))
  }
}, { immediate: true })

function resolveFields(ids: string[]): FormFieldConfig[] {
  const fields = new Map((props.config.fields || []).map((field) => [field.id, field]))
  return ids.map((id) => fields.get(id)).filter((field): field is FormFieldConfig => !!field && !field.hidden)
}

function buildFieldRules(fields: FormFieldConfig[]): Rule[] {
  return [{
    type: 'row',
    native: true,
    props: { gutter: layout.value.gutter ?? 16 },
    children: fields.map((field) => ({
      type: 'col',
      native: true,
      props: colProps(field.span),
      children: [toRule(field)]
    }))
  }]
}

/** FormCreate 会修改 Rule；使用 ref，不向它传入只读 computed 规则。 */
const finalRule = ref<Rule[]>([])
const option = ref<Options>({})

function buildLayoutRules(): Rule[] {
  if (layout.value.type === LayoutType.TABS) {
    const tabsProps = reactive({ modelValue: activeTab.value })
    const tabs: Rule = reactive({
      type: 'el-tabs',
      native: true,
      props: tabsProps,
      children: (layout.value.tabs || []).map((tab) => ({
        type: 'el-tab-pane',
        native: true,
        props: { label: tab.title, name: tab.name || tab.title },
        children: buildFieldRules(resolveFields(tab.fields))
      }))
    })
    tabs.on = { 'update:modelValue': (value: string) => {
      activeTab.value = value
      tabsProps.modelValue = value
    } }
    return [tabs]
  }
  if (layout.value.type === LayoutType.COLLAPSE) {
    const collapseProps = reactive({ modelValue: activeCollapse.value })
    const collapse: Rule = reactive({
      type: 'el-collapse',
      native: true,
      props: collapseProps,
      children: (layout.value.collapse || []).map((group, index) => ({
        type: 'el-collapse-item',
        native: true,
        props: { title: group.title, name: group.name || String(index) },
        children: buildFieldRules(resolveFields(group.fields))
      }))
    })
    collapse.on = { 'update:modelValue': (value: string[]) => {
      activeCollapse.value = value
      collapseProps.modelValue = value
    } }
    return [collapse]
  }
  if (!layout.value.type || layout.value.type === LayoutType.GRID) {
    return buildFieldRules((props.config.fields || []).filter((field) => !field.hidden))
  }
  return []
}

// 仅 Schema/Registry 变化重建规则；点击页签或折叠面板只改当前规则的 modelValue，
// 不重建字段、不丢失输入组件状态和校验结果。
watch([() => props.config, () => props.componentRegistry], () => {
  finalRule.value = buildLayoutRules()
  option.value = {
    form: {
      labelWidth: props.config.labelWidth ?? 100,
      labelPosition: props.config.labelPosition ?? 'right',
      size: props.config.size ?? 'default'
    },
    submitBtn: false,
    resetBtn: false
  }
}, { immediate: true, deep: true })

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
  if (await validate()) emit('submit', { ...formData })
}

function resetFields(): void {
  formApi.value?.resetFields()
  resetFieldDefaults(formData, props.config.fields || [])
}

function clearValidate(): void {
  formApi.value?.clearValidateState()
}

defineExpose({ validate, submit, resetFields, clearValidate, getFormData: () => ({ ...formData }), formRef, formApi })
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

:deep(.form-title) {
  margin: 8px 0;
  padding-left: 8px;
  border-left: 4px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
}
</style>
