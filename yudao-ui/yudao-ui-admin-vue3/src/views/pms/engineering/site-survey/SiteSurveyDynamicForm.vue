<template>
  <section aria-label="现场工勘动态表单" class="survey-dynamic-form">
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button @click="load">重新加载表单</el-button>
    </el-alert>
    <el-skeleton v-else-if="loading" :rows="6" animated />
    <template v-else>
      <el-alert
        title="数据保存到原现场工勘实体"
        type="info"
        :closable="false"
        description="模板仅控制字段与布局；原字段保存到原业务列，扩展字段保存到同一工勘记录。"
      />
      <form-create
        v-model="values"
        v-model:api="formApi"
        :rule="rules"
        :option="option"
        :disabled="readonly"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { Api } from '@form-create/element-ui'
import formCreate from '@form-create/element-ui'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import { getFormSchema } from '@/api/pms/engineering/site-survey'
import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { extractSurveyValues, mergeSurveyValues } from './siteSurveyForm'
import SurveyOutsourceShortcut from './SurveyOutsourceShortcut.vue'
import SurveyProcurementLinks from './SurveyProcurementLinks.vue'
import SurveyMaterialSelector from './SurveyMaterialSelector.vue'
import SurveyProjectEndDate from './SurveyProjectEndDate.vue'

// Domain components follow FormCreate's modelValue/update:modelValue contract.
// https://www.form-create.com/v3/guide/custom-form-component
const businessComponents = {
  SurveyOutsourceShortcut,
  SurveyProcurementLinks,
  SurveyMaterialSelector,
  SurveyProjectEndDate
}
Object.entries(businessComponents).forEach(([name, component]) =>
  formCreate.component(name, component)
)

const props = defineProps<{ modelValue: SiteSurveyVO; readonly: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: SiteSurveyVO]
  action: [kind: string, sn?: string]
  'integrated-outsource': [value: boolean]
}>()
const values = ref<Record<string, any>>({})
const rules = ref<any[]>([])
const option = ref<Record<string, any>>({})
const formApi = ref<Api>()
const loading = ref(false)
const error = ref('')
let loadSequence = 0
const load = async () => {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const schema = await getFormSchema(
      props.modelValue.formRevisionId!,
      props.modelValue.formRevisionVersion!
    )
    if (sequence !== loadSequence) return
    const decoded = decodeDynamicForm(schema.formConfJson, schema.formRulesJson)
    let integratedOutsource = false
    const visit = (items: any[]) =>
      items.forEach((rule) => {
        if (!rule || typeof rule !== 'object') return
        if (rule.type in businessComponents) {
          if (rule.type === 'SurveyOutsourceShortcut') integratedOutsource = true
          rule.props = {
            ...rule.props,
            getSurvey: () => props.modelValue,
            readonly: props.readonly,
            updateProjectVersion: (version: number, needsInitialSync: boolean) => {
              if (!props.readonly)
                emit('update:modelValue', {
                  ...props.modelValue,
                  projectEndDateVersion: version,
                  projectEndDateChanged: props.modelValue.projectEndDateChanged || needsInitialSync
                })
            },
            markDateChanged: () => {
              if (!props.readonly)
                emit('update:modelValue', { ...props.modelValue, projectEndDateChanged: true })
            },
            launch: (kind: string, sn?: string) => emit('action', kind, sn),
            updateRequired: (value: boolean) => {
              if (!props.readonly)
                emit('update:modelValue', { ...props.modelValue, outsourceRequired: value })
            }
          }
        }
        if (rule.type === 'Editor') {
          const readonly =
            props.readonly ||
            rule.props?.readonly === true ||
            rule.props?.editorConfig?.readOnly === true
          rule.props = {
            ...rule.props,
            readonly,
            editorConfig: { ...rule.props?.editorConfig, readOnly: readonly }
          }
        }
        if (Array.isArray(rule.children)) visit(rule.children)
      })
    visit(decoded.rule)
    emit('integrated-outsource', integratedOutsource)
    rules.value = decoded.rule
    option.value = { ...decoded.option, submitBtn: false, resetBtn: false }
    values.value = extractSurveyValues(props.modelValue)
    await nextTick()
  } catch {
    if (sequence === loadSequence) error.value = '工勘表单加载失败，原填写未丢失，请重试。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
// FormCreate's model contains business values only, never entity identity or status.
// https://form-create.com/v3/guide/global-props
watch(
  values,
  (value) => {
    if (!loading.value && !props.readonly)
      emit('update:modelValue', mergeSurveyValues(props.modelValue, value))
  },
  { deep: true }
)
watch(
  () => [
    props.modelValue.id,
    props.modelValue.formRevisionId,
    props.modelValue.formRevisionVersion,
    props.readonly
  ],
  load,
  { immediate: true }
)
defineExpose({
  validate: async () => {
    if (loading.value || error.value || !formApi.value) throw new Error('工勘表单尚未就绪')
    await formApi.value.validate()
    emit('update:modelValue', mergeSurveyValues(props.modelValue, formApi.value.formData()))
  }
})
</script>

<style scoped>
.survey-dynamic-form {
  display: grid;
  gap: 16px;
  min-width: 0;
  width: 100%;
}
</style>
