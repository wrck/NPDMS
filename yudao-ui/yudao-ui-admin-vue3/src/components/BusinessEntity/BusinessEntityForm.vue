<template>
  <el-form ref="formRef" :model="form" label-width="140px" :disabled="disabled">
    <el-form-item
      v-for="field in writableFields"
      :key="field.code"
      :label="field.name"
      :prop="field.code"
      :rules="rules[field.code]"
    >
      <el-input-number
        v-if="field.type === 'NUMBER'"
        v-model="form[field.code] as number | undefined"
        :controls="false"
        class="w-full!"
      />
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
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FieldVO } from '@/api/pms/platform/businessmodel'

defineOptions({ name: 'BusinessEntityForm' })
const props = defineProps<{
  writableFields: FieldVO[]
  /** 打开实体的字段值；为空表示新建。 */
  initialValues?: Record<string, unknown>
  disabled?: boolean
}>()

const formRef = ref()
const form = reactive<Record<string, unknown>>({})
const rules = reactive<Record<string, Array<{ required: boolean; message: string; trigger: string }>>>({})

const syncForm = () => {
  Object.keys(form).forEach((key) => delete form[key])
  Object.keys(rules).forEach((key) => delete rules[key])
  for (const field of props.writableFields) {
    const initial = props.initialValues?.[field.code]
    if (field.type === 'TEXT_LIST') form[field.code] = Array.isArray(initial) ? [...initial] : []
    else if (field.type === 'OBJECT_LIST')
      form[field.code] = initial == null ? '' : JSON.stringify(initial, null, 2)
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
  const input: Record<string, unknown> = {}
  for (const field of props.writableFields) {
    const raw = form[field.code]
    if (field.type === 'OBJECT_LIST') {
      if (raw == null || raw === '') continue
      input[field.code] = JSON.parse(String(raw))
    } else if (raw !== null && raw !== undefined && raw !== '') {
      input[field.code] = raw
    }
  }
  return input
}
// 只清校验状态；resetFields 会把模型重置回挂载时的空值，冲掉重开回填的服务端内容。
const resetValidation = () => formRef.value?.clearValidate()
defineExpose({ buildInput, resetValidation })
</script>
