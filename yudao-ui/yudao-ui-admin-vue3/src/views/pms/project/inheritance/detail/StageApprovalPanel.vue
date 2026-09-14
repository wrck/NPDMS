<template>
  <section aria-label="阶段审批办理">
    <p>审批状态：{{ statusLabel }}</p>
    <el-alert v-if="workbench.recoverableError || !workbench.approval || workbench.approval.current.outcome === 'UNKNOWN'"
      title="本轮审批结果暂不可用，请刷新；不会按未发起处理。" type="warning" :closable="false" />
    <div class="approval-actions">
      <el-button v-if="workbench.approval?.current.processInstanceId" :disabled="isBusy()" @click="openDetail">查看／办理审批</el-button>
      <el-button v-if="canStart && !editing" :disabled="disabled" @click="editing = true">填写审批表单</el-button>
    </div>
    <TaskApprovalForm v-if="editing && workbench.approval" ref="formRef" :key="formKey"
      :definition-id="workbench.approval.definitionId" :definition-key="workbench.approval.definitionKey"
      :disabled="disabled || busy || !canStart" :submit-approval="submit" @submitted="handleSubmitted" />
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { checkPermi } from '@/utils/permission'
import { startStageApproval, type StageBusinessContext } from '@/api/pms/project/stage-business'
import type { TaskApprovalSubmission } from '@/api/pms/project/task-workbench'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
import TaskApprovalForm from './TaskApprovalForm.vue'
const props = defineProps<{ workbench: StageBusinessContext; disabled?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const router = useRouter()
const formRef = ref<InstanceType<typeof TaskApprovalForm>>()
const busy = ref(false), editing = ref(false)
const intent = createSubmissionIdempotencyState()
const formKey = computed(() => JSON.stringify([props.workbench.projectId, props.workbench.stageId,
  props.workbench.execution?.planVersionId, props.workbench.execution?.executionId,
  props.workbench.executionContractId, props.workbench.approval?.definitionId, props.workbench.approval?.current.processInstanceId]))
const canStart = computed(() => {
  const w = props.workbench, fact = w.approval?.current
  return w.bindingType === 'APPROVAL' && !w.readonly && !w.recoverableError && w.execution?.writable
    && w.ownerActions.includes('APPROVAL') && fact?.outcome === 'NOT_SATISFIED'
    && ['NOT_STARTED', 'REJECTED', 'CANCELLED'].includes(fact.status) && checkPermi(['pms:project:update'])
})
const statusLabel = computed(() => ({ NOT_STARTED: '未发起', RUNNING: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回', CANCELLED: '已取消' }[props.workbench.approval?.current.status || ''] || '结果未知'))
const isBusy = () => busy.value || !!formRef.value?.isBusy()
const requestLeave = async () => !busy.value && (await formRef.value?.requestLeave()) !== false
const handleSubmitted = () => { editing.value = false; emit('changed') }
const submit = async (approval: TaskApprovalSubmission) => {
  const w = props.workbench
  if (busy.value || props.disabled || !editing.value || !canStart.value || !checkPermi(['pms:project:update']) || !w.execution)
    throw new Error('Stage approval unavailable')
  busy.value = true
  try {
    const result = await startStageApproval(w.projectId, w.stageCode, w.execution, approval,
      intent.keyFor({ formKey: formKey.value, execution: w.execution, approval }))
    if (!result.processInstanceId || result.definitionId !== w.approval?.definitionId || result.outcome === 'UNKNOWN')
      throw new Error('Stage approval not confirmed')
  } finally { busy.value = false }
}
const openDetail = async () => {
  const id = props.workbench.approval?.current.processInstanceId
  if (id && !isBusy()) await router.push({ name: 'BpmProcessInstanceDetail', query: { id } })
}
onBeforeRouteLeave(requestLeave)
defineExpose({ requestLeave, isBusy })
</script>
<style scoped>
.approval-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
</style>
