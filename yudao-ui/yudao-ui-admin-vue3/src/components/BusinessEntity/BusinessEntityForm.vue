<template>
  <section>
    <DeclaredBusinessFormLayout v-if="presentation?.layout" ref="layoutRef"
      :presentation="presentation" :fields="fields || writableFields" :initial-values="initialValues" :disabled="disabled" />
  <el-form ref="formRef" :model="form" :label-width="appearance?.labelWidth || '140px'" :label-position="appearance?.labelPosition" :disabled="disabled"
    :class="{ 'business-form-grid': !!appearance?.columns }" :style="appearance?.columns ? {gridTemplateColumns:`repeat(${appearance.columns},minmax(0,1fr))`} : undefined">
    <el-form-item
      v-for="field in baseFields"
      :key="field.code"
      :label="appearance?.fields?.[field.code]?.label || field.name"
      :prop="field.code"
      :rules="rules[field.code]"
      :style="appearance?.columns ? {gridColumn:`span ${appearance.fields?.[field.code]?.span || 1}`} : undefined"
    >
      <Editor v-if="appearance?.fields?.[field.code]?.control === 'editor'" v-model="form[field.code] as string" height="200px" :readonly="disabled" />
      <el-input v-else-if="appearance?.fields?.[field.code]?.control === 'textarea'" v-model="form[field.code] as string" type="textarea" />
      <el-checkbox v-else-if="appearance?.fields?.[field.code]?.control === 'checkbox'" v-model="form[field.code] as boolean">需要转包</el-checkbox>
      <el-select v-else-if="appearance?.fields?.[field.code]?.control === 'select'" v-model="form[field.code] as string | number" filterable clearable class="w-full!">
        <el-option v-for="option in appearance.fields[field.code].options || []" :key="String(option.value)" :label="option.label" :value="option.value" />
      </el-select>
      <el-input v-else-if="field.type === 'NUMBER' && losslessNumbers" v-model="form[field.code] as string" inputmode="decimal" />
      <el-input-number
        v-else-if="field.type === 'NUMBER'"
        v-model="form[field.code] as number | undefined"
        :controls="false"
        class="w-full!"
      />
      <el-select v-else-if="field.type === 'BOOLEAN' && losslessNumbers" v-model="form[field.code] as boolean" clearable>
        <el-option label="是" :value="true" /><el-option label="否" :value="false" />
      </el-select>
      <el-switch v-else-if="field.type === 'BOOLEAN'" v-model="form[field.code] as boolean" />
      <el-date-picker
        v-else-if="field.type === 'DATE'"
        v-model="form[field.code] as string"
        type="date"
        value-format="YYYY-MM-DD"
      />
      <el-date-picker
        v-else-if="field.type === 'DATETIME'"
        v-model="form[field.code] as string"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
      />
      <el-select
        v-else-if="field.type === 'TEXT_LIST'"
        v-model="form[field.code] as string[]"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="输入后回车添加"
      />
      <el-input
        v-else-if="field.type === 'OBJECT_LIST'"
        v-model="form[field.code] as string"
        type="textarea"
        :rows="3"
        placeholder="JSON 数组"
      />
      <el-input v-else v-model="form[field.code] as string" />
    </el-form-item>
    <slot name="extra"></slot>
  </el-form>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, defineAsyncComponent } from 'vue'
import type { BusinessFormAppearance } from './businessFormAppearance'
const Editor = defineAsyncComponent(() => import('@/components/Editor/src/Editor.vue').then(module=>module.default))
import DeclaredBusinessFormLayout from './DeclaredBusinessFormLayout.vue'
import type { BusinessEntityFormData, FieldVO } from '@/api/pms/platform/businessmodel'

defineOptions({ name: 'BusinessEntityForm' })
const props = defineProps<{
  appearance?: BusinessFormAppearance
  writableFields: FieldVO[]
  fields?: FieldVO[]
  presentation?: BusinessEntityFormData
  /** 打开实体的字段值；为空表示新建。 */
  initialValues?: Record<string, unknown>
  disabled?: boolean
  /** Preserve bigint IDs/decimal values as text on the direct inherited path. */
  losslessNumbers?: boolean
}>()

const formRef = ref()
const layoutRef = ref<{ buildInput: () => Promise<Record<string, unknown>> }>()
const baseFields = computed(() => {
  const mapped = new Set(Object.values(props.presentation?.layout?.binding.fieldBindings || {}))
  return props.writableFields.filter(field => !mapped.has(field.code) && !props.appearance?.fields?.[field.code]?.hidden)
    .slice().sort((a,b)=>(props.appearance?.fields?.[a.code]?.order ?? 999)-(props.appearance?.fields?.[b.code]?.order ?? 999))
})
const form = reactive<Record<string, unknown>>({})
const rules = reactive<Record<string, Array<{ required: boolean; message: string; trigger: string }>>>({})

const syncForm = () => {
  Object.keys(form).forEach((key) => delete form[key])
  Object.keys(rules).forEach((key) => delete rules[key])
  for (const field of baseFields.value) {
    const initial = props.initialValues?.[field.code]
    if (field.type === 'TEXT_LIST') form[field.code] = Array.isArray(initial) ? [...initial] : []
    else if (field.type === 'OBJECT_LIST')
      form[field.code] = initial == null ? '' : JSON.stringify(initial, null, 2)
    else if(props.appearance?.fields?.[field.code]?.control==='select')form[field.code]=props.appearance.fields[field.code].options?.find(option=>String(option.value)===String(initial))?.value ?? initial ?? null
    else form[field.code] = initial ?? null
    if (field.required) {
      rules[field.code] = [{ required: true, message: `${field.name}不能为空`, trigger: 'blur' }]
    }
  }
}
watch(() => [props.writableFields, props.initialValues], syncForm, { immediate: true, deep: false })

/** 校验并把表单值收敛为操作输入；OBJECT_LIST 解析为真实 JSON 数组。 */
const buildInput = async (): Promise<Record<string, unknown>> => {
  await formRef.value?.validate()
  const input: Record<string, unknown> = await layoutRef.value?.buildInput() || {}
  for (const field of baseFields.value) {
    const raw = form[field.code]
    const value = field.type === 'OBJECT_LIST'
      ? (raw == null || raw === '' ? null : JSON.parse(String(raw)))
      : (raw === '' || raw === undefined ? null : raw)
    if (props.initialValues) {
      // Omit unchanged/unreadable values, but preserve an explicit clearing of a loaded value.
      const initial = props.initialValues[field.code] ?? (field.type === 'TEXT_LIST' ? [] : null)
      if (JSON.stringify(value) === JSON.stringify(initial) || props.appearance?.fields?.[field.code]?.control==='select' && value!=null && initial!=null && String(value)===String(initial)) continue
      input[field.code] = value
    } else if (value !== null) {
      input[field.code] = value
    }
  }
  return input
}
// 只清校验状态；resetFields 会把模型重置回挂载时的空值，冲掉重开回填的服务端内容。
const resetValidation = () => formRef.value?.clearValidate()
defineExpose({ buildInput, resetValidation })
</script>

<style scoped>
.business-form-grid { display:grid;gap:0 16px; }
.business-form-grid :deep(.el-form-item__content) { min-width:0;width:100%; }
.business-form-grid :deep(.el-date-editor) { width:100%; }
@media(max-width:640px) { .business-form-grid { grid-template-columns:minmax(0,1fr) !important; } .business-form-grid > * { grid-column:span 1 !important; } }
</style>
