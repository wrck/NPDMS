<template>
  <section class="requirement-form-shell" aria-label="需求分析动态表单">
    <header class="form-header">
      <div>
        <strong>冻结模板修订 {{ detail.dynamicFormRevisionNo }}</strong>
        <p>
          模板 #{{ detail.templateId }} · 修订 #{{ detail.templateRevisionId }} · 实例版本
          {{ detail.dynamicFormInstanceVersion }}
        </p>
      </div>
      <div class="form-actions">
        <el-button
          v-if="editable"
          data-testid="save-requirement-form"
          :loading="saving"
          type="primary"
          @click="save"
        >
          保存表单
        </el-button>
      </div>
    </header>

    <el-alert
      title="模板已由项目工作绑定自动确定"
      description="当前版本始终按冻结修订渲染；项目内不选择或切换模板。"
      type="info"
      :closable="false"
      show-icon
    />
    <div class="form-host">
      <form-create
        v-model="values"
        v-model:api="formApi"
        :option="render.option"
        :rule="render.rule"
        :disabled="!editable"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { Api as FormCreateApi } from '@form-create/element-ui'
import { toRaw } from 'vue'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import type { RequirementAnalysisDetailVO } from '@/api/pms/engineering/requirement-analysis'
import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { buildInstanceRuntime } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'
import { registerDynamicFormComponents } from '@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents'
import {
  buildRequirementFormPatch,
  reconcileRequirementFormPatch,
  requirementFormHasChanges,
  stableRequirementFormIntent
} from './requirementAnalysisInteraction'

defineOptions({ name: 'RequirementAnalysisDynamicForm' })
const props = defineProps<{
  detail: RequirementAnalysisDetailVO
  reload?: () => Promise<RequirementAnalysisDetailVO>
}>()
const emit = defineEmits<{
  'dirty-change': [dirty: boolean]
  saved: [detail?: RequirementAnalysisDetailVO]
}>()
const message = useMessage()
registerDynamicFormComponents()

const baseline = ref<JsonObject>({})
const values = ref<JsonObject>({})
const ordinaryFields = ref(new Set<string>())
const render = reactive<{ option: JsonObject; rule: JsonObject[] }>({ option: {}, rule: [] })
const formApi = ref<FormCreateApi>()
const saving = ref(false)
const editable = computed(
  () => props.detail.status === 'DRAFT' && props.detail.allowedActions.includes('PATCH_FORM')
)
const pendingKey = computed(
  () => `pms:fsol003:requirement-form-patch:${props.detail.preparationId}`
)
const dirty = computed(() =>
  requirementFormHasChanges(values.value, baseline.value, ordinaryFields.value)
)
const cloneValues = (source: JsonObject): JsonObject => structuredClone(toRaw(source))

const readPending = (): JsonObject | undefined => {
  const raw = sessionStorage.getItem(pendingKey.value)
  return raw ? (JSON.parse(raw) as JsonObject) : undefined
}

const apply = (detail: RequirementAnalysisDetailVO, preserve?: JsonObject) => {
  const decoded = decodeDynamicForm(detail.formConfJson, detail.formRulesJson)
  const controlledFilesByField = Object.fromEntries(
    Object.entries(detail.controlledFiles || {}).map(([purposeCode, facts]) => [
      purposeCode.startsWith('FORM_FIELD_ATTACHMENT/')
        ? purposeCode.slice('FORM_FIELD_ATTACHMENT/'.length)
        : purposeCode,
      facts
    ])
  )
  const runtime = buildInstanceRuntime(decoded.rule as JsonObject[], {
    instanceId: detail.dynamicFormInstanceId,
    templateRevisionId: detail.templateRevisionId,
    controlledFiles: controlledFilesByField,
    allowedActions: editable.value ? ['PATCH_INSTANCE'] : []
  })
  baseline.value = cloneValues(detail.values || {})
  values.value = { ...cloneValues(detail.values || {}), ...(preserve || {}) }
  render.option = { ...decoded.option, submitBtn: false, resetBtn: false }
  render.rule = runtime.rules
  ordinaryFields.value = runtime.ordinary
}

const validate = async () => {
  if (!formApi.value) return true
  try {
    await formApi.value.validate()
    return true
  } catch {
    message.warning('请先修正表单中的校验错误')
    return false
  }
}

const save = async () => {
  if (!editable.value || !(await validate())) return false
  const patch = buildRequirementFormPatch(values.value, baseline.value, ordinaryFields.value)
  if (!Object.keys(patch.values).length) {
    message.info('普通字段没有变化')
    return true
  }
  const intent = stableRequirementFormIntent(props.detail.preparationId, patch.values)
  sessionStorage.setItem(pendingKey.value, JSON.stringify(patch.values))
  saving.value = true
  try {
    await RequirementAnalysisApi.patchForm(
      props.detail.preparationId,
      props.detail.dynamicFormInstanceVersion,
      props.detail.version,
      patch
    )
    intent.clear()
    sessionStorage.removeItem(pendingKey.value)
    const authoritative = await props.reload?.()
    if (authoritative) apply(authoritative)
    else baseline.value = cloneValues(values.value)
    message.success('需求分析表单已保存')
    emit('saved', authoritative)
    return true
  } catch (error) {
    if (props.reload) {
      const authoritative = await props.reload()
      const reconciled = reconcileRequirementFormPatch(authoritative.values || {}, patch.values)
      if (reconciled.committed) {
        intent.clear()
        sessionStorage.removeItem(pendingKey.value)
        apply(authoritative)
        message.success('已确认需求分析表单保存成功')
        emit('saved', authoritative)
        return true
      }
      apply(authoritative, reconciled.values)
    }
    message.warning('保存结果未知，已保留本次填写和原操作意图；请刷新后再次保存。')
    throw error
  } finally {
    saving.value = false
  }
}

const discardChanges = () => {
  values.value = cloneValues(baseline.value)
  sessionStorage.removeItem(pendingKey.value)
}

watch(
  () => props.detail,
  (detail) => apply(detail, readPending()),
  { immediate: true }
)
watch(dirty, (value) => emit('dirty-change', value), { immediate: true })

defineExpose({ save, discardChanges, isDirty: () => dirty.value })
</script>

<style scoped lang="scss">
.requirement-form-shell,
.form-host {
  display: grid;
  min-width: 0;
  gap: 14px;
}

.form-header,
.form-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-header {
  justify-content: space-between;
}

.form-header p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}

.form-host {
  width: min(1080px, 100%);
  margin: 0 auto;
}

@media (width <= 767px) {
  .form-header {
    align-items: stretch;
    flex-direction: column;
  }

  .form-actions,
  .form-actions :deep(.el-button) {
    width: 100%;
  }

  .form-host {
    overflow-x: clip;
  }

  .form-host :deep(.el-form-item) {
    display: block;
  }

  .form-host :deep(.el-form-item__label),
  .form-host :deep(.el-form-item__content) {
    width: 100% !important;
    margin-left: 0 !important;
  }
}
</style>
