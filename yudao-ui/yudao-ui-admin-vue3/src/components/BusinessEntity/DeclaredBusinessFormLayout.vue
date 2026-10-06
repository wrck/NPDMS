<template>
  <form-create v-model="values" v-model:api="api" :rule="render.rule" :option="render.option" :disabled="disabled" />
</template>
<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { BusinessEntityFormData, FieldVO } from '@/api/pms/platform/businessmodel'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { declaredFormPresentation } from './declaredFormPresentation'
const props = defineProps<{ presentation: BusinessEntityFormData; fields: FieldVO[]; initialValues?: JsonObject; disabled?: boolean }>()
const api = ref(), values = ref<JsonObject>({}), render = reactive<{ rule: JsonObject[]; option: JsonObject }>({ rule: [], option: {} })
let input: (values: JsonObject) => JsonObject = () => ({})
watch(() => [props.presentation, props.fields, props.initialValues], () => {
  const layout = props.presentation.layout!
  const presentation = declaredFormPresentation(props.presentation, props.fields, props.initialValues || {})
  const decoded = decodeDynamicForm(JSON.parse(layout.formConfJson), presentation.rule)
  values.value = presentation.values
  render.rule = decoded.rule
  render.option = { ...decoded.option, submitBtn: false, resetBtn: false }
  input = presentation.buildInput
}, { immediate: true })
const buildInput = async () => {
  if (api.value && !await api.value.validate()) throw new Error('请检查表单内容')
  return input(values.value)
}
defineExpose({ buildInput })
</script>
