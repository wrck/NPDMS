<template>
  <el-drawer v-model="visible" size="100%" :title="title" destroy-on-close>
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
          <el-button @click="reloadPreservingIntent">刷新权威事实</el-button>
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
      <el-empty v-if="!instance && !loading" description="实例不存在或无权访问" />
      <div v-else class="form-host">
        <form-create
          v-model="values"
          v-model:api="formApi"
          :option="render.option"
          :rule="render.rule"
          :disabled="!canSave"
        />
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import type { Api as FormCreateApi } from '@form-create/element-ui'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import type { DynamicFormInstanceVO, JsonObject } from '@/api/pms/platform/dynamic-form'
import { decodeDynamicForm } from '../components/dynamicFormCodec'
import {
  buildInstanceRuntime,
  changedOrdinaryValues,
  reconcileInstancePatch
} from '../components/dynamicFormRuntime'
import { registerDynamicFormComponents } from '../components/registerDynamicFormComponents'

defineOptions({ name: 'DynamicFormInstanceForm' })
const props = defineProps<{ modelValue: boolean; instanceId?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const message = useMessage()
registerDynamicFormComponents()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const instance = ref<DynamicFormInstanceVO>()
const baseline = ref<JsonObject>({})
const values = ref<JsonObject>({})
const ordinaryFields = ref(new Set<string>())
const render = reactive<{ option: JsonObject; rule: JsonObject[] }>({ option: {}, rule: [] })
const formApi = ref<FormCreateApi>()
const loading = ref(false)
const saving = ref(false)
const title = computed(() =>
  instance.value ? `填写动态表单：${instance.value.instanceName}` : '动态表单实例'
)
const canSave = computed(() => instance.value?.allowedActions.includes('PATCH_INSTANCE') ?? false)
const pendingKey = computed(() =>
  props.instanceId ? `pms:fplt002:instance-patch:${props.instanceId}` : ''
)

const readPending = (): JsonObject | undefined => {
  if (!pendingKey.value) return
  const raw = sessionStorage.getItem(pendingKey.value)
  return raw ? JSON.parse(raw) : undefined
}

const apply = (data: DynamicFormInstanceVO, preserve?: JsonObject) => {
  instance.value = data
  baseline.value = structuredClone(data.values || {})
  values.value = { ...structuredClone(data.values || {}), ...(preserve || {}) }
  const decoded = decodeDynamicForm(data.formConfJson, data.formRulesJson)
  const runtime = buildInstanceRuntime(decoded.rule as JsonObject[], {
    instanceId: data.instanceId,
    templateRevisionId: data.templateRevisionId,
    controlledFiles: data.controlledFiles,
    allowedActions: data.allowedActions
  })
  render.option = { ...decoded.option, submitBtn: false, resetBtn: false }
  render.rule = runtime.rules
  ordinaryFields.value = runtime.ordinary
}

const load = async (preserve?: JsonObject) => {
  if (!props.instanceId) return
  loading.value = true
  try {
    apply(await DynamicFormApi.getInstance(props.instanceId), preserve)
  } finally {
    loading.value = false
  }
}

const reloadPreservingIntent = () =>
  load(readPending() || changedOrdinaryValues(values.value, baseline.value, ordinaryFields.value))

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
  if (!instance.value || !(await validate())) return
  const intended = changedOrdinaryValues(values.value, baseline.value, ordinaryFields.value)
  if (!Object.keys(intended).length) return message.info('普通字段没有变化')
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
  } catch (error) {
    const authoritative = await DynamicFormApi.getInstance(instance.value.instanceId)
    const reconciled = reconcileInstancePatch(authoritative.values, intended)
    if (reconciled.committed) {
      sessionStorage.removeItem(pendingKey.value)
      apply(authoritative)
      message.success('已确认填写值保存成功')
      emit('changed')
      return
    }
    apply(authoritative, reconciled.values)
    message.warning('保存结果未知，已刷新实例版本并保留本次填写；请再次保存。')
    throw error
  } finally {
    saving.value = false
  }
}

watch(
  () => [visible.value, props.instanceId],
  ([open]) => {
    if (open) load(readPending())
  },
  { immediate: true }
)
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
