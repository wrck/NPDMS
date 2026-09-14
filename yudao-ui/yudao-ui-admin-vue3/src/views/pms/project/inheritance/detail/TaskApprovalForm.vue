<template>
  <section v-loading="loading || submitting" aria-label="审批表单">
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button v-if="ready" link @click="predict">重新读取审批节点</el-button>
    </el-alert>
    <template v-if="ready">
      <fieldset class="approval-form-layout" :disabled="disabled || submitting">
        <form-create v-model:api="formApi" v-model="form.value" :rule="form.rule" :option="form.option" />
        <ProcessInstanceTimeline :activity-nodes="nodes" :show-status-icon="false" @select-user-confirm="selectUsers" />
      </fieldset>
      <el-button type="primary" :loading="submitting" :disabled="disabled || loading || predicting || !!error" @click="submit">提交审批</el-button>
      <el-collapse class="approval-diagram">
        <el-collapse-item title="流程图" name="diagram">
          <ProcessInstanceBpmnViewer v-if="definition?.modelType === BpmModelType.BPMN" :bpmn-xml="definition.bpmnXml" />
          <ProcessInstanceSimpleViewer v-else-if="definition?.modelType === BpmModelType.SIMPLE" :simple-json="definition.simpleModel" />
        </el-collapse-item>
      </el-collapse>
    </template>
  </section>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import formCreate, { type Api, type Options, type Rule } from '@form-create/element-ui'
import { cloneDeep } from 'lodash-es'
import { setConfAndFields2 } from '@/utils/formCreate'
import { BpmModelFormType, BpmModelType } from '@/utils/constants'
import { CandidateStrategy, FieldPermissionType, NodeId } from '@/components/SimpleProcessDesignerV2/src/consts'
import { getProcessDefinition } from '@/api/bpm/definition'
import { getApprovalDetail, type ApprovalNodeInfo, type User } from '@/api/bpm/processInstance'
import type { TaskApprovalSubmission } from '@/api/pms/project/task-workbench'
import ProcessInstanceTimeline from '@/views/bpm/processInstance/detail/ProcessInstanceTimeline.vue'
import ProcessInstanceBpmnViewer from '@/views/bpm/processInstance/detail/ProcessInstanceBpmnViewer.vue'
import ProcessInstanceSimpleViewer from '@/views/bpm/processInstance/detail/ProcessInstanceSimpleViewer.vue'
import { useMessage } from '@/hooks/web/useMessage'

