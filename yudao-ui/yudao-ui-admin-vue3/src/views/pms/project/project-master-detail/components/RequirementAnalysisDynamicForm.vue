<template>
  <section class="requirement-form-shell" aria-label="需求分析动态表单">
    <header class="form-header">
      <div>
        <strong>需求分析正文 · V{{ detail.businessVersion }}</strong>
        <p>
          正文版本 {{ detail.contentVersion }} · 表单修订 {{ detail.dynamicFormRevisionNo }} ·
          文件上下文版本 {{ detail.dynamicFormInstanceVersion }}
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
          保存需求分析
        </el-button>
      </div>
    </header>

    <el-alert
      title="模板已由项目工作绑定自动确定"
      description="表单只负责展示与编辑，数据通过需求分析实体接口加载和保存；当前版本始终使用冻结修订。"
      type="info"
      :closable="false"
      show-icon
    />
    <div v-form-create-keyboard-rows="!editable" class="form-host">
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
import { vFormCreateKeyboardRows } from './formCreateKeyboardRows'
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
  allowedActions?: string[]
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
const editorReadonlyDefaults = new Map<string, boolean>()
const render = reactive<{ option: JsonObject; rule: JsonObject[] }>({ option: {}, rule: [] })
const formApi = ref<FormCreateApi>()
const saving = ref(false)
const editable = computed(
  () => props.detail.status === 'DRAFT' && props.detail.allowedActions.includes('PATCH_FORM') &&
    (props.allowedActions === undefined || props.allowedActions.includes('PATCH_FORM'))
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

const updateEditorReadonly = (rules: JsonObject[]) => {
  for (const rule of rules) {
    if (rule.type === 'Editor') {
      const field = String(rule.field)
      const editorProps = (rule.props || {}) as JsonObject
      const editorConfig = (editorProps.editorConfig || {}) as JsonObject
      if (!editorReadonlyDefaults.has(field)) {
        editorReadonlyDefaults.set(field, editorProps.readonly === true || editorConfig.readOnly === true)
      }
      const readonly = !editable.value || editorReadonlyDefaults.get(field) === true
      rule.props = { ...editorProps, readonly, editorConfig: { ...editorConfig, readOnly: readonly } }
    }
    if (Array.isArray(rule.children)) updateEditorReadonly(rule.children as JsonObject[])
  }
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
  editorReadonlyDefaults.clear()
  updateEditorReadonly(runtime.rules)
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
  const entityId = props.detail.preparationId
  const cacheKey = pendingKey.value
  if (editable.value && !props.reload) {
    message.warning('实体回读接口未配置，不能将本地表单标记为已保存。')
    return false
  }
  if (!editable.value || saving.value || !(await validate())) return false
  if (!editable.value || saving.value || entityId !== props.detail.preparationId || !props.reload) return false
  const patch = buildRequirementFormPatch(values.value, baseline.value, ordinaryFields.value)
  if (!Object.keys(patch.values).length) {
    message.info('普通字段没有变化')
    return true
  }
  const intent = stableRequirementFormIntent(entityId, patch.values)
  sessionStorage.setItem(cacheKey, JSON.stringify(patch.values))
  saving.value = true
  try {
    await RequirementAnalysisApi.patchForm(
      entityId,
      props.detail.dynamicFormInstanceVersion,
      props.detail.version,
      patch
    )
    if (entityId !== props.detail.preparationId) return false
    const authoritative = await props.reload()
    if (entityId !== props.detail.preparationId || authoritative.preparationId !== entityId) return false
    intent.clear()
    sessionStorage.removeItem(cacheKey)
    apply(authoritative)
    message.success('需求分析实体已保存并重新加载')
    emit('saved', authoritative)
    return true
  } catch {
    if (entityId !== props.detail.preparationId) return false
    try {
      const authoritative = await props.reload()
      if (entityId !== props.detail.preparationId || authoritative.preparationId !== entityId) return false
      const reconciled = reconcileRequirementFormPatch(authoritative.values || {}, patch.values)
      if (reconciled.committed) {
        intent.clear()
        sessionStorage.removeItem(cacheKey)
        apply(authoritative)
        message.success('已回读确认需求分析实体保存成功')
        emit('saved', authoritative)
        return true
      }
      apply(authoritative, reconciled.values)
    } catch {
      // Preserve the edit and request intent when the entity cannot yet be read back.
    }
    message.warning('保存结果未知，已保留本次填写和原操作意图；请刷新后再次保存。')
    return false
  } finally {
    saving.value = false
  }
}

const discardChanges = () => {
  if (saving.value) return false
  values.value = cloneValues(baseline.value)
  sessionStorage.removeItem(pendingKey.value)
  return true
}

watch(
  // Owner/body version changes reload the document; a new permission projection does not.
  () => [props.detail.preparationId, props.detail.contentVersion, props.detail.dynamicFormInstanceVersion, props.detail.templateRevisionId, props.detail.status].join(':'),
  () => apply(props.detail, editable.value ? readPending() : undefined),
  { immediate: true }
)
// PM-03: authorization is independent from document reload. Update controlled-file actions
// in place so neither form-create rules nor ordinary unsaved values are replaced.
watch(editable, () => {
  updateEditorReadonly(render.rule)
  const visit = (rules: JsonObject[]) => rules.forEach((rule) => {
    if (rule.type === 'PmsFileArtifact' && rule.props) {
      (rule.props as JsonObject).allowedActions = editable.value ? ['PATCH_INSTANCE'] : []
    }
    if (Array.isArray(rule.children)) visit(rule.children as JsonObject[])
  })
  visit(render.rule)
})
watch(dirty, (value) => emit('dirty-change', value), { immediate: true })

defineExpose({ save, discardChanges, isDirty: () => dirty.value, isSaving: () => saving.value })
</script>

<style scoped lang="scss">
.form-host :deep([role='button']:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
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
