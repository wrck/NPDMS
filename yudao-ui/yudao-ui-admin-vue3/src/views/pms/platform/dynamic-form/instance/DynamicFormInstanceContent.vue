<template>
  <div v-loading="loading" class="instance-form-shell">
    <div class="instance-header">
      <div class="identity">
        <strong>{{ instance?.instanceName }}</strong>
        <el-tag>{{ instance?.instanceCode }}</el-tag>
        <span>{{ instance?.templateName }}</span>
        <span>冻结修订 {{ instance?.templateRevisionNo ?? '-' }}</span>
        <span>实例版本 {{ instance?.instanceVersion ?? '-' }}</span>
      </div>
      <div class="actions">
        <el-button :disabled="loading || saving" @click="reloadPreservingIntent"
          >刷新权威事实</el-button
        >
        <el-button v-if="canSave" type="primary" :loading="saving" @click="save"
          >保存填写值</el-button
        >
      </div>
    </div>
    <el-alert
      title="冻结修订"
      description="本实例始终按创建时冻结的模板修订渲染；模板之后停用或发布新修订都不会改变本实例。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
    <el-empty v-if="!instance && !loading" description="实例不存在、修订不匹配或无权访问" />
    <div v-if="instance" class="form-host">
      <form-create
        v-model="values"
        v-model:api="formApi"
        :option="render.option"
        :rule="render.rule"
        :disabled="!canSave"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Api as FormCreateApi } from '@form-create/element-ui'
import { onBeforeRouteLeave } from 'vue-router'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import {
  legacyOwnerId,
  sameBusinessViewId,
  type BusinessViewId
} from '@/api/pms/platform/business-view/ids'
import type { DynamicFormInstanceVO, JsonObject } from '@/api/pms/platform/dynamic-form'
import { decodeDynamicForm } from '../components/dynamicFormCodec'
import {
  buildInstanceRuntime,
  changedOrdinaryValues,
  reconcileInstancePatch
} from '../components/dynamicFormRuntime'
import { registerDynamicFormComponents } from '../components/registerDynamicFormComponents'

