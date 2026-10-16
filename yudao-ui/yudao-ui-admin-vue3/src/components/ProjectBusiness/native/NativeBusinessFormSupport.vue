<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="load">重新读取</el-button></el-alert>
  <ProjectBusinessForm v-if="loaded && !nativeForm" ref="form" :api="bridge.api" :current="current" :scope-project-id="context.projectId()"
    :fields="fields" :writable-fields="[]" :initial-values="record" :presentation="presentation" :preferred-form-revision-id="bridge.state.model.value?.formRevisionId" :readonly="readOnly" :disabled="readOnly" />
  <el-button v-if="standaloneSave && presentation?.layout && current && !readOnly && !nativeForm" :loading="saving" @click="saveExtensions">保存扩展信息</el-button>
  <template v-if="current">
    <ProjectBusinessDeliveries v-if="bridge.state.model.value?.features?.attachments" ref="deliveries" :api="bridge.api" :entity-id="current.ref.entityId" :readonly="readOnly || !context.can('delivery', kind)" @changed="context.changed()" />
    <ProjectBusinessApprovals v-if="current.concurrencyBasis != null && bridge.state.model.value?.features?.approvals" ref="approvals" :api="bridge.api" :entity-id="current.ref.entityId"
      :version="record.version ?? current.concurrencyBasis" :fields="bridge.state.model.value?.fields" :readonly="readOnly || !context.can('save', kind)"
      :before-start="approvalReady" @changed="context.changed()" />
  </template>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BusinessEntityData, BusinessEntityFormData } from '@/api/pms/platform/businessmodel'
import ProjectBusinessForm from '../ProjectBusinessForm.vue'
import ProjectBusinessDeliveries from '../ProjectBusinessDeliveries.vue'
import ProjectBusinessApprovals from '../ProjectBusinessApprovals.vue'
import { useNativeBusiness, type NativeValues } from './nativeBusinessContext'

const props = defineProps<{ record: NativeValues; readonly?: boolean; kind?: string; nativeForm?: boolean;
  standaloneSave?: boolean; save?: () => Promise<unknown> }>()
const emit = defineEmits<{ saved: [] }>()
const context = useNativeBusiness()!, bridge = context.getBridge(props.kind)
const form = ref<InstanceType<typeof ProjectBusinessForm>>(), deliveries = ref<InstanceType<typeof ProjectBusinessDeliveries>>()
const approvals = ref<InstanceType<typeof ProjectBusinessApprovals>>()
const current = shallowRef<BusinessEntityData>(), presentation = shallowRef<BusinessEntityFormData>()
const loaded = ref(false), error = ref('')
const saving = ref(false)
const readOnly = computed(() => !!props.readonly || !context.can(props.record.id ? 'save' : 'create', props.kind))
const fields = computed(() => (bridge.state.model.value?.fields || []).map(field => ({ ...field, formVisible: false, tabVisible: false })))
let generation = 0, baseline = ''
const fingerprint = (record = props.record) => {
  const fields = new Map((bridge.state.model.value?.fields || []).map(field => [field.code, field]))
  // Native inputs also contain page-owned values such as trainingTypeList, serials and locationMaintenance.
  return JSON.stringify(Object.fromEntries(Object.entries(record).filter(([code]) =>
    !['id', 'version'].includes(code) && (!fields.has(code) || fields.get(code)!.writable))))
}
const load = async () => {
  const active = ++generation
  const original = JSON.parse(JSON.stringify(props.record))
  loaded.value = false; error.value = ''; current.value = undefined; presentation.value = undefined
  try {
    await bridge.ready()
    if (active !== generation) return
    const features = bridge.state.model.value?.features
    const needsPresentation = !props.nativeForm || features?.attachments === true || features?.approvals === true
    if (props.record.id && needsPresentation) {
      const result = await bridge.readPresentation(props.record.id, props.record.version)
      if (active !== generation) return
      current.value = result.row; presentation.value = result.form
    } else { current.value = undefined; presentation.value = undefined }
    if (active !== generation) return
    baseline = fingerprint(original); loaded.value = true
  } catch (failure) { if (active === generation) error.value = failure instanceof Error ? failure.message : '扩展表单读取失败' }
}
watch(() => props.record.id, load, { immediate: true })
// 原生表单已有自己的正文；仅在启用的附加功能需要实体上下文时读取通用详情。
// 后续配置开启功能时补读，不重建已经挂载的通用编辑表单。
watch(() => props.nativeForm && !!props.record.id && loaded.value && !current.value &&
  (bridge.state.model.value?.features?.attachments === true || bridge.state.model.value?.features?.approvals === true),
  required => { if (required) void load() })
const buildInput = async () => {
  if (readOnly.value || props.nativeForm) return {}
  if (!loaded.value || error.value) throw new Error(error.value || '扩展表单尚未就绪')
  return await form.value!.buildInput()
}
const dirty = async () => !props.nativeForm && (fingerprint() !== baseline || Object.keys(await buildInput()).length > 0)
const requestLeave = async () => {
  if (saving.value || form.value?.isBusy() || deliveries.value?.isBusy() || approvals.value && !await approvals.value.requestLeave()) return false
  if (readOnly.value) return true
  try { if (!await dirty()) return true } catch { /* Invalid input also needs an explicit discard. */ }
  try { await ElMessageBox.confirm('当前修改尚未保存，确认离开？', '未保存的修改', { type: 'warning' }); return true } catch { return false }
}
const approvalReady = async () => {
  try { if (!await dirty()) return true } catch { /* Keep incomplete form values in place. */ }
  ElMessage.warning('请先保存业务内容，再发起审批'); return false
}
const saveExtensions = async () => {
  if (!current.value || readOnly.value || saving.value) return
  saving.value = true
  try {
    const values = await buildInput()
    if (!Object.keys(values).length) { ElMessage.info('没有需要保存的扩展信息'); return }
    if (props.save) await props.save()
    else {
      await bridge.update({ id: current.value.ref.entityId, version: props.record.version ?? current.value.concurrencyBasis, ...values })
      await load()
      emit('saved'); context.changed()
    }
  } catch (failure) { ElMessage.error(failure instanceof Error ? failure.message : '扩展信息保存失败') }
  finally { saving.value = false }
}
const unregister = context.registerForm({ kind: props.kind, buildInput, requestLeave })
onBeforeUnmount(() => { generation++; unregister() })
</script>