// Reuse the original BPM form-create configuration, timeline/user picker and diagrams.
// Only project execution context and submission ownership differ from ProcessDefinitionDetail.
const props = defineProps<{
  definitionId: string
  definitionKey: string
  disabled?: boolean
  submitApproval: (submission: TaskApprovalSubmission) => Promise<void>
}>()
const emit = defineEmits<{ submitted: [] }>()
const message = useMessage()
const formApi = ref<Api>()
const form = ref<{ rule: Rule[]; option: Options; value: Record<string, unknown> }>({ rule: [], option: {}, value: {} })
const definition = ref<{ modelType: number; bpmnXml: string; simpleModel?: string }>()
const nodes = ref<ApprovalNodeInfo[]>([])
const selected = ref<Record<string, number[]>>({})
const loading = ref(true), ready = ref(false), predicting = ref(false), submitting = ref(false), dirty = ref(false), error = ref('')
let sequence = 0, revision = 0, disposed = false
const validation = new Map<string, { required: Rule['$required']; validate: Rule['validate'] }>()
const applyPermissions = (permissions: Record<string, string>) => {
  const api = formApi.value
  if (!api) return
  for (const [field, permission] of Object.entries(permissions)) {
    const rule = api.getRule(field)
    if (!rule) continue
    if (!validation.has(field)) validation.set(field, { required: rule.$required, validate: cloneDeep(rule.validate) })
    const original = validation.get(field)!
    const writable = permission === FieldPermissionType.WRITE
    api.disabled(!writable, field)
    api.hidden(permission === FieldPermissionType.NONE, field)
    rule.$required = writable ? original.required : false
    rule.validate = writable ? cloneDeep(original.validate) : []
  }
}
const predict = async () => {
  const token = ++sequence
  predicting.value = true; error.value = ''
  try {
    const result = await getApprovalDetail({ processDefinitionId: props.definitionId,
      activityId: NodeId.START_USER_NODE_ID, processVariablesStr: JSON.stringify(form.value.value) })
    if (disposed || token !== sequence) return false
    if (!Array.isArray(result?.activityNodes)) throw new Error('Missing approval prediction')
    nodes.value = result.activityNodes
    const next: Record<string, number[]> = {}
    for (const node of nodes.value.filter(node => node.candidateStrategy === CandidateStrategy.START_USER_SELECT))
      next[String(node.id)] = selected.value[String(node.id)] || []
    selected.value = next
    applyPermissions(result.formFieldsPermission || {})
    return true
  } catch {
    if (!disposed && token === sequence) error.value = '审批节点或字段权限读取失败，请重试；未提交审批。'
    return false
  } finally { if (!disposed && token === sequence) predicting.value = false }
}
const load = async () => {
  try {
    const row = await getProcessDefinition(props.definitionId)
    if (disposed) return
    if (row?.id !== props.definitionId || row?.key !== props.definitionKey || row?.suspensionState !== 1)
      throw new Error('Frozen definition unavailable')
    if (row.formType !== BpmModelFormType.NORMAL) {
      error.value = '该审批使用原模块业务表单，请通过业务页面绑定办理；不能以空表单替代。'; return
    }
    definition.value = row
    setConfAndFields2(form, row.formConf, row.formFields, {})
    form.value.option.submitBtn = false; form.value.option.resetBtn = false
    ready.value = true
    await nextTick()
    await predict()
  } catch { if (!disposed) error.value = '冻结审批定义或表单读取失败，请刷新办理结果后重试。' }
  finally { if (!disposed) { loading.value = false; dirty.value = false } }
}
// Ignore late predictions; watch the values (including clearing the last field), not the form configuration.
// https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup
watch(() => form.value.value, () => {
  ++revision
  if (!loading.value && !submitting.value) { dirty.value = true; void predict() }
}, { deep: true, flush: 'sync' })
const selectUsers = (id: string, users: User[]) => {
  if (submitting.value || props.disabled) return
  selected.value[id] = users.map(user => user.id); dirty.value = true
}
const submit = async () => {
  if (submitting.value || props.disabled || loading.value || predicting.value || !ready.value || !formApi.value) return
  submitting.value = true
  let succeeded = false
  const submittedRevision = revision
  try {
    if (!await predict()) return
    if (await formApi.value.validate() === false) return
    for (const node of nodes.value.filter(node => node.candidateStrategy === CandidateStrategy.START_USER_SELECT)) {
      if (!selected.value[String(node.id)]?.length) { message.warning(`请选择${node.name}的候选人`); return }
    }
    if (disposed || props.disabled) return
    if (submittedRevision !== revision) { error.value = '表单在校验期间发生变化，请重新读取审批节点后提交。'; return }
    await props.submitApproval({ variables: cloneDeep(form.value.value), selectedApprovers: cloneDeep(selected.value) })
    dirty.value = false
    succeeded = true
  } catch { if (!disposed) error.value = '审批未提交成功，请核对表单、权限和当前办理结果后重试。' }
  finally { submitting.value = false }
  if (succeeded && !disposed) emit('submitted')
}
const requestLeave = async () => {
  if (submitting.value) return false
  if (!dirty.value) return true
  try { await message.confirm('审批表单尚未提交，确认离开并放弃本次填写？'); return true }
  catch { return false }
}
onMounted(load)
onBeforeUnmount(() => { disposed = true; ++sequence })
defineExpose({ requestLeave, isBusy: () => submitting.value })
</script>
<style scoped>
.approval-form-layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 16px; border: 0; padding: 0; margin: 0 0 16px; min-width: 0; }
.approval-diagram { margin-top: 16px; }
@media (width <= 767px) { .approval-form-layout { grid-template-columns: minmax(0, 1fr); } }
</style>
