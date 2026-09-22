<template>
  <section class="requirement-form-shell" aria-label="需求分析动态表单">
    <div class="presentation-toolbar">
      <label for="requirement-presentation">展示模板</label>
      <el-select id="requirement-presentation" :model-value="selectedPresentation" :disabled="saving" style="width: 300px" @change="switchPresentation">
        <el-option label="基础业务表单" value="base" />
        <el-option v-if="detail.form" label="原始展示模板" value="original" />
        <el-option v-for="option in presentationOptions" :key="String(option.layout.binding.formRevisionId)"
          :label="`${option.name} · V${option.layout.revisionNo}`" :value="String(option.layout.binding.formRevisionId)" />
      </el-select>
      <span v-if="presentationError" role="status">{{ presentationError }}</span>
    </div>
    <div v-form-create-keyboard-rows="!editable" class="form-host" :class="{ 'is-readonly': !editable }">
      <form-create
        v-model="values"
        v-model:api="formApi"
        :option="render.option"
        :rule="render.rule"
        :disabled="!editable"
      />
    </div>
    <div class="form-actions mt-15px">
      <el-button v-if="editable" data-testid="save-requirement-form" :loading="saving" type="primary" @click="save">保存需求分析</el-button>
      <span v-else>{{ detail.revision.state === 'FROZEN' ? '该完成版本正文和附件已冻结，只能查看或对比。' : '当前上下文只读，草稿尚未完成。' }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { Api as FormCreateApi } from '@form-create/element-ui'
import { toRaw } from 'vue'
import { vFormCreateKeyboardRows } from '@/views/pms/project/project-master-detail/components/formCreateKeyboardRows'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis/entity'
import type { View, PresentationOption } from '@/api/pms/engineering/requirement-analysis/entity'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'
import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { collectValueFields, stableCommandIntent, changedOrdinaryValues, reconcileInstancePatch } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'
import formCreateEngine from '@form-create/element-ui'
import RevisionFiles from './RevisionFiles.vue'
import { formValues, businessPatch } from './entityForm'
import { basePresentation, canonicalValues, displayedValues, mergePresentationEdits } from './presentation'
import { registerDynamicFormComponents } from '@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents'


defineOptions({ name: 'RequirementAnalysisEntityForm' })
const props = defineProps<{
  detail: View
  execution?: ProjectBusinessExecutionSelection
  allowedActions?: string[]
  reload?: () => Promise<View>
}>()
const emit = defineEmits<{
  'dirty-change': [dirty: boolean]
  saved: [detail?: View]
}>()
const message = useMessage()
registerDynamicFormComponents()
formCreateEngine.component('RequirementRevisionFiles', RevisionFiles)

const selectedPresentation = ref(props.detail.form ? 'original' : 'base')
const presentationOptions = ref<PresentationOption[]>([])
const presentationError = ref('')
const retainedEdits = ref<JsonObject>({})
const renderedDetail = shallowRef<View>(props.detail)
const withPresentation = (detail: View): View => ({ ...detail, form:
  selectedPresentation.value === 'base' ? basePresentation(detail)
    : selectedPresentation.value === 'original' ? detail.form || basePresentation(detail)
      : presentationOptions.value.find(option => String(option.layout.binding.formRevisionId) === selectedPresentation.value)?.layout || basePresentation(detail)
})
const allEdits = computed(() => mergePresentationEdits(renderedDetail.value, retainedEdits.value,
  changedOrdinaryValues(values.value, baseline.value, ordinaryFields.value)))
const switchPresentation = (selected: string) => {
  if (saving.value) return
  const edits = cloneValues(allEdits.value)
  selectedPresentation.value = selected
  apply(props.detail, edits)
}
let presentationSequence = 0
const loadPresentations = async () => {
  const sequence = ++presentationSequence
  try {
    const result = await RequirementAnalysisApi.presentations(props.detail.revision.ref.revisionId, props.execution)
    if (sequence !== presentationSequence) return
    presentationOptions.value = result.options
    presentationError.value = ''
    if ((result.defaultRevisionId != null || !props.detail.form && selectedPresentation.value === 'base') && !dirty.value) {
      const preferred = result.options.find(option => result.defaultRevisionId != null
        ? String(option.layout.binding.formRevisionId) === String(result.defaultRevisionId)
        : result.defaultTemplateId != null && String(option.layout.templateId) === String(result.defaultTemplateId))
      if (preferred) switchPresentation(String(preferred.layout.binding.formRevisionId))
    }
  } catch {
    if (sequence === presentationSequence) presentationError.value = '展示模板暂时不可用，可继续使用基础业务表单。'
  }
}

const baseline = ref<JsonObject>({})
const values = ref<JsonObject>({})
const ordinaryFields = ref(new Set<string>())
const editorReadonlyDefaults = new Map<string, boolean>()
const render = reactive<{ option: JsonObject; rule: JsonObject[] }>({ option: {}, rule: [] })
const formApi = ref<FormCreateApi>()
const saving = ref(false)
const editable = computed(
  () => props.detail.revision.state === 'DRAFT' && props.detail.allowedActions.includes('PATCH_FORM') &&
    (props.allowedActions === undefined || props.allowedActions.includes('PATCH_FORM'))
)
const pendingKey = computed(
  () => `pms:requirement-entity:patch:${props.detail.revision.ref.revisionId}`
)
const dirty = computed(() => Object.keys(allEdits.value).length > 0)
// Form values are JSON; nested arrays/rows can still be Vue proxies after mapping field keys.
const cloneValues = (source: JsonObject): JsonObject => JSON.parse(JSON.stringify(source))

const readPending = (): JsonObject | undefined => {
  const raw = sessionStorage.getItem(pendingKey.value)
  if (!raw) return undefined
  const stored = JSON.parse(raw) as JsonObject
  return stored.encoding === 'ENTITY_FIELDS' ? stored.values as JsonObject : canonicalValues(props.detail, stored)
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

const apply = (source: View, preserve?: JsonObject) => {
  const detail = withPresentation(source)
  renderedDetail.value = detail
  retainedEdits.value = cloneValues(preserve || {})
  const decoded = detail.form
    ? decodeDynamicForm(JSON.parse(detail.form.formConfJson), JSON.parse(detail.form.formRulesJson))
    : { option: {}, rule: detail.fieldCatalog.map(field => ({ field: field.code, title: field.code,
        type: 'input', props: { type: 'textarea' }, validate: field.required ? [{ required: true, message: '请填写此项' }] : [] })) }
  const fields = collectValueFields(decoded.rule as JsonObject[])
  const visit = (rules: JsonObject[]) => rules.forEach(rule => {
    // Display aliases map to stable business fields; presentation is never the data owner.
    const code = detail.form?.binding.fieldBindings[String(rule.field)] || String(rule.field)
    // SOL checks business completeness on completion; layout validation cannot block draft saves.
    rule.validate = []
    const titles: Record<string, string> = {
      TRANSMISSION_REQUIREMENT: '传输现状说明', TRAFFIC_REQUIREMENT: '流量现状说明',
      BUSINESS_REQUIREMENT: '运行业务情况说明', IP_PLANNING: 'IP资源情况说明',
      REDUNDANCY_REQUIREMENT: '冗余备份要求', SECURITY_PROTECTION: '本机防护要求',
      OPERATIONS_REQUIREMENT: '运维管理要求', LOGGING_REQUIREMENT: '日志留存要求'
    }
    if (titles[code]) rule.title = titles[code]
    if (rule.type === 'Editor') {
      rule.props = { height: '180px', ...((rule.props as JsonObject) || {}) }
    }
    if (rule.type === 'PmsFileArtifact' && typeof rule.field === 'string') {
      const set = detail.attachments.find(item => item.key.purposeCode === `FORM_FIELD_ATTACHMENT/${rule.field}`)
      rule.type = 'RequirementRevisionFiles'
      rule.props = { ...((rule.props as JsonObject) || {}), revisionId: detail.revision.ref.revisionId,
        fieldKey: rule.field, currentFacts: set?.activeFacts || [], allowedActions: editable.value ? ['PATCH_FORM'] : [],
        ownerExecutionContext: props.execution ? structuredClone(toRaw(props.execution)) : undefined }
    }
    if (Array.isArray(rule.children)) visit(rule.children as JsonObject[])
  })
  visit(decoded.rule as JsonObject[])
  baseline.value = cloneValues(formValues(detail))
  values.value = { ...cloneValues(baseline.value), ...displayedValues(detail, preserve || {}) }
  editorReadonlyDefaults.clear()
  updateEditorReadonly(decoded.rule as JsonObject[])
  render.option = { ...decoded.option,
    form: { ...(((decoded.option as JsonObject).form as JsonObject) || {}), labelPosition: 'top' },
    submitBtn: false, resetBtn: false }
  render.rule = decoded.rule as JsonObject[]
  ordinaryFields.value = fields.ordinary
}

const save = async () => {
  const entityId = props.detail.revision.ref.revisionId
  const execution = props.execution ? {
    ...(props.execution.task ? { task: { ...props.execution.task } } : {}),
    ...(props.execution.stage ? { stage: { ...props.execution.stage } } : {})
  } : undefined
  const cacheKey = pendingKey.value
  if (editable.value && !props.reload) {
    message.warning('实体回读接口未配置，不能将本地表单标记为已保存。')
    return false
  }
  if (!editable.value || saving.value) return false
  if (!editable.value || saving.value || entityId !== props.detail.revision.ref.revisionId || !props.reload) return false
  const patch = { values: cloneValues(allEdits.value) }
  if (!Object.keys(patch.values).length) {
    message.info('普通字段没有变化')
    return true
  }
  sessionStorage.setItem(cacheKey, JSON.stringify({ encoding: 'ENTITY_FIELDS', values: patch.values }))
  let payload: ReturnType<typeof businessPatch> & { execution?: typeof execution }
  try {
    payload = { ...businessPatch({ ...props.detail, form: undefined }, patch.values), ...(execution ? { execution } : {}) }
  } catch (error) {
    message.warning(error instanceof Error ? error.message : '表单字段绑定无效，已保留填写内容')
    return false
  }
  const intent = stableCommandIntent(`requirement-save:${entityId}`, { version: props.detail.revision.version, payload })
  saving.value = true
  try {
    await RequirementAnalysisApi.save(props.detail.revision, payload, intent.key)
    if (entityId !== props.detail.revision.ref.revisionId) return false
    const authoritative = await props.reload()
    if (entityId !== props.detail.revision.ref.revisionId || authoritative.revision.ref.revisionId !== entityId) return false
    intent.clear()
    sessionStorage.removeItem(cacheKey)
    apply(authoritative)
    message.success('需求分析实体已保存并重新加载')
    emit('saved', authoritative)
    return true
  } catch {
    if (entityId !== props.detail.revision.ref.revisionId) return false
    try {
      const authoritative = await props.reload()
      if (entityId !== props.detail.revision.ref.revisionId || authoritative.revision.ref.revisionId !== entityId) return false
      const reconciled = reconcileInstancePatch(authoritative.values, patch.values)
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
  retainedEdits.value = {}
  sessionStorage.removeItem(pendingKey.value)
  return true
}

watch(
  // Owner/body version changes reload the document; a new permission projection does not.
  () => [props.detail.revision.ref.revisionId, props.detail.revision.version, props.detail.extensionValueVersion, props.detail.form?.binding.formRevisionId, props.detail.revision.state].join(':'),
  () => apply(props.detail, editable.value ? readPending() : undefined),
  { immediate: true }
)
watch(() => props.detail.revision.ref.revisionId, () => {
  selectedPresentation.value = props.detail.form ? 'original' : 'base'
  presentationOptions.value = []
  apply(props.detail, editable.value ? readPending() : undefined)
  void loadPresentations()
}, { immediate: true })
// PM-03: authorization is independent from document reload. Update controlled-file actions
// in place so neither form-create rules nor ordinary unsaved values are replaced.
watch([editable, () => props.execution], () => {
  updateEditorReadonly(render.rule)
  const visit = (rules: JsonObject[]) => rules.forEach((rule) => {
    if (rule.type === 'RequirementRevisionFiles' && rule.props) {
      const fileProps = rule.props as JsonObject
      fileProps.allowedActions = editable.value ? ['PATCH_FORM'] : []
      fileProps.ownerExecutionContext = props.execution
        ? structuredClone(toRaw(props.execution)) : undefined
    }
    if (Array.isArray(rule.children)) visit(rule.children as JsonObject[])
  })
  visit(render.rule)
}, { deep: true })
watch(dirty, (value) => emit('dirty-change', value), { immediate: true })

defineExpose({ save, discardChanges, isDirty: () => dirty.value, isSaving: () => saving.value, switchPresentation })
</script>

<style scoped lang="scss">
.form-host :deep([role='button']:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.requirement-form-shell,
.form-host {
  min-width: 0;
}

.presentation-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-host {
  width: 100%;
}

.form-host :deep(.el-form-item) { margin-bottom: 24px; }
.form-host :deep(.el-form-item__label) { font-weight: 600; color: var(--el-text-color-primary); }
.form-host :deep(.el-form-item__content > div:has(.w-e-text-container)) { width: 100%; border-color: var(--el-border-color); border-radius: var(--el-border-radius-base); overflow: hidden; }
.form-host.is-readonly :deep(.el-form-item__content > div > div:has(> .w-e-text-container)) { height: auto !important; }
.form-host.is-readonly :deep(.w-e-toolbar) { display: none; }
.form-host.is-readonly :deep(.w-e-text-container) { height: auto !important; min-height: 64px; background: var(--el-fill-color-extra-light); }
.form-host.is-readonly :deep(.w-e-scroll) { overflow: visible; }
.form-host.is-readonly :deep(.el-form-item.is-required > .el-form-item__label::before) { display: none; }
.form-host :deep(.el-checkbox-group) { display: flex; flex-wrap: wrap; gap: 4px 20px; }
.form-host :deep(.el-checkbox) { margin-right: 0; }

@media (width <= 767px) {
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