// PM-03 / F-PLT-002: the drawer and BusinessView use this single manual-instance renderer.
defineOptions({ name: 'DynamicFormInstanceContent' })
const props = defineProps<{
  instanceId: BusinessViewId
  expectedRevisionId?: BusinessViewId
  allowedActions?: string[]
  readonly?: boolean
}>()
const emit = defineEmits<{
  changed: []
  'dirty-change': [value: boolean]
  loaded: [instance: DynamicFormInstanceVO]
}>()
const message = useMessage()
registerDynamicFormComponents()
const instance = ref<DynamicFormInstanceVO>()
const baseline = ref<JsonObject>({})
const values = ref<JsonObject>({})
const ordinaryFields = ref(new Set<string>())
const render = reactive<{ option: JsonObject; rule: JsonObject[] }>({ option: {}, rule: [] })
const formApi = ref<FormCreateApi>()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const effectiveActions = computed(() =>
  props.readonly
    ? []
    : (instance.value?.allowedActions || []).filter(
        (action) => props.allowedActions === undefined || props.allowedActions.includes(action)
      )
)
const canSave = computed(
  () => !loading.value && !errorText.value && effectiveActions.value.includes('PATCH_INSTANCE')
)
const dirty = computed(
  () =>
    Object.keys(changedOrdinaryValues(values.value, baseline.value, ordinaryFields.value)).length >
    0
)
const pendingKey = computed(() => `pms:fplt002:instance-patch:${props.instanceId}`)
const clone = (source: JsonObject) => structuredClone(toRaw(source))
const readPending = (): JsonObject | undefined => {
  const raw = sessionStorage.getItem(pendingKey.value)
  return raw ? JSON.parse(raw) : undefined
}
const buildRender = () => {
  if (!instance.value) return
  const data = instance.value
  const decoded = decodeDynamicForm(data.formConfJson, data.formRulesJson)
  const runtime = buildInstanceRuntime(decoded.rule as JsonObject[], {
    instanceId: data.instanceId,
    templateRevisionId: data.templateRevisionId,
    controlledFiles: data.controlledFiles,
    allowedActions: effectiveActions.value
  })
  render.option = { ...decoded.option, submitBtn: false, resetBtn: false }
  render.rule = runtime.rules
  ordinaryFields.value = runtime.ordinary
}
const apply = (data: DynamicFormInstanceVO, preserve?: JsonObject) => {
  if (
    !sameBusinessViewId(data.instanceId, props.instanceId) ||
    (props.expectedRevisionId !== undefined &&
      !sameBusinessViewId(data.templateRevisionId, props.expectedRevisionId))
  ) {
    instance.value = undefined
    throw new Error('实例与冻结表单修订不匹配，未装载或修改该实例。')
  }
  instance.value = data
  baseline.value = clone(data.values || {})
  values.value = { ...clone(data.values || {}), ...(preserve || {}) }
  buildRender()
  emit('loaded', data)
}
let loadSequence = 0
const load = async (preserve?: JsonObject) => {
  const sequence = ++loadSequence
  loading.value = true
  errorText.value = ''
  try {
    const data = await DynamicFormApi.getInstance(legacyOwnerId(props.instanceId))
    if (sequence === loadSequence) apply(data, preserve)
  } catch (error) {
    if (sequence === loadSequence) {
      instance.value = undefined
      errorText.value = error instanceof Error ? error.message : '实例加载失败，请重试。'
    }
    throw error
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
const reloadPreservingIntent = async () => {
  try {
    await load(
      readPending() || changedOrdinaryValues(values.value, baseline.value, ordinaryFields.value)
    )
  } catch {
    /* visible error; no guessed success */
  }
}
const validate = async () => {
  if (!formApi.value) return true
  try {
    await formApi.value.validate()
    return true
  } catch {
    return false
  }
}
const save = async () => {
  if (!instance.value || !canSave.value || saving.value || !(await validate())) return false
  const intended = changedOrdinaryValues(values.value, baseline.value, ordinaryFields.value)
  if (!Object.keys(intended).length) {
    message.info('普通字段没有变化')
    return true
  }
  sessionStorage.setItem(pendingKey.value, JSON.stringify(intended))
  saving.value = true
  try {
    await DynamicFormApi.patchInstance(instance.value.instanceId, instance.value.instanceVersion, {
      values: intended
    })
    sessionStorage.removeItem(pendingKey.value)
    await load()
    message.success('填写值已保存')
    emit('changed')
    return true
  } catch {
    try {
      const authoritative = await DynamicFormApi.getInstance(legacyOwnerId(props.instanceId))
      const reconciled = reconcileInstancePatch(authoritative.values, intended)
      apply(authoritative, reconciled.committed ? undefined : reconciled.values)
      if (reconciled.committed) {
        sessionStorage.removeItem(pendingKey.value)
        message.success('已确认填写值保存成功')
        emit('changed')
        return true
      }
      message.warning('保存未确认，已刷新实例版本并保留本次填写；请再次保存。')
    } catch {
      errorText.value = '保存结果未知且刷新失败，本次填写意图仍保留；请重试刷新。'
    }
    return false
  } finally {
    saving.value = false
  }
}
const requestLeave = async () => {
  if (saving.value) {
    message.warning('正在保存，请等待结果后再切换。')
    return false
  }
  if (!dirty.value) return true
  try {
    await message.confirm('当前表单尚未保存，是否放弃本地修改并离开？')
    // Confirmation is read-only. Only the caller's still-current transition may discard.
    return !saving.value
  } catch {
    return false
  }
}
const discardChanges = () => {
  if (saving.value) return false
  values.value = clone(baseline.value)
  // Preserve the original sessionStorage retry-intent policy, including unknown writes.
  return true
}
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!dirty.value && !saving.value) return
  event.preventDefault()
  event.returnValue = ''
}
watch(effectiveActions, () => {
  const visit = (rules: JsonObject[]) =>
    rules.forEach((rule) => {
      if (rule.type === 'PmsFileArtifact' && rule.props) {
        ;(rule.props as JsonObject).allowedActions = effectiveActions.value
      }
      if (Array.isArray(rule.children)) visit(rule.children as JsonObject[])
    })
  visit(render.rule)
})
watch(dirty, (value) => emit('dirty-change', value), { immediate: true })
onMounted(() => {
  reloadPreservingIntent()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  ++loadSequence
  window.removeEventListener('beforeunload', beforeUnload)
})
onBeforeRouteLeave(requestLeave)
defineExpose({ requestLeave, discardChanges, isDirty: () => dirty.value, save })
</script>

<style scoped lang="scss">
.instance-form-shell,
.form-host {
  display: grid;
  gap: 14px;
  min-width: 0;
}
.instance-header,
.identity,
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.instance-header {
  justify-content: space-between;
}
.form-host {
  width: min(1080px, 100%);
  margin: 0 auto;
}
@media (width <= 767px) {
  .actions,
  .actions :deep(.el-button) {
    width: 100%;
  }
  .actions {
    display: grid;
    grid-template-columns: 1fr;
  }
  .actions :deep(.el-button + .el-button) {
    margin-left: 0;
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
